package org.springframework.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ReflectiveScan {
   @AliasFor("basePackages")
   String[] value() default {};

   @AliasFor("value")
   String[] basePackages() default {};

   Class<?>[] basePackageClasses() default {};
}
