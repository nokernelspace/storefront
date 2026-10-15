package org.springframework.boot.web.servlet;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebInitParam;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.Order;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Order
public @interface ServletRegistration {
   boolean enabled() default true;

   @AliasFor(annotation = Order.class, attribute = "value")
   int order() default Integer.MAX_VALUE;

   String name() default "";

   boolean asyncSupported() default true;

   boolean ignoreRegistrationFailure() default false;

   String[] urlMappings() default {};

   int loadOnStartup() default -1;

   WebInitParam[] initParameters() default {};

   MultipartConfig multipartConfig() default @MultipartConfig;
}
