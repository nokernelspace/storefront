package org.springframework.http.converter.json;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.ser.FilterProvider;
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
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.TypeUtils;

@Deprecated(since = "7.0", forRemoval = true)
public abstract class AbstractJackson2HttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
   private static final Map<String, JsonEncoding> ENCODINGS = CollectionUtils.newHashMap(JsonEncoding.values().length);
   protected ObjectMapper defaultObjectMapper;
   private @Nullable Map<Class<?>, Map<MediaType, ObjectMapper>> objectMapperRegistrations;
   private @Nullable Boolean prettyPrint;
   private final @Nullable PrettyPrinter ssePrettyPrinter;

   protected AbstractJackson2HttpMessageConverter(ObjectMapper objectMapper) {
      this.defaultObjectMapper = objectMapper;
      DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
      prettyPrinter.indentObjectsWith(new DefaultIndenter("  ", "\ndata: "));
      this.ssePrettyPrinter = prettyPrinter;
   }

   protected AbstractJackson2HttpMessageConverter(ObjectMapper objectMapper, MediaType supportedMediaType) {
      this(objectMapper);
      this.setSupportedMediaTypes(Collections.singletonList(supportedMediaType));
   }

   protected AbstractJackson2HttpMessageConverter(ObjectMapper objectMapper, MediaType... supportedMediaTypes) {
      this(objectMapper);
      this.setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
   }

   @Override
   public void setSupportedMediaTypes(List<MediaType> supportedMediaTypes) {
      super.setSupportedMediaTypes(supportedMediaTypes);
   }

   public void setObjectMapper(ObjectMapper objectMapper) {
      Assert.notNull(objectMapper, "ObjectMapper must not be null");
      this.defaultObjectMapper = objectMapper;
      this.configurePrettyPrint();
   }

   public ObjectMapper getObjectMapper() {
      return this.defaultObjectMapper;
   }

   public void registerObjectMappersForType(Class<?> clazz, Consumer<Map<MediaType, ObjectMapper>> registrar) {
      if (this.objectMapperRegistrations == null) {
         this.objectMapperRegistrations = new LinkedHashMap<>();
      }

      Map<MediaType, ObjectMapper> registrations = this.objectMapperRegistrations.computeIfAbsent(clazz, key -> new LinkedHashMap<>());
      registrar.accept(registrations);
   }

   public Map<MediaType, ObjectMapper> getObjectMappersForType(Class<?> clazz) {
      for (Entry<Class<?>, Map<MediaType, ObjectMapper>> entry : this.getObjectMapperRegistrations().entrySet()) {
         if (entry.getKey().isAssignableFrom(clazz)) {
            return entry.getValue();
         }
      }

      return Collections.emptyMap();
   }

   @Override
   public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
      List<MediaType> result = null;

      for (Entry<Class<?>, Map<MediaType, ObjectMapper>> entry : this.getObjectMapperRegistrations().entrySet()) {
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

   private Map<Class<?>, Map<MediaType, ObjectMapper>> getObjectMapperRegistrations() {
      return this.objectMapperRegistrations != null ? this.objectMapperRegistrations : Collections.emptyMap();
   }

   protected List<MediaType> getMediaTypesForProblemDetail() {
      return Collections.emptyList();
   }

   public void setPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
      this.configurePrettyPrint();
   }

   private void configurePrettyPrint() {
      if (this.prettyPrint != null) {
         this.defaultObjectMapper.configure(SerializationFeature.INDENT_OUTPUT, this.prettyPrint);
      }
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canRead(clazz, null, mediaType);
   }

   @Override
   public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
      if (!this.canRead(mediaType)) {
         return false;
      }

      JavaType javaType = this.getJavaType(type, contextClass);
      ObjectMapper objectMapper = this.selectObjectMapper(javaType.getRawClass(), mediaType);
      if (objectMapper == null) {
         return false;
      }

      AtomicReference<Throwable> causeRef = new AtomicReference<>();
      if (objectMapper.canDeserialize(javaType, causeRef)) {
         return true;
      }

      this.logWarningIfNecessary(javaType, causeRef.get());
      return false;
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      if (!this.canWrite(mediaType)) {
         return false;
      }

      if (mediaType != null && mediaType.getCharset() != null) {
         Charset charset = mediaType.getCharset();
         if (!ENCODINGS.containsKey(charset.name())) {
            return false;
         }
      }

      ObjectMapper objectMapper = this.selectObjectMapper(clazz, mediaType);
      if (objectMapper == null) {
         return false;
      }

      AtomicReference<Throwable> causeRef = new AtomicReference<>();
      if (objectMapper.canSerialize(clazz, causeRef)) {
         return true;
      }

      this.logWarningIfNecessary(clazz, causeRef.get());
      return false;
   }

   @Override
   public boolean canWriteRepeatedly(Object o, @Nullable MediaType contentType) {
      return true;
   }

   private @Nullable ObjectMapper selectObjectMapper(Class<?> targetType, @Nullable MediaType targetMediaType) {
      if (targetMediaType != null && !CollectionUtils.isEmpty(this.objectMapperRegistrations)) {
         for (Entry<Class<?>, Map<MediaType, ObjectMapper>> typeEntry : this.getObjectMapperRegistrations().entrySet()) {
            if (typeEntry.getKey().isAssignableFrom(targetType)) {
               for (Entry<MediaType, ObjectMapper> objectMapperEntry : typeEntry.getValue().entrySet()) {
                  if (objectMapperEntry.getKey().includes(targetMediaType)) {
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

   protected void logWarningIfNecessary(Type type, @Nullable Throwable cause) {
      if (cause != null) {
         boolean debugLevel = cause instanceof JsonMappingException && cause.getMessage() != null && cause.getMessage().startsWith("Cannot find");
         if (debugLevel ? this.logger.isDebugEnabled() : this.logger.isWarnEnabled()) {
            String msg = "Failed to evaluate Jackson " + (type instanceof JavaType ? "de" : "") + "serialization for type [" + type + "]";
            if (debugLevel) {
               this.logger.debug(msg, cause);
            } else if (this.logger.isDebugEnabled()) {
               this.logger.warn(msg, cause);
            } else {
               this.logger.warn(msg + ": " + cause);
            }
         }
      }
   }

   @Override
   public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      JavaType javaType = this.getJavaType(type, contextClass);
      return this.readJavaType(javaType, inputMessage);
   }

   @Override
   protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      JavaType javaType = this.getJavaType(clazz, null);
      return this.readJavaType(javaType, inputMessage);
   }

   private Object readJavaType(JavaType javaType, HttpInputMessage inputMessage) throws IOException {
      MediaType contentType = inputMessage.getHeaders().getContentType();
      Charset charset = this.getCharset(contentType);
      ObjectMapper objectMapper = this.selectObjectMapper(javaType.getRawClass(), contentType);
      Assert.state(objectMapper != null, () -> "No ObjectMapper for " + javaType);
      boolean isUnicode = ENCODINGS.containsKey(charset.name()) || "UTF-16".equals(charset.name()) || "UTF-32".equals(charset.name());

      try {
         InputStream inputStream = StreamUtils.nonClosing(inputMessage.getBody());
         if (inputMessage instanceof MappingJacksonInputMessage mappingJacksonInputMessage) {
            Class<?> deserializationView = mappingJacksonInputMessage.getDeserializationView();
            if (deserializationView != null) {
               ObjectReader objectReader = objectMapper.readerWithView(deserializationView).forType(javaType);
               objectReader = this.customizeReader(objectReader, javaType);
               if (isUnicode) {
                  return objectReader.readValue(inputStream);
               }

               Reader reader = new InputStreamReader(inputStream, charset);
               return objectReader.readValue(reader);
            }
         }

         ObjectReader objectReader = objectMapper.reader().forType(javaType);
         objectReader = this.customizeReader(objectReader, javaType);
         if (isUnicode) {
            return objectReader.readValue(inputStream);
         }

         Reader reader = new InputStreamReader(inputStream, charset);
         return objectReader.readValue(reader);
      } catch (InvalidDefinitionException ex) {
         throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
      } catch (JsonProcessingException ex) {
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
   protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      MediaType contentType = outputMessage.getHeaders().getContentType();
      JsonEncoding encoding = this.getJsonEncoding(contentType);
      Class<?> clazz = object instanceof MappingJacksonValue mappingJacksonValue ? mappingJacksonValue.getValue().getClass() : object.getClass();
      ObjectMapper objectMapper = this.selectObjectMapper(clazz, contentType);
      Assert.state(objectMapper != null, () -> "No ObjectMapper for " + clazz.getName());
      OutputStream outputStream = StreamUtils.nonClosing(outputMessage.getBody());

      try {
         JsonGenerator generator = objectMapper.getFactory().createGenerator(outputStream, encoding);

         try {
            this.writePrefix(generator, object);
            Object value = object;
            Class<?> serializationView = null;
            FilterProvider filters = null;
            JavaType javaType = null;
            if (object instanceof MappingJacksonValue mappingJacksonValue) {
               value = mappingJacksonValue.getValue();
               serializationView = mappingJacksonValue.getSerializationView();
               filters = mappingJacksonValue.getFilters();
            }

            if (type != null && TypeUtils.isAssignable(type, value.getClass())) {
               javaType = this.getJavaType(type, null);
            }

            ObjectWriter objectWriter = serializationView != null ? objectMapper.writerWithView(serializationView) : objectMapper.writer();
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
            objectWriter.writeValue(generator, value);
            this.writeSuffix(generator, object);
            generator.flush();
         } catch (Throwable var17) {
            if (generator != null) {
               try {
                  generator.close();
               } catch (Throwable var16) {
                  var17.addSuppressed(var16);
               }
            }

            throw var17;
         }

         if (generator != null) {
            generator.close();
         }
      } catch (InvalidDefinitionException ex) {
         throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
      } catch (JsonProcessingException ex) {
         throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getOriginalMessage(), ex);
      }
   }

   protected ObjectWriter customizeWriter(ObjectWriter writer, @Nullable JavaType javaType, @Nullable MediaType contentType) {
      return writer;
   }

   protected void writePrefix(JsonGenerator generator, Object object) throws IOException {
   }

   protected void writeSuffix(JsonGenerator generator, Object object) throws IOException {
   }

   protected JavaType getJavaType(Type type, @Nullable Class<?> contextClass) {
      return this.defaultObjectMapper.constructType(GenericTypeResolver.resolveType(type, contextClass));
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
   protected @Nullable MediaType getDefaultContentType(Object object) throws IOException {
      if (object instanceof MappingJacksonValue mappingJacksonValue) {
         object = mappingJacksonValue.getValue();
      }

      return super.getDefaultContentType(object);
   }

   @Override
   protected @Nullable Long getContentLength(Object object, @Nullable MediaType contentType) throws IOException {
      if (object instanceof MappingJacksonValue mappingJacksonValue) {
         object = mappingJacksonValue.getValue();
      }

      return super.getContentLength(object, contentType);
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
