package org.springframework.aot.hint.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.aot.hint.MemberCategory;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Reflective(RegisterReflectionReflectiveProcessor.class)
public @interface RegisterReflection {
   Class<?>[] classes() default {};

   String[] classNames() default {};

   MemberCategory[] memberCategories() default {};
}
