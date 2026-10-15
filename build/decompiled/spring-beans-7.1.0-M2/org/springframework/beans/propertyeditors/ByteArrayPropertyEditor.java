package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.Nullable;

public class ByteArrayPropertyEditor extends PropertyEditorSupport {
   @Override
   public void setAsText(@Nullable String text) {
      this.setValue(text != null ? text.getBytes() : null);
   }

   @Override
   public String getAsText() {
      byte[] value = (byte[])this.getValue();
      return value != null ? new String(value) : "";
   }
}
