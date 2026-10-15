package org.springframework.beans.factory.aot;

import java.util.List;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.javapoet.ClassName;
import org.springframework.util.StringUtils;

class BeanDefinitionMethodGenerator {
   private final BeanDefinitionMethodGeneratorFactory methodGeneratorFactory;
   private final RegisteredBean registeredBean;
   private final @Nullable String currentPropertyName;
   private final List<BeanRegistrationAotContribution> aotContributions;

   BeanDefinitionMethodGenerator(
      BeanDefinitionMethodGeneratorFactory methodGeneratorFactory,
      RegisteredBean registeredBean,
      @Nullable String currentPropertyName,
      List<BeanRegistrationAotContribution> aotContributions
   ) {
      this.methodGeneratorFactory = methodGeneratorFactory;
      this.registeredBean = registeredBean;
      this.currentPropertyName = currentPropertyName;
      this.aotContributions = aotContributions;
   }

   MethodReference generateBeanDefinitionMethod(GenerationContext generationContext, BeanRegistrationsCode beanRegistrationsCode) {
      BeanRegistrationCodeFragments codeFragments = this.getCodeFragments(generationContext, beanRegistrationsCode);
      ClassName target = codeFragments.getTarget(this.registeredBean);
      if (this.isWritablePackageName(target)) {
         GeneratedClass generatedClass = lookupGeneratedClass(generationContext, target);
         GeneratedMethods generatedMethods = generatedClass.getMethods().withPrefix(this.getName());
         GeneratedMethod generatedMethod = this.generateBeanDefinitionMethod(
            generationContext, generatedClass.getName(), generatedMethods, codeFragments, Modifier.PUBLIC
         );
         return generatedMethod.toMethodReference();
      } else {
         GeneratedMethods generatedMethods = beanRegistrationsCode.getMethods().withPrefix(this.getName());
         GeneratedMethod generatedMethod = this.generateBeanDefinitionMethod(
            generationContext, beanRegistrationsCode.getClassName(), generatedMethods, codeFragments, Modifier.PRIVATE
         );
         return generatedMethod.toMethodReference();
      }
   }

   private boolean isWritablePackageName(ClassName target) {
      String packageName = target.packageName();
      return !packageName.startsWith("java.") && !packageName.startsWith("javax.");
   }

   private static GeneratedClass lookupGeneratedClass(GenerationContext generationContext, ClassName target) {
      ClassName topLevelClassName = target.topLevelClassName();
      GeneratedClass generatedClass = generationContext.getGeneratedClasses().getOrAddForFeatureComponent("BeanDefinitions", topLevelClassName, type -> {
         type.addJavadoc("Bean definitions for {@link $T}.", new Object[]{topLevelClassName});
         type.addModifiers(new Modifier[]{Modifier.PUBLIC});
      });
      List<String> names = target.simpleNames();
      if (names.size() == 1) {
         return generatedClass;
      }

      List<String> namesToProcess = names.subList(1, names.size());
      ClassName currentTargetClassName = topLevelClassName;
      GeneratedClass tmp = generatedClass;

      for (String nameToProcess : namesToProcess) {
         currentTargetClassName = currentTargetClassName.nestedClass(nameToProcess);
         tmp = createInnerClass(tmp, nameToProcess, currentTargetClassName);
      }

      return tmp;
   }

   private static GeneratedClass createInnerClass(GeneratedClass generatedClass, String name, ClassName target) {
      return generatedClass.getOrAdd(name, type -> {
         type.addJavadoc("Bean definitions for {@link $T}.", new Object[]{target});
         type.addModifiers(new Modifier[]{Modifier.PUBLIC, Modifier.STATIC});
      });
   }

   private BeanRegistrationCodeFragments getCodeFragments(GenerationContext generationContext, BeanRegistrationsCode beanRegistrationsCode) {
      BeanRegistrationCodeFragments codeFragments = new DefaultBeanRegistrationCodeFragments(
         beanRegistrationsCode, this.registeredBean, this.methodGeneratorFactory
      );

      for (BeanRegistrationAotContribution aotContribution : this.aotContributions) {
         codeFragments = aotContribution.customizeBeanRegistrationCodeFragments(generationContext, codeFragments);
      }

      return codeFragments;
   }

   private GeneratedMethod generateBeanDefinitionMethod(
      GenerationContext generationContext,
      ClassName className,
      GeneratedMethods generatedMethods,
      BeanRegistrationCodeFragments codeFragments,
      Modifier modifier
   ) {
      BeanRegistrationCodeGenerator codeGenerator = new BeanRegistrationCodeGenerator(className, generatedMethods, this.registeredBean, codeFragments);
      this.aotContributions.forEach(aotContribution -> aotContribution.applyTo(generationContext, codeGenerator));
      CodeWarnings codeWarnings = new CodeWarnings();
      codeWarnings.detectDeprecation(this.registeredBean.getBeanType());
      return generatedMethods.add("getBeanDefinition", method -> {
         method.addJavadoc("Get the $L definition for '$L'.", new Object[]{this.registeredBean.isInnerBean() ? "inner-bean" : "bean", this.getName()});
         method.addModifiers(new Modifier[]{modifier, Modifier.STATIC});
         codeWarnings.suppress(method);
         method.returns(BeanDefinition.class);
         method.addCode(codeGenerator.generateCode(generationContext));
      });
   }

   private String getName() {
      if (this.currentPropertyName != null) {
         return this.currentPropertyName;
      }

      if (!this.registeredBean.isGeneratedBeanName()) {
         return this.getSimpleBeanName(this.registeredBean.getBeanName());
      }

      RegisteredBean nonGeneratedParent = this.registeredBean;

      while (nonGeneratedParent != null && nonGeneratedParent.isGeneratedBeanName()) {
         nonGeneratedParent = nonGeneratedParent.getParent();
      }

      return nonGeneratedParent != null ? this.getSimpleBeanName(nonGeneratedParent.getBeanName()) + "InnerBean" : "innerBean";
   }

   private String getSimpleBeanName(String beanName) {
      int lastDot = beanName.lastIndexOf(46);
      beanName = lastDot != -1 ? beanName.substring(lastDot + 1) : beanName;
      int lastDollar = beanName.lastIndexOf(36);
      beanName = lastDollar != -1 ? beanName.substring(lastDollar + 1) : beanName;
      return StringUtils.uncapitalize(beanName);
   }
}
