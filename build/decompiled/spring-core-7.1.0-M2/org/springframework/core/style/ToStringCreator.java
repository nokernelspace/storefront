package org.springframework.core.style;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class ToStringCreator {
   private static final ToStringStyler DEFAULT_TO_STRING_STYLER = new DefaultToStringStyler(StylerUtils.DEFAULT_VALUE_STYLER);
   private final StringBuilder buffer = new StringBuilder(256);
   private final ToStringStyler styler;
   private final Object object;
   private boolean styledFirstField;

   public ToStringCreator(Object obj) {
      this(obj, (ToStringStyler)null);
   }

   public ToStringCreator(Object obj, @Nullable ValueStyler styler) {
      this(obj, new DefaultToStringStyler(styler != null ? styler : StylerUtils.DEFAULT_VALUE_STYLER));
   }

   public ToStringCreator(Object obj, @Nullable ToStringStyler styler) {
      Assert.notNull(obj, "The object to be styled must not be null");
      this.object = obj;
      this.styler = styler != null ? styler : DEFAULT_TO_STRING_STYLER;
      this.styler.styleStart(this.buffer, this.object);
   }

   public ToStringCreator append(String fieldName, byte value) {
      return this.append(fieldName, Byte.valueOf(value));
   }

   public ToStringCreator append(String fieldName, short value) {
      return this.append(fieldName, Short.valueOf(value));
   }

   public ToStringCreator append(String fieldName, int value) {
      return this.append(fieldName, Integer.valueOf(value));
   }

   public ToStringCreator append(String fieldName, long value) {
      return this.append(fieldName, Long.valueOf(value));
   }

   public ToStringCreator append(String fieldName, float value) {
      return this.append(fieldName, Float.valueOf(value));
   }

   public ToStringCreator append(String fieldName, double value) {
      return this.append(fieldName, Double.valueOf(value));
   }

   public ToStringCreator append(String fieldName, boolean value) {
      return this.append(fieldName, Boolean.valueOf(value));
   }

   public ToStringCreator append(String fieldName, @Nullable Object value) {
      this.printFieldSeparatorIfNecessary();
      this.styler.styleField(this.buffer, fieldName, value);
      return this;
   }

   private void printFieldSeparatorIfNecessary() {
      if (this.styledFirstField) {
         this.styler.styleFieldSeparator(this.buffer);
      } else {
         this.styledFirstField = true;
      }
   }

   public ToStringCreator append(Object value) {
      this.styler.styleValue(this.buffer, value);
      return this;
   }

   @Override
   public String toString() {
      this.styler.styleEnd(this.buffer, this.object);
      return this.buffer.toString();
   }
}
