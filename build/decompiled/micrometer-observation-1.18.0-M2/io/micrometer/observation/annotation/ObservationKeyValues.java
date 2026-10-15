package io.micrometer.observation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Documented
public @interface ObservationKeyValues {
   ObservationKeyValue[] value();
}
