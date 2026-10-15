package org.springframework.core.convert.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.core.convert.converter.Converter;

final class PropertiesToStringConverter implements Converter<Properties, String> {
   public String convert(Properties source) {
      try {
         ByteArrayOutputStream os = new ByteArrayOutputStream(256);
         source.store(os, null);
         return os.toString(StandardCharsets.ISO_8859_1);
      } catch (IOException ex) {
         throw new IllegalArgumentException("Failed to store [" + source + "] into String", ex);
      }
   }
}
