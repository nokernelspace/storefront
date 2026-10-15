package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

public class PathEditor extends PropertyEditorSupport {
   private final ResourceEditor resourceEditor;

   public PathEditor() {
      this.resourceEditor = new ResourceEditor();
   }

   public PathEditor(ResourceEditor resourceEditor) {
      Assert.notNull(resourceEditor, "ResourceEditor must not be null");
      this.resourceEditor = resourceEditor;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      boolean nioPathCandidate = !text.startsWith("classpath:");
      if (nioPathCandidate && !text.startsWith("/")) {
         try {
            URI uri = ResourceUtils.toURI(text);
            String scheme = uri.getScheme();
            if (scheme != null) {
               nioPathCandidate = scheme.length() == 1;
               this.setValue(Paths.get(uri).normalize());
               return;
            }
         } catch (URISyntaxException ex) {
            nioPathCandidate = !text.startsWith("file:");
         } catch (FileSystemNotFoundException | IllegalArgumentException var8) {
         }
      }

      this.resourceEditor.setAsText(text);
      Resource resource = (Resource)this.resourceEditor.getValue();
      if (resource == null) {
         this.setValue(null);
      } else if (!nioPathCandidate || resource.isFile() && resource.exists()) {
         try {
            this.setValue(resource.getFilePath());
         } catch (IOException ex) {
            String msg = "Could not resolve \"" + text + "\" to 'java.nio.file.Path' for " + resource + ": " + ex.getMessage();
            if (nioPathCandidate) {
               msg = msg
                  + " - In case of ambiguity, consider adding the 'file:' prefix for an explicit reference to a file system resource of the same name: \"file:"
                  + text
                  + "\"";
            }

            throw new IllegalArgumentException(msg);
         }
      } else {
         this.setValue(Paths.get(text).normalize());
      }
   }

   @Override
   public String getAsText() {
      Path value = (Path)this.getValue();
      return value != null ? value.toString() : "";
   }
}
