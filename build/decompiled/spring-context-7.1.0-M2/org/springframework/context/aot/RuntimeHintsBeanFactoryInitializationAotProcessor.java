package org.springframework.context.aot;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.log.LogMessage;
import org.springframework.util.CollectionUtils;

class RuntimeHintsBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {
   private static final Log logger = LogFactory.getLog(RuntimeHintsBeanFactoryInitializationAotProcessor.class);

   public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
      Map<Class<? extends RuntimeHintsRegistrar>, RuntimeHintsRegistrar> registrars = AotServices.factories(beanFactory.getBeanClassLoader())
         .load(RuntimeHintsRegistrar.class)
         .stream()
         .collect(LinkedHashMap::new, (map, item) -> map.put(item.getClass(), item), Map::putAll);
      this.extractFromBeanFactory(beanFactory)
         .forEach(registrarClass -> registrars.computeIfAbsent((Class<? extends RuntimeHintsRegistrar>)registrarClass, BeanUtils::instantiateClass));
      return new RuntimeHintsBeanFactoryInitializationAotProcessor.RuntimeHintsRegistrarContribution(registrars.values(), beanFactory.getBeanClassLoader());
   }

   private Set<Class<? extends RuntimeHintsRegistrar>> extractFromBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      Set<Class<? extends RuntimeHintsRegistrar>> registrarClasses = new LinkedHashSet<>();

      for (String beanName : beanFactory.getBeanDefinitionNames()) {
         beanFactory.findAllAnnotationsOnBean(beanName, ImportRuntimeHints.class, true)
            .forEach(annotation -> registrarClasses.addAll(this.extractFromBeanDefinition(beanName, annotation)));
      }

      return registrarClasses;
   }

   private Set<Class<? extends RuntimeHintsRegistrar>> extractFromBeanDefinition(String beanName, ImportRuntimeHints annotation) {
      Class<? extends RuntimeHintsRegistrar>[] registrarClasses = annotation.value();
      Set<Class<? extends RuntimeHintsRegistrar>> registrars = CollectionUtils.newLinkedHashSet(registrarClasses.length);

      for (Class<? extends RuntimeHintsRegistrar> registrarClass : registrarClasses) {
         if (logger.isTraceEnabled()) {
            logger.trace(LogMessage.format("Loaded [%s] registrar from annotated bean [%s]", registrarClass.getCanonicalName(), beanName));
         }

         registrars.add(registrarClass);
      }

      return registrars;
   }

   static class RuntimeHintsRegistrarContribution implements BeanFactoryInitializationAotContribution {
      private final Iterable<RuntimeHintsRegistrar> registrars;
      private final @Nullable ClassLoader beanClassLoader;

      RuntimeHintsRegistrarContribution(Iterable<RuntimeHintsRegistrar> registrars, @Nullable ClassLoader beanClassLoader) {
         this.registrars = registrars;
         this.beanClassLoader = beanClassLoader;
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         RuntimeHints hints = generationContext.getRuntimeHints();
         this.registrars
            .forEach(
               registrar -> {
                  if (RuntimeHintsBeanFactoryInitializationAotProcessor.logger.isTraceEnabled()) {
                     RuntimeHintsBeanFactoryInitializationAotProcessor.logger
                        .trace(LogMessage.format("Processing RuntimeHints contribution from [%s]", registrar.getClass().getCanonicalName()));
                  }

                  registrar.registerHints(hints, this.beanClassLoader);
               }
            );
      }
   }
}
