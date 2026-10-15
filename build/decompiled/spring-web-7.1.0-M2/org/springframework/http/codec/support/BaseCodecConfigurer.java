package org.springframework.http.codec.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.util.Assert;

abstract class BaseCodecConfigurer implements CodecConfigurer {
   protected final BaseDefaultCodecs defaultCodecs;
   protected final BaseCodecConfigurer.DefaultCustomCodecs customCodecs;

   BaseCodecConfigurer(BaseDefaultCodecs defaultCodecs) {
      Assert.notNull(defaultCodecs, "'defaultCodecs' is required");
      this.defaultCodecs = defaultCodecs;
      this.customCodecs = new BaseCodecConfigurer.DefaultCustomCodecs();
      this.defaultCodecs.setPartWritersSupplier(this::getWriters);
   }

   protected BaseCodecConfigurer(BaseCodecConfigurer other) {
      this.defaultCodecs = other.cloneDefaultCodecs();
      this.customCodecs = new BaseCodecConfigurer.DefaultCustomCodecs(other.customCodecs);
      this.defaultCodecs.setPartWritersSupplier(this::getWriters);
   }

   protected abstract BaseDefaultCodecs cloneDefaultCodecs();

   @Override
   public CodecConfigurer.DefaultCodecs defaultCodecs() {
      return this.defaultCodecs;
   }

   @Override
   public void registerDefaults(boolean shouldRegister) {
      this.defaultCodecs.registerDefaults(shouldRegister);
   }

   @Override
   public CodecConfigurer.CustomCodecs customCodecs() {
      return this.customCodecs;
   }

   @Override
   public List<HttpMessageReader<?>> getReaders() {
      this.defaultCodecs.applyDefaultConfig(this.customCodecs);
      List<HttpMessageReader<?>> result = new ArrayList<>();
      result.addAll(this.customCodecs.getTypedReaders().keySet());
      result.addAll(this.defaultCodecs.getTypedReaders());
      result.addAll(this.customCodecs.getObjectReaders().keySet());
      result.addAll(this.defaultCodecs.getObjectReaders());
      result.addAll(this.defaultCodecs.getCatchAllReaders());
      return result;
   }

   @Override
   public List<HttpMessageWriter<?>> getWriters() {
      this.defaultCodecs.applyDefaultConfig(this.customCodecs);
      List<HttpMessageWriter<?>> result = new ArrayList<>();
      result.addAll(this.customCodecs.getTypedWriters().keySet());
      result.addAll(this.defaultCodecs.getTypedWriters());
      result.addAll(this.customCodecs.getObjectWriters().keySet());
      result.addAll(this.defaultCodecs.getObjectWriters());
      result.addAll(this.defaultCodecs.getCatchAllWriters());
      return result;
   }

   @Override
   public abstract CodecConfigurer clone();

   protected static final class DefaultCustomCodecs implements CodecConfigurer.CustomCodecs {
      private final Map<HttpMessageReader<?>, Boolean> typedReaders = new LinkedHashMap<>(4);
      private final Map<HttpMessageWriter<?>, Boolean> typedWriters = new LinkedHashMap<>(4);
      private final Map<HttpMessageReader<?>, Boolean> objectReaders = new LinkedHashMap<>(4);
      private final Map<HttpMessageWriter<?>, Boolean> objectWriters = new LinkedHashMap<>(4);
      private final List<Consumer<CodecConfigurer.DefaultCodecConfig>> defaultConfigConsumers = new ArrayList<>(4);

      DefaultCustomCodecs() {
      }

      DefaultCustomCodecs(BaseCodecConfigurer.DefaultCustomCodecs other) {
         this.typedReaders.putAll(other.typedReaders);
         this.typedWriters.putAll(other.typedWriters);
         this.objectReaders.putAll(other.objectReaders);
         this.objectWriters.putAll(other.objectWriters);
      }

      @Override
      public void register(Object codec) {
         this.addCodec(codec, false);
      }

      @Override
      public void registerWithDefaultConfig(Object codec) {
         this.addCodec(codec, true);
      }

      @Override
      public void registerWithDefaultConfig(Object codec, Consumer<CodecConfigurer.DefaultCodecConfig> configConsumer) {
         this.addCodec(codec, false);
         this.defaultConfigConsumers.add(configConsumer);
      }

      private void addCodec(Object codec, boolean applyDefaultConfig) {
         if (codec instanceof Decoder<?> decoder) {
            codec = new DecoderHttpMessageReader(decoder);
         } else if (codec instanceof Encoder<?> encoder) {
            codec = new EncoderHttpMessageWriter(encoder);
         }

         if (codec instanceof HttpMessageReader<?> reader) {
            boolean canReadToObject = reader.canRead(ResolvableType.forClass(Object.class), null);
            (canReadToObject ? this.objectReaders : this.typedReaders).put(reader, applyDefaultConfig);
         } else {
            if (!(codec instanceof HttpMessageWriter<?> writer)) {
               throw new IllegalArgumentException("Unexpected codec type: " + codec.getClass().getName());
            }

            boolean canWriteObject = writer.canWrite(ResolvableType.forClass(Object.class), null);
            (canWriteObject ? this.objectWriters : this.typedWriters).put(writer, applyDefaultConfig);
         }
      }

      Map<HttpMessageReader<?>, Boolean> getTypedReaders() {
         return this.typedReaders;
      }

      Map<HttpMessageWriter<?>, Boolean> getTypedWriters() {
         return this.typedWriters;
      }

      Map<HttpMessageReader<?>, Boolean> getObjectReaders() {
         return this.objectReaders;
      }

      Map<HttpMessageWriter<?>, Boolean> getObjectWriters() {
         return this.objectWriters;
      }

      List<Consumer<CodecConfigurer.DefaultCodecConfig>> getDefaultConfigConsumers() {
         return this.defaultConfigConsumers;
      }
   }
}
