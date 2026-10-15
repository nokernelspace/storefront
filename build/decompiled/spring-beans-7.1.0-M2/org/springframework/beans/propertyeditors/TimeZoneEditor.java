package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.TimeZone;
import org.springframework.util.StringUtils;

public class TimeZoneEditor extends PropertyEditorSupport {
   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      if (StringUtils.hasText(text)) {
         text = text.trim();
      }

      this.setValue(StringUtils.parseTimeZoneString(text));
   }

   @Override
   public String getAsText() {
      TimeZone value = (TimeZone)this.getValue();
      return value != null ? value.getID() : "";
   }
}
