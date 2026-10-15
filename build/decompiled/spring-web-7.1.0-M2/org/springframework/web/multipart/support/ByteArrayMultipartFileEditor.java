package org.springframework.web.multipart.support;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.propertyeditors.ByteArrayPropertyEditor;
import org.springframework.web.multipart.MultipartFile;

public class ByteArrayMultipartFileEditor extends ByteArrayPropertyEditor {
   public void setValue(@Nullable Object value) {
      if (value instanceof MultipartFile multipartFile) {
         try {
            super.setValue(multipartFile.getBytes());
         } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot read contents of multipart file", ex);
         }
      } else if (value instanceof byte[]) {
         super.setValue(value);
      } else {
         super.setValue(value != null ? value.toString().getBytes() : null);
      }
   }

   public String getAsText() {
      byte[] value = (byte[])this.getValue();
      return value != null ? new String(value) : "";
   }
}
