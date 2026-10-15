package org.springframework.http.converter.json;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonInclude.Value;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.DatatypeFeature;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.http.ProblemDetail;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.StaxUtils;

@Deprecated(since = "7.0", forRemoval = true)
public class Jackson2ObjectMapperBuilder {
   private static final boolean JACKSON_2_XML_PRESENT = ClassUtils.isPresent(
      "com.fasterxml.jackson.dataformat.xml.XmlMapper", Jackson2ObjectMapperBuilder.class.getClassLoader()
   );
   private final Map<Class<?>, Class<?>> mixIns = new LinkedHashMap<>();
   private final Map<Class<?>, JsonSerializer<?>> serializers = new LinkedHashMap<>();
   private final Map<Class<?>, JsonDeserializer<?>> deserializers = new LinkedHashMap<>();
   private final Map<PropertyAccessor, Visibility> visibilities = new LinkedHashMap<>();
   private final Map<Object, Boolean> features = new LinkedHashMap<>();
   private boolean createXmlMapper = false;
   private @Nullable JsonFactory factory;
   private @Nullable DateFormat dateFormat;
   private @Nullable Locale locale;
   private @Nullable TimeZone timeZone;
   private @Nullable AnnotationIntrospector annotationIntrospector;
   private @Nullable PropertyNamingStrategy propertyNamingStrategy;
   private @Nullable TypeResolverBuilder<?> defaultTyping;
   private @Nullable Value serializationInclusion;
   private @Nullable FilterProvider filters;
   private @Nullable List<Module> modules;
   private Class<? extends Module> @Nullable [] moduleClasses;
   private boolean findModulesViaServiceLoader = false;
   private boolean findWellKnownModules = true;
   private ClassLoader moduleClassLoader = this.getClass().getClassLoader();
   private @Nullable HandlerInstantiator handlerInstantiator;
   private @Nullable ApplicationContext applicationContext;
   private @Nullable Boolean defaultUseWrapper;
   private @Nullable Consumer<ObjectMapper> configurer;

   public Jackson2ObjectMapperBuilder createXmlMapper(boolean createXmlMapper) {
      this.createXmlMapper = createXmlMapper;
      return this;
   }

   public Jackson2ObjectMapperBuilder factory(JsonFactory factory) {
      this.factory = factory;
      return this;
   }

   public Jackson2ObjectMapperBuilder dateFormat(DateFormat dateFormat) {
      this.dateFormat = dateFormat;
      return this;
   }

   public Jackson2ObjectMapperBuilder simpleDateFormat(String format) {
      this.dateFormat = new SimpleDateFormat(format);
      return this;
   }

   public Jackson2ObjectMapperBuilder locale(Locale locale) {
      this.locale = locale;
      return this;
   }

   public Jackson2ObjectMapperBuilder locale(String localeString) {
      this.locale = StringUtils.parseLocale(localeString);
      return this;
   }

   public Jackson2ObjectMapperBuilder timeZone(TimeZone timeZone) {
      this.timeZone = timeZone;
      return this;
   }

   public Jackson2ObjectMapperBuilder timeZone(String timeZoneString) {
      this.timeZone = StringUtils.parseTimeZoneString(timeZoneString);
      return this;
   }

   public Jackson2ObjectMapperBuilder annotationIntrospector(AnnotationIntrospector annotationIntrospector) {
      this.annotationIntrospector = annotationIntrospector;
      return this;
   }

   public Jackson2ObjectMapperBuilder annotationIntrospector(Function<@Nullable AnnotationIntrospector, @Nullable AnnotationIntrospector> pairingFunction) {
      this.annotationIntrospector = pairingFunction.apply(this.annotationIntrospector);
      return this;
   }

