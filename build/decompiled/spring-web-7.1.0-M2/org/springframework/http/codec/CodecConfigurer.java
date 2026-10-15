package org.springframework.http.codec;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;

public interface CodecConfigurer {
   CodecConfigurer.DefaultCodecs defaultCodecs();

   CodecConfigurer.CustomCodecs customCodecs();

   void registerDefaults(boolean registerDefaults);

   List<HttpMessageReader<?>> getReaders();

   List<HttpMessageWriter<?>> getWriters();

   CodecConfigurer clone();

   interface CustomCodecs {
      void register(Object codec);

      void registerWithDefaultConfig(Object codec);

      void registerWithDefaultConfig(Object codec, Consumer<CodecConfigurer.DefaultCodecConfig> configConsumer);
   }

   interface DefaultCodecConfig {
      @Nullable Integer maxInMemorySize();

      @Nullable Boolean isEnableLoggingRequestDetails();
   }

   interface DefaultCodecs {
      void jacksonJsonDecoder(Decoder<?> decoder);

      @Deprecated(since = "7.0", forRemoval = true)
      default void jackson2JsonDecoder(Decoder<?> decoder) {
         this.jacksonJsonDecoder(decoder);
      }

      void jacksonJsonEncoder(Encoder<?> encoder);

      @Deprecated(since = "7.0", forRemoval = true)
      default void jackson2JsonEncoder(Encoder<?> encoder) {
         this.jacksonJsonEncoder(encoder);
      }

      void gsonDecoder(Decoder<?> decoder);

      void gsonEncoder(Encoder<?> encoder);

      void jacksonSmileDecoder(Decoder<?> decoder);

      @Deprecated(since = "7.0", forRemoval = true)
      default void jackson2SmileDecoder(Decoder<?> decoder) {
         this.jacksonSmileDecoder(decoder);
      }

      void jacksonSmileEncoder(Encoder<?> encoder);

      @Deprecated(since = "7.0", forRemoval = true)
      default void jackson2SmileEncoder(Encoder<?> encoder) {
         this.jacksonSmileEncoder(encoder);
      }

      void jacksonCborDecoder(Decoder<?> decoder);

      void jacksonCborEncoder(Encoder<?> encoder);

      void jacksonXmlDecoder(Decoder<?> decoder);

      void jacksonXmlEncoder(Encoder<?> encoder);

      void protobufDecoder(Decoder<?> decoder);

      void protobufEncoder(Encoder<?> encoder);

      void jaxb2Decoder(Decoder<?> decoder);

      void jaxb2Encoder(Encoder<?> encoder);

      void kotlinSerializationCborDecoder(Decoder<?> decoder);

      void kotlinSerializationCborEncoder(Encoder<?> encoder);

      void kotlinSerializationJsonDecoder(Decoder<?> decoder);

      void kotlinSerializationJsonEncoder(Encoder<?> encoder);

      void kotlinSerializationProtobufDecoder(Decoder<?> decoder);

      void kotlinSerializationProtobufEncoder(Encoder<?> encoder);

      void configureDefaultCodec(Consumer<Object> codecConsumer);

      void maxInMemorySize(int byteCount);

      void enableLoggingRequestDetails(boolean enable);

      CodecConfigurer.MultipartCodecs multipartCodecs();

      void multipartReader(HttpMessageReader<?> reader);
   }

   interface MultipartCodecs {
      CodecConfigurer.MultipartCodecs encoder(Encoder<?> encoder);

      CodecConfigurer.MultipartCodecs writer(HttpMessageWriter<?> writer);
   }
}
