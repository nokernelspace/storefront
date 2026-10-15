package org.springframework.web.bind.annotation;

import java.lang.annotation.Annotation;
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
public @interface ControllerAdvice {
   @AliasFor(annotation = Component.class, attribute = "value")
   String name() default "";

   @AliasFor("basePackages")
   String[] value() default {};

   @AliasFor("value")
   String[] basePackages() default {};

   Class<?>[] basePackageClasses() default {};

   Class<?>[] assignableTypes() default {};

   Class<? extends Annotation>[] annotations() default {};
}
