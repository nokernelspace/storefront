package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

public class PropertiesEditor extends PropertyEditorSupport {
   @Override
   public void setAsText(@Nullable String text) throws IllegalArgumentException {
      Properties props = new Properties();
      if (text != null) {
         try {
            props.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
         } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse [" + text + "] into Properties", ex);
         }
      }

      this.setValue(props);
   }

   @Override
   public void setValue(Object value) {
      if (!(value instanceof Properties) && value instanceof Map<?, ?> map) {
         Properties props = new Properties();
         props.putAll(map);
         super.setValue(props);
      } else {
         super.setValue(value);
      }
   }
}
