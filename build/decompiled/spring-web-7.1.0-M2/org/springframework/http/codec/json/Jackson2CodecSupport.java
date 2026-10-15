package org.springframework.http.codec.json;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpLogging;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.ObjectUtils;

@Deprecated(since = "7.0", forRemoval = true)
public abstract class Jackson2CodecSupport {
   public static final String JSON_VIEW_HINT = Jackson2CodecSupport.class.getName() + ".jsonView";
   static final String ACTUAL_TYPE_HINT = Jackson2CodecSupport.class.getName() + ".actualType";
   private static final String JSON_VIEW_HINT_ERROR = "@JsonView only supported for write hints with exactly 1 class argument: ";
   private static final List<MimeType> defaultMimeTypes = List.of(
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL
   );
   protected final Log logger = HttpLogging.forLogName(this.getClass());
   private ObjectMapper defaultObjectMapper;
   private @Nullable Map<Class<?>, Map<MimeType, ObjectMapper>> objectMapperRegistrations;
   private final List<MimeType> mimeTypes;

   protected Jackson2CodecSupport(ObjectMapper objectMapper, MimeType... mimeTypes) {
      Assert.notNull(objectMapper, "ObjectMapper must not be null");
      this.defaultObjectMapper = objectMapper;
      this.mimeTypes = !ObjectUtils.isEmpty(mimeTypes) ? List.of(mimeTypes) : defaultMimeTypes;
   }

   public void setObjectMapper(ObjectMapper objectMapper) {
      Assert.notNull(objectMapper, "ObjectMapper must not be null");
      this.defaultObjectMapper = objectMapper;
   }

   public ObjectMapper getObjectMapper() {
      return this.defaultObjectMapper;
   }

   public void registerObjectMappersForType(Class<?> clazz, Consumer<Map<MimeType, ObjectMapper>> registrar) {
      if (this.objectMapperRegistrations == null) {
         this.objectMapperRegistrations = new LinkedHashMap<>();
      }

      Map<MimeType, ObjectMapper> registrations = this.objectMapperRegistrations.computeIfAbsent(clazz, key -> new LinkedHashMap<>());
      registrar.accept(registrations);
   }

   public @Nullable Map<MimeType, ObjectMapper> getObjectMappersForType(Class<?> clazz) {
      for (Entry<Class<?>, Map<MimeType, ObjectMapper>> entry : this.getObjectMapperRegistrations().entrySet()) {
         if (entry.getKey().isAssignableFrom(clazz)) {
            return entry.getValue();
         }
      }

      return Collections.emptyMap();
   }

   protected Map<Class<?>, Map<MimeType, ObjectMapper>> getObjectMapperRegistrations() {
      return this.objectMapperRegistrations != null ? this.objectMapperRegistrations : Collections.emptyMap();
   }

   protected List<MimeType> getMimeTypes() {
      return this.mimeTypes;
   }

   protected List<MimeType> getMimeTypes(ResolvableType elementType) {
      Class<?> elementClass = elementType.toClass();
      List<MimeType> result = null;

      for (Entry<Class<?>, Map<MimeType, ObjectMapper>> entry : this.getObjectMapperRegistrations().entrySet()) {
         if (entry.getKey().isAssignableFrom(elementClass)) {
            result = result != null ? result : new ArrayList<>(entry.getValue().size());
            result.addAll(entry.getValue().keySet());
         }
      }

      if (!CollectionUtils.isEmpty(result)) {
         return result;
      } else {
         return ProblemDetail.class.isAssignableFrom(elementClass) ? this.getMediaTypesForProblemDetail() : this.getMimeTypes();
      }
   }

   protected List<MimeType> getMediaTypesForProblemDetail() {
      return Collections.emptyList();
   }

   protected boolean supportsMimeType(@Nullable MimeType mimeType) {
      if (mimeType == null) {
         return true;
      }

      for (MimeType supportedMimeType : this.mimeTypes) {
         if (supportedMimeType.isCompatibleWith(mimeType)) {
            return true;
         }
      }

      return false;
   }

   protected void logWarningIfNecessary(Type type, @Nullable Throwable cause) {
      if (cause != null) {
         if (this.logger.isDebugEnabled()) {
            String msg = "Failed to evaluate Jackson " + (type instanceof JavaType ? "de" : "") + "serialization for type [" + type + "]";
            this.logger.debug(msg, cause);
         }
      }
   }

   protected JavaType getJavaType(Type type, @Nullable Class<?> contextClass) {
      return this.defaultObjectMapper.constructType(GenericTypeResolver.resolveType(type, contextClass));
   }

   protected Map<String, Object> getHints(ResolvableType resolvableType) {
      MethodParameter param = this.getParameter(resolvableType);
      if (param != null) {
         Map<String, Object> hints = null;
         if (resolvableType.hasGenerics()) {
            hints = new HashMap<>(2);
            hints.put(ACTUAL_TYPE_HINT, resolvableType);
         }

         JsonView annotation = this.getAnnotation(param, JsonView.class);
         if (annotation != null) {
            Class<?>[] classes = annotation.value();
            Assert.isTrue(classes.length == 1, () -> "@JsonView only supported for write hints with exactly 1 class argument: " + param);
            hints = hints != null ? hints : new HashMap<>(1);
            hints.put(JSON_VIEW_HINT, classes[0]);
         }

         if (hints != null) {
            return hints;
         }
      }

      return Hints.none();
   }

   protected @Nullable MethodParameter getParameter(ResolvableType type) {
      return type.getSource() instanceof MethodParameter methodParameter ? methodParameter : null;
   }

   protected abstract <A extends Annotation> @Nullable A getAnnotation(MethodParameter parameter, Class<A> annotType);

   protected @Nullable ObjectMapper selectObjectMapper(ResolvableType targetType, @Nullable MimeType targetMimeType) {
      if (targetMimeType != null && !CollectionUtils.isEmpty(this.objectMapperRegistrations)) {
         Class<?> targetClass = targetType.toClass();

         for (Entry<Class<?>, Map<MimeType, ObjectMapper>> typeEntry : this.getObjectMapperRegistrations().entrySet()) {
            if (typeEntry.getKey().isAssignableFrom(targetClass)) {
               for (Entry<MimeType, ObjectMapper> objectMapperEntry : typeEntry.getValue().entrySet()) {
                  if (objectMapperEntry.getKey().includes(targetMimeType)) {
                     return objectMapperEntry.getValue();
                  }
               }

               return null;
            }
         }

         return this.defaultObjectMapper;
      } else {
         return this.defaultObjectMapper;
      }
   }
}
