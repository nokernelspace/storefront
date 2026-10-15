package org.springframework.http.codec;

import com.fasterxml.jackson.annotation.JsonView;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.http.HttpLogging;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.ser.FilterProvider;

public abstract class JacksonCodecSupport<T extends ObjectMapper> {
   public static final String JSON_VIEW_HINT = JsonView.class.getName();
   public static final String FILTER_PROVIDER_HINT = FilterProvider.class.getName();
   static final String ACTUAL_TYPE_HINT = JacksonCodecSupport.class.getName() + ".actualType";
   private static final String JSON_VIEW_HINT_ERROR = "@JsonView only supported for write hints with exactly 1 class argument: ";
   protected final Log logger = HttpLogging.forLogName(this.getClass());
   private final T defaultMapper;
   protected @Nullable Map<Class<?>, Map<MimeType, T>> mapperRegistrations;
   private final List<MimeType> mimeTypes;
   private static volatile @Nullable List<JacksonModule> modules = null;

   protected JacksonCodecSupport(MapperBuilder<T, ?> builder, MimeType... mimeTypes) {
      Assert.notNull(builder, "MapperBuilder must not be null");
      Assert.notEmpty(mimeTypes, "MimeTypes must not be empty");
      this.defaultMapper = (T)builder.addModules(this.initModules()).build();
      this.mimeTypes = List.of(mimeTypes);
   }

   protected JacksonCodecSupport(T mapper, MimeType... mimeTypes) {
      Assert.notNull(mapper, "ObjectMapper must not be null");
      Assert.notEmpty(mimeTypes, "MimeTypes must not be empty");
      this.defaultMapper = mapper;
      this.mimeTypes = List.of(mimeTypes);
   }

   private List<JacksonModule> initModules() {
      if (modules == null) {
         modules = MapperBuilder.findModules(JacksonCodecSupport.class.getClassLoader());
      }

      return Objects.requireNonNull(modules);
   }

   public T getMapper() {
      return this.defaultMapper;
   }

   public void registerMappersForType(Class<?> clazz, Consumer<Map<MimeType, T>> registrar) {
      if (this.mapperRegistrations == null) {
         this.mapperRegistrations = new LinkedHashMap<>();
      }

      Map<MimeType, T> registrations = this.mapperRegistrations.computeIfAbsent(clazz, c -> new LinkedHashMap<>());
      registrar.accept(registrations);
   }

   public @Nullable Map<MimeType, T> getMappersForType(Class<?> clazz) {
      for (Entry<Class<?>, Map<MimeType, T>> entry : this.getMapperRegistrations().entrySet()) {
         if (entry.getKey().isAssignableFrom(clazz)) {
            return entry.getValue();
         }
      }

      return Collections.emptyMap();
   }

   protected Map<Class<?>, Map<MimeType, T>> getMapperRegistrations() {
      return this.mapperRegistrations != null ? this.mapperRegistrations : Collections.emptyMap();
   }

   protected List<MimeType> getMimeTypes() {
      return this.mimeTypes;
   }

   protected List<MimeType> getMimeTypes(ResolvableType elementType) {
      Class<?> elementClass = elementType.toClass();
      List<MimeType> result = null;

      for (Entry<Class<?>, Map<MimeType, T>> entry : this.getMapperRegistrations().entrySet()) {
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

   protected JavaType getJavaType(Type type, @Nullable Class<?> contextClass) {
      return this.defaultMapper.constructType(GenericTypeResolver.resolveType(type, contextClass));
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

   protected @Nullable T selectMapper(ResolvableType targetType, @Nullable MimeType targetMimeType) {
      if (targetMimeType != null && !CollectionUtils.isEmpty(this.mapperRegistrations)) {
         Class<?> targetClass = targetType.toClass();

         for (Entry<Class<?>, Map<MimeType, T>> typeEntry : this.getMapperRegistrations().entrySet()) {
            if (typeEntry.getKey().isAssignableFrom(targetClass)) {
               for (Entry<MimeType, T> mapperEntry : typeEntry.getValue().entrySet()) {
                  if (mapperEntry.getKey().includes(targetMimeType)) {
                     return mapperEntry.getValue();
                  }
               }

               return null;
            }
         }

         return this.defaultMapper;
      } else {
         return this.defaultMapper;
      }
   }
}
