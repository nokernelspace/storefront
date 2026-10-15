package org.springframework.jmx.export.assembler;

import javax.management.JMException;
import javax.management.modelmbean.ModelMBeanInfo;

public interface MBeanInfoAssembler {
   ModelMBeanInfo getMBeanInfo(Object managedBean, String beanKey) throws JMException;
}
