package org.springframework.core.log;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public abstract class LogMessage implements CharSequence {
   private @Nullable String result;

   @Override
   public int length() {
      return this.toString().length();
   }

   @Override
   public char charAt(int index) {
      return this.toString().charAt(index);
   }

   @Override
   public CharSequence subSequence(int start, int end) {
      return this.toString().subSequence(start, end);
   }

   @Override
   public String toString() {
      if (this.result == null) {
         this.result = this.buildString();
      }

      return this.result;
   }

   abstract String buildString();

   public static LogMessage of(Supplier<? extends CharSequence> supplier) {
      return new LogMessage.SupplierMessage(supplier);
   }

   public static LogMessage format(String format, @Nullable Object arg1) {
      return new LogMessage.FormatMessage1(format, arg1);
   }

   public static LogMessage format(String format, @Nullable Object arg1, @Nullable Object arg2) {
      return new LogMessage.FormatMessage2(format, arg1, arg2);
   }

   public static LogMessage format(String format, @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3) {
      return new LogMessage.FormatMessage3(format, arg1, arg2, arg3);
   }

   public static LogMessage format(String format, @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3, @Nullable Object arg4) {
      return new LogMessage.FormatMessage4(format, arg1, arg2, arg3, arg4);
   }

   public static LogMessage format(String format, @Nullable Object... args) {
      return new LogMessage.FormatMessageX(format, args);
   }

   private abstract static class FormatMessage extends LogMessage {
      protected final String format;

      FormatMessage(String format) {
         Assert.notNull(format, "Format must not be null");
         this.format = format;
      }
   }

   private static final class FormatMessage1 extends LogMessage.FormatMessage {
      private final @Nullable Object arg1;

      FormatMessage1(String format, @Nullable Object arg1) {
         super(format);
         this.arg1 = arg1;
      }

      @Override
      protected String buildString() {
         return String.format(this.format, this.arg1);
      }
   }

   private static final class FormatMessage2 extends LogMessage.FormatMessage {
      private final @Nullable Object arg1;
      private final @Nullable Object arg2;

      FormatMessage2(String format, @Nullable Object arg1, @Nullable Object arg2) {
         super(format);
         this.arg1 = arg1;
         this.arg2 = arg2;
      }

      @Override
      String buildString() {
         return String.format(this.format, this.arg1, this.arg2);
      }
   }

   private static final class FormatMessage3 extends LogMessage.FormatMessage {
      private final @Nullable Object arg1;
      private final @Nullable Object arg2;
      private final @Nullable Object arg3;

      FormatMessage3(String format, @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3) {
         super(format);
         this.arg1 = arg1;
         this.arg2 = arg2;
         this.arg3 = arg3;
      }

      @Override
      String buildString() {
         return String.format(this.format, this.arg1, this.arg2, this.arg3);
      }
   }

   private static final class FormatMessage4 extends LogMessage.FormatMessage {
      private final @Nullable Object arg1;
      private final @Nullable Object arg2;
      private final @Nullable Object arg3;
      private final @Nullable Object arg4;

      FormatMessage4(String format, @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3, @Nullable Object arg4) {
         super(format);
         this.arg1 = arg1;
         this.arg2 = arg2;
         this.arg3 = arg3;
         this.arg4 = arg4;
      }

      @Override
      String buildString() {
         return String.format(this.format, this.arg1, this.arg2, this.arg3, this.arg4);
      }
   }

   private static final class FormatMessageX extends LogMessage.FormatMessage {
      private final @Nullable Object[] args;

      FormatMessageX(String format, @Nullable Object... args) {
         super(format);
         this.args = args;
      }

      @Override
      String buildString() {
         return String.format(this.format, this.args);
      }
   }

   private static final class SupplierMessage extends LogMessage {
      private final Supplier<? extends CharSequence> supplier;

      SupplierMessage(Supplier<? extends CharSequence> supplier) {
         Assert.notNull(supplier, "Supplier must not be null");
         this.supplier = supplier;
      }

      @Override
      String buildString() {
         return this.supplier.get().toString();
      }
   }
}
