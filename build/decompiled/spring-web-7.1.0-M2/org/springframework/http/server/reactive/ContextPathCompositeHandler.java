package org.springframework.http.server.reactive;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

public class ContextPathCompositeHandler implements HttpHandler {
   private final Map<String, HttpHandler> handlerMap;

   public ContextPathCompositeHandler(Map<String, ? extends HttpHandler> handlerMap) {
      Assert.notEmpty(handlerMap, "Handler map must not be empty");
      this.handlerMap = initHandlers(handlerMap);
   }

   private static Map<String, HttpHandler> initHandlers(Map<String, ? extends HttpHandler> map) {
      map.keySet().forEach(ContextPathCompositeHandler::assertValidContextPath);
      return new LinkedHashMap<>(map);
   }

   private static void assertValidContextPath(String contextPath) {
      Assert.hasText(contextPath, "Context path must not be empty");
      if (!contextPath.equals("/")) {
         Assert.isTrue(contextPath.startsWith("/"), "Context path must begin with '/'");
         Assert.isTrue(!contextPath.endsWith("/"), "Context path must not end with '/'");
      }
   }

   @Override
   public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
      String path = request.getPath().pathWithinApplication().value();
      return this.handlerMap.entrySet().stream().filter(entry -> path.startsWith(entry.getKey())).findFirst().map(entry -> {
         String contextPath = request.getPath().contextPath().value() + entry.getKey();
         ServerHttpRequest newRequest = request.mutate().contextPath(contextPath).build();
         return entry.getValue().handle(newRequest, response);
      }).orElseGet(() -> {
         response.setStatusCode(HttpStatus.NOT_FOUND);
         return response.setComplete();
      });
   }
}
