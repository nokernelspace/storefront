package org.springframework.resilience.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Reflective
public @interface ConcurrencyLimit {
   @AliasFor("limit")
   int value() default Integer.MIN_VALUE;

   @AliasFor("value")
   int limit() default Integer.MIN_VALUE;

   String limitString() default "";

   ConcurrencyLimit.ThrottlePolicy policy() default ConcurrencyLimit.ThrottlePolicy.BLOCK;

   enum ThrottlePolicy {
      BLOCK,
      REJECT;
   }
}
