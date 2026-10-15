package org.springframework.context.aot;

import java.util.function.Supplier;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

public class ApplicationContextAotGenerator {
   public ClassName processAheadOfTime(GenericApplicationContext applicationContext, GenerationContext generationContext) {
      return this.withCglibClassHandler(new CglibClassHandler(generationContext), () -> {
         applicationContext.refreshForAotProcessing(generationContext.getRuntimeHints());
         ApplicationContextInitializationCodeGenerator codeGenerator = new ApplicationContextInitializationCodeGenerator(applicationContext, generationContext);
         DefaultListableBeanFactory beanFactory = applicationContext.getDefaultListableBeanFactory();
         new BeanFactoryInitializationAotContributions(beanFactory).applyTo(generationContext, codeGenerator);
         return codeGenerator.getClassName();
      });
   }

   private <T> T withCglibClassHandler(CglibClassHandler cglibClassHandler, Supplier<T> task) {
      try {
         ReflectUtils.setLoadedClassHandler(cglibClassHandler::handleLoadedClass);
         ReflectUtils.setGeneratedClassHandler(cglibClassHandler::handleGeneratedClass);
         return task.get();
      } finally {
         ReflectUtils.setLoadedClassHandler(null);
         ReflectUtils.setGeneratedClassHandler(null);
      }
   }
}
