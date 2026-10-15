package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.ParserConfigurationException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.parsing.EmptyReaderEventListener;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.ReaderEventListener;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.Assert;
import org.springframework.util.xml.SimpleSaxErrorHandler;
import org.springframework.util.xml.XmlValidationModeDetector;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XmlBeanDefinitionReader extends AbstractBeanDefinitionReader {
   public static final int VALIDATION_NONE = 0;
   public static final int VALIDATION_AUTO = 1;
   public static final int VALIDATION_DTD = 2;
   public static final int VALIDATION_XSD = 3;
   private static final Map<String, Integer> constants = Map.of("VALIDATION_NONE", 0, "VALIDATION_AUTO", 1, "VALIDATION_DTD", 2, "VALIDATION_XSD", 3);
   private int validationMode = 1;
   private boolean namespaceAware = false;
   private Class<? extends BeanDefinitionDocumentReader> documentReaderClass = DefaultBeanDefinitionDocumentReader.class;
   private ProblemReporter problemReporter = new FailFastProblemReporter();
   private ReaderEventListener eventListener = new EmptyReaderEventListener();
   private SourceExtractor sourceExtractor = new NullSourceExtractor();
   private @Nullable NamespaceHandlerResolver namespaceHandlerResolver;
   private DocumentLoader documentLoader = new DefaultDocumentLoader();
   private @Nullable EntityResolver entityResolver;
   private ErrorHandler errorHandler = new SimpleSaxErrorHandler(this.logger);
   private final XmlValidationModeDetector validationModeDetector = new XmlValidationModeDetector();
   private final ThreadLocal<Set<EncodedResource>> resourcesCurrentlyBeingLoaded = NamedThreadLocal.withInitial(
      "XML bean definition resources currently being loaded", () -> new HashSet(4)
   );

   public XmlBeanDefinitionReader(BeanDefinitionRegistry registry) {
      super(registry);
   }

   public void setValidating(boolean validating) {
      this.validationMode = validating ? 1 : 0;
      this.namespaceAware = !validating;
   }

   public void setValidationModeName(String validationModeName) {
      Assert.hasText(validationModeName, "'validationModeName' must not be null or blank");
      Integer validationMode = constants.get(validationModeName);
      Assert.notNull(validationMode, "Only validation mode constants allowed");
      this.validationMode = validationMode;
   }

   public void setValidationMode(int validationMode) {
      Assert.isTrue(constants.containsValue(validationMode), "Only values of validation mode constants allowed");
      this.validationMode = validationMode;
   }

   public int getValidationMode() {
      return this.validationMode;
   }

   public void setNamespaceAware(boolean namespaceAware) {
      this.namespaceAware = namespaceAware;
   }

   public boolean isNamespaceAware() {
      return this.namespaceAware;
   }

   public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
      this.problemReporter = problemReporter != null ? problemReporter : new FailFastProblemReporter();
   }

   public void setEventListener(@Nullable ReaderEventListener eventListener) {
      this.eventListener = eventListener != null ? eventListener : new EmptyReaderEventListener();
   }

   public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
      this.sourceExtractor = sourceExtractor != null ? sourceExtractor : new NullSourceExtractor();
   }

   public void setNamespaceHandlerResolver(@Nullable NamespaceHandlerResolver namespaceHandlerResolver) {
      this.namespaceHandlerResolver = namespaceHandlerResolver;
   }

   public void setDocumentLoader(@Nullable DocumentLoader documentLoader) {
      this.documentLoader = documentLoader != null ? documentLoader : new DefaultDocumentLoader();
   }

   public void setEntityResolver(@Nullable EntityResolver entityResolver) {
      this.entityResolver = entityResolver;
   }

   protected EntityResolver getEntityResolver() {
      if (this.entityResolver == null) {
         ResourceLoader resourceLoader = this.getResourceLoader();
         if (resourceLoader != null) {
            this.entityResolver = new ResourceEntityResolver(resourceLoader);
         } else {
            this.entityResolver = new DelegatingEntityResolver(this.getBeanClassLoader());
         }
      }

      return this.entityResolver;
   }

   public void setErrorHandler(ErrorHandler errorHandler) {
      this.errorHandler = errorHandler;
   }

   public void setDocumentReaderClass(Class<? extends BeanDefinitionDocumentReader> documentReaderClass) {
      this.documentReaderClass = documentReaderClass;
   }

   @Override
   public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(new EncodedResource(resource));
   }

   public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
      Assert.notNull(encodedResource, "EncodedResource must not be null");
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Loading XML bean definitions from " + encodedResource);
      }

      Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
      if (!currentResources.add(encodedResource)) {
         throw new BeanDefinitionStoreException("Detected cyclic loading of " + encodedResource + " - check your import definitions!");
      }

      try {
         AtomicInteger count = new AtomicInteger();
         encodedResource.getResource().consumeContent(inputStream -> {
            InputSource inputSource = new InputSource(inputStream);
            if (encodedResource.getEncoding() != null) {
               inputSource.setEncoding(encodedResource.getEncoding());
            }

            count.addAndGet(this.doLoadBeanDefinitions(inputSource, encodedResource.getResource()));
         });
         return count.get();
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException("IOException parsing XML document from " + encodedResource.getResource(), ex);
      } finally {
         currentResources.remove(encodedResource);
         if (currentResources.isEmpty()) {
            this.resourcesCurrentlyBeingLoaded.remove();
         }
      }
   }

   public int loadBeanDefinitions(InputSource inputSource) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(inputSource, "resource loaded through SAX InputSource");
   }

   public int loadBeanDefinitions(InputSource inputSource, @Nullable String resourceDescription) throws BeanDefinitionStoreException {
      return this.doLoadBeanDefinitions(inputSource, new DescriptiveResource(resourceDescription));
   }

   protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource) throws BeanDefinitionStoreException {
      try {
         Document doc = this.doLoadDocument(inputSource, resource);
         int count = this.registerBeanDefinitions(doc, resource);
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Loaded " + count + " bean definitions from " + resource);
         }

         return count;
      } catch (BeanDefinitionStoreException ex) {
         throw ex;
      } catch (SAXParseException ex) {
         throw new XmlBeanDefinitionStoreException(
            resource.getDescription(), "Line " + ex.getLineNumber() + " in XML document from " + resource + " is invalid", ex
         );
      } catch (SAXException ex) {
         throw new XmlBeanDefinitionStoreException(resource.getDescription(), "XML document from " + resource + " is invalid", ex);
      } catch (ParserConfigurationException ex) {
         throw new BeanDefinitionStoreException(resource.getDescription(), "Parser configuration exception parsing XML from " + resource, ex);
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException(resource.getDescription(), "IOException parsing XML document from " + resource, ex);
      } catch (Throwable ex) {
         throw new BeanDefinitionStoreException(resource.getDescription(), "Unexpected exception parsing XML document from " + resource, ex);
      }
   }

   protected Document doLoadDocument(InputSource inputSource, Resource resource) throws Exception {
      return this.documentLoader
         .loadDocument(inputSource, this.getEntityResolver(), this.errorHandler, this.getValidationModeForResource(resource), this.isNamespaceAware());
   }

   protected int getValidationModeForResource(Resource resource) {
      int validationModeToUse = this.getValidationMode();
      if (validationModeToUse != 1) {
         return validationModeToUse;
      }

      int detectedMode = this.detectValidationMode(resource);
      return detectedMode != 1 ? detectedMode : 3;
   }

   protected int detectValidationMode(Resource resource) {
      if (resource.isOpen()) {
         throw new BeanDefinitionStoreException(
            "Passed-in Resource ["
               + resource
               + "] contains an open stream: cannot determine validation mode automatically. Either pass in a Resource that is able to create fresh streams, or explicitly specify the validationMode on your XmlBeanDefinitionReader instance."
         );
      }

      InputStream inputStream;
      try {
         inputStream = resource.getInputStream();
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException(
            "Unable to determine validation mode for ["
               + resource
               + "]: cannot open InputStream. Did you attempt to load directly from a SAX InputSource without specifying the validationMode on your XmlBeanDefinitionReader instance?",
            ex
         );
      }

      try {
         return this.validationModeDetector.detectValidationMode(inputStream);
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException(
            "Unable to determine validation mode for [" + resource + "]: an error occurred whilst reading from the InputStream.", ex
         );
      }
   }

   public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
      BeanDefinitionDocumentReader documentReader = this.createBeanDefinitionDocumentReader();
      int countBefore = this.getRegistry().getBeanDefinitionCount();
      documentReader.registerBeanDefinitions(doc, this.createReaderContext(resource));
      return this.getRegistry().getBeanDefinitionCount() - countBefore;
   }

   protected BeanDefinitionDocumentReader createBeanDefinitionDocumentReader() {
      return BeanUtils.instantiateClass(this.documentReaderClass);
   }

   public XmlReaderContext createReaderContext(Resource resource) {
      return new XmlReaderContext(resource, this.problemReporter, this.eventListener, this.sourceExtractor, this, this.getNamespaceHandlerResolver());
   }

   public NamespaceHandlerResolver getNamespaceHandlerResolver() {
      if (this.namespaceHandlerResolver == null) {
         this.namespaceHandlerResolver = this.createDefaultNamespaceHandlerResolver();
      }

      return this.namespaceHandlerResolver;
   }

   protected NamespaceHandlerResolver createDefaultNamespaceHandlerResolver() {
      ResourceLoader resourceLoader = this.getResourceLoader();
      ClassLoader cl = resourceLoader != null ? resourceLoader.getClassLoader() : this.getBeanClassLoader();
      return new DefaultNamespaceHandlerResolver(cl);
   }
}
