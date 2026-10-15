package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.spi.ElementSelector;
import ch.qos.logback.core.joran.spi.RuleStore;
import ch.qos.logback.core.joran.util.PropertySetter;
import ch.qos.logback.core.joran.util.beans.BeanDescription;
import ch.qos.logback.core.model.ComponentModel;
import ch.qos.logback.core.model.IncludeModel;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.ModelUtil;
import ch.qos.logback.core.model.processor.DefaultProcessor;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.util.AggregationType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.GeneratedFiles.FileHandler;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.core.CollectionFactory;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.util.function.ThrowingConsumer;

class SpringBootJoranConfigurator extends JoranConfigurator {
   private final LoggingInitializationContext initializationContext;

   SpringBootJoranConfigurator(LoggingInitializationContext initializationContext) {
      this.initializationContext = initializationContext;
   }

   protected void sanityCheck(Model topModel) {
      super.sanityCheck(topModel);
      this.performCheck(new SpringProfileIfNestedWithinSecondPhaseElementSanityChecker(), topModel);
   }

   protected void addModelHandlerAssociations(DefaultProcessor defaultProcessor) {
      defaultProcessor.addHandler(
         SpringPropertyModel.class, (handlerContext, handlerMic) -> new SpringPropertyModelHandler(this.context, this.initializationContext.getEnvironment())
      );
      defaultProcessor.addHandler(
         SpringProfileModel.class, (handlerContext, handlerMic) -> new SpringProfileModelHandler(this.context, this.initializationContext.getEnvironment())
      );
      super.addModelHandlerAssociations(defaultProcessor);
   }

   public void addElementSelectorAndActionAssociations(RuleStore ruleStore) {
      super.addElementSelectorAndActionAssociations(ruleStore);
      ruleStore.addRule(new ElementSelector("configuration/springProperty"), SpringPropertyAction::new);
      ruleStore.addRule(new ElementSelector("*/springProfile"), SpringProfileAction::new);
      ruleStore.addTransparentPathPart("springProfile");
   }

   public void buildModelInterpretationContext() {
      super.buildModelInterpretationContext();
      this.modelInterpretationContext.setConfiguratorSupplier(() -> {
         SpringBootJoranConfigurator configurator = new SpringBootJoranConfigurator(this.initializationContext);
         configurator.setContext(this.context);
         return configurator;
      });
   }

   boolean configureUsingAotGeneratedArtifacts() {
      if (!new SpringBootJoranConfigurator.PatternRules(this.getContext()).load()) {
         return false;
      }

      Model model = new SpringBootJoranConfigurator.ModelReader().read();
      this.processModel(model);
      this.registerSafeConfiguration(model);
      return true;
   }

   public void processModel(Model model) {
      super.processModel(model);
      if (!NativeDetector.inNativeImage() && this.isAotProcessingInProgress()) {
         this.getContext()
            .putObject(
               BeanFactoryInitializationAotContribution.class.getName(),
               new SpringBootJoranConfigurator.LogbackConfigurationAotContribution(model, this.getModelInterpretationContext(), this.getContext())
            );
      }
   }

   private boolean isAotProcessingInProgress() {
      return Boolean.getBoolean("spring.aot.processing");
   }

   static final class LogbackConfigurationAotContribution implements BeanFactoryInitializationAotContribution {
      private final SpringBootJoranConfigurator.ModelWriter modelWriter;
      private final SpringBootJoranConfigurator.PatternRules patternRules;

      private LogbackConfigurationAotContribution(Model model, ModelInterpretationContext interpretationContext, Context context) {
         this.modelWriter = new SpringBootJoranConfigurator.ModelWriter(model, interpretationContext);
         this.patternRules = new SpringBootJoranConfigurator.PatternRules(context);
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         this.modelWriter.writeTo(generationContext);
         this.patternRules.save(generationContext);
      }
   }

   private static final class ModelReader {
      private Model read() {
         try (
            InputStream modelInput = this.getClass().getClassLoader().getResourceAsStream("META-INF/spring/logback-model");
            ObjectInputStream input = new ObjectInputStream(modelInput);
         ) {
            Model model = (Model)input.readObject();
            ModelUtil.resetForReuse(model);
            this.markIncludesAsHandled(model);
            return model;
         } catch (Exception ex) {
            throw new RuntimeException("Failed to load model from 'META-INF/spring/logback-model'", ex);
         }
      }

      private void markIncludesAsHandled(Model model) {
         if (model instanceof IncludeModel) {
            model.markAsHandled();
         }

         for (Model submodel : model.getSubModels()) {
            this.markIncludesAsHandled(submodel);
         }
      }
   }

