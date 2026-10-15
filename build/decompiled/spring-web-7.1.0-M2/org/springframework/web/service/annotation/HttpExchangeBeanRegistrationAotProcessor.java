package org.springframework.web.service.annotation;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ProxyHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.Search;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

class HttpExchangeBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      Class<?> beanClass = registeredBean.getBeanClass();
      List<Class<?>> exchangeInterfaces = new ArrayList<>();
      Search search = MergedAnnotations.search(SearchStrategy.TYPE_HIERARCHY);

      for (Class<?> interfaceClass : ClassUtils.getAllInterfacesForClass(beanClass)) {
         ReflectionUtils.doWithMethods(interfaceClass, method -> {
            if (!exchangeInterfaces.contains(interfaceClass) && search.from(method).isPresent(HttpExchange.class)) {
               exchangeInterfaces.add(interfaceClass);
            }
         });
      }

      return !exchangeInterfaces.isEmpty() ? new HttpExchangeBeanRegistrationAotProcessor.AotContribution(exchangeInterfaces) : null;
   }

   private static class AotContribution implements BeanRegistrationAotContribution {
      private final List<Class<?>> httpExchangeInterfaces;

      public AotContribution(List<Class<?>> httpExchangeInterfaces) {
         this.httpExchangeInterfaces = httpExchangeInterfaces;
      }

      public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         ProxyHints proxyHints = generationContext.getRuntimeHints().proxies();

         for (Class<?> httpExchangeInterface : this.httpExchangeInterfaces) {
            proxyHints.registerJdkProxy(AopProxyUtils.completeJdkProxyInterfaces(new Class[]{httpExchangeInterface}));
         }
      }
   }
}
