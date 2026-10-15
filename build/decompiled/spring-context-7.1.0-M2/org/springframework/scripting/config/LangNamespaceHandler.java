package org.springframework.scripting.config;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

@Deprecated(since = "7.0")
public class LangNamespaceHandler extends NamespaceHandlerSupport {
   public void init() {
      this.registerScriptBeanDefinitionParser("groovy", "org.springframework.scripting.groovy.GroovyScriptFactory");
      this.registerScriptBeanDefinitionParser("bsh", "org.springframework.scripting.bsh.BshScriptFactory");
      this.registerScriptBeanDefinitionParser("std", "org.springframework.scripting.support.StandardScriptFactory");
      this.registerBeanDefinitionParser("defaults", new ScriptingDefaultsParser());
   }

   private void registerScriptBeanDefinitionParser(String key, String scriptFactoryClassName) {
      this.registerBeanDefinitionParser(key, new ScriptBeanDefinitionParser(scriptFactoryClassName));
   }
}
