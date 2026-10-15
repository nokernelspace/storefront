package org.springframework.http.converter;

import com.fasterxml.jackson.annotation.JsonView;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJacksonInputMessage;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.TypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.ser.FilterProvider;

public abstract class AbstractJacksonHttpMessageConverter<T extends ObjectMapper> extends AbstractSmartHttpMessageConverter<Object> {
   private static final String JSON_VIEW_HINT = JsonView.class.getName();
   private static final String FILTER_PROVIDER_HINT = FilterProvider.class.getName();
   private static final Map<String, JsonEncoding> ENCODINGS = CollectionUtils.newHashMap(JsonEncoding.values().length);
   private static volatile @Nullable List<JacksonModule> modules = null;
   protected final T defaultMapper;
   private @Nullable Map<Class<?>, Map<MediaType, T>> mapperRegistrations;
   private final @Nullable PrettyPrinter ssePrettyPrinter;

   private AbstractJacksonHttpMessageConverter(MapperBuilder<T, ?> builder) {
      this.defaultMapper = (T)builder.addModules(this.initModules()).build();
      this.ssePrettyPrinter = this.initSsePrettyPrinter();
   }

   protected AbstractJacksonHttpMessageConverter(MapperBuilder<T, ?> builder, MediaType supportedMediaType) {
      this(builder);
      this.setSupportedMediaTypes(Collections.singletonList(supportedMediaType));
   }

   protected AbstractJacksonHttpMessageConverter(MapperBuilder<T, ?> builder, MediaType... supportedMediaTypes) {
      this(builder);
      this.setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
   }

   protected AbstractJacksonHttpMessageConverter(T mapper) {
      this.defaultMapper = mapper;
      this.ssePrettyPrinter = this.initSsePrettyPrinter();
   }

   protected AbstractJacksonHttpMessageConverter(T mapper, MediaType supportedMediaType) {
      this(mapper);
      this.setSupportedMediaTypes(Collections.singletonList(supportedMediaType));
   }

   protected AbstractJacksonHttpMessageConverter(T mapper, MediaType... supportedMediaTypes) {
      this(mapper);
      this.setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
   }

   private List<JacksonModule> initModules() {
      if (modules == null) {
         modules = MapperBuilder.findModules(AbstractJacksonHttpMessageConverter.class.getClassLoader());
      }

      return Objects.requireNonNull(modules);
   }

   private PrettyPrinter initSsePrettyPrinter() {
      DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
      prettyPrinter.indentObjectsWith(new DefaultIndenter("  ", "\ndata: "));
      return prettyPrinter;
   }

   @Override
   public void setSupportedMediaTypes(List<MediaType> supportedMediaTypes) {
      super.setSupportedMediaTypes(supportedMediaTypes);
   }

   public T getMapper() {
      return this.defaultMapper;
   }

   public void registerMappersForType(Class<?> clazz, Consumer<Map<MediaType, T>> registrar) {
      if (this.mapperRegistrations == null) {
         this.mapperRegistrations = new LinkedHashMap<>();
      }

      Map<MediaType, T> registrations = this.mapperRegistrations.computeIfAbsent(clazz, c -> new LinkedHashMap<>());
      registrar.accept(registrations);
   }

   public Map<MediaType, T> getMappersForType(Class<?> clazz) {
      for (Entry<Class<?>, Map<MediaType, T>> entry : this.getMapperRegistrations().entrySet()) {
         if (entry.getKey().isAssignableFrom(clazz)) {
            return entry.getValue();
         }
      }

      return Collections.emptyMap();
   }

   @Override
   public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
      List<MediaType> result = null;

      for (Entry<Class<?>, Map<MediaType, T>> entry : this.getMapperRegistrations().entrySet()) {
         if (entry.getKey().isAssignableFrom(clazz)) {
            result = result != null ? result : new ArrayList<>(entry.getValue().size());
            result.addAll(entry.getValue().keySet());
         }
      }