   private static final class ModelWriter {
      private static final String MODEL_RESOURCE_LOCATION = "META-INF/spring/logback-model";
      private final Model model;
      private final ModelInterpretationContext modelInterpretationContext;

      private ModelWriter(Model model, ModelInterpretationContext modelInterpretationContext) {
         this.model = model;
         this.modelInterpretationContext = modelInterpretationContext;
      }

      private void writeTo(GenerationContext generationContext) {
         byte[] serializedModel = this.serializeModel();
         generationContext.getGeneratedFiles()
            .handleFile(Kind.RESOURCE, "META-INF/spring/logback-model", new SpringBootJoranConfigurator.RequireNewOrMatchingContentFileHandler(serializedModel));
         generationContext.getRuntimeHints().resources().registerPattern("META-INF/spring/logback-model");
         ReflectionHints reflectionHints = generationContext.getRuntimeHints().reflection();
         this.serializationTypes(this.model).forEach(reflectionHints::registerJavaSerialization);
         this.reflectionTypes(this.model)
            .forEach(
               type -> generationContext.getRuntimeHints()
                  .reflection()
                  .registerType(TypeReference.of(type), new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS})
            );
      }

      private byte[] serializeModel() {
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();

         try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(this.model);
         } catch (IOException ex) {
            throw new RuntimeException(ex);
         }

