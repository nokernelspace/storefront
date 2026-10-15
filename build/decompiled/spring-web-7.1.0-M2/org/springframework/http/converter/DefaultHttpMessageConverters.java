package org.springframework.http.converter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.cbor.JacksonCborHttpMessageConverter;
import org.springframework.http.converter.cbor.KotlinSerializationCborHttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.http.converter.feed.AtomFeedHttpMessageConverter;
import org.springframework.http.converter.feed.RssChannelHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.JsonbHttpMessageConverter;
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.multipart.MultipartHttpMessageConverter;
import org.springframework.http.converter.protobuf.KotlinSerializationProtobufHttpMessageConverter;
import org.springframework.http.converter.smile.JacksonSmileHttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.converter.yaml.JacksonYamlHttpMessageConverter;
import org.springframework.http.converter.yaml.MappingJackson2YamlHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

class DefaultHttpMessageConverters implements HttpMessageConverters {
   private final List<HttpMessageConverter<?>> messageConverters;

   DefaultHttpMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
      this.messageConverters = messageConverters;
   }

   @Override
   public boolean isEmpty() {
      return this.messageConverters.isEmpty();
   }

   @Override
   public Iterator<HttpMessageConverter<?>> iterator() {
      return this.messageConverters.iterator();
   }

   abstract static class DefaultBuilder {
      static final boolean JACKSON_PRESENT;
      static final boolean JACKSON_2_PRESENT;
      static final boolean GSON_PRESENT;
      static final boolean JSONB_PRESENT;
      static final boolean KOTLIN_SERIALIZATION_JSON_PRESENT;
      static final boolean JACKSON_XML_PRESENT;
      static final boolean JACKSON_2_XML_PRESENT;
      static final boolean JAXB_2_PRESENT;
      static final boolean JACKSON_SMILE_PRESENT;
      static final boolean JACKSON_2_SMILE_PRESENT;
      static final boolean JACKSON_CBOR_PRESENT;
      static final boolean JACKSON_2_CBOR_PRESENT;
      static final boolean KOTLIN_SERIALIZATION_CBOR_PRESENT;
      static final boolean JACKSON_YAML_PRESENT;
      static final boolean JACKSON_2_YAML_PRESENT;
      static final boolean KOTLIN_SERIALIZATION_PROTOBUF_PRESENT;
      static final boolean ROME_PRESENT;
      boolean registerDefaults;
      @Nullable ByteArrayHttpMessageConverter byteArrayConverter;
      @Nullable HttpMessageConverter<?> stringConverter;
      @Nullable HttpMessageConverter<?> resourceConverter;
      @Nullable HttpMessageConverter<?> resourceRegionConverter;
      @Nullable HttpMessageConverter<?> formConverter;
      @Nullable Consumer<HttpMessageConverter<?>> configurer;
      @Nullable Consumer<List<HttpMessageConverter<?>>> convertersListConfigurer;
      @Nullable HttpMessageConverter<?> kotlinJsonConverter;
      @Nullable HttpMessageConverter<?> jsonConverter;
      @Nullable HttpMessageConverter<?> xmlConverter;
      @Nullable HttpMessageConverter<?> smileConverter;
      @Nullable HttpMessageConverter<?> kotlinCborConverter;
      @Nullable HttpMessageConverter<?> cborConverter;
      @Nullable HttpMessageConverter<?> yamlConverter;
      @Nullable HttpMessageConverter<?> protobufConverter;
      @Nullable HttpMessageConverter<?> atomConverter;
      @Nullable HttpMessageConverter<?> rssConverter;
      final List<HttpMessageConverter<?>> customConverters = new ArrayList<>();

      void setStringConverter(HttpMessageConverter<?> stringConverter) {
         this.checkConverterSupports(stringConverter, MediaType.TEXT_PLAIN);
         this.stringConverter = stringConverter;
      }

      public void setFormConverter(HttpMessageConverter<?> formConverter) {
         this.checkConverterSupports(formConverter, MediaType.APPLICATION_FORM_URLENCODED);
         this.formConverter = formConverter;
      }

      void setKotlinSerializationJsonConverter(HttpMessageConverter<?> kotlinJsonConverter) {
         Assert.notNull(kotlinJsonConverter, "kotlinJsonConverter must not be null");
         this.kotlinJsonConverter = kotlinJsonConverter;
      }

      void setJsonConverter(HttpMessageConverter<?> jsonConverter) {
         this.checkConverterSupports(jsonConverter, MediaType.APPLICATION_JSON);
         this.jsonConverter = jsonConverter;
      }

      void setXmlConverter(HttpMessageConverter<?> xmlConverter) {
         this.checkConverterSupports(xmlConverter, MediaType.TEXT_XML);
         this.xmlConverter = xmlConverter;
      }

      void setSmileConverter(HttpMessageConverter<?> smileConverter) {
         this.checkConverterSupports(smileConverter, new MediaType("application", "x-jackson-smile"));
         this.smileConverter = smileConverter;
      }

      void setKotlinSerializationCborConverter(HttpMessageConverter<?> kotlinCborConverter) {
         Assert.notNull(kotlinCborConverter, "kotlinCborConverter must not be null");
         this.kotlinCborConverter = kotlinCborConverter;
      }

      void setCborConverter(HttpMessageConverter<?> cborConverter) {
         this.checkConverterSupports(cborConverter, MediaType.APPLICATION_CBOR);
         this.cborConverter = cborConverter;
      }

      void setYamlConverter(HttpMessageConverter<?> yamlConverter) {
         this.checkConverterSupports(yamlConverter, MediaType.APPLICATION_YAML);
         this.yamlConverter = yamlConverter;
      }

      private void checkConverterSupports(HttpMessageConverter<?> converter, MediaType mediaType) {
         for (MediaType supportedMediaType : converter.getSupportedMediaTypes()) {
            if (mediaType.equalsTypeAndSubtype(supportedMediaType)) {
               return;
            }
         }

         throw new IllegalArgumentException("converter should support '" + mediaType + "'");
      }

      void addCustomMessageConverter(HttpMessageConverter<?> customConverter) {
         Assert.notNull(customConverter, "'customConverter' must not be null");
         this.customConverters.add(customConverter);
      }

      void addMessageConverterConfigurer(Consumer<HttpMessageConverter<?>> configurer) {
         this.configurer = this.configurer != null ? this.configurer.andThen(configurer) : configurer;
      }

      void addMessageConvertersListConfigurer(Consumer<List<HttpMessageConverter<?>>> configurer) {
         this.convertersListConfigurer = this.convertersListConfigurer != null ? this.convertersListConfigurer.andThen(configurer) : configurer;
      }

      List<HttpMessageConverter<?>> getBaseConverters() {
         List<HttpMessageConverter<?>> converters = new ArrayList<>();
         if (this.byteArrayConverter != null) {
            converters.add(this.byteArrayConverter);
         }

         if (this.stringConverter != null) {
            converters.add(this.stringConverter);
         }

         if (this.formConverter != null) {
            converters.add(this.formConverter);
         }

         return converters;
      }

      List<HttpMessageConverter<?>> getCoreConverters() {
         List<HttpMessageConverter<?>> converters = new ArrayList<>();
         if (this.kotlinJsonConverter != null) {
            converters.add(this.kotlinJsonConverter);
         }

         if (this.jsonConverter != null) {
            converters.add(this.jsonConverter);
         }

         if (this.smileConverter != null) {
            converters.add(this.smileConverter);
         }

         if (this.kotlinCborConverter != null) {
            converters.add(this.kotlinCborConverter);
         }

         if (this.cborConverter != null) {
            converters.add(this.cborConverter);
         }

         if (this.yamlConverter != null) {
            converters.add(this.yamlConverter);
         }

         if (this.xmlConverter != null) {
            converters.add(this.xmlConverter);
         }

         if (this.protobufConverter != null) {
            converters.add(this.protobufConverter);
         }

         if (this.atomConverter != null) {
            converters.add(this.atomConverter);
         }

         if (this.rssConverter != null) {
            converters.add(this.rssConverter);
         }

         return converters;
      }

      List<HttpMessageConverter<?>> getCustomConverters() {
         return this.customConverters;
      }

      void detectMessageConverters() {
         this.byteArrayConverter = new ByteArrayHttpMessageConverter();
         if (this.stringConverter == null) {
            this.stringConverter = new StringHttpMessageConverter();
         }

         if (this.formConverter == null) {
            this.formConverter = new FormHttpMessageConverter();
         }

         if (this.kotlinJsonConverter == null && KOTLIN_SERIALIZATION_JSON_PRESENT) {
            if (this.jsonConverter == null && !JACKSON_PRESENT && !JACKSON_2_PRESENT && !GSON_PRESENT && !JSONB_PRESENT) {
               this.kotlinJsonConverter = new KotlinSerializationJsonHttpMessageConverter(type -> true);
            } else {
               this.kotlinJsonConverter = new KotlinSerializationJsonHttpMessageConverter();
            }
         }

         if (this.jsonConverter == null) {
            if (JACKSON_PRESENT) {
               this.jsonConverter = new JacksonJsonHttpMessageConverter();
            } else if (JACKSON_2_PRESENT) {
               this.jsonConverter = new MappingJackson2HttpMessageConverter();
            } else if (GSON_PRESENT) {
               this.jsonConverter = new GsonHttpMessageConverter();
            } else if (JSONB_PRESENT) {
               this.jsonConverter = new JsonbHttpMessageConverter();
            }
         }

         if (this.xmlConverter == null) {
            if (JACKSON_XML_PRESENT) {
               this.xmlConverter = new JacksonXmlHttpMessageConverter();
            } else if (JACKSON_2_XML_PRESENT) {
               this.xmlConverter = new MappingJackson2XmlHttpMessageConverter();
            } else if (JAXB_2_PRESENT) {
               this.xmlConverter = new Jaxb2RootElementHttpMessageConverter();
            }
         }

         if (this.smileConverter == null) {
            if (JACKSON_SMILE_PRESENT) {
               this.smileConverter = new JacksonSmileHttpMessageConverter();
            } else if (JACKSON_2_SMILE_PRESENT) {
               this.smileConverter = new MappingJackson2SmileHttpMessageConverter();
            }
         }

         if (this.kotlinCborConverter == null && KOTLIN_SERIALIZATION_CBOR_PRESENT) {
            if (this.cborConverter == null && !JACKSON_CBOR_PRESENT && !JACKSON_2_CBOR_PRESENT) {
               this.kotlinCborConverter = new KotlinSerializationCborHttpMessageConverter(type -> true);
            } else {
               this.kotlinCborConverter = new KotlinSerializationCborHttpMessageConverter();
            }
         }

         if (this.cborConverter == null) {
            if (JACKSON_CBOR_PRESENT) {
               this.cborConverter = new JacksonCborHttpMessageConverter();
            } else if (JACKSON_2_CBOR_PRESENT) {
               this.cborConverter = new MappingJackson2CborHttpMessageConverter();
            }
         }

         if (this.yamlConverter == null) {
            if (JACKSON_YAML_PRESENT) {
               this.yamlConverter = new JacksonYamlHttpMessageConverter();
            } else if (JACKSON_2_YAML_PRESENT) {
               this.yamlConverter = new MappingJackson2YamlHttpMessageConverter();
            }
         }

         if (this.protobufConverter == null && KOTLIN_SERIALIZATION_PROTOBUF_PRESENT) {
            this.protobufConverter = new KotlinSerializationProtobufHttpMessageConverter(type -> true);
         }

         if (ROME_PRESENT) {
            if (this.atomConverter == null) {
               this.atomConverter = new AtomFeedHttpMessageConverter();
            }

            if (this.rssConverter == null) {
               this.rssConverter = new RssChannelHttpMessageConverter();
            }
         }
      }

      static {
         ClassLoader classLoader = DefaultHttpMessageConverters.DefaultBuilder.class.getClassLoader();
         JACKSON_PRESENT = ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", classLoader);
         JACKSON_2_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
            && ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
         GSON_PRESENT = ClassUtils.isPresent("com.google.gson.Gson", classLoader);
         JSONB_PRESENT = ClassUtils.isPresent("jakarta.json.bind.Jsonb", classLoader);
         KOTLIN_SERIALIZATION_JSON_PRESENT = ClassUtils.isPresent("kotlinx.serialization.json.Json", classLoader);
         JACKSON_SMILE_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.smile.SmileMapper", classLoader);
         JACKSON_2_SMILE_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory", classLoader);
         JAXB_2_PRESENT = ClassUtils.isPresent("jakarta.xml.bind.Binder", classLoader);
         JACKSON_XML_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.xml.XmlMapper", classLoader);
         JACKSON_2_XML_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper", classLoader);
         JACKSON_CBOR_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.cbor.CBORMapper", classLoader);
         JACKSON_2_CBOR_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory", classLoader);
         JACKSON_YAML_PRESENT = JACKSON_PRESENT && ClassUtils.isPresent("tools.jackson.dataformat.yaml.YAMLMapper", classLoader);
         JACKSON_2_YAML_PRESENT = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.yaml.YAMLFactory", classLoader);
         KOTLIN_SERIALIZATION_CBOR_PRESENT = ClassUtils.isPresent("kotlinx.serialization.cbor.Cbor", classLoader);
         KOTLIN_SERIALIZATION_PROTOBUF_PRESENT = ClassUtils.isPresent("kotlinx.serialization.protobuf.ProtoBuf", classLoader);
         ROME_PRESENT = ClassUtils.isPresent("com.rometools.rome.feed.WireFeed", classLoader);
      }
   }

   static class DefaultClientBuilder extends DefaultHttpMessageConverters.DefaultBuilder implements HttpMessageConverters.ClientBuilder {
      public DefaultHttpMessageConverters.DefaultClientBuilder registerDefaults() {
         this.registerDefaults = true;
         return this;
      }

      public HttpMessageConverters.ClientBuilder disableDefaults() {
         this.registerDefaults = false;
         return this;
      }

      public HttpMessageConverters.ClientBuilder withStringConverter(HttpMessageConverter<?> stringConverter) {
         this.setStringConverter(stringConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withFormConverter(HttpMessageConverter<?> formMessageConverter) {
         this.setFormConverter(formMessageConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withKotlinSerializationJsonConverter(HttpMessageConverter<?> kotlinSerializationJsonConverter) {
         this.setKotlinSerializationJsonConverter(kotlinSerializationJsonConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withJsonConverter(HttpMessageConverter<?> jsonConverter) {
         this.setJsonConverter(jsonConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withXmlConverter(HttpMessageConverter<?> xmlConverter) {
         this.setXmlConverter(xmlConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withSmileConverter(HttpMessageConverter<?> smileConverter) {
         this.setSmileConverter(smileConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withKotlinSerializationCborConverter(HttpMessageConverter<?> kotlinSerializationCborConverter) {
         this.setKotlinSerializationCborConverter(kotlinSerializationCborConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withCborConverter(HttpMessageConverter<?> cborConverter) {
         this.setCborConverter(cborConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder withYamlConverter(HttpMessageConverter<?> yamlConverter) {
         this.setYamlConverter(yamlConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder addCustomConverter(HttpMessageConverter<?> customConverter) {
         this.addCustomMessageConverter(customConverter);
         return this;
      }

      public HttpMessageConverters.ClientBuilder configureMessageConverters(Consumer<HttpMessageConverter<?>> configurer) {
         this.addMessageConverterConfigurer(configurer);
         return this;
      }

      public HttpMessageConverters.ClientBuilder configureMessageConvertersList(Consumer<List<HttpMessageConverter<?>>> configurer) {
         this.addMessageConvertersListConfigurer(configurer);
         return this;
      }

      @Override
      void detectMessageConverters() {
         super.detectMessageConverters();
         if (this.xmlConverter == null) {
            if (JACKSON_XML_PRESENT) {
               this.xmlConverter = new JacksonXmlHttpMessageConverter();
            } else if (JACKSON_2_XML_PRESENT) {
               this.xmlConverter = new MappingJackson2XmlHttpMessageConverter();
            } else if (JAXB_2_PRESENT) {
               this.xmlConverter = new Jaxb2RootElementHttpMessageConverter();
            }
         }
      }

      @Override
      public HttpMessageConverters build() {
         if (this.registerDefaults) {
            this.resourceConverter = new ResourceHttpMessageConverter(false);
            this.detectMessageConverters();
         }

         List<HttpMessageConverter<?>> partConverters = new ArrayList<>(this.getCustomConverters());
         List<HttpMessageConverter<?>> allConverters = new ArrayList<>(this.getCustomConverters());
         allConverters.addAll(this.getBaseConverters());
         if (this.resourceConverter != null) {
            allConverters.add(this.resourceConverter);
         }

         if (!this.getCoreConverters().isEmpty()) {
            partConverters.addAll(List.of(new ByteArrayHttpMessageConverter(), new StringHttpMessageConverter(), new ResourceHttpMessageConverter()));
            partConverters.addAll(this.getCoreConverters());
         }

         if (!partConverters.isEmpty() || !allConverters.isEmpty()) {
            allConverters.add(new MultipartHttpMessageConverter(partConverters));
         }

         allConverters.addAll(this.getCoreConverters());
         if (this.convertersListConfigurer != null) {
            this.convertersListConfigurer.accept(allConverters);
         }

         if (this.configurer != null) {
            allConverters.forEach(this.configurer);
         }

         return new DefaultHttpMessageConverters(allConverters);
      }
   }

   static class DefaultServerBuilder extends DefaultHttpMessageConverters.DefaultBuilder implements HttpMessageConverters.ServerBuilder {
      public HttpMessageConverters.ServerBuilder registerDefaults() {
         this.registerDefaults = true;
         return this;
      }

      public HttpMessageConverters.ServerBuilder disableDefaults() {
         this.registerDefaults = false;
         return this;
      }

      public HttpMessageConverters.ServerBuilder withStringConverter(HttpMessageConverter<?> stringConverter) {
         this.setStringConverter(stringConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withFormConverter(HttpMessageConverter<?> formMessageConverter) {
         this.setFormConverter(formMessageConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withKotlinSerializationJsonConverter(HttpMessageConverter<?> kotlinSerializationJsonConverter) {
         this.setKotlinSerializationJsonConverter(kotlinSerializationJsonConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withJsonConverter(HttpMessageConverter<?> jsonConverter) {
         this.setJsonConverter(jsonConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withXmlConverter(HttpMessageConverter<?> xmlConverter) {
         this.setXmlConverter(xmlConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withSmileConverter(HttpMessageConverter<?> smileConverter) {
         this.setSmileConverter(smileConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withKotlinSerializationCborConverter(HttpMessageConverter<?> kotlinSerializationCborConverter) {
         this.setKotlinSerializationCborConverter(kotlinSerializationCborConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withCborConverter(HttpMessageConverter<?> cborConverter) {
         this.setCborConverter(cborConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder withYamlConverter(HttpMessageConverter<?> yamlConverter) {
         this.setYamlConverter(yamlConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder addCustomConverter(HttpMessageConverter<?> customConverter) {
         this.addCustomMessageConverter(customConverter);
         return this;
      }

      public HttpMessageConverters.ServerBuilder configureMessageConverters(Consumer<HttpMessageConverter<?>> configurer) {
         this.addMessageConverterConfigurer(configurer);
         return this;
      }

      public HttpMessageConverters.ServerBuilder configureMessageConvertersList(Consumer<List<HttpMessageConverter<?>>> configurer) {
         this.addMessageConvertersListConfigurer(configurer);
         return this;
      }

      @Override
      void detectMessageConverters() {
         super.detectMessageConverters();
         if (this.xmlConverter == null) {
            if (JACKSON_XML_PRESENT) {
               this.xmlConverter = new JacksonXmlHttpMessageConverter();
            } else if (JACKSON_2_XML_PRESENT) {
               this.xmlConverter = new MappingJackson2XmlHttpMessageConverter();
            }
         }
      }

      @Override
      public HttpMessageConverters build() {
         if (this.registerDefaults) {
            this.resourceConverter = new ResourceHttpMessageConverter();
            this.resourceRegionConverter = new ResourceRegionHttpMessageConverter();
            this.detectMessageConverters();
         }

         List<HttpMessageConverter<?>> partConverters = new ArrayList<>(this.getCustomConverters());
         List<HttpMessageConverter<?>> allConverters = new ArrayList<>(this.getCustomConverters());
         allConverters.addAll(this.getBaseConverters());
         if (this.resourceConverter != null) {
            allConverters.add(this.resourceConverter);
         }

         if (this.resourceRegionConverter != null) {
            allConverters.add(this.resourceRegionConverter);
         }

         if (!this.getCoreConverters().isEmpty()) {
            partConverters.addAll(List.of(new ByteArrayHttpMessageConverter(), new StringHttpMessageConverter(), new ResourceHttpMessageConverter()));
            partConverters.addAll(this.getCoreConverters());
         }

         if (!partConverters.isEmpty() || !allConverters.isEmpty()) {
            allConverters.add(new MultipartHttpMessageConverter(partConverters));
         }

         allConverters.addAll(this.getCoreConverters());
         if (this.convertersListConfigurer != null) {
            this.convertersListConfigurer.accept(allConverters);
         }

         if (this.configurer != null) {
            allConverters.forEach(this.configurer);
         }

         return new DefaultHttpMessageConverters(allConverters);
      }
   }
}
