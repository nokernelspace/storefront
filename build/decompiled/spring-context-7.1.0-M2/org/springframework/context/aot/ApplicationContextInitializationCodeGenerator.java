package org.springframework.context.aot;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.MethodSpec;
import org.springframework.javapoet.ParameterizedTypeName;
import org.springframework.javapoet.TypeName;
import org.springframework.javapoet.TypeSpec.Builder;

class ApplicationContextInitializationCodeGenerator implements BeanFactoryInitializationCode {
   private static final String INITIALIZE_METHOD = "initialize";
   private static final String APPLICATION_CONTEXT_VARIABLE = "applicationContext";
   private final GenericApplicationContext applicationContext;
   private final GeneratedClass generatedClass;
   private final List<MethodReference> initializers = new ArrayList<>();

   ApplicationContextInitializationCodeGenerator(GenericApplicationContext applicationContext, GenerationContext generationContext) {
      this.applicationContext = applicationContext;
      this.generatedClass = generationContext.getGeneratedClasses().addForFeature("ApplicationContextInitializer", this::generateType);
      this.generatedClass.reserveMethodNames(new String[]{"initialize"});
   }

   private void generateType(Builder type) {
      type.addJavadoc("{@link $T} to restore an application context based on previous AOT processing.", new Object[]{ApplicationContextInitializer.class});
      type.addModifiers(new Modifier[]{Modifier.PUBLIC});
      type.addSuperinterface(ParameterizedTypeName.get(ApplicationContextInitializer.class, new Type[]{GenericApplicationContext.class}));
      type.addMethod(this.generateInitializeMethod());
   }

   private MethodSpec generateInitializeMethod() {
      org.springframework.javapoet.MethodSpec.Builder method = MethodSpec.methodBuilder("initialize");
      method.addAnnotation(Override.class);
      method.addModifiers(new Modifier[]{Modifier.PUBLIC});
      method.addParameter(GenericApplicationContext.class, "applicationContext", new Modifier[0]);
      method.addCode(this.generateInitializeCode());
      return method.build();
   }

   private CodeBlock generateInitializeCode() {
      org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
      code.addStatement("$T $L = $L.getDefaultListableBeanFactory()", new Object[]{DefaultListableBeanFactory.class, "beanFactory", "applicationContext"});
      code.addStatement("$L.setAutowireCandidateResolver(new $T())", new Object[]{"beanFactory", ContextAnnotationAutowireCandidateResolver.class});
      code.addStatement("$L.setDependencyComparator($T.INSTANCE)", new Object[]{"beanFactory", AnnotationAwareOrderComparator.class});
      code.add(this.generateActiveProfilesInitializeCode());
      ArgumentCodeGenerator argCodeGenerator = createInitializerMethodArgumentCodeGenerator();

      for (MethodReference initializer : this.initializers) {
         code.addStatement(initializer.toInvokeCodeBlock(argCodeGenerator, this.generatedClass.getName()));
      }

      return code.build();
   }

   private CodeBlock generateActiveProfilesInitializeCode() {
      org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
      ConfigurableEnvironment environment = this.applicationContext.getEnvironment();
      if (!Arrays.equals(environment.getActiveProfiles(), environment.getDefaultProfiles())) {
         for (String activeProfile : environment.getActiveProfiles()) {
            code.addStatement("$L.getEnvironment().addActiveProfile($S)", new Object[]{"applicationContext", activeProfile});
         }
      }

      return code.build();
   }

   static ArgumentCodeGenerator createInitializerMethodArgumentCodeGenerator() {
      return ArgumentCodeGenerator.from(new ApplicationContextInitializationCodeGenerator.InitializerMethodArgumentCodeGenerator());
   }

   public ClassName getClassName() {
      return this.generatedClass.getName();
   }

   public GeneratedMethods getMethods() {
      return this.generatedClass.getMethods();
   }

   public void addInitializer(MethodReference methodReference) {
      this.initializers.add(methodReference);
   }

   private static class InitializerMethodArgumentCodeGenerator implements Function<TypeName, CodeBlock> {
      public @Nullable CodeBlock apply(TypeName typeName) {
         return typeName instanceof ClassName className ? this.apply(className) : null;
      }

      private @Nullable CodeBlock apply(ClassName className) {
         String name = className.canonicalName();
         if (name.equals(DefaultListableBeanFactory.class.getName())
            || name.equals(ListableBeanFactory.class.getName())
            || name.equals(ConfigurableListableBeanFactory.class.getName())) {
            return CodeBlock.of("beanFactory", new Object[0]);
         } else if (name.equals(ConfigurableEnvironment.class.getName()) || name.equals(Environment.class.getName())) {
            return CodeBlock.of("$L.getEnvironment()", new Object[]{"applicationContext"});
         } else {
            return name.equals(ResourceLoader.class.getName()) ? CodeBlock.of("applicationContext", new Object[0]) : null;
         }
      }
   }
}
