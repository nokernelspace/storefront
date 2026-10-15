package org.springframework.beans.factory.config;

import org.springframework.beans.BeanMetadataElement;

public interface BeanReference extends BeanMetadataElement {
   String getBeanName();
}
