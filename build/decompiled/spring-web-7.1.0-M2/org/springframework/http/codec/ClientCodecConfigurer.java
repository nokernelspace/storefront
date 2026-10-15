package org.springframework.http.codec;

import org.springframework.core.codec.Decoder;

public interface ClientCodecConfigurer extends CodecConfigurer {
   ClientCodecConfigurer.ClientDefaultCodecs defaultCodecs();

   ClientCodecConfigurer clone();

   static ClientCodecConfigurer create() {
      return CodecConfigurerFactory.create(ClientCodecConfigurer.class);
   }

   interface ClientDefaultCodecs extends CodecConfigurer.DefaultCodecs {
      void serverSentEventDecoder(Decoder<?> decoder);
   }
}
