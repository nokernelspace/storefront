package org.springframework.jmx.export.naming;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ObjectNamingStrategy {
   ObjectName getObjectName(Object managedBean, @Nullable String beanKey) throws MalformedObjectNameException;
}