      if (!CollectionUtils.isEmpty(result)) {
         return result;
      } else {
         return ProblemDetail.class.isAssignableFrom(clazz) ? this.getMediaTypesForProblemDetail() : this.getSupportedMediaTypes();
      }
   }

   private Map<Class<?>, Map<MediaType, T>> getMapperRegistrations() {
      return this.mapperRegistrations != null ? this.mapperRegistrations : Collections.emptyMap();
   }

   protected List<MediaType> getMediaTypesForProblemDetail() {
      return Collections.emptyList();
   }

   @Override
   public boolean canRead(ResolvableType type, @Nullable MediaType mediaType) {
      return !this.canRead(mediaType) ? false : this.mapperRegistrations == null || this.selectMapper(type.toClass(), mediaType) != null;
   }

   @Override
   public boolean canWrite(ResolvableType type, Class<?> valueClass, @Nullable MediaType mediaType) {
      if (!this.canWrite(mediaType)) {
         return false;
      }

      if (mediaType != null && mediaType.getCharset() != null) {
         Charset charset = mediaType.getCharset();
         if (!ENCODINGS.containsKey(charset.name())) {
            return false;
         }
      }

      if (MappingJacksonValue.class.isAssignableFrom(valueClass)) {
         throw new UnsupportedOperationException("MappingJacksonValue is not supported, use hints instead");
      } else {
         return this.mapperRegistrations == null || this.selectMapper(valueClass, mediaType) != null;
      }
   }

   @Override
   public boolean canWriteRepeatedly(Object o, @Nullable MediaType contentType) {
      return true;
   }

   private @Nullable T selectMapper(Class<?> targetType, @Nullable MediaType targetMediaType) {
      if (targetMediaType != null && !CollectionUtils.isEmpty(this.mapperRegistrations)) {
         for (Entry<Class<?>, Map<MediaType, T>> typeEntry : this.getMapperRegistrations().entrySet()) {
            if (typeEntry.getKey().isAssignableFrom(targetType)) {
               for (Entry<MediaType, T> mapperEntry : typeEntry.getValue().entrySet()) {
                  if (mapperEntry.getKey().includes(targetMediaType)) {
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

   @Override
   public Object read(ResolvableType type, HttpInputMessage inputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotReadableException {
      Class<?> contextClass = type.getSource() instanceof MethodParameter parameter
         ? parameter.getContainingClass()
         : (hints != null ? (Class)hints.get("contextClass") : null);
      JavaType javaType = this.getJavaType(type.getType(), contextClass);
      return this.readJavaType(javaType, inputMessage, hints);
   }

   @Override
   protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      JavaType javaType = this.getJavaType(clazz, null);
      return this.readJavaType(javaType, inputMessage, null);
   }

   private Object readJavaType(JavaType javaType, HttpInputMessage inputMessage, @Nullable Map<String, Object> hints) throws IOException {
      MediaType contentType = inputMessage.getHeaders().getContentType();
      Charset charset = this.getCharset(contentType);
      T mapper = this.selectMapper(javaType.getRawClass(), contentType);
      Assert.state(mapper != null, () -> "No ObjectMapper for " + javaType);
      boolean isUnicode = ENCODINGS.containsKey(charset.name()) || "UTF-16".equals(charset.name()) || "UTF-32".equals(charset.name());

      try {
         InputStream inputStream = StreamUtils.nonClosing(inputMessage.getBody());
         if (inputMessage instanceof MappingJacksonInputMessage) {
            throw new UnsupportedOperationException("MappingJacksonInputMessage is not supported, use hints instead");
         }

         ObjectReader objectReader = mapper.readerFor(javaType);
         if (hints != null && hints.containsKey(JSON_VIEW_HINT)) {
            objectReader = objectReader.withView((Class)hints.get(JSON_VIEW_HINT));
         }

         objectReader = this.customizeReader(objectReader, javaType);
         if (isUnicode) {
            return objectReader.readValue(inputStream);
         }

         Reader reader = new InputStreamReader(inputStream, charset);
         return objectReader.readValue(reader);
      } catch (InvalidDefinitionException ex) {
         throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
      } catch (JacksonException ex) {
         throw new HttpMessageNotReadableException("JSON parse error: " + ex.getOriginalMessage(), ex, inputMessage);
      }
   }

   protected ObjectReader customizeReader(ObjectReader reader, JavaType javaType) {
      return reader;
   }

   protected Charset getCharset(@Nullable MediaType contentType) {
      return contentType != null && contentType.getCharset() != null ? contentType.getCharset() : StandardCharsets.UTF_8;
   }

   @Override
   protected void writeInternal(Object object, ResolvableType resolvableType, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotWritableException {
      MediaType contentType = outputMessage.getHeaders().getContentType();
      JsonEncoding encoding = this.getJsonEncoding(contentType);
      Class<?> clazz = object.getClass();
      T mapper = this.selectMapper(clazz, contentType);
      Assert.state(mapper != null, () -> "No ObjectMapper for " + clazz.getName());
      OutputStream outputStream = StreamUtils.nonClosing(outputMessage.getBody());
      Class<?> jsonView = null;
      FilterProvider filters = null;
      JavaType javaType = null;
      Type type = resolvableType.getType();
      if (TypeUtils.isAssignable(type, object.getClass())) {
         javaType = this.getJavaType(type, null);
      }

      if (hints != null) {
         jsonView = (Class<?>)hints.get(JSON_VIEW_HINT);
         filters = (FilterProvider)hints.get(FILTER_PROVIDER_HINT);
      }

      ObjectWriter objectWriter = jsonView != null ? mapper.writerWithView(jsonView) : mapper.writer();
      if (filters != null) {
         objectWriter = objectWriter.with(filters);
      }

      if (javaType != null && (javaType.isContainerType() || javaType.isTypeOrSubTypeOf(Optional.class))) {
         objectWriter = objectWriter.forType(javaType);
      }

      SerializationConfig config = objectWriter.getConfig();
      if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) && config.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
         objectWriter = objectWriter.with(this.ssePrettyPrinter);
      }

      objectWriter = this.customizeWriter(objectWriter, javaType, contentType);

      try {
         JsonGenerator generator = objectWriter.createGenerator(outputStream, encoding);

         try {
            this.writePrefix(generator, object);
            objectWriter.writeValue(generator, object);
            this.writeSuffix(generator, object);
            generator.flush();
         } catch (Throwable var20) {
            if (generator != null) {
               try {
                  generator.close();
               } catch (Throwable var19) {
                  var20.addSuppressed(var19);
               }
            }

            throw var20;
         }

         if (generator != null) {
            generator.close();
         }
      } catch (InvalidDefinitionException ex) {
         throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
      } catch (JacksonException ex) {
         throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getOriginalMessage(), ex);
      }
   }

   protected ObjectWriter customizeWriter(ObjectWriter writer, @Nullable JavaType javaType, @Nullable MediaType contentType) {
      return writer;
   }

   protected void writePrefix(JsonGenerator generator, Object object) {
   }

   protected void writeSuffix(JsonGenerator generator, Object object) {
   }

   protected JavaType getJavaType(Type type, @Nullable Class<?> contextClass) {
      return this.defaultMapper.constructType(GenericTypeResolver.resolveType(type, contextClass));
   }

   protected JsonEncoding getJsonEncoding(@Nullable MediaType contentType) {
      if (contentType != null && contentType.getCharset() != null) {
         Charset charset = contentType.getCharset();
         JsonEncoding encoding = ENCODINGS.get(charset.name());
         if (encoding != null) {
            return encoding;
         }
      }

      return JsonEncoding.UTF8;
   }

   @Override
   protected boolean supportsRepeatableWrites(Object o) {
      return true;
   }

   static {
      for (JsonEncoding encoding : JsonEncoding.values()) {
         ENCODINGS.put(encoding.getJavaName(), encoding);
      }

      ENCODINGS.put("US-ASCII", JsonEncoding.UTF8);
   }
}