         return bytes.toByteArray();
      }

      private Set<Class<? extends Serializable>> serializationTypes(Model model) {
         Set<Class<? extends Serializable>> modelClasses = new HashSet<>();
         Class<?> candidate = model.getClass();

         while (Model.class.isAssignableFrom(candidate)) {
            if (modelClasses.add((Class<? extends Serializable>)candidate)) {
               ReflectionUtils.doWithFields(candidate, field -> {
                  if (!Modifier.isStatic(field.getModifiers())) {
                     ReflectionUtils.makeAccessible(field);
                     Object value = field.get(model);
                     if (value != null) {
                        Class<?> fieldType = value.getClass();
                        if (Serializable.class.isAssignableFrom(fieldType)) {
                           modelClasses.add((Class<? extends Serializable>)fieldType);
                        }
                     }
                  }
               });
               candidate = candidate.getSuperclass();
            }
         }

         for (Model submodel : model.getSubModels()) {
            modelClasses.addAll(this.serializationTypes(submodel));
         }

         return modelClasses;
      }

      private Set<Class<?>> reflectionTypes(Model model) {
         return this.reflectionTypes(model, () -> null);
      }

      private Set<Class<?>> reflectionTypes(Model model, Supplier<Object> parent) {
         Set<Class<?>> reflectionTypes = new HashSet<>();
         Class<?> componentType = this.determineType(model, parent);
         if (componentType != null) {
            this.processComponent(componentType, reflectionTypes);
         }

         Supplier<Object> componentSupplier = SingletonSupplier.of(() -> this.instantiate(componentType));

         for (Model submodel : model.getSubModels()) {
            reflectionTypes.addAll(this.reflectionTypes(submodel, componentSupplier));
         }

         return reflectionTypes;
      }

      private @Nullable Class<?> determineType(Model model, Supplier<Object> parentSupplier) {
         String className = model instanceof ComponentModel componentModel ? componentModel.getClassName() : null;
         if (className != null) {
            return this.loadImportType(className);
         } else {
            String tag = model.getTag();
            if (tag != null) {
               className = this.modelInterpretationContext.getDefaultNestedComponentRegistry().findDefaultComponentTypeByTag(tag);
               return className != null ? this.loadImportType(className) : this.inferTypeFromParent(parentSupplier, tag);
            } else {
               return null;
            }
         }
      }

      private Class<?> loadImportType(String className) {
         return this.loadComponentType(this.modelInterpretationContext.getImport(className));
      }

      private @Nullable Class<?> inferTypeFromParent(Supplier<Object> parentSupplier, String tag) {
         Object parent = parentSupplier.get();
         if (parent != null) {
            try {
               PropertySetter propertySetter = new PropertySetter(this.modelInterpretationContext.getBeanDescriptionCache(), parent);
               return propertySetter.getClassNameViaImplicitRules(
                  tag, AggregationType.AS_COMPLEX_PROPERTY, this.modelInterpretationContext.getDefaultNestedComponentRegistry()
               );
            } catch (Exception ex) {
               return null;
            }
         } else {
            return null;
         }
      }

      private Class<?> loadComponentType(String componentType) {
         try {
            return ClassUtils.forName(this.modelInterpretationContext.subst(componentType), this.getClass().getClassLoader());
         } catch (Throwable ex) {
            throw new RuntimeException("Failed to load component type '" + componentType + "'", ex);
         }
      }

      private @Nullable Object instantiate(@Nullable Class<?> type) {
         if (type == null) {
            return null;
         }

         try {
            return type.getConstructor().newInstance();
         } catch (Exception ex) {
            return null;
         }
      }

      private void processComponent(Class<?> componentType, Set<Class<?>> reflectionTypes) {
         BeanDescription beanDescription = this.modelInterpretationContext.getBeanDescriptionCache().getBeanDescription(componentType);
         reflectionTypes.addAll(this.parameterTypesNames(beanDescription.getPropertyNameToAdder().values()));
         reflectionTypes.addAll(this.parameterTypesNames(beanDescription.getPropertyNameToSetter().values()));
         reflectionTypes.add(componentType);
      }

      private Collection<Class<?>> parameterTypesNames(Collection<Method> methods) {
         return methods.stream()
            .filter(method -> !method.getDeclaringClass().equals(ContextAware.class) && !method.getDeclaringClass().equals(ContextAwareBase.class))
            .map(Method::getParameterTypes)
            .flatMap(Stream::of)
            .filter(type -> !type.isPrimitive() && !type.equals(String.class))
            .map(type -> type.isArray() ? type.getComponentType() : type)
            .toList();
      }
   }

   private static final class PatternRules {
      private static final String RESOURCE_LOCATION = "META-INF/spring/logback-pattern-rules";
      private final Context context;

      private PatternRules(Context context) {
         this.context = context;
      }

      private boolean load() {
         try {
            ClassPathResource resource = new ClassPathResource("META-INF/spring/logback-pattern-rules");
            if (!resource.exists()) {
               return false;
            }

            Properties properties = PropertiesLoaderUtils.loadProperties(resource);
            Map<String, String> patternRuleRegistry = this.getRegistryMap();

            for (String word : properties.stringPropertyNames()) {
               patternRuleRegistry.put(word, properties.getProperty(word));
            }

            return true;
         } catch (Exception ex) {
            throw new RuntimeException(ex);
         }
      }

      private Map<String, String> getRegistryMap() {
         Map<String, String> patternRuleRegistry = (Map<String, String>)this.context.getObject("PATTERN_RULE_REGISTRY");
         if (patternRuleRegistry == null) {
            patternRuleRegistry = new HashMap<>();
            this.context.putObject("PATTERN_RULE_REGISTRY", patternRuleRegistry);
         }

         return patternRuleRegistry;
      }

      private void save(GenerationContext generationContext) {
         Map<String, String> registryMap = this.getRegistryMap();
         byte[] rules = this.asBytes(registryMap);
         generationContext.getGeneratedFiles()
            .handleFile(Kind.RESOURCE, "META-INF/spring/logback-pattern-rules", new SpringBootJoranConfigurator.RequireNewOrMatchingContentFileHandler(rules));
         generationContext.getRuntimeHints().resources().registerPattern("META-INF/spring/logback-pattern-rules");

         for (String ruleClassName : registryMap.values()) {
            generationContext.getRuntimeHints()
               .reflection()
               .registerType(TypeReference.of(ruleClassName), new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS});
         }
      }

      private byte[] asBytes(Map<String, String> patternRuleRegistry) {
         Properties properties = CollectionFactory.createSortedProperties(true);
         patternRuleRegistry.forEach(properties::setProperty);
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();

         try {
            properties.store(bytes, "");
         } catch (IOException ex) {
            throw new RuntimeException(ex);
         }

         return bytes.toByteArray();
      }
   }

   private static final class RequireNewOrMatchingContentFileHandler implements ThrowingConsumer<FileHandler> {
      private final byte[] newContent;

      private RequireNewOrMatchingContentFileHandler(byte[] newContent) {
         this.newContent = newContent;
      }

      public void acceptWithException(FileHandler file) throws Exception {
         if (file.exists()) {
            InputStreamSource content = file.getContent();
            Assert.state(content != null, "Unable to get file content");
            byte[] existingContent = this.toByteArray(content);
            if (!Arrays.equals(this.newContent, existingContent)) {
               throw new IllegalStateException(
                  "Logging configuration differs from the configuration that has already been written. Update your logging configuration so that it is the same for each context"
               );
            }
         } else {
            file.create(new ByteArrayResource(this.newContent));
         }
      }

      private byte[] toByteArray(InputStreamSource content) throws IOException {
         try (InputStream inputStream = content.getInputStream()) {
            return inputStream.readAllBytes();
         }
      }
   }
}
