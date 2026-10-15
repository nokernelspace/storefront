package org.springframework.aop.framework;

public interface AopProxyFactory {
   AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException;
}
