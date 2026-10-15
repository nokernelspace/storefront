package org.springframework.http.server.reactive.observation;

import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.Propagator.Getter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

public class ServerRequestObservationContext extends RequestReplyReceiverContext<ServerHttpRequest, ServerHttpResponse> {
   public static final String CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE = ServerRequestObservationContext.class.getName();
   private static final ServerRequestObservationContext.HeaderGetter GETTER = new ServerRequestObservationContext.HeaderGetter();
   private final Map<String, Object> attributes;
   private @Nullable String pathPattern;
   private boolean connectionAborted;

   public ServerRequestObservationContext(ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> attributes) {
      super(GETTER);
      this.setCarrier(request);
      this.setResponse(response);
      this.attributes = Collections.unmodifiableMap(attributes);
   }

   public Map<String, Object> getAttributes() {
      return this.attributes;
   }

   public @Nullable String getPathPattern() {
      return this.pathPattern;
   }

   public void setPathPattern(@Nullable String pathPattern) {
      this.pathPattern = pathPattern;
   }

   public boolean isConnectionAborted() {
      return this.connectionAborted;
   }

   public void setConnectionAborted(boolean connectionAborted) {
      this.connectionAborted = connectionAborted;
   }

   public static Optional<ServerRequestObservationContext> findCurrent(Map<String, Object> attributes) {
      return Optional.ofNullable((ServerRequestObservationContext)attributes.get(CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE));
   }

   static final class HeaderGetter implements Getter<ServerHttpRequest> {
      public @Nullable String get(ServerHttpRequest carrier, String key) {
         return carrier.getHeaders().getFirst(key);
      }

      public Iterable<String> getAll(ServerHttpRequest carrier, String key) {
         return carrier.getHeaders().getOrEmpty(key);
      }
   }
}
