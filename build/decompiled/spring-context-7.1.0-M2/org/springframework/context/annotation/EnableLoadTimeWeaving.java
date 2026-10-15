package org.springframework.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(LoadTimeWeavingConfiguration.class)
public @interface EnableLoadTimeWeaving {
   EnableLoadTimeWeaving.AspectJWeaving aspectjWeaving() default EnableLoadTimeWeaving.AspectJWeaving.AUTODETECT;

   enum AspectJWeaving {
      ENABLED,
      DISABLED,
      AUTODETECT;
   }
}
