package io.micrometer.observation.annotation;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.aop.Cardinality;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Repeatable(ObservationKeyValues.class)
public @interface ObservationKeyValue {
   String value() default "";

   String key() default "";

   String expression() default "";

   Class<? extends ValueResolver> resolver() default NoOpValueResolver.class;

   Cardinality cardinality() default Cardinality.HIGH;
}
