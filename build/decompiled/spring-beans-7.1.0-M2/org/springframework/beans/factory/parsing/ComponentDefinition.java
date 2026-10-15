package org.springframework.beans.factory.parsing;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;

public interface ComponentDefinition extends BeanMetadataElement {
   String getName();

   String getDescription();

   BeanDefinition[] getBeanDefinitions();

   BeanDefinition[] getInnerBeanDefinitions();

   BeanReference[] getBeanReferences();
}
