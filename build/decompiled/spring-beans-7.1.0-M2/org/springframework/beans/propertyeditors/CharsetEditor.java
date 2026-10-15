package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.nio.charset.Charset;
import org.springframework.util.StringUtils;

public class CharsetEditor extends PropertyEditorSupport {
   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      if (StringUtils.hasText(text)) {
         this.setValue(Charset.forName(text.trim()));
      } else {
         this.setValue(null);
      }
   }

   @Override
   public String getAsText() {
      Charset value = (Charset)this.getValue();
      return value != null ? value.name() : "";
   }
}
