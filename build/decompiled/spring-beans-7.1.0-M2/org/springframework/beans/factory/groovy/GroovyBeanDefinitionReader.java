package org.springframework.beans.factory.groovy;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class GroovyBeanDefinitionReader extends AbstractBeanDefinitionReader implements GroovyObject {
   private final XmlBeanDefinitionReader standardXmlBeanDefinitionReader;
   private final XmlBeanDefinitionReader groovyDslXmlBeanDefinitionReader;
   private final Map<String, String> namespaces = new HashMap<>();
   private final Map<String, GroovyBeanDefinitionReader.DeferredProperty> deferredProperties = new HashMap<>();
   private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(this.getClass());
   private @Nullable Binding binding;
   private @Nullable GroovyBeanDefinitionWrapper currentBeanDefinition;

   public GroovyBeanDefinitionReader(BeanDefinitionRegistry registry) {
      super(registry);
      this.standardXmlBeanDefinitionReader = new XmlBeanDefinitionReader(registry);
      this.groovyDslXmlBeanDefinitionReader = new XmlBeanDefinitionReader(registry);
      this.groovyDslXmlBeanDefinitionReader.setValidating(false);
   }

   public GroovyBeanDefinitionReader(XmlBeanDefinitionReader xmlBeanDefinitionReader) {
      super(xmlBeanDefinitionReader.getRegistry());
      this.standardXmlBeanDefinitionReader = new XmlBeanDefinitionReader(xmlBeanDefinitionReader.getRegistry());
      this.groovyDslXmlBeanDefinitionReader = xmlBeanDefinitionReader;
   }

   public void setMetaClass(MetaClass metaClass) {
      this.metaClass = metaClass;
   }

   public MetaClass getMetaClass() {
      return this.metaClass;
   }

   public void setBinding(Binding binding) {
      this.binding = binding;
   }

   public @Nullable Binding getBinding() {
      return this.binding;
   }

   @Override
   public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(new EncodedResource(resource));
   }

   public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
      String filename = encodedResource.getResource().getFilename();
      if (StringUtils.endsWithIgnoreCase(filename, ".xml")) {
         return this.standardXmlBeanDefinitionReader.loadBeanDefinitions(encodedResource);
      }

      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Loading Groovy bean definitions from " + encodedResource);
      }

      Closure<Object> beans = new Closure<Object>(this) {
         public @Nullable Object call(Object... args) {
            GroovyBeanDefinitionReader.this.invokeBeanDefiningClosure((Closure<?>)args[0]);
            return null;
         }
      };
      Binding binding = new Binding() {
         public void setVariable(String name, Object value) {
            if (GroovyBeanDefinitionReader.this.currentBeanDefinition != null) {
               GroovyBeanDefinitionReader.this.applyPropertyToBeanDefinition(name, value);
            } else {
               super.setVariable(name, value);
            }
         }
      };
      binding.setVariable("beans", beans);
      int countBefore = this.getRegistry().getBeanDefinitionCount();

      try {
         GroovyShell shell = new GroovyShell(this.getBeanClassLoader(), binding);
         shell.evaluate(encodedResource.getReader(), "beans");
      } catch (Throwable ex) {
         throw new BeanDefinitionParsingException(
            new Problem("Error evaluating Groovy script: " + ex.getMessage(), new Location(encodedResource.getResource()), null, ex)
         );
      }

      int count = this.getRegistry().getBeanDefinitionCount() - countBefore;
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Loaded " + count + " bean definitions from " + encodedResource);
      }

      return count;
   }

   public GroovyBeanDefinitionReader beans(Closure<?> closure) {
      return this.invokeBeanDefiningClosure(closure);
   }

   public GenericBeanDefinition bean(Class<?> type) {
      GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
      beanDefinition.setBeanClass(type);
      return beanDefinition;
   }

   public AbstractBeanDefinition bean(Class<?> type, Object... args) {
      GroovyBeanDefinitionWrapper current = this.currentBeanDefinition;

      try {
         Closure<?> callable = null;
         Collection<Object> constructorArgs = null;
         if (!ObjectUtils.isEmpty(args)) {
            int index = args.length;
            if (args[index - 1] instanceof Closure<?> closure) {
               callable = closure;
               index--;
            }

            constructorArgs = this.resolveConstructorArguments(args, 0, index);
         }

         this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(null, type, constructorArgs);
         if (callable != null) {
            callable.call(this.currentBeanDefinition);
         }

         return this.currentBeanDefinition.getBeanDefinition();
      } finally {
         this.currentBeanDefinition = current;
      }
   }

   public void xmlns(Map<String, String> definition) {
      if (!definition.isEmpty()) {
         for (Entry<String, String> entry : definition.entrySet()) {
            String namespace = entry.getKey();
            String uri = entry.getValue();
            if (uri == null) {
               throw new IllegalArgumentException("Namespace definition must supply a non-null URI");
            }

            NamespaceHandler namespaceHandler = this.groovyDslXmlBeanDefinitionReader.getNamespaceHandlerResolver().resolve(uri);
            if (namespaceHandler == null) {
               throw new BeanDefinitionParsingException(
                  new Problem("No namespace handler found for URI: " + uri, new Location(new DescriptiveResource("Groovy")))
               );
            }

            this.namespaces.put(namespace, uri);
         }
      }
   }

   public void importBeans(String resourcePattern) throws IOException {
      this.loadBeanDefinitions(resourcePattern);
   }

   public Object invokeMethod(String name, Object arg) {
      Object[] args = (Object[])arg;
      if ("beans".equals(name) && args.length == 1 && args[0] instanceof Closure<?> closure) {
         return this.beans(closure);
      }

      if ("ref".equals(name)) {
         if (args[0] == null) {
            throw new IllegalArgumentException("Argument to ref() is not a valid bean or was not found");
         }

         String refName;
         if (args[0] instanceof RuntimeBeanReference runtimeBeanReference) {
            refName = runtimeBeanReference.getBeanName();
         } else {
            refName = args[0].toString();
         }

         boolean parentRef = false;
         if (args.length > 1 && args[1] instanceof Boolean bool) {
            parentRef = bool;
         }

         return new RuntimeBeanReference(refName, parentRef);
      } else {
         if (this.namespaces.containsKey(name) && args.length > 0 && args[0] instanceof Closure) {
            GroovyDynamicElementReader reader = this.createDynamicElementReader(name);
            reader.invokeMethod("doCall", args);
         } else {
            if (args.length > 0 && args[0] instanceof Closure) {
               return this.invokeBeanDefiningMethod(name, args);
            }

            if (args.length > 0 && (args[0] instanceof Class || args[0] instanceof RuntimeBeanReference || args[0] instanceof Map)) {
               return this.invokeBeanDefiningMethod(name, args);
            }

            if (args.length > 1 && args[args.length - 1] instanceof Closure) {
               return this.invokeBeanDefiningMethod(name, args);
            }
         }

         MetaClass mc = DefaultGroovyMethods.getMetaClass(this.getRegistry());
         return !mc.respondsTo(this.getRegistry(), name, args).isEmpty() ? mc.invokeMethod(this.getRegistry(), name, args) : this;
      }
   }

   private boolean addDeferredProperty(String property, Object newValue) {
      if (!(newValue instanceof List) && !(newValue instanceof Map)) {
         return false;
      }

      Assert.state(this.currentBeanDefinition != null, "No current bean definition set");
      this.deferredProperties
         .put(
            this.currentBeanDefinition.getBeanName() + "." + property,
            new GroovyBeanDefinitionReader.DeferredProperty(this.currentBeanDefinition, property, newValue)
         );
      return true;
   }

   private void finalizeDeferredProperties() {
      for (GroovyBeanDefinitionReader.DeferredProperty dp : this.deferredProperties.values()) {
         if (dp.value instanceof List<?> list) {
            dp.value = this.manageListIfNecessary(list);
         } else if (dp.value instanceof Map<?, ?> map) {
            dp.value = this.manageMapIfNecessary(map);
         }

         dp.apply();
      }

      this.deferredProperties.clear();
   }

   protected GroovyBeanDefinitionReader invokeBeanDefiningClosure(Closure<?> callable) {
      callable.setDelegate(this);
      callable.call();
      this.finalizeDeferredProperties();
      return this;
   }

   private GroovyBeanDefinitionWrapper invokeBeanDefiningMethod(String beanName, Object[] args) {
      boolean hasClosureArgument = args[args.length - 1] instanceof Closure;
      if (args[0] instanceof Class<?> beanClass) {
         if (hasClosureArgument) {
            if (args.length - 1 != 1) {
               this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName, beanClass, this.resolveConstructorArguments(args, 1, args.length - 1));
            } else {
               this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName, beanClass);
            }
         } else {
            this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName, beanClass, this.resolveConstructorArguments(args, 1, args.length));
         }
      } else if (args[0] instanceof RuntimeBeanReference runtimeBeanReference) {
         this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName);
         this.currentBeanDefinition.getBeanDefinition().setFactoryBeanName(runtimeBeanReference.getBeanName());
      } else if (args[0] instanceof Map<?, ?> namedArgs) {
         if (args.length > 1 && args[1] instanceof Class<?> clazz) {
            List<Object> constructorArgs = this.resolveConstructorArguments(args, 2, hasClosureArgument ? args.length - 1 : args.length);
            this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName, clazz, constructorArgs);

            for (Entry<?, ?> entity : namedArgs.entrySet()) {
               String propName = (String)entity.getKey();
               this.setProperty(propName, entity.getValue());
            }
         } else {
            this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName);
            Entry<?, ?> factoryBeanEntry = namedArgs.entrySet().iterator().next();
            int constructorArgsTest = hasClosureArgument ? 2 : 1;
            if (args.length > constructorArgsTest) {
               int endOfConstructArgs = hasClosureArgument ? args.length - 1 : args.length;
               this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName, null, this.resolveConstructorArguments(args, 1, endOfConstructArgs));
            } else {
               this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName);
            }

            this.currentBeanDefinition.getBeanDefinition().setFactoryBeanName(factoryBeanEntry.getKey().toString());
            this.currentBeanDefinition.getBeanDefinition().setFactoryMethodName(factoryBeanEntry.getValue().toString());
         }
      } else if (args[0] instanceof Closure) {
         this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName);
         this.currentBeanDefinition.getBeanDefinition().setAbstract(true);
      } else {
         List<Object> constructorArgs = this.resolveConstructorArguments(args, 0, hasClosureArgument ? args.length - 1 : args.length);
         this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(beanName, null, constructorArgs);
      }

      if (hasClosureArgument) {
         Closure<?> callable = (Closure<?>)args[args.length - 1];
         callable.setDelegate(this);
         callable.setResolveStrategy(1);
         callable.call(this.currentBeanDefinition);
      }

      GroovyBeanDefinitionWrapper beanDefinition = this.currentBeanDefinition;
      this.currentBeanDefinition = null;
      beanDefinition.getBeanDefinition().setAttribute(GroovyBeanDefinitionWrapper.class.getName(), beanDefinition);
      this.getRegistry().registerBeanDefinition(beanName, beanDefinition.getBeanDefinition());
      return beanDefinition;
   }

   protected List<Object> resolveConstructorArguments(Object[] args, int start, int end) {
      Object[] constructorArgs = Arrays.copyOfRange(args, start, end);

      for (int i = 0; i < constructorArgs.length; i++) {
         if (constructorArgs[i] instanceof GString) {
            constructorArgs[i] = constructorArgs[i].toString();
         } else if (constructorArgs[i] instanceof List<?> list) {
            constructorArgs[i] = this.manageListIfNecessary(list);
         } else if (constructorArgs[i] instanceof Map<?, ?> map) {
            constructorArgs[i] = this.manageMapIfNecessary(map);
         }
      }

      return List.of(constructorArgs);
   }

   private Object manageMapIfNecessary(Map<?, ?> map) {
      boolean containsRuntimeRefs = false;

      for (Object element : map.values()) {
         if (element instanceof RuntimeBeanReference) {
            containsRuntimeRefs = true;
            break;
         }
      }

      if (containsRuntimeRefs) {
         Map<Object, Object> managedMap = new ManagedMap<>();
         managedMap.putAll((Map<? extends Object, ? extends Object>)map);
         return managedMap;
      } else {
         return map;
      }
   }

   private Object manageListIfNecessary(List<?> list) {
      boolean containsRuntimeRefs = false;

      for (Object element : list) {
         if (element instanceof RuntimeBeanReference) {
            containsRuntimeRefs = true;
            break;
         }
      }

      if (containsRuntimeRefs) {
         List<Object> managedList = new ManagedList<>();
         managedList.addAll((Collection<? extends Object>)list);
         return managedList;
      } else {
         return list;
      }
   }

   public void setProperty(String name, Object value) {
      if (this.currentBeanDefinition != null) {
         this.applyPropertyToBeanDefinition(name, value);
      }
   }

   protected void applyPropertyToBeanDefinition(String name, Object value) {
      if (value instanceof GString) {
         value = value.toString();
      }

      if (!this.addDeferredProperty(name, value)) {
         if (value instanceof Closure<?> callable) {
            GroovyBeanDefinitionWrapper current = this.currentBeanDefinition;

            try {
               Class<?> parameterType = callable.getParameterTypes()[0];
               if (Object.class == parameterType) {
                  this.currentBeanDefinition = new GroovyBeanDefinitionWrapper("");
                  callable.call(this.currentBeanDefinition);
               } else {
                  this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(null, parameterType);
                  callable.call(null);
               }

               value = this.currentBeanDefinition.getBeanDefinition();
            } finally {
               this.currentBeanDefinition = current;
            }
         }

         Assert.state(this.currentBeanDefinition != null, "No current bean definition set");
         this.currentBeanDefinition.addProperty(name, value);
      }
   }

   public @Nullable Object getProperty(String name) {
      Binding binding = this.getBinding();
      if (binding != null && binding.hasVariable(name)) {
         return binding.getVariable(name);
      }

      if (this.namespaces.containsKey(name)) {
         return this.createDynamicElementReader(name);
      }

      if (this.getRegistry().containsBeanDefinition(name)) {
         GroovyBeanDefinitionWrapper beanDefinition = (GroovyBeanDefinitionWrapper)this.getRegistry()
            .getBeanDefinition(name)
            .getAttribute(GroovyBeanDefinitionWrapper.class.getName());
         return beanDefinition != null
            ? new GroovyBeanDefinitionReader.GroovyRuntimeBeanReference(name, beanDefinition, false)
            : new RuntimeBeanReference(name, false);
      }

      if (this.currentBeanDefinition != null) {
         MutablePropertyValues pvs = this.currentBeanDefinition.getBeanDefinition().getPropertyValues();
         if (pvs.contains(name)) {
            return pvs.get(name);
         }

         GroovyBeanDefinitionReader.DeferredProperty dp = this.deferredProperties.get(this.currentBeanDefinition.getBeanName() + name);
         return dp != null ? dp.value : this.getMetaClass().getProperty(this, name);
      } else {
         return this.getMetaClass().getProperty(this, name);
      }
   }

   private GroovyDynamicElementReader createDynamicElementReader(String namespace) {
      XmlReaderContext readerContext = this.groovyDslXmlBeanDefinitionReader.createReaderContext(new DescriptiveResource("Groovy"));
      BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
      boolean decorating = this.currentBeanDefinition != null;
      if (!decorating) {
         this.currentBeanDefinition = new GroovyBeanDefinitionWrapper(namespace);
      }

      return new GroovyDynamicElementReader(namespace, this.namespaces, delegate, this.currentBeanDefinition, decorating) {
         @Override
         protected void afterInvocation() {
            if (!this.decorating) {
               GroovyBeanDefinitionReader.this.currentBeanDefinition = null;
            }
         }
      };
   }

   private static class DeferredProperty {
      private final GroovyBeanDefinitionWrapper beanDefinition;
      private final String name;
      public @Nullable Object value;

      public DeferredProperty(GroovyBeanDefinitionWrapper beanDefinition, String name, @Nullable Object value) {
         this.beanDefinition = beanDefinition;
         this.name = name;
         this.value = value;
      }

      public void apply() {
         this.beanDefinition.addProperty(this.name, this.value);
      }
   }

   private class GroovyRuntimeBeanReference extends RuntimeBeanReference implements GroovyObject {
      private final GroovyBeanDefinitionWrapper beanDefinition;
      private MetaClass metaClass;

      public GroovyRuntimeBeanReference(String beanName, GroovyBeanDefinitionWrapper beanDefinition, boolean toParent) {
         super(beanName, toParent);
         this.beanDefinition = beanDefinition;
         this.metaClass = InvokerHelper.getMetaClass(this);
      }

      public MetaClass getMetaClass() {
         return this.metaClass;
      }

      public @Nullable Object getProperty(String property) {
         if (property.equals("beanName")) {
            return this.getBeanName();
         } else {
            return property.equals("source")
               ? this.getSource()
               : new GroovyBeanDefinitionReader.GroovyRuntimeBeanReference.GroovyPropertyValue(
                  property, this.beanDefinition.getBeanDefinition().getPropertyValues().get(property)
               );
         }
      }

      public Object invokeMethod(String name, Object args) {
         return this.metaClass.invokeMethod(this, name, args);
      }

      public void setMetaClass(MetaClass metaClass) {
         this.metaClass = metaClass;
      }

      public void setProperty(String property, Object newValue) {
         if (!GroovyBeanDefinitionReader.this.addDeferredProperty(property, newValue)) {
            this.beanDefinition.getBeanDefinition().getPropertyValues().add(property, newValue);
         }
      }

      private class GroovyPropertyValue extends GroovyObjectSupport {
         private final String propertyName;
         private final @Nullable Object propertyValue;

         public GroovyPropertyValue(String propertyName, @Nullable Object propertyValue) {
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
         }

         public void leftShift(Object value) {
            InvokerHelper.invokeMethod(this.propertyValue, "leftShift", value);
            this.updateDeferredProperties(value);
         }

         public boolean add(Object value) {
            boolean retVal = (Boolean)InvokerHelper.invokeMethod(this.propertyValue, "add", value);
            this.updateDeferredProperties(value);
            return retVal;
         }

         public boolean addAll(Collection<?> values) {
            boolean retVal = (Boolean)InvokerHelper.invokeMethod(this.propertyValue, "addAll", values);

            for (Object value : values) {
               this.updateDeferredProperties(value);
            }

            return retVal;
         }

         public Object invokeMethod(String name, Object args) {
            return InvokerHelper.invokeMethod(this.propertyValue, name, args);
         }

         public Object getProperty(String name) {
            return InvokerHelper.getProperty(this.propertyValue, name);
         }

         public void setProperty(String name, Object value) {
            InvokerHelper.setProperty(this.propertyValue, name, value);
         }

         private void updateDeferredProperties(Object value) {
            if (value instanceof RuntimeBeanReference) {
               GroovyBeanDefinitionReader.this.deferredProperties
                  .put(
                     GroovyRuntimeBeanReference.this.beanDefinition.getBeanName(),
                     new GroovyBeanDefinitionReader.DeferredProperty(GroovyRuntimeBeanReference.this.beanDefinition, this.propertyName, this.propertyValue)
                  );
            }
         }
      }
   }
}
