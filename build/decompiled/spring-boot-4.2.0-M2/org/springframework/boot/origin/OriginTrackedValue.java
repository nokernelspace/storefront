package org.springframework.boot.origin;

import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.ObjectUtils;

public class OriginTrackedValue implements OriginProvider {
   private final Object value;
   private final @Nullable Origin origin;

   private OriginTrackedValue(Object value, @Nullable Origin origin) {
      this.value = value;
      this.origin = origin;
   }

   public Object getValue() {
      return this.value;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   @Override
   public boolean equals(Object obj) {
      return obj != null && obj.getClass() == this.getClass() ? ObjectUtils.nullSafeEquals(this.value, ((OriginTrackedValue)obj).value) : false;
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHashCode(this.value);
   }

   @Override
   public @Nullable String toString() {
      return this.value != null ? this.value.toString() : null;
   }

   @Contract("!null -> !null")
   public static @Nullable OriginTrackedValue of(@Nullable Object value) {
      return of(value, null);
   }

   @Contract("null, _ -> null; !null, _ -> !null")
   public static @Nullable OriginTrackedValue of(@Nullable Object value, @Nullable Origin origin) {
      if (value == null) {
         return null;
      } else {
         return value instanceof CharSequence charSequence
            ? new OriginTrackedValue.OriginTrackedCharSequence(charSequence, origin)
            : new OriginTrackedValue(value, origin);
      }
   }

   private static class OriginTrackedCharSequence extends OriginTrackedValue implements CharSequence {
      OriginTrackedCharSequence(CharSequence value, @Nullable Origin origin) {
         super(value, origin);
      }

      @Override
      public int length() {
         return this.getValue().length();
      }

      @Override
      public char charAt(int index) {
         return this.getValue().charAt(index);
      }

      @Override
      public CharSequence subSequence(int start, int end) {
         return this.getValue().subSequence(start, end);
      }

      public CharSequence getValue() {
         return (CharSequence)super.getValue();
      }
   }
}
