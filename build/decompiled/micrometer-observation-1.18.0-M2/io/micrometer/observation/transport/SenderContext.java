package io.micrometer.observation.transport;

import io.micrometer.observation.Observation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class SenderContext<C> extends Observation.Context {
   private final Propagator.Setter<C> setter;
   private final Kind kind;
   private @Nullable C carrier;
   private @Nullable String remoteServiceName;
   private @Nullable String remoteServiceAddress;

   public SenderContext(Propagator.Setter<C> setter, Kind kind) {
      this.setter = Objects.requireNonNull(setter, "Setter must be set");
      this.kind = Objects.requireNonNull(kind, "Kind must be set");
   }

   public SenderContext(Propagator.Setter<C> setter) {
      this(setter, Kind.PRODUCER);
   }

   public @Nullable C getCarrier() {
      return this.carrier;
   }

   public void setCarrier(C carrier) {
      this.carrier = carrier;
   }

   public Propagator.Setter<C> getSetter() {
      return this.setter;
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
