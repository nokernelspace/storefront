package org.springframework.jmx.export.naming;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public interface SelfNaming {
   ObjectName getObjectName() throws MalformedObjectNameException;
}
