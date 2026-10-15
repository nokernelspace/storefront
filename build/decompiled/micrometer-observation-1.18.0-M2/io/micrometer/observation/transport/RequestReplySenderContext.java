package io.micrometer.observation.transport;

import org.jspecify.annotations.Nullable;

public class RequestReplySenderContext<C, RES> extends SenderContext<C> implements ResponseContext<RES> {
   private @Nullable RES response;

   public RequestReplySenderContext(Propagator.Setter<C> setter, Kind kind) {
      super(setter, kind);
   }

   public RequestReplySenderContext(Propagator.Setter<C> setter) {
      this(setter, Kind.CLIENT);
   }

   @Override
   public @Nullable RES getResponse() {
      return this.response;
   }

   @Override
   public void setResponse(RES response) {
      this.response = response;
   }
}
