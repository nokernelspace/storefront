package org.springframework.jmx.export;

import javax.management.ObjectName;

public interface MBeanExporterListener {
   void mbeanRegistered(ObjectName objectName);

   void mbeanUnregistered(ObjectName objectName);
}
