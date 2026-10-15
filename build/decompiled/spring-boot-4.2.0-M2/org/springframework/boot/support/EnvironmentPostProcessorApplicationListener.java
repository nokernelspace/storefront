package org.springframework.boot.support;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.AotDetector;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedTypeReference;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class EnvironmentPostProcessorApplicationListener implements SmartApplicationListener, Ordered {
   static final String AOT_FEATURE_NAME = "EnvironmentPostProcessor";
   public static final int DEFAULT_ORDER = -2147483638;
   private final DeferredLogs deferredLogs;
   private int order = -2147483638;
   private final Function<@Nullable ClassLoader, EnvironmentPostProcessorsFactory> postProcessorsFactory;

   public EnvironmentPostProcessorApplicationListener() {
      this(EnvironmentPostProcessorsFactory::fromSpringFactories);
   }

   private EnvironmentPostProcessorApplicationListener(Function<@Nullable ClassLoader, EnvironmentPostProcessorsFactory> postProcessorsFactory) {
      this.postProcessorsFactory = postProcessorsFactory;
      this.deferredLogs = new DeferredLogs();
   }

   public static EnvironmentPostProcessorApplicationListener with(EnvironmentPostProcessorsFactory postProcessorsFactory) {
      return new EnvironmentPostProcessorApplicationListener(classloader -> postProcessorsFactory);
   }

   public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
      return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType)
         || ApplicationPreparedEvent.class.isAssignableFrom(eventType)
         || ApplicationFailedEvent.class.isAssignableFrom(eventType);
   }

   public void onApplicationEvent(ApplicationEvent event) {
      if (event instanceof ApplicationEnvironmentPreparedEvent environmentPreparedEvent) {
         this.onApplicationEnvironmentPreparedEvent(environmentPreparedEvent);
      }

      if (event instanceof ApplicationPreparedEvent) {
         this.onApplicationPreparedEvent();
      }

      if (event instanceof ApplicationFailedEvent) {
         this.onApplicationFailedEvent();
      }
   }

   private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
      ConfigurableEnvironment environment = event.getEnvironment();
      SpringApplication application = event.getSpringApplication();
      List<EnvironmentPostProcessor> postProcessors = this.getEnvironmentPostProcessors(application.getResourceLoader(), event.getBootstrapContext());
      this.addAotGeneratedEnvironmentPostProcessorIfNecessary(postProcessors, application);

      for (EnvironmentPostProcessor postProcessor : postProcessors) {
         postProcessor.postProcessEnvironment(environment, application);
      }
   }

   private void onApplicationPreparedEvent() {
      this.finish();
   }

   private void onApplicationFailedEvent() {
      this.finish();
   }

   private void finish() {
      this.deferredLogs.switchOverAll();
   }

   List<EnvironmentPostProcessor> getEnvironmentPostProcessors(@Nullable ResourceLoader resourceLoader, ConfigurableBootstrapContext bootstrapContext) {
      ClassLoader classLoader = resourceLoader != null ? resourceLoader.getClassLoader() : null;
      EnvironmentPostProcessorsFactory postProcessorsFactory = this.postProcessorsFactory.apply(classLoader);
      return postProcessorsFactory.getEnvironmentPostProcessors(this.deferredLogs, bootstrapContext);
   }

   private void addAotGeneratedEnvironmentPostProcessorIfNecessary(List<EnvironmentPostProcessor> postProcessors, SpringApplication springApplication) {
      if (AotDetector.useGeneratedArtifacts()) {
         ClassLoader classLoader = springApplication.getResourceLoader() != null ? springApplication.getResourceLoader().getClassLoader() : null;
         Class<?> mainApplicationClass = springApplication.getMainApplicationClass();
         Assert.state(mainApplicationClass != null, "mainApplicationClass not found");
         String postProcessorClassName = mainApplicationClass.getName() + "__EnvironmentPostProcessor";
         if (ClassUtils.isPresent(postProcessorClassName, classLoader)) {
            postProcessors.add(0, this.instantiateEnvironmentPostProcessor(postProcessorClassName, classLoader));
         }
      }
   }

   private EnvironmentPostProcessor instantiateEnvironmentPostProcessor(String postProcessorClassName, @Nullable ClassLoader classLoader) {
      try {
         Class<?> initializerClass = ClassUtils.resolveClassName(postProcessorClassName, classLoader);
         Assert.isAssignable(EnvironmentPostProcessor.class, initializerClass);
         return (EnvironmentPostProcessor)BeanUtils.instantiateClass(initializerClass);
      } catch (BeanInstantiationException ex) {
         throw new IllegalArgumentException("Failed to instantiate EnvironmentPostProcessor: " + postProcessorClassName, ex);
      }
   }

   public int getOrder() {
      return this.order;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   private static final class EnvironmentAotContribution implements BeanFactoryInitializationAotContribution {
      private static final String ENVIRONMENT_VARIABLE = "environment";
      private final String[] activeProfiles;

      private EnvironmentAotContribution(String[] activeProfiles) {
         this.activeProfiles = activeProfiles;
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         GeneratedClass generatedClass = generationContext.getGeneratedClasses().addForFeature("EnvironmentPostProcessor", type -> {
            type.addModifiers(new Modifier[]{Modifier.PUBLIC});
            type.addJavadoc("Configure the environment with AOT optimizations.", new Object[0]);
            type.addSuperinterface(EnvironmentPostProcessor.class);
         });
         generatedClass.getMethods().add("postProcessEnvironment", method -> {
            method.addModifiers(new Modifier[]{Modifier.PUBLIC});
            method.addAnnotation(Override.class);
            method.addParameter(ConfigurableEnvironment.class, "environment", new Modifier[0]);
            method.addParameter(SpringApplication.class, "application", new Modifier[0]);
            method.addCode(this.generateActiveProfilesInitializeCode());
         });
         generationContext.getRuntimeHints()
            .reflection()
            .registerType(GeneratedTypeReference.of(generatedClass.getName()), new MemberCategory[]{MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS});
      }

      private CodeBlock generateActiveProfilesInitializeCode() {
         Builder code = CodeBlock.builder();

         for (String activeProfile : this.activeProfiles) {
            code.addStatement("$L.addActiveProfile($S)", new Object[]{"environment", activeProfile});
         }

         return code.build();
      }
   }

   static class EnvironmentBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {
      public @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
         Environment environment = (Environment)beanFactory.getBean("environment", Environment.class);
         String[] activeProfiles = environment.getActiveProfiles();
         String[] defaultProfiles = environment.getDefaultProfiles();
         return !ObjectUtils.isEmpty(activeProfiles) && !Arrays.equals(activeProfiles, defaultProfiles)
            ? new EnvironmentPostProcessorApplicationListener.EnvironmentAotContribution(activeProfiles)
            : null;
      }
   }
}
