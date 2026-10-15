package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.Nullable;

public class CharArrayPropertyEditor extends PropertyEditorSupport {
   @Override
   public void setAsText(@Nullable String text) {
      this.setValue(text != null ? text.toCharArray() : null);
   }

   @Override
   public String getAsText() {
      char[] value = (char[])this.getValue();
      return value != null ? new String(value) : "";
   }
}
