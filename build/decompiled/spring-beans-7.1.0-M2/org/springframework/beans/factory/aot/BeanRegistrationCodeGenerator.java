package org.springframework.beans.factory.aot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.util.Assert;

class BeanRegistrationCodeGenerator implements BeanRegistrationCode {
   private static final Predicate<String> REJECT_ALL_ATTRIBUTES_FILTER = attribute -> false;
   private final ClassName className;
   private final GeneratedMethods generatedMethods;
   private final List<MethodReference> instancePostProcessors = new ArrayList<>();
   private final RegisteredBean registeredBean;
   private final BeanRegistrationCodeFragments codeFragments;

   BeanRegistrationCodeGenerator(
      ClassName className, GeneratedMethods generatedMethods, RegisteredBean registeredBean, BeanRegistrationCodeFragments codeFragments
   ) {
      this.className = className;
      this.generatedMethods = generatedMethods;
      this.registeredBean = registeredBean;
      this.codeFragments = codeFragments;
   }

   @Override
   public ClassName getClassName() {
      return this.className;
   }

   @Override
   public GeneratedMethods getMethods() {
      return this.generatedMethods;
   }

   @Override
   public void addInstancePostProcessor(MethodReference methodReference) {
      Assert.notNull(methodReference, "'methodReference' must not be null");
      this.instancePostProcessors.add(methodReference);
   }

   CodeBlock generateCode(GenerationContext generationContext) {
      Builder code = CodeBlock.builder();
      code.add(this.codeFragments.generateNewBeanDefinitionCode(generationContext, this.registeredBean.getBeanType(), this));
      code.add(
         this.codeFragments
            .generateSetBeanDefinitionPropertiesCode(generationContext, this, this.registeredBean.getMergedBeanDefinition(), REJECT_ALL_ATTRIBUTES_FILTER)
      );
      CodeBlock instanceSupplierCode = this.codeFragments.generateInstanceSupplierCode(generationContext, this, this.instancePostProcessors.isEmpty());
      code.add(this.codeFragments.generateSetBeanInstanceSupplierCode(generationContext, this, instanceSupplierCode, this.instancePostProcessors));
      code.add(this.codeFragments.generateReturnCode(generationContext, this));
      return code.build();
   }
}
