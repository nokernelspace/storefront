package org.springframework.http.codec.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.codec.ByteBufferDecoder;
import org.springframework.core.codec.ByteBufferEncoder;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.DataBufferDecoder;
import org.springframework.core.codec.DataBufferEncoder;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.NettyByteBufDecoder;
import org.springframework.core.codec.NettyByteBufEncoder;
import org.springframework.core.codec.ResourceDecoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.FormHttpMessageReader;
import org.springframework.http.codec.FormHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageReader;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.ServerSentEventHttpMessageReader;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.cbor.Jackson2CborDecoder;
import org.springframework.http.codec.cbor.Jackson2CborEncoder;
import org.springframework.http.codec.cbor.JacksonCborDecoder;
import org.springframework.http.codec.cbor.JacksonCborEncoder;
import org.springframework.http.codec.cbor.KotlinSerializationCborDecoder;
import org.springframework.http.codec.cbor.KotlinSerializationCborEncoder;
import org.springframework.http.codec.json.AbstractJackson2Decoder;
import org.springframework.http.codec.json.GsonDecoder;
import org.springframework.http.codec.json.GsonEncoder;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.codec.json.Jackson2SmileDecoder;
import org.springframework.http.codec.json.Jackson2SmileEncoder;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder;
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageWriter;
import org.springframework.http.codec.multipart.PartEventHttpMessageReader;
import org.springframework.http.codec.multipart.PartEventHttpMessageWriter;
import org.springframework.http.codec.multipart.PartHttpMessageWriter;
import org.springframework.http.codec.protobuf.KotlinSerializationProtobufDecoder;
import org.springframework.http.codec.protobuf.KotlinSerializationProtobufEncoder;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.http.codec.protobuf.ProtobufEncoder;
import org.springframework.http.codec.protobuf.ProtobufHttpMessageWriter;
import org.springframework.http.codec.smile.JacksonSmileDecoder;
import org.springframework.http.codec.smile.JacksonSmileEncoder;
import org.springframework.http.codec.xml.JacksonXmlDecoder;
import org.springframework.http.codec.xml.JacksonXmlEncoder;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

class BaseDefaultCodecs implements CodecConfigurer.DefaultCodecs, CodecConfigurer.DefaultCodecConfig {
   static final boolean JACKSON_PRESENT;
   static final boolean JACKSON_2_PRESENT;
   static final boolean GSON_PRESENT;
   private static final boolean JACKSON_SMILE_PRESENT;
   private static final boolean JACKSON_2_SMILE_PRESENT;
   private static final boolean JACKSON_CBOR_PRESENT;
   private static final boolean JACKSON_2_CBOR_PRESENT;
   private static final boolean JACKSON_XML_PRESENT;
   private static final boolean JAXB_2_PRESENT;
   private static final boolean PROTOBUF_PRESENT;
   static final boolean NETTY_BYTE_BUF_PRESENT;
   static final boolean KOTLIN_SERIALIZATION_CBOR_PRESENT;
   static final boolean KOTLIN_SERIALIZATION_JSON_PRESENT;
   static final boolean KOTLIN_SERIALIZATION_PROTOBUF_PRESENT;
   private @Nullable Decoder<?> jacksonJsonDecoder;
   private @Nullable Encoder<?> jacksonJsonEncoder;
   private @Nullable Decoder<?> gsonDecoder;
   private @Nullable Encoder<?> gsonEncoder;
   private @Nullable Encoder<?> jacksonSmileEncoder;
   private @Nullable Decoder<?> jacksonSmileDecoder;
   private @Nullable Encoder<?> jacksonCborEncoder;
   private @Nullable Decoder<?> jacksonCborDecoder;
   private @Nullable Encoder<?> jacksonXmlEncoder;
   private @Nullable Decoder<?> jacksonXmlDecoder;
   private @Nullable Decoder<?> protobufDecoder;
   private @Nullable Encoder<?> protobufEncoder;
   private @Nullable Decoder<?> jaxb2Decoder;
   private @Nullable Encoder<?> jaxb2Encoder;
   private @Nullable Decoder<?> kotlinSerializationCborDecoder;
   private @Nullable Encoder<?> kotlinSerializationCborEncoder;
   private @Nullable Decoder<?> kotlinSerializationJsonDecoder;
   private @Nullable Encoder<?> kotlinSerializationJsonEncoder;
   private @Nullable Decoder<?> kotlinSerializationProtobufDecoder;
   private @Nullable Encoder<?> kotlinSerializationProtobufEncoder;
   private BaseDefaultCodecs.@Nullable DefaultMultipartCodecs multipartCodecs;
   private @Nullable Supplier<List<HttpMessageWriter<?>>> partWritersSupplier;
   private @Nullable HttpMessageReader<?> multipartReader;
   private @Nullable Consumer<Object> codecConsumer;
   private @Nullable Integer maxInMemorySize;
   private @Nullable Boolean enableLoggingRequestDetails;
   private boolean registerDefaults = true;
   private final List<HttpMessageReader<?>> typedReaders = new ArrayList<>();
   private final List<HttpMessageReader<?>> objectReaders = new ArrayList<>();
   private final List<HttpMessageWriter<?>> typedWriters = new ArrayList<>();
   private final List<HttpMessageWriter<?>> objectWriters = new ArrayList<>();

