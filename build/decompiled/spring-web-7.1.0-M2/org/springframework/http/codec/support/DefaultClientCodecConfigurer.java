package org.springframework.http.codec.support;

import org.springframework.http.codec.ClientCodecConfigurer;

public class DefaultClientCodecConfigurer extends BaseCodecConfigurer implements ClientCodecConfigurer {
   public DefaultClientCodecConfigurer() {
      super(new ClientDefaultCodecsImpl());
   }

   private DefaultClientCodecConfigurer(DefaultClientCodecConfigurer other) {
      super(other);
   }

   @Override
   public ClientCodecConfigurer.ClientDefaultCodecs defaultCodecs() {
      return (ClientCodecConfigurer.ClientDefaultCodecs)super.defaultCodecs();
   }

   public DefaultClientCodecConfigurer clone() {
      return new DefaultClientCodecConfigurer(this);
   }

   @Override
   protected BaseDefaultCodecs cloneDefaultCodecs() {
      return new ClientDefaultCodecsImpl((ClientDefaultCodecsImpl)this.defaultCodecs());
   }
}
