package org.springframework.http;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.util.StreamUtils;

public interface StreamingHttpOutputMessage extends HttpOutputMessage {
   void setBody(StreamingHttpOutputMessage.Body body);

   default void setBody(byte[] body) throws IOException {
      this.setBody(new StreamingHttpOutputMessage.Body() {
         @Override
         public void writeTo(OutputStream outputStream) throws IOException {
            StreamUtils.copy(body, outputStream);
         }

         @Override
         public boolean repeatable() {
            return true;
         }
      });
   }

   @FunctionalInterface
   interface Body {
      void writeTo(OutputStream outputStream) throws IOException;

      default boolean repeatable() {
         return false;
      }
   }
}
