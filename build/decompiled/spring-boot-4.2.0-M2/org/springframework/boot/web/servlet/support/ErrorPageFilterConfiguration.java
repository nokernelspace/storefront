package org.springframework.boot.web.servlet.support;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.error.ErrorPageRegistrarBeanPostProcessor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ErrorPageFilterConfiguration {
   @Bean
   ErrorPageFilter errorPageFilter() {
      return new ErrorPageFilter();
   }

   @Bean
   FilterRegistrationBean<ErrorPageFilter> errorPageFilterRegistration(ErrorPageFilter filter) {
      FilterRegistrationBean<ErrorPageFilter> registration = new FilterRegistrationBean<>(filter);
      registration.setOrder(filter.getOrder());
      registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
      return registration;
   }

   @Bean
   static ErrorPageRegistrarBeanPostProcessor errorPageRegistrarBeanPostProcessor() {
      return new ErrorPageRegistrarBeanPostProcessor();
   }
}
