package org.springframework.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Bean {
   @AliasFor("name")
   String[] value() default {};

   @AliasFor("value")
   String[] name() default {};

   boolean autowireCandidate() default true;

   boolean defaultCandidate() default true;

   Bean.Bootstrap bootstrap() default Bean.Bootstrap.DEFAULT;

   String initMethod() default "";

   String destroyMethod() default "(inferred)";

   enum Bootstrap {
      DEFAULT,
      BACKGROUND;
   }
}
