package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Reflective(ExceptionHandlerReflectiveProcessor.class)
public @interface ExceptionHandler {
   @AliasFor("exception")
   Class<? extends Throwable>[] value() default {};

   @AliasFor("value")
   Class<? extends Throwable>[] exception() default {};

   String[] produces() default {};
}
