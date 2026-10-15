package org.springframework.format.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface DateTimeFormat {
   String style() default "SS";

   DateTimeFormat.ISO iso() default DateTimeFormat.ISO.NONE;

   String pattern() default "";

   String[] fallbackPatterns() default {};

   enum ISO {
      DATE,
      TIME,
      DATE_TIME,
      NONE;
   }
}
