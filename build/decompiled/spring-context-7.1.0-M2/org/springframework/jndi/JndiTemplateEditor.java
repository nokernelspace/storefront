package org.springframework.jndi;

import java.beans.PropertyEditorSupport;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.propertyeditors.PropertiesEditor;

public class JndiTemplateEditor extends PropertyEditorSupport {
   private final PropertiesEditor propertiesEditor = new PropertiesEditor();

   @Override
   public void setAsText(@Nullable String text) throws IllegalArgumentException {
      if (text == null) {
         throw new IllegalArgumentException("JndiTemplate cannot be created from null string");
      }

      if (text.isEmpty()) {
         this.setValue(new JndiTemplate());
      } else {
         this.propertiesEditor.setAsText(text);
         Properties props = (Properties)this.propertiesEditor.getValue();
         this.setValue(new JndiTemplate(props));
      }
   }
}
