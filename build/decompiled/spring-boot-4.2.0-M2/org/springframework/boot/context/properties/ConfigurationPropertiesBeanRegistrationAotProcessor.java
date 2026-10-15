package org.springframework.boot.context.properties;

import java.util.function.Predicate;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.BindMethod;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;

class ConfigurationPropertiesBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      return !this.isImmutableConfigurationPropertiesBeanDefinition(registeredBean.getMergedBeanDefinition())
         ? null
         : BeanRegistrationAotContribution.withCustomCodeFragments(
            codeFragments -> new ConfigurationPropertiesBeanRegistrationAotProcessor.ConfigurationPropertiesBeanRegistrationCodeFragments(
               codeFragments, registeredBean
            )
         );
   }

   private boolean isImmutableConfigurationPropertiesBeanDefinition(BeanDefinition beanDefinition) {
      return BindMethod.VALUE_OBJECT.equals(BindMethodAttribute.get(beanDefinition));
   }

   private static class ConfigurationPropertiesBeanRegistrationCodeFragments extends BeanRegistrationCodeFragmentsDecorator {
      private static final String REGISTERED_BEAN_PARAMETER_NAME = "registeredBean";
      private final RegisteredBean registeredBean;

      ConfigurationPropertiesBeanRegistrationCodeFragments(BeanRegistrationCodeFragments codeFragments, RegisteredBean registeredBean) {
         super(codeFragments);
         this.registeredBean = registeredBean;
      }

      public CodeBlock generateSetBeanDefinitionPropertiesCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition, Predicate<String> attributeFilter
      ) {
         return super.generateSetBeanDefinitionPropertiesCode(
            generationContext, beanRegistrationCode, beanDefinition, attributeFilter.or(BindMethodAttribute.NAME::equals)
         );
      }

      public ClassName getTarget(RegisteredBean registeredBean) {
         return ClassName.get(this.registeredBean.getBeanClass());
      }

      public CodeBlock generateInstanceSupplierCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, boolean allowDirectSupplierShortcut
      ) {
         GeneratedMethod generatedMethod = beanRegistrationCode.getMethods()
            .add(
               "getInstance",
               method -> {
                  Class<?> beanClass = this.registeredBean.getBeanClass();
                  method.addJavadoc("Get the bean instance for '$L'.", new Object[]{this.registeredBean.getBeanName()})
                     .addModifiers(new Modifier[]{Modifier.PRIVATE, Modifier.STATIC})
                     .returns(beanClass)
                     .addParameter(RegisteredBean.class, "registeredBean", new Modifier[0])
                     .addStatement("$T beanFactory = registeredBean.getBeanFactory()", new Object[]{BeanFactory.class})
                     .addStatement("$T beanName = registeredBean.getBeanName()", new Object[]{String.class})
                     .addStatement("$T<?> beanClass = registeredBean.getBeanClass()", new Object[]{Class.class})
                     .addStatement("return ($T) $T.from(beanFactory, beanName, beanClass)", new Object[]{beanClass, ConstructorBound.class});
               }
            );
         return CodeBlock.of("$T.of($T::$L)", new Object[]{InstanceSupplier.class, beanRegistrationCode.getClassName(), generatedMethod.getName()});
      }
   }
}
