package org.springframework.context;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.util.Assert;

public class PayloadApplicationEvent<T> extends ApplicationEvent implements ResolvableTypeProvider {
   private final T payload;
   private final ResolvableType payloadType;

   public PayloadApplicationEvent(Object source, T payload) {
      this(source, payload, null);
   }

   public PayloadApplicationEvent(Object source, T payload, @Nullable ResolvableType payloadType) {
      super(source);
      Assert.notNull(payload, "Payload must not be null");
      this.payload = payload;
      this.payloadType = payloadType != null ? payloadType : ResolvableType.forInstance(payload);
   }

   public ResolvableType getResolvableType() {
      return ResolvableType.forClassWithGenerics(this.getClass(), new ResolvableType[]{this.payloadType});
   }

   public T getPayload() {
      return this.payload;
   }
}
