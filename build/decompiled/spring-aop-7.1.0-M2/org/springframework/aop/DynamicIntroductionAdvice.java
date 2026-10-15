package org.springframework.aop;

import org.aopalliance.aop.Advice;

public interface DynamicIntroductionAdvice extends Advice {
   boolean implementsInterface(Class<?> intf);
}
