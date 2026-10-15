package org.springframework.scripting.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

@Deprecated(since = "7.0")
class ScriptingDefaultsParser implements BeanDefinitionParser {
   private static final String REFRESH_CHECK_DELAY_ATTRIBUTE = "refresh-check-delay";
   private static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

   public @Nullable BeanDefinition parse(Element element, ParserContext parserContext) {
      BeanDefinition bd = LangNamespaceUtils.registerScriptFactoryPostProcessorIfNecessary(parserContext.getRegistry());
      String refreshCheckDelay = element.getAttribute("refresh-check-delay");
      if (StringUtils.hasText(refreshCheckDelay)) {
         bd.getPropertyValues().add("defaultRefreshCheckDelay", Long.valueOf(refreshCheckDelay));
      }

      String proxyTargetClass = element.getAttribute("proxy-target-class");
      if (StringUtils.hasText(proxyTargetClass)) {
         bd.getPropertyValues().add("defaultProxyTargetClass", new TypedStringValue(proxyTargetClass, Boolean.class));
      }

      return null;
   }
}
