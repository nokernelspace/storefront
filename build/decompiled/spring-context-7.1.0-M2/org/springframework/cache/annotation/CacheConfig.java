package org.springframework.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheConfig {
   @AliasFor("cacheNames")
   String[] value() default {};

   @AliasFor("value")
   String[] cacheNames() default {};

   String keyGenerator() default "";

   String cacheManager() default "";

   String cacheResolver() default "";
}
