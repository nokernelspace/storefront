package org.springframework.format.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface NumberFormat {
   NumberFormat.Style style() default NumberFormat.Style.DEFAULT;

   String pattern() default "";

   enum Style {
      DEFAULT,
      NUMBER,
      PERCENT,
      CURRENCY;
   }
}
