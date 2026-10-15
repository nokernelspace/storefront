package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.net.URL;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.util.Assert;

public class URLEditor extends PropertyEditorSupport {
   private final ResourceEditor resourceEditor;

   public URLEditor() {
      this.resourceEditor = new ResourceEditor();
   }

   public URLEditor(ResourceEditor resourceEditor) {
      Assert.notNull(resourceEditor, "ResourceEditor must not be null");
      this.resourceEditor = resourceEditor;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      this.resourceEditor.setAsText(text);
      Resource resource = (Resource)this.resourceEditor.getValue();

      try {
         this.setValue(resource != null ? resource.getURL() : null);
      } catch (IOException ex) {
         throw new IllegalArgumentException("Could not retrieve URL for " + resource + ": " + ex.getMessage());
      }
   }

   @Override
   public String getAsText() {
      URL value = (URL)this.getValue();
      return value != null ? value.toExternalForm() : "";
   }
}
