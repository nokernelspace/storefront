package org.springframework.beans.factory.aot;

import org.springframework.beans.factory.support.RegisteredBean;

@FunctionalInterface
public interface BeanRegistrationExcludeFilter {
   boolean isExcludedFromAotProcessing(RegisteredBean registeredBean);
}