   BaseDefaultCodecs() {
      this.initReaders();
      this.initWriters();
   }

   protected void initReaders() {
      this.initTypedReaders();
      this.initObjectReaders();
   }

   protected void initWriters() {
      this.initTypedWriters();
      this.initObjectWriters();
   }

   protected BaseDefaultCodecs(BaseDefaultCodecs other) {
      this.jacksonJsonDecoder = other.jacksonJsonDecoder;
      this.jacksonJsonEncoder = other.jacksonJsonEncoder;
      this.gsonDecoder = other.gsonDecoder;
      this.gsonEncoder = other.gsonEncoder;
      this.jacksonSmileDecoder = other.jacksonSmileDecoder;
      this.jacksonSmileEncoder = other.jacksonSmileEncoder;
      this.jacksonCborDecoder = other.jacksonCborDecoder;
      this.jacksonCborEncoder = other.jacksonCborEncoder;
      this.jacksonXmlDecoder = other.jacksonXmlDecoder;
      this.jacksonXmlEncoder = other.jacksonXmlEncoder;
      this.protobufDecoder = other.protobufDecoder;
      this.protobufEncoder = other.protobufEncoder;
      this.jaxb2Decoder = other.jaxb2Decoder;
      this.jaxb2Encoder = other.jaxb2Encoder;
      this.kotlinSerializationCborDecoder = other.kotlinSerializationCborDecoder;
      this.kotlinSerializationCborEncoder = other.kotlinSerializationCborEncoder;
      this.kotlinSerializationJsonDecoder = other.kotlinSerializationJsonDecoder;
      this.kotlinSerializationJsonEncoder = other.kotlinSerializationJsonEncoder;
      this.kotlinSerializationProtobufDecoder = other.kotlinSerializationProtobufDecoder;
      this.kotlinSerializationProtobufEncoder = other.kotlinSerializationProtobufEncoder;
      this.multipartCodecs = other.multipartCodecs != null ? new BaseDefaultCodecs.DefaultMultipartCodecs(other.multipartCodecs) : null;
      this.multipartReader = other.multipartReader;
      this.codecConsumer = other.codecConsumer;
      this.maxInMemorySize = other.maxInMemorySize;
      this.enableLoggingRequestDetails = other.enableLoggingRequestDetails;
      this.registerDefaults = other.registerDefaults;
      this.typedReaders.addAll(other.typedReaders);
      this.objectReaders.addAll(other.objectReaders);
      this.typedWriters.addAll(other.typedWriters);
      this.objectWriters.addAll(other.objectWriters);
   }

