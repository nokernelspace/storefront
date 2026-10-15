package org.springframework.web.service.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ImportHttpServices.Container.class)
@Import(ImportHttpServiceRegistrar.class)
public @interface ImportHttpServices {
   @AliasFor("types")
   Class<?>[] value() default {};

   @AliasFor("value")
   Class<?>[] types() default {};

   String group() default "default";

   Class<?>[] basePackageClasses() default {};

   String[] basePackages() default {};

   HttpServiceGroup.ClientType clientType() default HttpServiceGroup.ClientType.UNSPECIFIED;

   @Target(ElementType.TYPE)
   @Retention(RetentionPolicy.RUNTIME)
   @Documented
   @Import(ImportHttpServiceRegistrar.class)
   @interface Container {
      ImportHttpServices[] value();
   }
}
