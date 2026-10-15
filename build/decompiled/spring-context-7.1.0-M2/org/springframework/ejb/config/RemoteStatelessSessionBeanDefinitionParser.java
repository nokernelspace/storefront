package org.springframework.ejb.config;

import org.springframework.beans.BeanUtils;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.w3c.dom.Element;

class RemoteStatelessSessionBeanDefinitionParser extends AbstractJndiLocatingBeanDefinitionParser {
   protected Class<?> getBeanClass(Element element) {
      return JndiObjectFactoryBean.class;
   }

   @Override
   protected boolean isEligibleAttribute(String attributeName) {
      return super.isEligibleAttribute(attributeName)
         && BeanUtils.getPropertyDescriptor(JndiObjectFactoryBean.class, this.extractPropertyName(attributeName)) != null;
   }
}