   @Override
   public void jacksonJsonDecoder(Decoder<?> decoder) {
      this.jacksonJsonDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void jacksonJsonEncoder(Encoder<?> encoder) {
      this.jacksonJsonEncoder = encoder;
      this.initObjectWriters();
      this.initTypedWriters();
   }

   @Override
   public void gsonDecoder(Decoder<?> decoder) {
      this.gsonDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void gsonEncoder(Encoder<?> encoder) {
      this.gsonEncoder = encoder;
      this.initObjectWriters();
      this.initTypedWriters();
   }

   @Override
   public void jacksonSmileDecoder(Decoder<?> decoder) {
      this.jacksonSmileDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void jacksonSmileEncoder(Encoder<?> encoder) {
      this.jacksonSmileEncoder = encoder;
      this.initObjectWriters();
      this.initTypedWriters();
   }

   @Override
   public void jacksonCborDecoder(Decoder<?> decoder) {
      this.jacksonCborDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void jacksonCborEncoder(Encoder<?> encoder) {
      this.jacksonCborEncoder = encoder;
      this.initObjectWriters();
      this.initTypedWriters();
   }

   @Override
   public void jacksonXmlDecoder(Decoder<?> decoder) {
      this.jacksonXmlDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void jacksonXmlEncoder(Encoder<?> encoder) {
      this.jacksonXmlEncoder = encoder;
      this.initObjectWriters();
      this.initTypedWriters();
   }

   @Override
   public void protobufDecoder(Decoder<?> decoder) {
      this.protobufDecoder = decoder;
      this.initTypedReaders();
   }

   @Override
   public void protobufEncoder(Encoder<?> encoder) {
      this.protobufEncoder = encoder;
      this.initTypedWriters();
   }

   @Override
   public void jaxb2Decoder(Decoder<?> decoder) {
      this.jaxb2Decoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void jaxb2Encoder(Encoder<?> encoder) {
      this.jaxb2Encoder = encoder;
      this.initObjectWriters();
   }

   @Override
   public void kotlinSerializationCborDecoder(Decoder<?> decoder) {
      this.kotlinSerializationCborDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void kotlinSerializationCborEncoder(Encoder<?> encoder) {
      this.kotlinSerializationCborEncoder = encoder;
      this.initObjectWriters();
   }

   @Override
   public void kotlinSerializationJsonDecoder(Decoder<?> decoder) {
      this.kotlinSerializationJsonDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void kotlinSerializationJsonEncoder(Encoder<?> encoder) {
      this.kotlinSerializationJsonEncoder = encoder;
      this.initObjectWriters();
   }

   @Override
   public void kotlinSerializationProtobufDecoder(Decoder<?> decoder) {
      this.kotlinSerializationProtobufDecoder = decoder;
      this.initObjectReaders();
   }

   @Override
   public void kotlinSerializationProtobufEncoder(Encoder<?> encoder) {
      this.kotlinSerializationProtobufEncoder = encoder;
      this.initObjectWriters();
   }

   @Override
   public void configureDefaultCodec(Consumer<Object> codecConsumer) {
      this.codecConsumer = this.codecConsumer != null ? this.codecConsumer.andThen(codecConsumer) : codecConsumer;
      this.initReaders();
      this.initWriters();
   }

   @Override
   public void maxInMemorySize(int byteCount) {
      if (!ObjectUtils.nullSafeEquals(this.maxInMemorySize, byteCount)) {
         this.maxInMemorySize = byteCount;
         this.initReaders();
      }
   }

   @Override
   public @Nullable Integer maxInMemorySize() {
      return this.maxInMemorySize;
   }

   @Override
   public void enableLoggingRequestDetails(boolean enable) {
      if (!ObjectUtils.nullSafeEquals(this.enableLoggingRequestDetails, enable)) {
         this.enableLoggingRequestDetails = enable;
         this.initReaders();
         this.initWriters();
      }
   }

   @Override
   public CodecConfigurer.MultipartCodecs multipartCodecs() {
      if (this.multipartCodecs == null) {
         this.multipartCodecs = new BaseDefaultCodecs.DefaultMultipartCodecs();
      }

      return this.multipartCodecs;
   }

   @Override
   public void multipartReader(HttpMessageReader<?> multipartReader) {
      this.multipartReader = multipartReader;
      this.initTypedReaders();
   }

   void setPartWritersSupplier(Supplier<List<HttpMessageWriter<?>>> supplier) {
      this.partWritersSupplier = supplier;
      this.initTypedWriters();
   }

   @Override
   public @Nullable Boolean isEnableLoggingRequestDetails() {
      return this.enableLoggingRequestDetails;
   }

   void registerDefaults(boolean registerDefaults) {
      if (this.registerDefaults != registerDefaults) {
         this.registerDefaults = registerDefaults;
         this.initReaders();
         this.initWriters();
      }
   }

   final List<HttpMessageReader<?>> getTypedReaders() {
      return this.typedReaders;
   }

   protected void initTypedReaders() {
      this.typedReaders.clear();
      if (this.registerDefaults) {
         this.addCodec(this.typedReaders, new DecoderHttpMessageReader(new ByteArrayDecoder()));
         this.addCodec(this.typedReaders, new DecoderHttpMessageReader(new ByteBufferDecoder()));
         this.addCodec(this.typedReaders, new DecoderHttpMessageReader(new DataBufferDecoder()));
         if (NETTY_BYTE_BUF_PRESENT) {
            this.addCodec(this.typedReaders, new DecoderHttpMessageReader(new NettyByteBufDecoder()));
         }

         this.addCodec(this.typedReaders, new ResourceHttpMessageReader(new ResourceDecoder()));
         this.addCodec(this.typedReaders, new DecoderHttpMessageReader(StringDecoder.textPlainOnly()));
         if (PROTOBUF_PRESENT) {
            this.addCodec(this.typedReaders, new DecoderHttpMessageReader(this.protobufDecoder != null ? this.protobufDecoder : new ProtobufDecoder()));
         }

         this.addCodec(this.typedReaders, new FormHttpMessageReader());
         if (this.multipartReader != null) {
            this.addCodec(this.typedReaders, this.multipartReader);
         } else {
            DefaultPartHttpMessageReader partReader = new DefaultPartHttpMessageReader();
            this.addCodec(this.typedReaders, partReader);
            this.addCodec(this.typedReaders, new MultipartHttpMessageReader(partReader));
         }

         this.addCodec(this.typedReaders, new PartEventHttpMessageReader());
         this.extendTypedReaders(this.typedReaders);
      }
   }

   protected <T> void addCodec(List<T> codecs, T codec) {
      this.initCodec(codec);
      codecs.add(codec);
   }

   private void initCodec(@Nullable Object codec) {
      if (codec instanceof DecoderHttpMessageReader<?> decoderHttpMessageReader) {
         codec = decoderHttpMessageReader.getDecoder();
      } else if (codec instanceof EncoderHttpMessageWriter<?> encoderHttpMessageWriter) {
         codec = encoderHttpMessageWriter.getEncoder();
      }

      if (codec != null) {
         Integer size = this.maxInMemorySize;
         if (size != null) {
            if (codec instanceof AbstractDataBufferDecoder<?> abstractDataBufferDecoder) {
               abstractDataBufferDecoder.setMaxInMemorySize(size);
            }

            if (PROTOBUF_PRESENT && codec instanceof ProtobufDecoder protobufDec) {
               protobufDec.setMaxMessageSize(size);
            }

            if (KOTLIN_SERIALIZATION_CBOR_PRESENT && codec instanceof KotlinSerializationCborDecoder kotlinSerializationCborDec) {
               kotlinSerializationCborDec.setMaxInMemorySize(size);
            }

            if (KOTLIN_SERIALIZATION_JSON_PRESENT && codec instanceof KotlinSerializationJsonDecoder kotlinSerializationJsonDec) {
               kotlinSerializationJsonDec.setMaxInMemorySize(size);
            }

            if (KOTLIN_SERIALIZATION_PROTOBUF_PRESENT && codec instanceof KotlinSerializationProtobufDecoder kotlinSerializationProtobufDec) {
               kotlinSerializationProtobufDec.setMaxInMemorySize(size);
            }

            if (JACKSON_PRESENT && codec instanceof AbstractJacksonDecoder<?> abstractJacksonDecoder) {
               abstractJacksonDecoder.setMaxInMemorySize(size);
            }

            if (JACKSON_2_PRESENT && codec instanceof AbstractJackson2Decoder abstractJackson2Decoder) {
               abstractJackson2Decoder.setMaxInMemorySize(size);
            }

            if (JAXB_2_PRESENT && codec instanceof Jaxb2XmlDecoder jaxb2XmlDecoder) {
               jaxb2XmlDecoder.setMaxInMemorySize(size);
            }

            if (codec instanceof FormHttpMessageReader formHttpMessageReader) {
               formHttpMessageReader.setMaxInMemorySize(size);
            }

            if (codec instanceof ServerSentEventHttpMessageReader serverSentEventHttpMessageReader) {
               serverSentEventHttpMessageReader.setMaxInMemorySize(size);
            }

            if (codec instanceof DefaultPartHttpMessageReader defaultPartHttpMessageReader) {
               defaultPartHttpMessageReader.setMaxInMemorySize(size);
            }

            if (codec instanceof PartEventHttpMessageReader partEventHttpMessageReader) {
               partEventHttpMessageReader.setMaxInMemorySize(size);
            }
         }

         Boolean enable = this.enableLoggingRequestDetails;
         if (enable != null) {
            if (codec instanceof FormHttpMessageReader formHttpMessageReader) {
               formHttpMessageReader.setEnableLoggingRequestDetails(enable);
            }

            if (codec instanceof MultipartHttpMessageReader multipartHttpMessageReader) {
               multipartHttpMessageReader.setEnableLoggingRequestDetails(enable);
            }

            if (codec instanceof DefaultPartHttpMessageReader defaultPartHttpMessageReader) {
               defaultPartHttpMessageReader.setEnableLoggingRequestDetails(enable);
            }

            if (codec instanceof PartEventHttpMessageReader partEventHttpMessageReader) {
               partEventHttpMessageReader.setEnableLoggingRequestDetails(enable);
            }

            if (codec instanceof FormHttpMessageWriter formHttpMessageWriter) {
               formHttpMessageWriter.setEnableLoggingRequestDetails(enable);
            }

            if (codec instanceof MultipartHttpMessageWriter multipartHttpMessageWriter) {
               multipartHttpMessageWriter.setEnableLoggingRequestDetails(enable);
            }
         }

         if (this.codecConsumer != null) {
            this.codecConsumer.accept(codec);
         }

         if (codec instanceof MultipartHttpMessageReader multipartHttpMessageReader) {
            this.initCodec(multipartHttpMessageReader.getPartReader());
         } else if (codec instanceof MultipartHttpMessageWriter multipartHttpMessageWriter) {
            this.initCodec(multipartHttpMessageWriter.getFormWriter());
         } else if (codec instanceof ServerSentEventHttpMessageReader serverSentEventHttpMessageReader) {
            this.initCodec(serverSentEventHttpMessageReader.getDecoder());
         } else if (codec instanceof ServerSentEventHttpMessageWriter serverSentEventHttpMessageWriter) {
            this.initCodec(serverSentEventHttpMessageWriter.getEncoder());
         }
      }
   }

   protected void extendTypedReaders(List<HttpMessageReader<?>> typedReaders) {
   }

   final List<HttpMessageReader<?>> getObjectReaders() {
      return this.objectReaders;
   }

   protected void initObjectReaders() {
      this.objectReaders.clear();
      if (this.registerDefaults) {
         if (KOTLIN_SERIALIZATION_JSON_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getKotlinSerializationJsonDecoder()));
         }

         if (JACKSON_PRESENT || JACKSON_2_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getJacksonJsonDecoder()));
         } else if (GSON_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getGsonDecoder()));
         }

         if (JACKSON_SMILE_PRESENT || JACKSON_2_SMILE_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getJacksonSmileDecoder()));
         }

         if (KOTLIN_SERIALIZATION_CBOR_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getKotlinSerializationCborDecoder()));
         }

