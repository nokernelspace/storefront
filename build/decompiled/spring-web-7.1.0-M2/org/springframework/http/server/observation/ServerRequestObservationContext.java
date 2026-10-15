package org.springframework.http.server.observation;

import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.Propagator.Getter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.jspecify.annotations.Nullable;

public class ServerRequestObservationContext extends RequestReplyReceiverContext<HttpServletRequest, HttpServletResponse> {
   private static final ServerRequestObservationContext.HeaderGetter GETTER = new ServerRequestObservationContext.HeaderGetter();
   private @Nullable String pathPattern;

   public ServerRequestObservationContext(HttpServletRequest request, HttpServletResponse response) {
      super(GETTER);
      this.setCarrier(request);
      this.setResponse(response);
   }

   public @Nullable String getPathPattern() {
      return this.pathPattern;
   }

   public void setPathPattern(@Nullable String pathPattern) {
      this.pathPattern = pathPattern;
   }

   static final class HeaderGetter implements Getter<HttpServletRequest> {
      public String get(HttpServletRequest carrier, String key) {
         return carrier.getHeader(key);
      }

      public Iterable<String> getAll(HttpServletRequest carrier, String key) {
         return Collections.list(carrier.getHeaders(key));
      }
   }
}
