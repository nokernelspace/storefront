package org.springframework.core.serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface Serializer<T> {
   void serialize(T object, OutputStream outputStream) throws IOException;

   default byte[] serializeToByteArray(T object) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
      this.serialize(object, out);
      return out.toByteArray();
   }
}
