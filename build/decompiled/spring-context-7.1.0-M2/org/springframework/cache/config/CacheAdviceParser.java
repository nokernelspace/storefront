package org.springframework.cache.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.parsing.ReaderContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.cache.interceptor.CacheEvictOperation;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CachePutOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.cache.interceptor.NameMatchCacheOperationSource;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

class CacheAdviceParser extends AbstractSingleBeanDefinitionParser {
   private static final String CACHEABLE_ELEMENT = "cacheable";
   private static final String CACHE_EVICT_ELEMENT = "cache-evict";
   private static final String CACHE_PUT_ELEMENT = "cache-put";
   private static final String METHOD_ATTRIBUTE = "method";
   private static final String DEFS_ELEMENT = "caching";

   protected Class<?> getBeanClass(Element element) {
      return CacheInterceptor.class;
   }

   protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
      builder.addPropertyReference("cacheManager", CacheNamespaceHandler.extractCacheManager(element));
      CacheNamespaceHandler.parseKeyGenerator(element, builder.getBeanDefinition());
      List<Element> cacheDefs = DomUtils.getChildElementsByTagName(element, "caching");
      if (!cacheDefs.isEmpty()) {
         List<RootBeanDefinition> attributeSourceDefinitions = this.parseDefinitionsSources(cacheDefs, parserContext);
         builder.addPropertyValue("cacheOperationSources", attributeSourceDefinitions);
      } else {
         builder.addPropertyValue("cacheOperationSources", new RootBeanDefinition("org.springframework.cache.annotation.AnnotationCacheOperationSource"));
      }
   }

   private List<RootBeanDefinition> parseDefinitionsSources(List<Element> definitions, ParserContext parserContext) {
      ManagedList<RootBeanDefinition> defs = new ManagedList(definitions.size());

      for (Element element : definitions) {
         defs.add(this.parseDefinitionSource(element, parserContext));
      }

      return defs;
   }

   private RootBeanDefinition parseDefinitionSource(Element definition, ParserContext parserContext) {
      CacheAdviceParser.Props prop = new CacheAdviceParser.Props(definition);
      ManagedMap<TypedStringValue, Collection<CacheOperation>> cacheOpMap = new ManagedMap();
      cacheOpMap.setSource(parserContext.extractSource(definition));

      for (Element opElement : DomUtils.getChildElementsByTagName(definition, "cacheable")) {
         String name = prop.merge(opElement, parserContext.getReaderContext());
         TypedStringValue nameHolder = new TypedStringValue(name);
         nameHolder.setSource(parserContext.extractSource(opElement));
         CacheableOperation.Builder builder = prop.merge(opElement, parserContext.getReaderContext(), new CacheableOperation.Builder());
         builder.setUnless(getAttributeValue(opElement, "unless", ""));
         builder.setSync(Boolean.parseBoolean(getAttributeValue(opElement, "sync", "false")));
         Collection<CacheOperation> col = (Collection<CacheOperation>)cacheOpMap.computeIfAbsent(nameHolder, key -> new ArrayList(2));
         col.add(builder.build());
      }

      for (Element opElement : DomUtils.getChildElementsByTagName(definition, "cache-evict")) {
         String name = prop.merge(opElement, parserContext.getReaderContext());
         TypedStringValue nameHolder = new TypedStringValue(name);
         nameHolder.setSource(parserContext.extractSource(opElement));
         CacheEvictOperation.Builder builder = prop.merge(opElement, parserContext.getReaderContext(), new CacheEvictOperation.Builder());
         String wide = opElement.getAttribute("all-entries");
         if (StringUtils.hasText(wide)) {
            builder.setCacheWide(Boolean.parseBoolean(wide.trim()));
         }

         String after = opElement.getAttribute("before-invocation");
         if (StringUtils.hasText(after)) {
            builder.setBeforeInvocation(Boolean.parseBoolean(after.trim()));
         }

         Collection<CacheOperation> col = (Collection<CacheOperation>)cacheOpMap.computeIfAbsent(nameHolder, key -> new ArrayList(2));
         col.add(builder.build());
      }

      for (Element opElement : DomUtils.getChildElementsByTagName(definition, "cache-put")) {
         String name = prop.merge(opElement, parserContext.getReaderContext());
         TypedStringValue nameHolder = new TypedStringValue(name);
         nameHolder.setSource(parserContext.extractSource(opElement));
         CachePutOperation.Builder builder = prop.merge(opElement, parserContext.getReaderContext(), new CachePutOperation.Builder());
         builder.setUnless(getAttributeValue(opElement, "unless", ""));
         Collection<CacheOperation> col = (Collection<CacheOperation>)cacheOpMap.computeIfAbsent(nameHolder, key -> new ArrayList(2));
         col.add(builder.build());
      }

      RootBeanDefinition attributeSourceDefinition = new RootBeanDefinition(NameMatchCacheOperationSource.class);
      attributeSourceDefinition.setSource(parserContext.extractSource(definition));
      attributeSourceDefinition.getPropertyValues().add("nameMap", cacheOpMap);
      return attributeSourceDefinition;
   }

   private static String getAttributeValue(Element element, String attributeName, String defaultValue) {
      String attribute = element.getAttribute(attributeName);
      return StringUtils.hasText(attribute) ? attribute.trim() : defaultValue;
   }

   private static class Props {
      private final String key;
      private final String keyGenerator;
      private final String cacheManager;
      private final String condition;
      private final String method;
      private String @Nullable [] caches;

      Props(Element root) {
         String defaultCache = root.getAttribute("cache");
         this.key = root.getAttribute("key");
         this.keyGenerator = root.getAttribute("key-generator");
         this.cacheManager = root.getAttribute("cache-manager");
         this.condition = root.getAttribute("condition");
         this.method = root.getAttribute("method");
         if (StringUtils.hasText(defaultCache)) {
            this.caches = StringUtils.commaDelimitedListToStringArray(defaultCache.trim());
         }
      }

      <T extends CacheOperation.Builder> T merge(Element element, ReaderContext readerCtx, T builder) {
         String cache = element.getAttribute("cache");
         String[] localCaches = this.caches;
         if (StringUtils.hasText(cache)) {
            localCaches = StringUtils.commaDelimitedListToStringArray(cache.trim());
         }

         if (localCaches != null) {
            builder.setCacheNames(localCaches);
         } else {
            readerCtx.error("No cache specified for " + element.getNodeName(), element);
         }

         builder.setKey(CacheAdviceParser.getAttributeValue(element, "key", this.key));
         builder.setKeyGenerator(CacheAdviceParser.getAttributeValue(element, "key-generator", this.keyGenerator));
         builder.setCacheManager(CacheAdviceParser.getAttributeValue(element, "cache-manager", this.cacheManager));
         builder.setCondition(CacheAdviceParser.getAttributeValue(element, "condition", this.condition));
         if (StringUtils.hasText(builder.getKey()) && StringUtils.hasText(builder.getKeyGenerator())) {
            throw new IllegalStateException(
               "Invalid cache advice configuration on '"
                  + element
                  + "'. Both 'key' and 'keyGenerator' attributes have been set. These attributes are mutually exclusive: either set the SpEL expression used tocompute the key at runtime or set the name of the KeyGenerator bean to use."
            );
         } else {
            return builder;
         }
      }

      @Nullable String merge(Element element, ReaderContext readerCtx) {
         String method = element.getAttribute("method");
         if (StringUtils.hasText(method)) {
            return method.trim();
         }

         if (StringUtils.hasText(this.method)) {
            return this.method;
         }

         readerCtx.error("No method specified for " + element.getNodeName(), element);
         return null;
      }
   }
}
