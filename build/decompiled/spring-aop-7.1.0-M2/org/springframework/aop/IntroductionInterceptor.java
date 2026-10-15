package org.springframework.aop;

import org.aopalliance.intercept.MethodInterceptor;

public interface IntroductionInterceptor extends MethodInterceptor, DynamicIntroductionAdvice {
}
