package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.text.NumberFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;

public class CustomNumberEditor extends PropertyEditorSupport {
   private final Class<? extends Number> numberClass;
   private final @Nullable NumberFormat numberFormat;
   private final boolean allowEmpty;

   public CustomNumberEditor(Class<? extends Number> numberClass, boolean allowEmpty) throws IllegalArgumentException {
      this(numberClass, null, allowEmpty);
   }

   public CustomNumberEditor(Class<? extends Number> numberClass, @Nullable NumberFormat numberFormat, boolean allowEmpty) throws IllegalArgumentException {
      if (!Number.class.isAssignableFrom(numberClass)) {
         throw new IllegalArgumentException("Property class must be a subclass of Number");
      }

      this.numberClass = numberClass;
      this.numberFormat = numberFormat;
      this.allowEmpty = allowEmpty;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      if (this.allowEmpty && !StringUtils.hasText(text)) {
         this.setValue(null);
      } else if (this.numberFormat != null) {
         this.setValue(NumberUtils.parseNumber(text, this.numberClass, this.numberFormat));
      } else {
         this.setValue(NumberUtils.parseNumber(text, this.numberClass));
      }
   }

   @Override
   public void setValue(@Nullable Object value) {
      if (value instanceof Number num) {
         super.setValue(NumberUtils.convertNumberToTargetClass(num, this.numberClass));
      } else {
         super.setValue(value);
      }
   }

   @Override
   public String getAsText() {
      Object value = this.getValue();
      if (value == null) {
         return "";
      } else {
         return this.numberFormat != null ? this.numberFormat.format(value) : value.toString();
      }
   }
}
