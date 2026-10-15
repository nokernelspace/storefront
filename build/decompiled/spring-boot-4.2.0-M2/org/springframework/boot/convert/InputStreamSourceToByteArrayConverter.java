package org.springframework.boot.convert;

import java.io.IOException;
import org.springframework.boot.origin.Origin;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

class InputStreamSourceToByteArrayConverter implements Converter<InputStreamSource, byte[]> {
   public byte[] convert(InputStreamSource source) {
      try {
         return FileCopyUtils.copyToByteArray(source.getInputStream());
      } catch (IOException ex) {
         throw new IllegalStateException("Unable to read from " + this.getName(source), ex);
      }
   }

   private String getName(InputStreamSource source) {
      Origin origin = Origin.from(source);
      if (origin != null) {
         return origin.toString();
      } else {
         return source instanceof Resource resource ? resource.getDescription() : "input stream source";
      }
   }
}
