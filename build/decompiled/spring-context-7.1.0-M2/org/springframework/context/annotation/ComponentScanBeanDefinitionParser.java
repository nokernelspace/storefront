package org.springframework.context.annotation;

import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ComponentScanBeanDefinitionParser implements BeanDefinitionParser {
   private static final String BASE_PACKAGE_ATTRIBUTE = "base-package";
   private static final String RESOURCE_PATTERN_ATTRIBUTE = "resource-pattern";
   private static final String USE_DEFAULT_FILTERS_ATTRIBUTE = "use-default-filters";
   private static final String ANNOTATION_CONFIG_ATTRIBUTE = "annotation-config";
   private static final String NAME_GENERATOR_ATTRIBUTE = "name-generator";
   private static final String SCOPE_RESOLVER_ATTRIBUTE = "scope-resolver";
   private static final String SCOPED_PROXY_ATTRIBUTE = "scoped-proxy";
   private static final String EXCLUDE_FILTER_ELEMENT = "exclude-filter";
   private static final String INCLUDE_FILTER_ELEMENT = "include-filter";
   private static final String FILTER_TYPE_ATTRIBUTE = "type";
   private static final String FILTER_EXPRESSION_ATTRIBUTE = "expression";

   public @Nullable BeanDefinition parse(Element element, ParserContext parserContext) {
      String basePackage = element.getAttribute("base-package");
      basePackage = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(basePackage);
      String[] basePackages = StringUtils.tokenizeToStringArray(basePackage, ",; \t\n");
      ClassPathBeanDefinitionScanner scanner = this.configureScanner(parserContext, element);
      Set<BeanDefinitionHolder> beanDefinitions = scanner.doScan(basePackages);
      this.registerComponents(parserContext.getReaderContext(), beanDefinitions, element);
      return null;
   }

   protected ClassPathBeanDefinitionScanner configureScanner(ParserContext parserContext, Element element) {
      boolean useDefaultFilters = true;
      if (element.hasAttribute("use-default-filters")) {
         useDefaultFilters = Boolean.parseBoolean(element.getAttribute("use-default-filters"));
      }

      ClassPathBeanDefinitionScanner scanner = this.createScanner(parserContext.getReaderContext(), useDefaultFilters);
      scanner.setBeanDefinitionDefaults(parserContext.getDelegate().getBeanDefinitionDefaults());
      scanner.setAutowireCandidatePatterns(parserContext.getDelegate().getAutowireCandidatePatterns());
      if (element.hasAttribute("resource-pattern")) {
         scanner.setResourcePattern(element.getAttribute("resource-pattern"));
      }

      try {
         this.parseBeanNameGenerator(element, scanner);
      } catch (Exception ex) {
         String message = ex.getMessage();
         Assert.state(message != null, "Exception message must not be null");
         parserContext.getReaderContext().error(message, parserContext.extractSource(element), ex.getCause());
      }

      try {
         this.parseScope(element, scanner);
      } catch (Exception ex) {
         String message = ex.getMessage();
         Assert.state(message != null, "Exception message must not be null");
         parserContext.getReaderContext().error(message, parserContext.extractSource(element), ex.getCause());
      }

      this.parseTypeFilters(element, scanner, parserContext);
      return scanner;
   }

   protected ClassPathBeanDefinitionScanner createScanner(XmlReaderContext readerContext, boolean useDefaultFilters) {
      return new ClassPathBeanDefinitionScanner(
         readerContext.getRegistry(), useDefaultFilters, readerContext.getEnvironment(), readerContext.getResourceLoader()
      );
   }

   protected void registerComponents(XmlReaderContext readerContext, Set<BeanDefinitionHolder> beanDefinitions, Element element) {
      Object source = readerContext.extractSource(element);
      CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(), source);

      for (BeanDefinitionHolder beanDefHolder : beanDefinitions) {
         compositeDef.addNestedComponent(new BeanComponentDefinition(beanDefHolder));
      }

      boolean annotationConfig = true;
      if (element.hasAttribute("annotation-config")) {
         annotationConfig = Boolean.parseBoolean(element.getAttribute("annotation-config"));
      }

      if (annotationConfig) {
         for (BeanDefinitionHolder processorDefinition : AnnotationConfigUtils.registerAnnotationConfigProcessors(readerContext.getRegistry(), source)) {
            compositeDef.addNestedComponent(new BeanComponentDefinition(processorDefinition));
         }
      }

      readerContext.fireComponentRegistered(compositeDef);
   }

   protected void parseBeanNameGenerator(Element element, ClassPathBeanDefinitionScanner scanner) {
      if (element.hasAttribute("name-generator")) {
         BeanNameGenerator beanNameGenerator = (BeanNameGenerator)this.instantiateUserDefinedStrategy(
            element.getAttribute("name-generator"), BeanNameGenerator.class, scanner.getResourceLoader().getClassLoader()
         );
         scanner.setBeanNameGenerator(beanNameGenerator);
      }
   }

   protected void parseScope(Element element, ClassPathBeanDefinitionScanner scanner) {
      if (element.hasAttribute("scope-resolver")) {
         if (element.hasAttribute("scoped-proxy")) {
            throw new IllegalArgumentException("Cannot define both 'scope-resolver' and 'scoped-proxy' on <component-scan> tag");
         }

         ScopeMetadataResolver scopeMetadataResolver = (ScopeMetadataResolver)this.instantiateUserDefinedStrategy(
            element.getAttribute("scope-resolver"), ScopeMetadataResolver.class, scanner.getResourceLoader().getClassLoader()
         );
         scanner.setScopeMetadataResolver(scopeMetadataResolver);
      }

      if (element.hasAttribute("scoped-proxy")) {
         String mode = element.getAttribute("scoped-proxy");
         switch (mode) {
            case "targetClass":
               scanner.setScopedProxyMode(ScopedProxyMode.TARGET_CLASS);
               break;
            case "interfaces":
               scanner.setScopedProxyMode(ScopedProxyMode.INTERFACES);
               break;
            case "no":
               scanner.setScopedProxyMode(ScopedProxyMode.NO);
               break;
            default:
               throw new IllegalArgumentException("scoped-proxy only supports 'no', 'interfaces' and 'targetClass'");
         }
      }
   }

   protected void parseTypeFilters(Element element, ClassPathBeanDefinitionScanner scanner, ParserContext parserContext) {
      ClassLoader classLoader = scanner.getResourceLoader().getClassLoader();
      NodeList nodeList = element.getChildNodes();

      for (int i = 0; i < nodeList.getLength(); i++) {
         Node node = nodeList.item(i);
         if (node.getNodeType() == 1) {
            String localName = parserContext.getDelegate().getLocalName(node);

            try {
               if ("include-filter".equals(localName)) {
                  TypeFilter typeFilter = this.createTypeFilter((Element)node, classLoader, parserContext);
                  scanner.addIncludeFilter(typeFilter);
               } else if ("exclude-filter".equals(localName)) {
                  TypeFilter typeFilter = this.createTypeFilter((Element)node, classLoader, parserContext);
                  scanner.addExcludeFilter(typeFilter);
               }
            } catch (ClassNotFoundException ex) {
               parserContext.getReaderContext().warning("Ignoring non-present type filter class: " + ex, parserContext.extractSource(element));
            } catch (Exception ex) {
               String message = ex.getMessage();
               Assert.state(message != null, "Exception message must not be null");
               parserContext.getReaderContext().error(message, parserContext.extractSource(element), ex.getCause());
            }
         }
      }
   }

   protected TypeFilter createTypeFilter(Element element, @Nullable ClassLoader classLoader, ParserContext parserContext) throws ClassNotFoundException {
      String filterType = element.getAttribute("type");
      String expression = element.getAttribute("expression");
      expression = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(expression);
      switch (filterType) {
         case "annotation":
            return new AnnotationTypeFilter(ClassUtils.forName(expression, classLoader));
         case "assignable":
            return new AssignableTypeFilter(ClassUtils.forName(expression, classLoader));
         case "aspectj":
            return new AspectJTypeFilter(expression, classLoader);
         case "regex":
            return new RegexPatternTypeFilter(Pattern.compile(expression));
         case "custom":
            Class<?> filterClass = ClassUtils.forName(expression, classLoader);
            if (!TypeFilter.class.isAssignableFrom(filterClass)) {
               throw new IllegalArgumentException("Class is not assignable to [" + TypeFilter.class.getName() + "]: " + expression);
            }

            return (TypeFilter)BeanUtils.instantiateClass(filterClass);
         default:
            throw new IllegalArgumentException("Unsupported filter type: " + filterType);
      }
   }

   private Object instantiateUserDefinedStrategy(String className, Class<?> strategyType, @Nullable ClassLoader classLoader) {
      Object result;
      try {
         result = ReflectionUtils.accessibleConstructor(ClassUtils.forName(className, classLoader), new Class[0]).newInstance();
      } catch (ClassNotFoundException ex) {
         throw new IllegalArgumentException("Class [" + className + "] for strategy [" + strategyType.getName() + "] not found", ex);
      } catch (Throwable ex) {
         throw new IllegalArgumentException(
            "Unable to instantiate class [" + className + "] for strategy [" + strategyType.getName() + "]: a zero-argument constructor is required", ex
         );
      }

      if (!strategyType.isInstance(result)) {
         throw new IllegalArgumentException("Provided class name must be an implementation of " + strategyType);
      } else {
         return result;
      }
   }
}
