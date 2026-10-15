package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.util.Assert;
import org.xml.sax.InputSource;

public class InputSourceEditor extends PropertyEditorSupport {
   private final ResourceEditor resourceEditor;

   public InputSourceEditor() {
      this.resourceEditor = new ResourceEditor();
   }

   public InputSourceEditor(ResourceEditor resourceEditor) {
      Assert.notNull(resourceEditor, "ResourceEditor must not be null");
      this.resourceEditor = resourceEditor;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      this.resourceEditor.setAsText(text);
      Resource resource = (Resource)this.resourceEditor.getValue();

      try {
         this.setValue(resource != null ? new InputSource(resource.getURL().toString()) : null);
      } catch (IOException ex) {
         throw new IllegalArgumentException("Could not retrieve URL for " + resource + ": " + ex.getMessage());
      }
   }

   @Override
   public String getAsText() {
      InputSource value = (InputSource)this.getValue();
      return value != null ? value.getSystemId() : "";
   }
}
