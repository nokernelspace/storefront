package org.springframework.web.service.registry;

import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;

final class HttpServiceProxyBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      return registeredBean.getMergedBeanDefinition().getAttribute("httpServiceGroupName") instanceof String groupName
         ? BeanRegistrationAotContribution.withCustomCodeFragments(
            codeFragments -> new HttpServiceProxyBeanRegistrationAotProcessor.HttpServiceProxyRegistrationCodeFragments(
               codeFragments, groupName, registeredBean.getBeanClass()
            )
         )
         : null;
   }

   private static class HttpServiceProxyRegistrationCodeFragments extends BeanRegistrationCodeFragmentsDecorator {
      private static final String REGISTERED_BEAN_PARAMETER = "registeredBean";
      private final String groupName;
      private final Class<?> clientType;

      HttpServiceProxyRegistrationCodeFragments(BeanRegistrationCodeFragments delegate, String groupName, Class<?> clientType) {
         super(delegate);
         this.groupName = groupName;
         this.clientType = clientType;
      }

      public ClassName getTarget(RegisteredBean registeredBean) {
         return ClassName.get(registeredBean.getBeanClass());
      }

      public CodeBlock generateInstanceSupplierCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, boolean allowDirectSupplierShortcut
      ) {
         GeneratedMethod generatedMethod = beanRegistrationCode.getMethods()
            .add(
               "getHttpServiceProxy",
               method -> {
                  method.addJavadoc("Create the HTTP service proxy for {@link $T} and group {@code $L}.", new Object[]{this.clientType, this.groupName});
                  method.addModifiers(new Modifier[]{Modifier.PRIVATE, Modifier.STATIC});
                  method.addParameter(RegisteredBean.class, "registeredBean", new Modifier[0]);
                  method.returns(Object.class);
                  method.addStatement(
                     "return $L.getBeanFactory().getBean($S, $T.class).getClient($S, $T.class)",
                     new Object[]{"registeredBean", "httpServiceProxyRegistry", HttpServiceProxyRegistry.class, this.groupName, this.clientType}
                  );
               }
            );
         return CodeBlock.of("$T.of($L)", new Object[]{InstanceSupplier.class, generatedMethod.toMethodReference().toCodeBlock()});
      }
   }
}
