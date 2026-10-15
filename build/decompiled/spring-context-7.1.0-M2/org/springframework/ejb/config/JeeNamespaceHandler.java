package org.springframework.ejb.config;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class JeeNamespaceHandler extends NamespaceHandlerSupport {
   public void init() {
      this.registerBeanDefinitionParser("jndi-lookup", new JndiLookupBeanDefinitionParser());
      this.registerBeanDefinitionParser("local-slsb", new LocalStatelessSessionBeanDefinitionParser());
      this.registerBeanDefinitionParser("remote-slsb", new RemoteStatelessSessionBeanDefinitionParser());
   }
}
