package org.springframework.http.codec;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerialFormat;
import kotlinx.serialization.SerializersKt;
import org.jspecify.annotations.Nullable;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.MimeType;

public abstract class KotlinSerializationSupport<T extends SerialFormat> {
   private final Map<Type, KSerializer<Object>> typeSerializerCache = new ConcurrentReferenceHashMap();
   private final Map<KType, KSerializer<Object>> kTypeSerializerCache = new ConcurrentReferenceHashMap();
   private final T format;
   private final List<MimeType> supportedMimeTypes;
   private final Predicate<ResolvableType> typePredicate;

   protected KotlinSerializationSupport(T format, MimeType... supportedMimeTypes) {
      this.format = format;
      this.typePredicate = KotlinDetector::hasSerializableAnnotation;
      this.supportedMimeTypes = Arrays.asList(supportedMimeTypes);
   }

   protected KotlinSerializationSupport(T format, Predicate<ResolvableType> typePredicate, MimeType... supportedMimeTypes) {
      this.format = format;
      this.typePredicate = typePredicate;
      this.supportedMimeTypes = Arrays.asList(supportedMimeTypes);
   }

   protected final T format() {
      return this.format;
   }

   protected final List<MimeType> supportedMimeTypes() {
      return this.supportedMimeTypes;
   }

   protected final boolean canSerialize(ResolvableType type, @Nullable MimeType mimeType) {
      return this.typePredicate.test(type) && !ResolvableType.NONE.equals(type)
         ? this.serializer(type) != null && this.supports(mimeType) && !ServerSentEvent.class.isAssignableFrom(type.toClass())
         : false;
   }

   private boolean supports(@Nullable MimeType mimeType) {
      if (mimeType == null) {
         return true;
      }

      for (MimeType candidate : this.supportedMimeTypes) {
         if (candidate.isCompatibleWith(mimeType)) {
            return true;
         }
      }

      return false;
   }

   protected final @Nullable KSerializer<Object> serializer(ResolvableType resolvableType) {
      if (resolvableType.getSource() instanceof MethodParameter parameter) {
         Method method = parameter.getMethod();
         Assert.notNull(method, "Method must not be null");
         if (KotlinDetector.isKotlinType(method.getDeclaringClass())) {
            KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
            if (function != null) {
               KType type = parameter.getParameterIndex() == -1
                  ? function.getReturnType()
                  : ((KParameter)KCallables.getValueParameters(function).get(parameter.getParameterIndex())).getType();
               KSerializer<Object> serializer = this.kTypeSerializerCache.get(type);
               if (serializer == null) {
                  try {
                     serializer = SerializersKt.serializerOrNull(this.format.getSerializersModule(), type);
                  } catch (IllegalArgumentException var8) {
                  }

                  if (serializer != null) {
                     this.kTypeSerializerCache.put(type, serializer);
                  }
               }

               return serializer;
            }
         }
      }

      Type type = resolvableType.getType();
      KSerializer<Object> serializer = this.typeSerializerCache.get(type);
      if (serializer == null) {
         try {
            serializer = SerializersKt.serializerOrNull(this.format.getSerializersModule(), type);
         } catch (IllegalArgumentException var9) {
         }

         if (serializer != null) {
            this.typeSerializerCache.put(type, serializer);
         }
      }

      return serializer;
   }
}
