package org.springframework.http.codec;

import org.springframework.core.codec.Encoder;

public interface ServerCodecConfigurer extends CodecConfigurer {
   ServerCodecConfigurer.ServerDefaultCodecs defaultCodecs();

   ServerCodecConfigurer clone();

   static ServerCodecConfigurer create() {
      return CodecConfigurerFactory.create(ServerCodecConfigurer.class);
   }

   interface ServerDefaultCodecs extends CodecConfigurer.DefaultCodecs {
      void serverSentEventEncoder(Encoder<?> encoder);
   }
}
