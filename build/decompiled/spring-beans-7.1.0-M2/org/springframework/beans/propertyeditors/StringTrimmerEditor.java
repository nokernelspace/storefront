package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class StringTrimmerEditor extends PropertyEditorSupport {
   private final @Nullable String charsToDelete;
   private final boolean emptyAsNull;

   public StringTrimmerEditor(boolean emptyAsNull) {
      this.charsToDelete = null;
      this.emptyAsNull = emptyAsNull;
   }

   public StringTrimmerEditor(String charsToDelete, boolean emptyAsNull) {
      this.charsToDelete = charsToDelete;
      this.emptyAsNull = emptyAsNull;
   }

   @Override
   public void setAsText(@Nullable String text) {
      if (text == null) {
         this.setValue(null);
      } else {
         String value = text.trim();
         if (this.charsToDelete != null) {
            value = StringUtils.deleteAny(value, this.charsToDelete);
         }

         if (this.emptyAsNull && value.isEmpty()) {
            this.setValue(null);
         } else {
            this.setValue(value);
         }
      }
   }

   @Override
   public String getAsText() {
      Object value = this.getValue();
      return value != null ? value.toString() : "";
   }
}
