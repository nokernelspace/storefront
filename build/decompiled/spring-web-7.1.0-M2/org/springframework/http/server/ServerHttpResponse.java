package org.springframework.http.server;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatusCode;

public interface ServerHttpResponse extends HttpOutputMessage, Flushable, Closeable {
   void setStatusCode(HttpStatusCode status);

   @Override
   void flush() throws IOException;

   @Override
   void close();
}
