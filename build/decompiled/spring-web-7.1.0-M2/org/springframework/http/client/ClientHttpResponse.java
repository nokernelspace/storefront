package org.springframework.http.client;

import java.io.Closeable;
import java.io.IOException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatusCode;

public interface ClientHttpResponse extends HttpInputMessage, Closeable {
   HttpStatusCode getStatusCode() throws IOException;

   String getStatusText() throws IOException;

   @Override
   void close();
}