         if (JACKSON_CBOR_PRESENT || JACKSON_2_CBOR_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getJacksonCborDecoder()));
         }

         if (JACKSON_XML_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getJacksonXmlDecoder()));
         } else if (JAXB_2_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader((Decoder)(this.jaxb2Decoder != null ? this.jaxb2Decoder : new Jaxb2XmlDecoder())));
         }

         if (KOTLIN_SERIALIZATION_PROTOBUF_PRESENT) {
            this.addCodec(this.objectReaders, new DecoderHttpMessageReader(this.getKotlinSerializationProtobufDecoder()));
         }

         this.extendObjectReaders(this.objectReaders);
      }
   }

   protected void extendObjectReaders(List<HttpMessageReader<?>> objectReaders) {
   }

   final List<HttpMessageReader<?>> getCatchAllReaders() {
      if (!this.registerDefaults) {
         return Collections.emptyList();
      }

      List<HttpMessageReader<?>> readers = new ArrayList<>();
      this.addCodec(readers, new DecoderHttpMessageReader(StringDecoder.allMimeTypes()));
      return readers;
   }

   final List<HttpMessageWriter<?>> getTypedWriters() {
      return this.typedWriters;
   }

   protected void initTypedWriters() {
      this.typedWriters.clear();
      if (this.registerDefaults) {
         this.typedWriters.addAll(this.getBaseTypedWriters());
         this.extendTypedWriters(this.typedWriters);
      }
   }

   final List<HttpMessageWriter<?>> getBaseTypedWriters() {
      if (!this.registerDefaults) {
         return Collections.emptyList();
      }

      List<HttpMessageWriter<?>> writers = new ArrayList<>();
      this.addCodec(writers, new EncoderHttpMessageWriter(new ByteArrayEncoder()));
      this.addCodec(writers, new EncoderHttpMessageWriter(new ByteBufferEncoder()));
      this.addCodec(writers, new EncoderHttpMessageWriter(new DataBufferEncoder()));
      if (NETTY_BYTE_BUF_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(new NettyByteBufEncoder()));
      }

      this.addCodec(writers, new ResourceHttpMessageWriter());
      this.addCodec(writers, new EncoderHttpMessageWriter(CharSequenceEncoder.textPlainOnly()));
      if (PROTOBUF_PRESENT) {
         this.addCodec(writers, new ProtobufHttpMessageWriter(this.protobufEncoder != null ? (ProtobufEncoder)this.protobufEncoder : new ProtobufEncoder()));
      }

      this.addCodec(writers, new MultipartHttpMessageWriter(this::getPartWriters, new FormHttpMessageWriter()));
      this.addCodec(writers, new PartEventHttpMessageWriter());
      this.addCodec(writers, new PartHttpMessageWriter());
      return writers;
   }

   private List<HttpMessageWriter<?>> getPartWriters() {
      if (this.multipartCodecs != null) {
         return this.multipartCodecs.getWriters();
      } else {
         return this.partWritersSupplier != null ? this.partWritersSupplier.get() : Collections.emptyList();
      }
   }

   protected void extendTypedWriters(List<HttpMessageWriter<?>> typedWriters) {
   }

   final List<HttpMessageWriter<?>> getObjectWriters() {
      return this.objectWriters;
   }

   protected void initObjectWriters() {
      this.objectWriters.clear();
      if (this.registerDefaults) {
         this.objectWriters.addAll(this.getBaseObjectWriters());
         this.extendObjectWriters(this.objectWriters);
      }
   }

   final List<HttpMessageWriter<?>> getBaseObjectWriters() {
      List<HttpMessageWriter<?>> writers = new ArrayList<>();
      if (KOTLIN_SERIALIZATION_JSON_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getKotlinSerializationJsonEncoder()));
      }

      if (JACKSON_PRESENT || JACKSON_2_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getJacksonJsonEncoder()));
      } else if (GSON_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getGsonEncoder()));
      }

      if (JACKSON_SMILE_PRESENT || JACKSON_2_SMILE_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getJacksonSmileEncoder()));
      }

      if (KOTLIN_SERIALIZATION_CBOR_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getKotlinSerializationCborEncoder()));
      }

      if (JACKSON_CBOR_PRESENT || JACKSON_2_CBOR_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getJacksonCborEncoder()));
      }

      if (JACKSON_XML_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getJacksonXmlEncoder()));
      } else if (JAXB_2_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter((Encoder)(this.jaxb2Encoder != null ? this.jaxb2Encoder : new Jaxb2XmlEncoder())));
      }

      if (KOTLIN_SERIALIZATION_PROTOBUF_PRESENT) {
         this.addCodec(writers, new EncoderHttpMessageWriter(this.getKotlinSerializationProtobufEncoder()));
      }

      return writers;
   }

   protected void extendObjectWriters(List<HttpMessageWriter<?>> objectWriters) {
   }

   List<HttpMessageWriter<?>> getCatchAllWriters() {
      if (!this.registerDefaults) {
         return Collections.emptyList();
      }

      List<HttpMessageWriter<?>> result = new ArrayList<>();
      result.add(new EncoderHttpMessageWriter(CharSequenceEncoder.allMimeTypes()));
      return result;
   }

   void applyDefaultConfig(BaseCodecConfigurer.DefaultCustomCodecs customCodecs) {
      this.applyDefaultConfig(customCodecs.getTypedReaders());
      this.applyDefaultConfig(customCodecs.getObjectReaders());
      this.applyDefaultConfig(customCodecs.getTypedWriters());
      this.applyDefaultConfig(customCodecs.getObjectWriters());
      customCodecs.getDefaultConfigConsumers().forEach(consumer -> consumer.accept(this));
   }

   private void applyDefaultConfig(Map<?, Boolean> readers) {
      readers.entrySet().stream().filter(Entry::getValue).map(Entry::getKey).forEach(this::initCodec);
   }

   protected Decoder<?> getJacksonJsonDecoder() {
      if (this.jacksonJsonDecoder == null) {
         if (JACKSON_PRESENT) {
            this.jacksonJsonDecoder = new JacksonJsonDecoder();
         } else {
            if (!JACKSON_2_PRESENT) {
               throw new IllegalStateException("Jackson not present");
            }

            this.jacksonJsonDecoder = new Jackson2JsonDecoder();
         }
      }

      return this.jacksonJsonDecoder;
   }

   @Deprecated(since = "7.0", forRemoval = true)
   protected Decoder<?> getJackson2JsonDecoder() {
      return this.getJacksonJsonDecoder();
   }

   protected Encoder<?> getJacksonJsonEncoder() {
      if (this.jacksonJsonEncoder == null) {
         if (JACKSON_PRESENT) {
            this.jacksonJsonEncoder = new JacksonJsonEncoder();
         } else {
            if (!JACKSON_2_PRESENT) {
               throw new IllegalStateException("Jackson not present");
            }

            this.jacksonJsonEncoder = new Jackson2JsonEncoder();
         }
      }

      return this.jacksonJsonEncoder;
   }

   @Deprecated(since = "7.0", forRemoval = true)
   protected Encoder<?> getJackson2JsonEncoder() {
      return this.getJacksonJsonEncoder();
   }

   protected Decoder<?> getGsonDecoder() {
      if (this.gsonDecoder == null) {
         this.gsonDecoder = new GsonDecoder();
      }

      return this.gsonDecoder;
   }

   protected Encoder<?> getGsonEncoder() {
      if (this.gsonEncoder == null) {
         this.gsonEncoder = new GsonEncoder();
      }

      return this.gsonEncoder;
   }

   protected Decoder<?> getJacksonSmileDecoder() {
      if (this.jacksonSmileDecoder == null) {
         if (JACKSON_SMILE_PRESENT) {
            this.jacksonSmileDecoder = new JacksonSmileDecoder();
         } else {
            if (!JACKSON_2_SMILE_PRESENT) {
               throw new IllegalStateException("Jackson Smile support not present");
            }

            this.jacksonSmileDecoder = new Jackson2SmileDecoder();
         }
      }

      return this.jacksonSmileDecoder;
   }

   protected Encoder<?> getJacksonSmileEncoder() {
      if (this.jacksonSmileEncoder == null) {
         if (JACKSON_SMILE_PRESENT) {
            this.jacksonSmileEncoder = new JacksonSmileEncoder();
         } else {
            if (!JACKSON_2_SMILE_PRESENT) {
               throw new IllegalStateException("Jackson Smile support not present");
            }

            this.jacksonSmileEncoder = new Jackson2SmileEncoder();
         }
      }

      return this.jacksonSmileEncoder;
   }

   protected Decoder<?> getJacksonCborDecoder() {
      if (this.jacksonCborDecoder == null) {
         if (JACKSON_CBOR_PRESENT) {
            this.jacksonCborDecoder = new JacksonCborDecoder();
         } else {
            if (!JACKSON_2_CBOR_PRESENT) {
               throw new IllegalStateException("Jackson CBOR support not present");
            }

            this.jacksonCborDecoder = new Jackson2CborDecoder();
         }
      }

      return this.jacksonCborDecoder;
   }

   protected Encoder<?> getJacksonCborEncoder() {
      if (this.jacksonCborEncoder == null) {
         if (JACKSON_CBOR_PRESENT) {
            this.jacksonCborEncoder = new JacksonCborEncoder();
         } else {
            if (!JACKSON_2_CBOR_PRESENT) {
               throw new IllegalStateException("Jackson CBOR support not present");
            }

            this.jacksonCborEncoder = new Jackson2CborEncoder();
         }
      }

      return this.jacksonCborEncoder;
   }

   protected Decoder<?> getJacksonXmlDecoder() {
      if (this.jacksonXmlDecoder == null) {
         if (!JACKSON_XML_PRESENT) {
            throw new IllegalStateException("Jackson XML support not present");
         }

         this.jacksonXmlDecoder = new JacksonXmlDecoder();
      }

      return this.jacksonXmlDecoder;
   }

   protected Encoder<?> getJacksonXmlEncoder() {
      if (this.jacksonXmlEncoder == null) {
         if (!JACKSON_XML_PRESENT) {
            throw new IllegalStateException("Jackson XML support not present");
         }

         this.jacksonXmlEncoder = new JacksonXmlEncoder();
      }

      return this.jacksonXmlEncoder;
   }

   protected Decoder<?> getKotlinSerializationJsonDecoder() {
      if (this.kotlinSerializationJsonDecoder == null) {
         this.kotlinSerializationJsonDecoder = this.jacksonJsonDecoder == null && !JACKSON_PRESENT && !JACKSON_2_PRESENT && !GSON_PRESENT
            ? new KotlinSerializationJsonDecoder(type -> true)
            : new KotlinSerializationJsonDecoder();
      }

      return this.kotlinSerializationJsonDecoder;
   }

   protected Encoder<?> getKotlinSerializationJsonEncoder() {
      if (this.kotlinSerializationJsonEncoder == null) {
         this.kotlinSerializationJsonEncoder = this.jacksonJsonDecoder == null && !JACKSON_PRESENT && !JACKSON_2_PRESENT && !GSON_PRESENT
            ? new KotlinSerializationJsonEncoder(type -> true)
            : new KotlinSerializationJsonEncoder();
      }

      return this.kotlinSerializationJsonEncoder;
   }

   protected Decoder<?> getKotlinSerializationCborDecoder() {
      if (this.kotlinSerializationCborDecoder == null) {
         this.kotlinSerializationCborDecoder = this.jacksonCborDecoder == null && !JACKSON_CBOR_PRESENT
            ? new KotlinSerializationCborDecoder(type -> true)
            : new KotlinSerializationCborDecoder();
      }

      return this.kotlinSerializationCborDecoder;
   }

   protected Encoder<?> getKotlinSerializationCborEncoder() {
      if (this.kotlinSerializationCborEncoder == null) {
         this.kotlinSerializationCborEncoder = this.jacksonCborDecoder == null && !JACKSON_CBOR_PRESENT
            ? new KotlinSerializationCborEncoder(type -> true)
            : new KotlinSerializationCborEncoder();
      }

      return this.kotlinSerializationCborEncoder;
   }

   protected Decoder<?> getKotlinSerializationProtobufDecoder() {
      if (this.kotlinSerializationProtobufDecoder == null) {
         this.kotlinSerializationProtobufDecoder = new KotlinSerializationProtobufDecoder(type -> true);
      }

      return this.kotlinSerializationProtobufDecoder;
   }

   protected Encoder<?> getKotlinSerializationProtobufEncoder() {
      if (this.kotlinSerializationProtobufEncoder == null) {
         this.kotlinSerializationProtobufEncoder = new KotlinSerializationProtobufEncoder(type -> true);
      }

      return this.kotlinSerializationProtobufEncoder;
   }

   static {
      ClassLoader classLoader = BaseCodecConfigurer.class.getClassLoader();
      JACKSON_PRESENT = ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", classLoader);
      JACKSON_2_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
         && ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
      GSON_PRESENT = ClassUtils.isPresent("com.google.gson.Gson", classLoader);
      JACKSON_SMILE_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.smile.SmileMapper", classLoader);
      JACKSON_2_SMILE_PRESENT = JACKSON_2_PRESENT && ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory", classLoader);
      JACKSON_CBOR_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.cbor.CBORMapper", classLoader);
      JACKSON_2_CBOR_PRESENT = JACKSON_2_PRESENT && ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper", classLoader);
      JACKSON_XML_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.xml.XmlMapper", classLoader);
      JAXB_2_PRESENT = ClassUtils.isPresent("jakarta.xml.bind.Binder", classLoader);
      PROTOBUF_PRESENT = ClassUtils.isPresent("com.google.protobuf.Message", classLoader);
      NETTY_BYTE_BUF_PRESENT = ClassUtils.isPresent("io.netty.buffer.ByteBuf", classLoader);
      KOTLIN_SERIALIZATION_CBOR_PRESENT = ClassUtils.isPresent("kotlinx.serialization.cbor.Cbor", classLoader);
      KOTLIN_SERIALIZATION_JSON_PRESENT = ClassUtils.isPresent("kotlinx.serialization.json.Json", classLoader);
      KOTLIN_SERIALIZATION_PROTOBUF_PRESENT = ClassUtils.isPresent("kotlinx.serialization.protobuf.ProtoBuf", classLoader);
   }

   protected class DefaultMultipartCodecs implements CodecConfigurer.MultipartCodecs {
      private final List<HttpMessageWriter<?>> writers = new ArrayList<>();

      DefaultMultipartCodecs() {
      }

      DefaultMultipartCodecs(BaseDefaultCodecs.DefaultMultipartCodecs other) {
         this.writers.addAll(other.writers);
      }

      @Override
      public CodecConfigurer.MultipartCodecs encoder(Encoder<?> encoder) {
         this.writer(new EncoderHttpMessageWriter(encoder));
         BaseDefaultCodecs.this.initTypedWriters();
         return this;
      }

      @Override
      public CodecConfigurer.MultipartCodecs writer(HttpMessageWriter<?> writer) {
         this.writers.add(writer);
         BaseDefaultCodecs.this.initTypedWriters();
         return this;
      }

      List<HttpMessageWriter<?>> getWriters() {
         return this.writers;
      }
   }
}
