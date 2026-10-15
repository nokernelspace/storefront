package org.springframework.boot.logging;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class StandardStackTracePrinter implements StackTracePrinter {
   private static final String DEFAULT_LINE_SEPARATOR = System.lineSeparator();
   private static final ToIntFunction<StackTraceElement> DEFAULT_FRAME_HASHER = frame -> Objects.hash(
      frame.getClassName(), frame.getMethodName(), frame.getLineNumber()
   );
   private static final int UNLIMITED = Integer.MAX_VALUE;
   private final EnumSet<StandardStackTracePrinter.Option> options;
   private final int maximumLength;
   private final String lineSeparator;
   private final Predicate<Throwable> filter;
   private final BiPredicate<Integer, StackTraceElement> frameFilter;
   private final Function<Throwable, String> formatter;
   private final Function<StackTraceElement, String> frameFormatter;
   private final @Nullable ToIntFunction<StackTraceElement> frameHasher;

   private StandardStackTracePrinter(
      EnumSet<StandardStackTracePrinter.Option> options,
      int maximumLength,
      @Nullable String lineSeparator,
      @Nullable Predicate<Throwable> filter,
      @Nullable BiPredicate<Integer, StackTraceElement> frameFilter,
      @Nullable Function<Throwable, String> formatter,
      @Nullable Function<StackTraceElement, String> frameFormatter,
      @Nullable ToIntFunction<StackTraceElement> frameHasher
   ) {
      this.options = options;
      this.maximumLength = maximumLength;
      this.lineSeparator = lineSeparator != null ? lineSeparator : DEFAULT_LINE_SEPARATOR;
      this.filter = filter != null ? filter : t -> true;
      this.frameFilter = frameFilter != null ? frameFilter : (i, t) -> true;
      this.formatter = formatter != null ? formatter : Object::toString;
      this.frameFormatter = frameFormatter != null ? frameFormatter : Object::toString;
      this.frameHasher = frameHasher;
   }

   @Override
   public void printStackTrace(Throwable throwable, Appendable out) throws IOException {
      if (this.filter.test(throwable)) {
         Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
         StandardStackTracePrinter.Output output = new StandardStackTracePrinter.Output(out);
         StandardStackTracePrinter.Print print = new StandardStackTracePrinter.Print("", "", output);
         this.printFullStackTrace(seen, print, new StandardStackTracePrinter.StackTrace(throwable), null);
      }
   }

   private void printFullStackTrace(
      Set<Throwable> seen,
      StandardStackTracePrinter.Print print,
      StandardStackTracePrinter.@Nullable StackTrace stackTrace,
      StandardStackTracePrinter.@Nullable StackTrace enclosing
   ) throws IOException {
      if (stackTrace != null) {
         if (!seen.add(stackTrace.throwable())) {
            String hashPrefix = stackTrace.hashPrefix(this.frameHasher);
            String throwable = this.formatter.apply(stackTrace.throwable());
            print.circularReference(hashPrefix, throwable);
         } else {
            StandardStackTracePrinter.StackTrace cause = stackTrace.cause();
            if (!this.hasOption(StandardStackTracePrinter.Option.ROOT_FIRST)) {
               this.printSingleStackTrace(seen, print, stackTrace, enclosing);
               this.printFullStackTrace(seen, print.withCausedByCaption(cause), cause, stackTrace);
            } else {
               this.printFullStackTrace(seen, print, cause, stackTrace);
               this.printSingleStackTrace(seen, print.withWrappedByCaption(cause), stackTrace, enclosing);
            }
         }
      }
   }

   private void printSingleStackTrace(
      Set<Throwable> seen,
      StandardStackTracePrinter.Print print,
      StandardStackTracePrinter.StackTrace stackTrace,
      StandardStackTracePrinter.@Nullable StackTrace enclosing
   ) throws IOException {
      String hashPrefix = stackTrace.hashPrefix(this.frameHasher);
      String throwable = this.formatter.apply(stackTrace.throwable());
      print.thrown(hashPrefix, throwable);
      this.printFrames(print, stackTrace, enclosing);
      if (!this.hasOption(StandardStackTracePrinter.Option.HIDE_SUPPRESSED)) {
         StandardStackTracePrinter.StackTrace[] suppressed = stackTrace.suppressed();
         if (suppressed != null) {
            for (StandardStackTracePrinter.StackTrace suppressedStackTrace : suppressed) {
               this.printFullStackTrace(seen, print.withSuppressedCaption(), suppressedStackTrace, stackTrace);
            }
         }
      }
   }

   private void printFrames(
      StandardStackTracePrinter.Print print, StandardStackTracePrinter.StackTrace stackTrace, StandardStackTracePrinter.@Nullable StackTrace enclosing
   ) throws IOException {
      int commonFrames = !this.hasOption(StandardStackTracePrinter.Option.SHOW_COMMON_FRAMES) ? stackTrace.commonFramesCount(enclosing) : 0;
      int filteredFrames = 0;
      StackTraceElement[] frames = stackTrace.frames();
      if (frames != null) {
         for (int i = 0; i < frames.length - commonFrames; i++) {
            StackTraceElement element = frames[i];
            if (!this.frameFilter.test(i, element)) {
               filteredFrames++;
            } else {
               print.omittedFilteredFrames(filteredFrames);
               filteredFrames = 0;
               print.at(this.frameFormatter.apply(element));
            }
         }
      }

      print.omittedFilteredFrames(filteredFrames);
      if (commonFrames != 0) {
         print.omittedCommonFrames(commonFrames);
      }
   }

   public StandardStackTracePrinter withCommonFrames() {
      return this.withOption(StandardStackTracePrinter.Option.SHOW_COMMON_FRAMES);
   }

   public StandardStackTracePrinter withoutSuppressed() {
      return this.withOption(StandardStackTracePrinter.Option.HIDE_SUPPRESSED);
   }

   public StandardStackTracePrinter withMaximumLength(int maximumLength) {
      Assert.isTrue(maximumLength > 0, "'maximumLength' must be positive");
      return new StandardStackTracePrinter(
         this.options, maximumLength, this.lineSeparator, this.filter, this.frameFilter, this.formatter, this.frameFormatter, this.frameHasher
      );
   }

   public StandardStackTracePrinter withMaximumThrowableDepth(int maximumThrowableDepth) {
      Assert.isTrue(maximumThrowableDepth > 0, "'maximumThrowableDepth' must be positive");
      return this.withFrameFilter((index, element) -> index < maximumThrowableDepth);
   }

   public StandardStackTracePrinter withFilter(Predicate<Throwable> predicate) {
      Assert.notNull(predicate, "'predicate' must not be null");
      return new StandardStackTracePrinter(
         this.options,
         this.maximumLength,
         this.lineSeparator,
         this.filter.and(predicate),
         this.frameFilter,
         this.formatter,
         this.frameFormatter,
         this.frameHasher
      );
   }

   public StandardStackTracePrinter withFrameFilter(BiPredicate<Integer, StackTraceElement> predicate) {
      Assert.notNull(predicate, "'predicate' must not be null");
      return new StandardStackTracePrinter(
         this.options,
         this.maximumLength,
         this.lineSeparator,
         this.filter,
         this.frameFilter.and(predicate),
         this.formatter,
         this.frameFormatter,
         this.frameHasher
      );
   }

   public StandardStackTracePrinter withLineSeparator(String lineSeparator) {
      Assert.notNull(lineSeparator, "'lineSeparator' must not be null");
      return new StandardStackTracePrinter(
         this.options, this.maximumLength, lineSeparator, this.filter, this.frameFilter, this.formatter, this.frameFormatter, this.frameHasher
      );
   }

   public StandardStackTracePrinter withFormatter(Function<Throwable, String> formatter) {
      Assert.notNull(formatter, "'formatter' must not be null");
      return new StandardStackTracePrinter(
         this.options, this.maximumLength, this.lineSeparator, this.filter, this.frameFilter, formatter, this.frameFormatter, this.frameHasher
      );
   }

   public StandardStackTracePrinter withFrameFormatter(Function<StackTraceElement, String> frameFormatter) {
      Assert.notNull(frameFormatter, "'frameFormatter' must not be null");
      return new StandardStackTracePrinter(
         this.options, this.maximumLength, this.lineSeparator, this.filter, this.frameFilter, this.formatter, frameFormatter, this.frameHasher
      );
   }

   public StandardStackTracePrinter withHashes() {
      return this.withHashes(true);
   }

   public StandardStackTracePrinter withHashes(boolean hashes) {
      return this.withHashes(!hashes ? null : DEFAULT_FRAME_HASHER);
   }

   public StandardStackTracePrinter withHashes(@Nullable ToIntFunction<StackTraceElement> frameHasher) {
      return new StandardStackTracePrinter(
         this.options, this.maximumLength, this.lineSeparator, this.filter, this.frameFilter, this.formatter, this.frameFormatter, frameHasher
      );
   }

   private StandardStackTracePrinter withOption(StandardStackTracePrinter.Option option) {
      EnumSet<StandardStackTracePrinter.Option> options = EnumSet.copyOf(this.options);
      options.add(option);
      return new StandardStackTracePrinter(
         options, this.maximumLength, this.lineSeparator, this.filter, this.frameFilter, this.formatter, this.frameFormatter, this.frameHasher
      );
   }

   private boolean hasOption(StandardStackTracePrinter.Option option) {
      return this.options.contains(option);
   }

   public static StandardStackTracePrinter rootLast() {
      return new StandardStackTracePrinter(EnumSet.noneOf(StandardStackTracePrinter.Option.class), Integer.MAX_VALUE, null, null, null, null, null, null);
   }

   public static StandardStackTracePrinter rootFirst() {
      return new StandardStackTracePrinter(EnumSet.of(StandardStackTracePrinter.Option.ROOT_FIRST), Integer.MAX_VALUE, null, null, null, null, null, null);
   }

   private enum Option {
      ROOT_FIRST,
      SHOW_COMMON_FRAMES,
      HIDE_SUPPRESSED;
   }

   private class Output {
      private static final String ELLIPSIS = "...";
      private final Appendable out;
      private int remaining;

      Output(Appendable out) {
         this.out = out;
         this.remaining = StandardStackTracePrinter.this.maximumLength - "...".length();
      }

      void println(String indent, String string) throws IOException {
         if (this.remaining > 0) {
            String line = indent + string + StandardStackTracePrinter.this.lineSeparator;
            if (line.length() > this.remaining) {
               line = line.substring(0, this.remaining) + "...";
            }

            this.out.append(line);
            this.remaining = this.remaining - line.length();
         }
      }
   }

   private record Print(String indent, String caption, StandardStackTracePrinter.Output output) {
      void circularReference(String hashPrefix, String throwable) throws IOException {
         this.output.println(this.indent, this.caption + "[CIRCULAR REFERENCE: " + hashPrefix + throwable + "]");
      }

      void thrown(String hashPrefix, String throwable) throws IOException {
         this.output.println(this.indent, this.caption + hashPrefix + throwable);
      }

      void at(String frame) throws IOException {
         this.output.println(this.indent, "\tat " + frame);
      }

      void omittedFilteredFrames(int filteredFrameCount) throws IOException {
         if (filteredFrameCount > 0) {
            this.output.println(this.indent, "\t... " + filteredFrameCount + " filtered");
         }
      }

      void omittedCommonFrames(int commonFrameCount) throws IOException {
         this.output.println(this.indent, "\t... " + commonFrameCount + " more");
      }

      StandardStackTracePrinter.Print withCausedByCaption(StandardStackTracePrinter.@Nullable StackTrace causedBy) {
         return this.withCaption(causedBy != null, "", "Caused by: ");
      }

      StandardStackTracePrinter.Print withWrappedByCaption(StandardStackTracePrinter.@Nullable StackTrace wrappedBy) {
         return this.withCaption(wrappedBy != null, "", "Wrapped by: ");
      }

      public StandardStackTracePrinter.Print withSuppressedCaption() {
         return this.withCaption(true, "\t", "Suppressed: ");
      }

      private StandardStackTracePrinter.Print withCaption(boolean test, String extraIndent, String caption) {
         return test ? new StandardStackTracePrinter.Print(this.indent + extraIndent, caption, this.output) : this;
      }
   }

   private static final class StackTrace {
      private final Throwable throwable;
      private final StackTraceElement @Nullable [] frames;
      private StandardStackTracePrinter.StackTrace @Nullable [] suppressed;
      private StandardStackTracePrinter.@Nullable StackTrace cause;
      private @Nullable Integer hash;
      private @Nullable String hashPrefix;

      private StackTrace(Throwable throwable) {
         this.throwable = throwable;
         this.frames = throwable != null ? throwable.getStackTrace() : null;
      }

      Throwable throwable() {
         return this.throwable;
      }

      StackTraceElement @Nullable [] frames() {
         return this.frames;
      }

      int commonFramesCount(StandardStackTracePrinter.@Nullable StackTrace other) {
         if (other != null && this.frames != null && other.frames != null) {
            int index = this.frames.length - 1;

            for (int otherIndex = other.frames.length - 1; index >= 0 && otherIndex >= 0 && this.frames[index].equals(other.frames[otherIndex]); otherIndex--) {
               index--;
            }

            return this.frames.length - 1 - index;
         } else {
            return 0;
         }
      }

      StandardStackTracePrinter.StackTrace @Nullable [] suppressed() {
         if (this.suppressed == null && this.throwable != null) {
            this.suppressed = Arrays.stream(this.throwable.getSuppressed())
               .map(StandardStackTracePrinter.StackTrace::new)
               .toArray(StandardStackTracePrinter.StackTrace[]::new);
         }

         return this.suppressed;
      }

      StandardStackTracePrinter.@Nullable StackTrace cause() {
         if (this.cause == null && this.throwable != null) {
            Throwable cause = this.throwable.getCause();
            this.cause = cause != null ? new StandardStackTracePrinter.StackTrace(cause) : null;
         }

         return this.cause;
      }

      String hashPrefix(@Nullable ToIntFunction<StackTraceElement> frameHasher) {
         if (frameHasher != null && this.throwable() != null) {
            this.hashPrefix = this.hashPrefix != null ? this.hashPrefix : String.format("<#%08x> ", this.hash(new HashSet<>(), frameHasher));
            return this.hashPrefix;
         } else {
            return "";
         }
      }

      private int hash(HashSet<Throwable> seen, ToIntFunction<StackTraceElement> frameHasher) {
         if (this.hash != null) {
            return this.hash;
         }

         int hash = 0;
         StandardStackTracePrinter.StackTrace cause = this.cause();
         if (cause != null && seen.add(cause.throwable())) {
            hash = cause.hash(seen, frameHasher);
         }

         hash = 31 * hash + this.throwable().getClass().getName().hashCode();
         if (this.frames() != null) {
            for (StackTraceElement frame : this.frames()) {
               hash = 31 * hash + frameHasher.applyAsInt(frame);
            }
         }

         this.hash = hash;
         return hash;
      }
   }
}