   public Jackson2ObjectMapperBuilder propertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
      this.propertyNamingStrategy = propertyNamingStrategy;
      return this;
   }

   public Jackson2ObjectMapperBuilder defaultTyping(TypeResolverBuilder<?> typeResolverBuilder) {
      this.defaultTyping = typeResolverBuilder;
      return this;
   }

   public Jackson2ObjectMapperBuilder serializationInclusion(Include inclusion) {
      return this.serializationInclusion(Value.construct(inclusion, inclusion));
   }

   public Jackson2ObjectMapperBuilder serializationInclusion(Value serializationInclusion) {
      this.serializationInclusion = serializationInclusion;
      return this;
   }

   public Jackson2ObjectMapperBuilder filters(FilterProvider filters) {
      this.filters = filters;
      return this;
   }

   public Jackson2ObjectMapperBuilder mixIn(Class<?> target, Class<?> mixinSource) {
      this.mixIns.put(target, mixinSource);
      return this;
   }

   public Jackson2ObjectMapperBuilder mixIns(Map<Class<?>, Class<?>> mixIns) {
      this.mixIns.putAll(mixIns);
      return this;
   }

   public Jackson2ObjectMapperBuilder serializers(JsonSerializer<?>... serializers) {
      for (JsonSerializer<?> serializer : serializers) {
         Class<?> handledType = serializer.handledType();
         if (handledType == null || handledType == Object.class) {
            throw new IllegalArgumentException("Unknown handled type in " + serializer.getClass().getName());
         }

         this.serializers.put(serializer.handledType(), serializer);
      }

      return this;
   }

   public Jackson2ObjectMapperBuilder serializerByType(Class<?> type, JsonSerializer<?> serializer) {
      this.serializers.put(type, serializer);
      return this;
   }

   public Jackson2ObjectMapperBuilder serializersByType(Map<Class<?>, JsonSerializer<?>> serializers) {
      this.serializers.putAll(serializers);
      return this;
   }

   public Jackson2ObjectMapperBuilder deserializers(JsonDeserializer<?>... deserializers) {
      for (JsonDeserializer<?> deserializer : deserializers) {
         Class<?> handledType = deserializer.handledType();
         if (handledType == null || handledType == Object.class) {
            throw new IllegalArgumentException("Unknown handled type in " + deserializer.getClass().getName());
         }

         this.deserializers.put(deserializer.handledType(), deserializer);
      }

      return this;
   }

   public Jackson2ObjectMapperBuilder deserializerByType(Class<?> type, JsonDeserializer<?> deserializer) {
      this.deserializers.put(type, deserializer);
      return this;
   }

   public Jackson2ObjectMapperBuilder deserializersByType(Map<Class<?>, JsonDeserializer<?>> deserializers) {
      this.deserializers.putAll(deserializers);
      return this;
   }

   public Jackson2ObjectMapperBuilder autoDetectFields(boolean autoDetectFields) {
      this.features.put(MapperFeature.AUTO_DETECT_FIELDS, autoDetectFields);
      return this;
   }

   public Jackson2ObjectMapperBuilder autoDetectGettersSetters(boolean autoDetectGettersSetters) {
      this.features.put(MapperFeature.AUTO_DETECT_GETTERS, autoDetectGettersSetters);
      this.features.put(MapperFeature.AUTO_DETECT_SETTERS, autoDetectGettersSetters);
      this.features.put(MapperFeature.AUTO_DETECT_IS_GETTERS, autoDetectGettersSetters);
      return this;
   }

   public Jackson2ObjectMapperBuilder defaultViewInclusion(boolean defaultViewInclusion) {
      this.features.put(MapperFeature.DEFAULT_VIEW_INCLUSION, defaultViewInclusion);
      return this;
   }

   public Jackson2ObjectMapperBuilder failOnUnknownProperties(boolean failOnUnknownProperties) {
      this.features.put(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
      return this;
   }

   public Jackson2ObjectMapperBuilder failOnEmptyBeans(boolean failOnEmptyBeans) {
      this.features.put(SerializationFeature.FAIL_ON_EMPTY_BEANS, failOnEmptyBeans);
      return this;
   }

   public Jackson2ObjectMapperBuilder indentOutput(boolean indentOutput) {
      this.features.put(SerializationFeature.INDENT_OUTPUT, indentOutput);
      return this;
   }

   public Jackson2ObjectMapperBuilder defaultUseWrapper(boolean defaultUseWrapper) {
      this.defaultUseWrapper = defaultUseWrapper;
      return this;
   }

   public Jackson2ObjectMapperBuilder visibility(PropertyAccessor accessor, Visibility visibility) {
      this.visibilities.put(accessor, visibility);
      return this;
   }

   public Jackson2ObjectMapperBuilder featuresToEnable(Object... featuresToEnable) {
      for (Object feature : featuresToEnable) {
         this.features.put(feature, Boolean.TRUE);
      }

      return this;
   }

   public Jackson2ObjectMapperBuilder featuresToDisable(Object... featuresToDisable) {
      for (Object feature : featuresToDisable) {
         this.features.put(feature, Boolean.FALSE);
      }

      return this;
   }

   public Jackson2ObjectMapperBuilder modules(Module... modules) {
      return this.modules(Arrays.asList(modules));
   }

   public Jackson2ObjectMapperBuilder modules(List<Module> modules) {
      this.modules = new ArrayList<>(modules);
      this.findModulesViaServiceLoader = false;
      this.findWellKnownModules = false;
      return this;
   }

   public Jackson2ObjectMapperBuilder modules(Consumer<List<Module>> consumer) {
      this.modules = this.modules != null ? this.modules : new ArrayList<>();
      this.findModulesViaServiceLoader = false;
      this.findWellKnownModules = false;
      consumer.accept(this.modules);
      return this;
   }

   public Jackson2ObjectMapperBuilder modulesToInstall(Module... modules) {
      this.modules = new ArrayList<>(Arrays.asList(modules));
      this.findWellKnownModules = true;
      return this;
   }

   public Jackson2ObjectMapperBuilder modulesToInstall(Consumer<List<Module>> consumer) {
      this.modules = this.modules != null ? this.modules : new ArrayList<>();
      this.findWellKnownModules = true;
      consumer.accept(this.modules);
      return this;
   }

   @SafeVarargs
   public final Jackson2ObjectMapperBuilder modulesToInstall(Class<? extends Module>... modules) {
      this.moduleClasses = modules;
      this.findWellKnownModules = true;
      return this;
   }

   public Jackson2ObjectMapperBuilder findModulesViaServiceLoader(boolean findModules) {
      this.findModulesViaServiceLoader = findModules;
      return this;
   }

   public Jackson2ObjectMapperBuilder moduleClassLoader(ClassLoader moduleClassLoader) {
      this.moduleClassLoader = moduleClassLoader;
      return this;
   }

   public Jackson2ObjectMapperBuilder handlerInstantiator(HandlerInstantiator handlerInstantiator) {
      this.handlerInstantiator = handlerInstantiator;
      return this;
   }

   public Jackson2ObjectMapperBuilder applicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
      return this;
   }

   public Jackson2ObjectMapperBuilder postConfigurer(Consumer<ObjectMapper> configurer) {
      this.configurer = this.configurer != null ? this.configurer.andThen(configurer) : configurer;
      return this;
   }

   public <T extends ObjectMapper> T build() {
      ObjectMapper mapper;
      if (this.createXmlMapper) {
         mapper = this.defaultUseWrapper != null
            ? new Jackson2ObjectMapperBuilder.XmlObjectMapperInitializer().create(this.defaultUseWrapper, this.factory)
            : new Jackson2ObjectMapperBuilder.XmlObjectMapperInitializer().create(this.factory);
      } else {
         mapper = this.factory != null ? new ObjectMapper(this.factory) : new ObjectMapper();
      }

      this.configure(mapper);
      return (T)mapper;
   }

   public void configure(ObjectMapper objectMapper) {
      Assert.notNull(objectMapper, "ObjectMapper must not be null");
      MultiValueMap<Object, Module> modulesToRegister = new LinkedMultiValueMap();
      if (this.findModulesViaServiceLoader) {
         ObjectMapper.findModules(this.moduleClassLoader).forEach(module -> this.registerModule(module, modulesToRegister));
      } else if (this.findWellKnownModules) {
         this.registerWellKnownModulesIfAvailable(modulesToRegister);
      }

      if (this.modules != null) {
         this.modules.forEach(module -> this.registerModule(module, modulesToRegister));
      }

      if (this.moduleClasses != null) {
         for (Class<? extends Module> moduleClass : this.moduleClasses) {
            this.registerModule((Module)BeanUtils.instantiateClass(moduleClass), modulesToRegister);
         }
      }

      List<Module> modules = new ArrayList<>();

      for (List<Module> nestedModules : modulesToRegister.values()) {
         modules.addAll(nestedModules);
      }

      objectMapper.registerModules(modules);
      if (this.dateFormat != null) {
         objectMapper.setDateFormat(this.dateFormat);
      }

      if (this.locale != null) {
         objectMapper.setLocale(this.locale);
      }

      if (this.timeZone != null) {
         objectMapper.setTimeZone(this.timeZone);
      }

      if (this.annotationIntrospector != null) {
         objectMapper.setAnnotationIntrospector(this.annotationIntrospector);
      }

      if (this.propertyNamingStrategy != null) {
         objectMapper.setPropertyNamingStrategy(this.propertyNamingStrategy);
      }

      if (this.defaultTyping != null) {
         objectMapper.setDefaultTyping(this.defaultTyping);
      }

      if (this.serializationInclusion != null) {
         objectMapper.setDefaultPropertyInclusion(this.serializationInclusion);
      }

      if (this.filters != null) {
         objectMapper.setFilterProvider(this.filters);
      }

      if (JACKSON_2_XML_PRESENT) {
         objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonXmlMixin.class);
      } else {
         objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
      }

      this.mixIns.forEach(objectMapper::addMixIn);
      if (!this.serializers.isEmpty() || !this.deserializers.isEmpty()) {
         SimpleModule module = new SimpleModule();
         this.addSerializers(module);
         this.addDeserializers(module);
         objectMapper.registerModule(module);
      }

      this.visibilities.forEach(objectMapper::setVisibility);
      this.customizeDefaultFeatures(objectMapper);
      this.features.forEach((feature, enabled) -> this.configureFeature(objectMapper, feature, enabled));
      if (this.handlerInstantiator != null) {
         objectMapper.setHandlerInstantiator(this.handlerInstantiator);
      } else if (this.applicationContext != null) {
         objectMapper.setHandlerInstantiator(new SpringHandlerInstantiator(this.applicationContext.getAutowireCapableBeanFactory()));
      }

      if (this.configurer != null) {
         this.configurer.accept(objectMapper);
      }
   }

   private void registerModule(Module module, MultiValueMap<Object, Module> modulesToRegister) {
      if (module.getTypeId() == null) {
         modulesToRegister.add(SimpleModule.class.getName(), module);
      } else {
         modulesToRegister.set(module.getTypeId(), module);
      }
   }

   private void customizeDefaultFeatures(ObjectMapper objectMapper) {
      if (!this.features.containsKey(MapperFeature.DEFAULT_VIEW_INCLUSION)) {
         this.configureFeature(objectMapper, MapperFeature.DEFAULT_VIEW_INCLUSION, false);
      }

      if (!this.features.containsKey(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
         this.configureFeature(objectMapper, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      }
   }

   private <T> void addSerializers(SimpleModule module) {
      this.serializers.forEach((type, serializer) -> module.addSerializer(type, serializer));
   }

   private <T> void addDeserializers(SimpleModule module) {
      this.deserializers.forEach((type, deserializer) -> module.addDeserializer(type, deserializer));
   }

   private void configureFeature(ObjectMapper objectMapper, Object feature, boolean enabled) {
      if (feature instanceof Feature jsonParserFeature) {
         objectMapper.configure(jsonParserFeature, enabled);
      } else if (feature instanceof com.fasterxml.jackson.core.JsonGenerator.Feature jsonGeneratorFeature) {
         objectMapper.configure(jsonGeneratorFeature, enabled);
      } else if (feature instanceof SerializationFeature serializationFeature) {
         objectMapper.configure(serializationFeature, enabled);
      } else if (feature instanceof DeserializationFeature deserializationFeature) {
         objectMapper.configure(deserializationFeature, enabled);
      } else if (feature instanceof DatatypeFeature datatypeFeature) {
         objectMapper.configure(datatypeFeature, enabled);
      } else {
         if (!(feature instanceof MapperFeature mapperFeature)) {
            throw new IllegalArgumentException("Unknown feature class: " + feature.getClass().getName());
         }

         objectMapper.configure(mapperFeature, enabled);
      }
   }

   private void registerWellKnownModulesIfAvailable(MultiValueMap<Object, Module> modulesToRegister) {
      try {
         Class<? extends Module> jdk8ModuleClass = ClassUtils.forName("com.fasterxml.jackson.datatype.jdk8.Jdk8Module", this.moduleClassLoader);
         Module jdk8Module = (Module)BeanUtils.instantiateClass(jdk8ModuleClass);
         modulesToRegister.set(jdk8Module.getTypeId(), jdk8Module);
      } catch (ClassNotFoundException var7) {
      }

      try {
         Class<? extends Module> parameterNamesModuleClass = ClassUtils.forName(
            "com.fasterxml.jackson.module.paramnames.ParameterNamesModule", this.moduleClassLoader
         );
         Module parameterNamesModule = (Module)BeanUtils.instantiateClass(parameterNamesModuleClass);
         modulesToRegister.set(parameterNamesModule.getTypeId(), parameterNamesModule);
      } catch (ClassNotFoundException var6) {
      }

      try {
         Class<? extends Module> javaTimeModuleClass = ClassUtils.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", this.moduleClassLoader);
         Module javaTimeModule = (Module)BeanUtils.instantiateClass(javaTimeModuleClass);
         modulesToRegister.set(javaTimeModule.getTypeId(), javaTimeModule);
      } catch (ClassNotFoundException var5) {
      }

      if (KotlinDetector.isKotlinReflectPresent()) {
         try {
            Class<? extends Module> kotlinModuleClass = ClassUtils.forName("com.fasterxml.jackson.module.kotlin.KotlinModule", this.moduleClassLoader);
            Module kotlinModule = (Module)BeanUtils.instantiateClass(kotlinModuleClass);
            modulesToRegister.set(kotlinModule.getTypeId(), kotlinModule);
         } catch (ClassNotFoundException var4) {
         }
      }
   }

   public static Jackson2ObjectMapperBuilder json() {
      return new Jackson2ObjectMapperBuilder();
   }

   public static Jackson2ObjectMapperBuilder xml() {
      return new Jackson2ObjectMapperBuilder().createXmlMapper(true);
   }

   public static Jackson2ObjectMapperBuilder smile() {
      return new Jackson2ObjectMapperBuilder().factory(new Jackson2ObjectMapperBuilder.SmileFactoryInitializer().create());
   }

   public static Jackson2ObjectMapperBuilder cbor() {
      return new Jackson2ObjectMapperBuilder().factory(new Jackson2ObjectMapperBuilder.CborFactoryInitializer().create());
   }

   public static Jackson2ObjectMapperBuilder yaml() {
      return new Jackson2ObjectMapperBuilder().factory(new Jackson2ObjectMapperBuilder.YamlFactoryInitializer().create());
   }

   private static class CborFactoryInitializer {
      public JsonFactory create() {
         return new CBORFactory();
      }
   }

   private static class SmileFactoryInitializer {
      public JsonFactory create() {
         return new SmileFactory();
      }
   }

   private static class XmlObjectMapperInitializer {
      public ObjectMapper create(@Nullable JsonFactory factory) {
         return factory != null ? new XmlMapper((XmlFactory)factory) : new XmlMapper(StaxUtils.createDefensiveInputFactory());
      }

      public ObjectMapper create(boolean defaultUseWrapper, @Nullable JsonFactory factory) {
         JacksonXmlModule module = new JacksonXmlModule();
         module.setDefaultUseWrapper(defaultUseWrapper);
         return factory != null ? new XmlMapper((XmlFactory)factory, module) : new XmlMapper(new XmlFactory(StaxUtils.createDefensiveInputFactory()), module);
      }
   }

   private static class YamlFactoryInitializer {
      public JsonFactory create() {
         return new YAMLFactory();
      }
   }
}
