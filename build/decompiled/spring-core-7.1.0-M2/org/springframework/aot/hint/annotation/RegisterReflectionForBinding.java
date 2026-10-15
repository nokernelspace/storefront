package org.springframework.aot.hint.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RegisterReflection
@Reflective(RegisterReflectionForBindingProcessor.class)
public @interface RegisterReflectionForBinding {
   @AliasFor(annotation = RegisterReflection.class, attribute = "classes")
   Class<?>[] value() default {};

   @AliasFor(annotation = RegisterReflection.class, attribute = "classes")
   Class<?>[] classes() default {};

   @AliasFor(annotation = RegisterReflection.class, attribute = "classNames")
   String[] classNames() default {};
}
