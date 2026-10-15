package org.springframework.beans.factory.groovy;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.Writable;
import groovy.xml.StreamingMarkupBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.w3c.dom.Element;

class GroovyDynamicElementReader extends GroovyObjectSupport {
   private final String rootNamespace;
   private final Map<String, String> xmlNamespaces;
   private final BeanDefinitionParserDelegate delegate;
   private final GroovyBeanDefinitionWrapper beanDefinition;
   protected final boolean decorating;
   private boolean callAfterInvocation = true;

   public GroovyDynamicElementReader(
      String namespace, Map<String, String> namespaceMap, BeanDefinitionParserDelegate delegate, GroovyBeanDefinitionWrapper beanDefinition, boolean decorating
   ) {
      this.rootNamespace = namespace;
      this.xmlNamespaces = namespaceMap;
      this.delegate = delegate;
      this.beanDefinition = beanDefinition;
      this.decorating = decorating;
   }

   public @Nullable Object invokeMethod(String name, Object obj) {
      final Object[] args = (Object[])obj;
      if (name.equals("doCall")) {
         Closure<Object> callable = (Closure<Object>)args[0];
         callable.setResolveStrategy(1);
         callable.setDelegate(this);
         Object result = callable.call();
         if (this.callAfterInvocation) {
            this.afterInvocation();
            this.callAfterInvocation = false;
         }

         return result;
      } else {
         final StreamingMarkupBuilder builder = new StreamingMarkupBuilder();
         final String myNamespace = this.rootNamespace;
         final Map<String, String> myNamespaces = this.xmlNamespaces;
         Closure<Object> callable = new Closure<Object>(this) {
            public Object call(Object... arguments) {
               ((GroovyObject)this.getProperty("mkp")).invokeMethod("declareNamespace", new Object[]{myNamespaces});
               int len = args.length;
               if (len > 0 && args[len - 1] instanceof Closure<?> callablex) {
                  callablex.setResolveStrategy(1);
                  callablex.setDelegate(builder);
               }

               return ((GroovyObject)((GroovyObject)this.getDelegate()).getProperty(myNamespace)).invokeMethod(name, args);
            }
         };
         callable.setResolveStrategy(1);
         callable.setDelegate(builder);
         Writable writable = (Writable)builder.bind(callable);
         StringWriter sw = new StringWriter();

         try {
            writable.writeTo(sw);
         } catch (IOException ex) {
            throw new IllegalStateException(ex);
         }

         Element element = this.delegate.getReaderContext().readDocumentFromString(sw.toString()).getDocumentElement();
         this.delegate.initDefaults(element);
         if (this.decorating) {
            BeanDefinitionHolder holder = this.beanDefinition.getBeanDefinitionHolder();
            holder = this.delegate.decorateIfRequired(element, holder, null);
            this.beanDefinition.setBeanDefinitionHolder(holder);
         } else {
            BeanDefinition beanDefinition = this.delegate.parseCustomElement(element);
            if (beanDefinition != null) {
               this.beanDefinition.setBeanDefinition((AbstractBeanDefinition)beanDefinition);
            }
         }

         if (this.callAfterInvocation) {
            this.afterInvocation();
            this.callAfterInvocation = false;
         }

         return element;
      }
   }

   protected void afterInvocation() {
   }
}
