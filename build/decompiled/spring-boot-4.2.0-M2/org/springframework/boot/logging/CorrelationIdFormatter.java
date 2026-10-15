package org.springframework.boot.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public final class CorrelationIdFormatter {
   public static final CorrelationIdFormatter DEFAULT = of("traceId(32),spanId(16)");
   private final List<CorrelationIdFormatter.Part> parts;
   private final String blank;

   private CorrelationIdFormatter(List<CorrelationIdFormatter.Part> parts) {
      this.parts = parts;
      this.blank = String.format("[%s] ", parts.stream().map(CorrelationIdFormatter.Part::blank).collect(Collectors.joining(" ")));
   }

   public String format(UnaryOperator<@Nullable String> resolver) {
      StringBuilder result = new StringBuilder(this.blank.length());
      this.formatTo(resolver, result);
      return result.toString();
   }

   public void formatTo(UnaryOperator<@Nullable String> resolver, Appendable appendable) {
      Predicate<CorrelationIdFormatter.Part> canResolve = part -> StringUtils.hasLength(resolver.apply(part.name()));

      try {
         if (this.parts.stream().anyMatch(canResolve)) {
            appendable.append('[');
            Iterator<CorrelationIdFormatter.Part> iterator = this.parts.iterator();

            while (iterator.hasNext()) {
               appendable.append(iterator.next().resolve(resolver));
               if (iterator.hasNext()) {
                  appendable.append('-');
               }
            }

            appendable.append("] ");
         } else {
            appendable.append(this.blank);
         }
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   @Override
   public String toString() {
      return this.parts.stream().map(CorrelationIdFormatter.Part::toString).collect(Collectors.joining(","));
   }

   public static CorrelationIdFormatter of(@Nullable String spec) {
      try {
         return !StringUtils.hasText(spec) ? DEFAULT : of(List.of(spec.split(",")));
      } catch (Exception ex) {
         throw new IllegalStateException("Unable to parse correlation formatter spec '%s'".formatted(spec), ex);
      }
   }

   public static CorrelationIdFormatter of(String @Nullable [] spec) {
      return of(spec != null ? List.of(spec) : Collections.emptyList());
   }

   public static CorrelationIdFormatter of(Collection<String> spec) {
      if (CollectionUtils.isEmpty(spec)) {
         return DEFAULT;
      }

      List<CorrelationIdFormatter.Part> parts = spec.stream().map(CorrelationIdFormatter.Part::of).toList();
      return new CorrelationIdFormatter(parts);
   }

   record Part(String name, int length) {
      private static final Pattern pattern = Pattern.compile("^(.+?)\\((\\d+)\\)$");

      String resolve(UnaryOperator<@Nullable String> resolver) {
         String resolved = resolver.apply(this.name());
         if (resolved == null) {
            return this.blank();
         }

         int padding = this.length() - resolved.length();
         return padding <= 0 ? resolved : resolved + " ".repeat(padding);
      }

      String blank() {
         return " ".repeat(this.length);
      }

      @Override
      public String toString() {
         return "%s(%s)".formatted(this.name(), this.length());
      }

      static CorrelationIdFormatter.Part of(String part) {
         Matcher matcher = pattern.matcher(part.trim());
         Assert.state(matcher.matches(), () -> "Invalid specification part '%s'".formatted(part));
         String name = matcher.group(1);
         int length = Integer.parseInt(matcher.group(2));
         return new CorrelationIdFormatter.Part(name, length);
      }
   }
}
