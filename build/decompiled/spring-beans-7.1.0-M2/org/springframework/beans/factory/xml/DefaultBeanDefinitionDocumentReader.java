package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {
   public static final String BEAN_ELEMENT = "bean";
   public static final String NESTED_BEANS_ELEMENT = "beans";
   public static final String ALIAS_ELEMENT = "alias";
   public static final String NAME_ATTRIBUTE = "name";
   public static final String ALIAS_ATTRIBUTE = "alias";
   public static final String IMPORT_ELEMENT = "import";
   public static final String RESOURCE_ATTRIBUTE = "resource";
   public static final String PROFILE_ATTRIBUTE = "profile";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable XmlReaderContext readerContext;
   private @Nullable BeanDefinitionParserDelegate delegate;

   @Override
   public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
      this.readerContext = readerContext;
      this.doRegisterBeanDefinitions(doc.getDocumentElement());
   }

   protected final XmlReaderContext getReaderContext() {
      Assert.state(this.readerContext != null, "No XmlReaderContext available");
      return this.readerContext;
   }

   protected @Nullable Object extractSource(Element ele) {
      return this.getReaderContext().extractSource(ele);
   }

   protected void doRegisterBeanDefinitions(Element root) {
      BeanDefinitionParserDelegate parent = this.delegate;
      BeanDefinitionParserDelegate current = this.createDelegate(this.getReaderContext(), root, parent);
      this.delegate = current;
      if (current.isDefaultNamespace(root)) {
         String profileSpec = root.getAttribute("profile");
         if (StringUtils.hasText(profileSpec)) {
            String[] specifiedProfiles = StringUtils.tokenizeToStringArray(profileSpec, ",; ");
            if (!this.getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
               if (this.logger.isDebugEnabled()) {
                  this.logger
                     .debug(
                        "Skipped XML bean definition file due to specified profiles ["
                           + profileSpec
                           + "] not matching: "
                           + this.getReaderContext().getResource()
                     );
               }

               return;
            }
         }
      }

      this.preProcessXml(root);
      this.parseBeanDefinitions(root, current);
      this.postProcessXml(root);
      this.delegate = parent;
   }

   protected BeanDefinitionParserDelegate createDelegate(XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {
      BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
      delegate.initDefaults(root, parentDelegate);
      return delegate;
   }

   protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
      if (delegate.isDefaultNamespace(root)) {
         NodeList nl = root.getChildNodes();

         for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element ele) {
               if (delegate.isDefaultNamespace(ele)) {
                  this.parseDefaultElement(ele, delegate);
               } else {
                  delegate.parseCustomElement(ele);
               }
            }
         }
      } else {
         delegate.parseCustomElement(root);
      }
   }

   private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
      if (delegate.nodeNameEquals(ele, "import")) {
         this.importBeanDefinitionResource(ele);
      } else if (delegate.nodeNameEquals(ele, "alias")) {
         this.processAliasRegistration(ele);
      } else if (delegate.nodeNameEquals(ele, "bean")) {
         this.processBeanDefinition(ele, delegate);
      } else if (delegate.nodeNameEquals(ele, "beans")) {
         this.doRegisterBeanDefinitions(ele);
      }
   }

   protected void importBeanDefinitionResource(Element ele) {
      String location = ele.getAttribute("resource");
      if (!StringUtils.hasText(location)) {
         this.getReaderContext().error("Resource location must not be empty", ele);
      } else {
         location = this.getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);
         Set<Resource> actualResources = new LinkedHashSet<>(4);
         boolean absoluteLocation = false;

         try {
            absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
         } catch (URISyntaxException var11) {
         }

         if (absoluteLocation) {
            try {
               int importCount = this.getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
               }
            } catch (BeanDefinitionStoreException ex) {
               this.getReaderContext().error("Failed to import bean definitions from URL location [" + location + "]", ele, ex);
            }
         } else {
            try {
               Resource relativeResource = this.getReaderContext().getResource().createRelative(location);
               int importCount;
               if (relativeResource.exists()) {
                  importCount = this.getReaderContext().getReader().loadBeanDefinitions(relativeResource);
                  actualResources.add(relativeResource);
               } else {
                  String baseLocation = this.getReaderContext().getResource().getURL().toString();
                  importCount = this.getReaderContext().getReader().loadBeanDefinitions(StringUtils.applyRelativePath(baseLocation, location), actualResources);
               }

               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
               }
            } catch (IOException ex) {
               this.getReaderContext().error("Failed to resolve current resource location", ele, ex);
            } catch (BeanDefinitionStoreException ex) {
               this.getReaderContext().error("Failed to import bean definitions from relative location [" + location + "]", ele, ex);
            }
         }

         Resource[] actResArray = actualResources.toArray(new Resource[0]);
         this.getReaderContext().fireImportProcessed(location, actResArray, this.extractSource(ele));
      }
   }

   protected void processAliasRegistration(Element ele) {
      String name = ele.getAttribute("name");
      String alias = ele.getAttribute("alias");
      boolean valid = true;
      if (!StringUtils.hasText(name)) {
         this.getReaderContext().error("Name must not be empty", ele);
         valid = false;
      }

      if (!StringUtils.hasText(alias)) {
         this.getReaderContext().error("Alias must not be empty", ele);
         valid = false;
      }

      if (valid) {
         try {
            this.getReaderContext().getRegistry().registerAlias(name, alias);
         } catch (Exception ex) {
            this.getReaderContext().error("Failed to register alias '" + alias + "' for bean with name '" + name + "'", ele, ex);
         }

         this.getReaderContext().fireAliasRegistered(name, alias, this.extractSource(ele));
      }
   }

   protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
      BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
      if (bdHolder != null) {
         bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);

         try {
            BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, this.getReaderContext().getRegistry());
         } catch (BeanDefinitionStoreException ex) {
            this.getReaderContext().error("Failed to register bean definition with name '" + bdHolder.getBeanName() + "'", ele, ex);
         }

         this.getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
      }
   }

   protected void preProcessXml(Element root) {
   }

   protected void postProcessXml(Element root) {
   }
}
