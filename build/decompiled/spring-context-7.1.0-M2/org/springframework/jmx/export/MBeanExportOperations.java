package org.springframework.jmx.export;

import javax.management.ObjectName;

public interface MBeanExportOperations {
   ObjectName registerManagedResource(Object managedResource) throws MBeanExportException;

   void registerManagedResource(Object managedResource, ObjectName objectName) throws MBeanExportException;

   void unregisterManagedResource(ObjectName objectName);
}
