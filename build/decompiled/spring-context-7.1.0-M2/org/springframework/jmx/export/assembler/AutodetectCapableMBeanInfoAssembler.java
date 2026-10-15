package org.springframework.jmx.export.assembler;

public interface AutodetectCapableMBeanInfoAssembler extends MBeanInfoAssembler {
   boolean includeBean(Class<?> beanClass, String beanName);
}
