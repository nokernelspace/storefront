package io.micrometer.observation.transport;

import org.jspecify.annotations.Nullable;

public class RequestReplyReceiverContext<C, RES> extends ReceiverContext<C> implements ResponseContext<RES> {
   private @Nullable RES response;

   public RequestReplyReceiverContext(Propagator.Getter<C> getter, Kind kind) {
      super(getter, kind);
   }

   public RequestReplyReceiverContext(Propagator.Getter<C> getter) {
      this(getter, Kind.SERVER);
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
