package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.Assert;

public class ReaderEditor extends PropertyEditorSupport {
   private final ResourceEditor resourceEditor;

   public ReaderEditor() {
      this.resourceEditor = new ResourceEditor();
   }

   public ReaderEditor(ResourceEditor resourceEditor) {
      Assert.notNull(resourceEditor, "ResourceEditor must not be null");
      this.resourceEditor = resourceEditor;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      this.resourceEditor.setAsText(text);
      Resource resource = (Resource)this.resourceEditor.getValue();

      try {
         this.setValue(resource != null ? new EncodedResource(resource).getReader() : null);
      } catch (IOException ex) {
         throw new IllegalArgumentException("Failed to retrieve Reader for " + resource, ex);
      }
   }

   @Override
   public @Nullable String getAsText() {
      return null;
   }
}
