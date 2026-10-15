package org.springframework.context.aot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

public class ReflectiveProcessorAotContributionBuilder {
   private static final ReflectiveRuntimeHintsRegistrar registrar = new ReflectiveRuntimeHintsRegistrar();
   private final Set<Class<?>> classes = new LinkedHashSet<>();

   public ReflectiveProcessorAotContributionBuilder withClasses(Iterable<Class<?>> classes) {
      this.classes.addAll(StreamSupport.stream(classes.spliterator(), false).filter(registrar::isCandidate).toList());
      return this;
   }

   public ReflectiveProcessorAotContributionBuilder withClasses(Class<?>[] classes) {
      return this.withClasses(Arrays.asList(classes));
   }

   public ReflectiveProcessorAotContributionBuilder scan(@Nullable ClassLoader classLoader, String... packageNames) {
      ReflectiveProcessorAotContributionBuilder.ReflectiveClassPathScanner scanner = new ReflectiveProcessorAotContributionBuilder.ReflectiveClassPathScanner(
         classLoader
      );
      return this.withClasses(scanner.scan(packageNames));
   }

   public @Nullable BeanFactoryInitializationAotContribution build() {
      return !this.classes.isEmpty() ? new ReflectiveProcessorAotContributionBuilder.AotContribution(this.classes) : null;
   }

   private static class AotContribution implements BeanFactoryInitializationAotContribution {
      private final Class<?>[] classes;

      public AotContribution(Set<Class<?>> classes) {
         this.classes = classes.toArray(Class[]::new);
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         RuntimeHints runtimeHints = generationContext.getRuntimeHints();
         ReflectiveProcessorAotContributionBuilder.registrar.registerRuntimeHints(runtimeHints, this.classes);
      }
   }

   private static class ReflectiveClassPathScanner extends ClassPathScanningCandidateComponentProvider {
      private final @Nullable ClassLoader classLoader;

      ReflectiveClassPathScanner(@Nullable ClassLoader classLoader) {
         super(false);
         this.classLoader = classLoader;
         this.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
      }

      Class<?>[] scan(String... packageNames) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Scanning all types for reflective usage from " + Arrays.toString(packageNames));
         }

         Set<BeanDefinition> candidates = new HashSet<>();

         for (String packageName : packageNames) {
            candidates.addAll(this.findCandidateComponents(packageName));
         }

         return candidates.stream().map(c -> (Class)c.getAttribute("type")).toArray(Class[]::new);
      }

      @Override
      protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
         String className = beanDefinition.getBeanClassName();
         if (className != null) {
            try {
               Class<?> type = ClassUtils.forName(className, this.classLoader);
               beanDefinition.setAttribute("type", type);
               return ReflectiveProcessorAotContributionBuilder.registrar.isCandidate(type);
            } catch (Exception ex) {
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Ignoring '%s' for reflective usage: %s".formatted(className, ex.getMessage()));
               }
            }
         }

         return false;
      }
   }
}
