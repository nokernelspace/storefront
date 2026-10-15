package org.springframework.http.converter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlin.reflect.KType;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerialFormat;
import kotlinx.serialization.SerializersKt;
import org.jspecify.annotations.Nullable;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.util.ConcurrentReferenceHashMap;

public abstract class AbstractKotlinSerializationHttpMessageConverter<T extends SerialFormat> extends AbstractSmartHttpMessageConverter<Object> {
   private final Map<KType, KSerializer<Object>> kTypeSerializerCache = new ConcurrentReferenceHashMap();
   private final Map<Type, KSerializer<Object>> typeSerializerCache = new ConcurrentReferenceHashMap();
   private final T format;
   private final Predicate<ResolvableType> typePredicate;

   protected AbstractKotlinSerializationHttpMessageConverter(T format, MediaType... supportedMediaTypes) {
      super(supportedMediaTypes);
      this.typePredicate = KotlinDetector::hasSerializableAnnotation;
      this.format = format;
   }

   protected AbstractKotlinSerializationHttpMessageConverter(T format, Predicate<ResolvableType> typePredicate, MediaType... supportedMediaTypes) {
      super(supportedMediaTypes);
      this.typePredicate = typePredicate;
      this.format = format;
   }

   @Override
   public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
      return this.getSupportedMediaTypes();
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      ResolvableType type = ResolvableType.forClass(clazz);
      return !this.typePredicate.test(type) ? false : this.serializer(type, null) != null;
   }

   @Override
   public boolean canRead(ResolvableType type, @Nullable MediaType mediaType) {
      return this.typePredicate.test(type) && !ResolvableType.NONE.equals(type) ? this.serializer(type, null) != null && this.canRead(mediaType) : false;
   }

   @Override
   public boolean canWrite(ResolvableType type, Class<?> valueClass, @Nullable MediaType mediaType) {
      ResolvableType resolvableType = ResolvableType.NONE.equals(type) ? ResolvableType.forClass(valueClass) : type;
      return !this.typePredicate.test(resolvableType) ? false : this.serializer(resolvableType, null) != null && this.canWrite(mediaType);
   }

   @Override
   public boolean canWriteRepeatedly(Object o, @Nullable MediaType contentType) {
      return true;
   }

   @Override
   public final Object read(ResolvableType type, HttpInputMessage inputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotReadableException {
      KSerializer<Object> serializer = this.serializer(type, hints);
      if (serializer == null) {
         throw new HttpMessageNotReadableException("Could not find KSerializer for " + type, inputMessage);
      } else {
         return this.readInternal(serializer, this.format, inputMessage);
      }
   }

   protected abstract Object readInternal(KSerializer<Object> serializer, T format, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException;

   @Override
   protected final void writeInternal(Object object, ResolvableType type, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotWritableException {
      ResolvableType resolvableType = ResolvableType.NONE.equals(type) ? ResolvableType.forInstance(object) : type;
      KSerializer<Object> serializer = this.serializer(resolvableType, hints);
      if (serializer == null) {
         throw new HttpMessageNotWritableException("Could not find KSerializer for " + resolvableType);
      }

      this.writeInternal(object, serializer, this.format, outputMessage);
   }

   protected abstract void writeInternal(Object object, KSerializer<Object> serializer, T format, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException;

   private @Nullable KSerializer<Object> serializer(ResolvableType resolvableType, @Nullable Map<String, Object> hints) {
      if (hints != null && hints.containsKey(KType.class.getName())) {
         KType type = (KType)hints.get(KType.class.getName());
         KSerializer<Object> serializer = this.kTypeSerializerCache.get(type);
         if (serializer == null) {
            try {
               serializer = SerializersKt.serializerOrNull(this.format.getSerializersModule(), type);
            } catch (IllegalArgumentException var6) {
            }

            if (serializer != null) {
               this.kTypeSerializerCache.put(type, serializer);
            }
         }

         return serializer;
      } else {
         Type type = resolvableType.getType();
         KSerializer<Object> serializer = this.typeSerializerCache.get(type);
         if (serializer == null) {
            try {
               serializer = SerializersKt.serializerOrNull(this.format.getSerializersModule(), type);
            } catch (IllegalArgumentException var7) {
            }

            if (serializer != null) {
               this.typeSerializerCache.put(type, serializer);
            }
         }

         return serializer;
      }
   }

   @Override
   protected boolean supportsRepeatableWrites(Object object) {
      return true;
   }
}
