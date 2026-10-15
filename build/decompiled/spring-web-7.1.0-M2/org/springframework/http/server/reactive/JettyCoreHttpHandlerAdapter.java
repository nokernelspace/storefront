package org.springframework.http.server.reactive;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Handler.Abstract.NonBlocking;
import org.eclipse.jetty.util.Callback;
import org.springframework.core.io.buffer.JettyDataBufferFactory;
import org.springframework.util.Assert;

public class JettyCoreHttpHandlerAdapter extends NonBlocking {
   private final HttpHandler httpHandler;
   private JettyDataBufferFactory dataBufferFactory = new JettyDataBufferFactory();

   public JettyCoreHttpHandlerAdapter(HttpHandler httpHandler) {
      this.httpHandler = httpHandler;
   }

   public void setDataBufferFactory(JettyDataBufferFactory dataBufferFactory) {
      Assert.notNull(dataBufferFactory, "DataBufferFactory must not be null");
      this.dataBufferFactory = dataBufferFactory;
   }

   public boolean handle(Request request, Response response, Callback callback) throws Exception {
      this.httpHandler
         .handle(new JettyCoreServerHttpRequest(request, this.dataBufferFactory), new JettyCoreServerHttpResponse(response, this.dataBufferFactory))
         .subscribe(unused -> {}, callback::failed, callback::succeeded);
      return true;
   }
}
