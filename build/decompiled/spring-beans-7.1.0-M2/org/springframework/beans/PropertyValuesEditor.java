package org.springframework.beans;

import java.beans.PropertyEditorSupport;
import java.util.Properties;
import org.springframework.beans.propertyeditors.PropertiesEditor;

public class PropertyValuesEditor extends PropertyEditorSupport {
   private final PropertiesEditor propertiesEditor = new PropertiesEditor();

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      this.propertiesEditor.setAsText(text);
      Properties props = (Properties)this.propertiesEditor.getValue();
      this.setValue(new MutablePropertyValues(props));
   }
}
