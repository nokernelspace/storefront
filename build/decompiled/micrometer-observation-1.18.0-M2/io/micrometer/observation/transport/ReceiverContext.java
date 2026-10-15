package io.micrometer.observation.transport;

import io.micrometer.observation.Observation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class ReceiverContext<C> extends Observation.Context {
   private final Propagator.Getter<C> getter;
   private final Kind kind;
   private @Nullable C carrier;
   private @Nullable String remoteServiceName;
   private @Nullable String remoteServiceAddress;

   public ReceiverContext(Propagator.Getter<C> getter, Kind kind) {
      this.getter = Objects.requireNonNull(getter, "Getter must be set");
      this.kind = Objects.requireNonNull(kind, "Kind must be set");
   }

   public ReceiverContext(Propagator.Getter<C> getter) {
      this(getter, Kind.CONSUMER);
   }

   public @Nullable C getCarrier() {
      return this.carrier;
   }

   public void setCarrier(C carrier) {
      this.carrier = carrier;
   }

   public Propagator.Getter<C> getGetter() {
      return this.getter;
   }

   public Kind getKind() {
      return this.kind;
   }

   public @Nullable String getRemoteServiceName() {
      return this.remoteServiceName;
   }

   public void setRemoteServiceName(@Nullable String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
   }

   public @Nullable String getRemoteServiceAddress() {
      return this.remoteServiceAddress;
   }

   public void setRemoteServiceAddress(@Nullable String remoteServiceAddress) {
      this.remoteServiceAddress = remoteServiceAddress;
   }
}
