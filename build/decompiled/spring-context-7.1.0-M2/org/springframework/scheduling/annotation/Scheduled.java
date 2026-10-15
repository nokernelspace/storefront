package org.springframework.scheduling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.springframework.aot.hint.annotation.Reflective;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Schedules.class)
@Reflective
public @interface Scheduled {
   String CRON_DISABLED = "-";

   String cron() default "";

   String zone() default "";

   long fixedRate() default -1L;

   String fixedRateString() default "";

   long fixedDelay() default -1L;

   String fixedDelayString() default "";

   long initialDelay() default -1L;

   String initialDelayString() default "";

   TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

   String scheduler() default "";
}
