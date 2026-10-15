package org.springframework.web.service.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.Mapping;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Mapping
@Reflective(HttpExchangeReflectiveProcessor.class)
public @interface HttpExchange {
   @AliasFor("url")
   String value() default "";

   @AliasFor("value")
   String url() default "";

   String method() default "";

   String contentType() default "";

   String[] accept() default {};

   String[] headers() default {};

   String version() default "";
}
