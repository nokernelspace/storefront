package org.springframework.aop.scope;

import java.util.function.Predicate;
import javax.lang.model.element.Modifier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;

class ScopedProxyBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   private static final Log logger = LogFactory.getLog(ScopedProxyBeanRegistrationAotProcessor.class);

   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      Class<?> beanClass = registeredBean.getBeanClass();
      if (beanClass.equals(ScopedProxyFactoryBean.class)) {
         String targetBeanName = this.getTargetBeanName(registeredBean.getMergedBeanDefinition());
         BeanDefinition targetBeanDefinition = this.getTargetBeanDefinition(registeredBean.getBeanFactory(), targetBeanName);
         if (targetBeanDefinition == null) {
            logger.warn("Could not handle " + ScopedProxyFactoryBean.class.getSimpleName() + ": no target bean definition found with name " + targetBeanName);
            return null;
         } else {
            return BeanRegistrationAotContribution.withCustomCodeFragments(
               codeFragments -> new ScopedProxyBeanRegistrationAotProcessor.ScopedProxyBeanRegistrationCodeFragments(
                  codeFragments, registeredBean, targetBeanName, targetBeanDefinition
               )
            );
         }
      } else {
         return null;
      }
   }

   private @Nullable String getTargetBeanName(BeanDefinition beanDefinition) {
      return beanDefinition.getPropertyValues().get("targetBeanName") instanceof String targetBeanName ? targetBeanName : null;
   }

   private @Nullable BeanDefinition getTargetBeanDefinition(ConfigurableBeanFactory beanFactory, @Nullable String targetBeanName) {
      return targetBeanName != null && beanFactory.containsBean(targetBeanName) ? beanFactory.getMergedBeanDefinition(targetBeanName) : null;
   }

   private static class ScopedProxyBeanRegistrationCodeFragments extends BeanRegistrationCodeFragmentsDecorator {
      private static final String REGISTERED_BEAN_PARAMETER_NAME = "registeredBean";
      private final RegisteredBean registeredBean;
      private final String targetBeanName;
      private final BeanDefinition targetBeanDefinition;

      ScopedProxyBeanRegistrationCodeFragments(
         BeanRegistrationCodeFragments delegate, RegisteredBean registeredBean, String targetBeanName, BeanDefinition targetBeanDefinition
      ) {
         super(delegate);
         this.registeredBean = registeredBean;
         this.targetBeanName = targetBeanName;
         this.targetBeanDefinition = targetBeanDefinition;
      }

      public ClassName getTarget(RegisteredBean registeredBean) {
         return ClassName.get(this.targetBeanDefinition.getResolvableType().toClass());
      }

      public CodeBlock generateNewBeanDefinitionCode(GenerationContext generationContext, ResolvableType beanType, BeanRegistrationCode beanRegistrationCode) {
         return super.generateNewBeanDefinitionCode(generationContext, this.targetBeanDefinition.getResolvableType(), beanRegistrationCode);
      }

      public CodeBlock generateSetBeanDefinitionPropertiesCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition, Predicate<String> attributeFilter
      ) {
         RootBeanDefinition processedBeanDefinition = new RootBeanDefinition(beanDefinition);
         processedBeanDefinition.setTargetType(this.targetBeanDefinition.getResolvableType());
         processedBeanDefinition.getPropertyValues().removePropertyValue("targetBeanName");
         return super.generateSetBeanDefinitionPropertiesCode(generationContext, beanRegistrationCode, processedBeanDefinition, attributeFilter);
      }

      public CodeBlock generateInstanceSupplierCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, boolean allowDirectSupplierShortcut
      ) {
         GeneratedMethod generatedMethod = beanRegistrationCode.getMethods().add("getScopedProxyInstance", method -> {
            method.addJavadoc("Create the scoped proxy bean instance for '$L'.", new Object[]{this.registeredBean.getBeanName()});
            method.addModifiers(new Modifier[]{Modifier.PRIVATE, Modifier.STATIC});
            method.returns(ScopedProxyFactoryBean.class);
            method.addParameter(RegisteredBean.class, "registeredBean", new Modifier[0]);
            method.addStatement("$T factory = new $T()", new Object[]{ScopedProxyFactoryBean.class, ScopedProxyFactoryBean.class});
            method.addStatement("factory.setTargetBeanName($S)", new Object[]{this.targetBeanName});
            method.addStatement("factory.setBeanFactory($L.getBeanFactory())", new Object[]{"registeredBean"});
            method.addStatement("return factory", new Object[0]);
         });
         return CodeBlock.of("$T.of($L)", new Object[]{InstanceSupplier.class, generatedMethod.toMethodReference().toCodeBlock()});
      }
   }
}
