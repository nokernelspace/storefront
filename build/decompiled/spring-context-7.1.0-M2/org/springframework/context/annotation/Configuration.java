package org.springframework.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Configuration {
   @AliasFor(annotation = Component.class)
   String value() default "";

   boolean proxyBeanMethods() default true;

   @Deprecated(since = "7.0")
   boolean enforceUniqueMethods() default true;
}
