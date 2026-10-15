package org.springframework.beans.factory.xml;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.ComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

public final class ParserContext {
   private final XmlReaderContext readerContext;
   private final BeanDefinitionParserDelegate delegate;
   private @Nullable BeanDefinition containingBeanDefinition;
   private final Deque<CompositeComponentDefinition> containingComponents = new ArrayDeque<>();

   public ParserContext(XmlReaderContext readerContext, BeanDefinitionParserDelegate delegate) {
      this.readerContext = readerContext;
      this.delegate = delegate;
   }

   public ParserContext(XmlReaderContext readerContext, BeanDefinitionParserDelegate delegate, @Nullable BeanDefinition containingBeanDefinition) {
      this.readerContext = readerContext;
      this.delegate = delegate;
      this.containingBeanDefinition = containingBeanDefinition;
   }

   public XmlReaderContext getReaderContext() {
      return this.readerContext;
   }

   public BeanDefinitionRegistry getRegistry() {
      return this.readerContext.getRegistry();
   }

   public BeanDefinitionParserDelegate getDelegate() {
      return this.delegate;
   }

   public @Nullable BeanDefinition getContainingBeanDefinition() {
      return this.containingBeanDefinition;
   }

   public boolean isNested() {
      return this.containingBeanDefinition != null;
   }

   public boolean isDefaultLazyInit() {
      return "true".equals(this.delegate.getDefaults().getLazyInit());
   }

   public @Nullable Object extractSource(Object sourceCandidate) {
      return this.readerContext.extractSource(sourceCandidate);
   }

   public @Nullable CompositeComponentDefinition getContainingComponent() {
      return this.containingComponents.peek();
   }

   public void pushContainingComponent(CompositeComponentDefinition containingComponent) {
      this.containingComponents.push(containingComponent);
   }

   public CompositeComponentDefinition popContainingComponent() {
      return this.containingComponents.pop();
   }

   public void popAndRegisterContainingComponent() {
      this.registerComponent(this.popContainingComponent());
   }

   public void registerComponent(ComponentDefinition component) {
      CompositeComponentDefinition containingComponent = this.getContainingComponent();
      if (containingComponent != null) {
         containingComponent.addNestedComponent(component);
      } else {
         this.readerContext.fireComponentRegistered(component);
      }
   }

   public void registerBeanComponent(BeanComponentDefinition component) {
      BeanDefinitionReaderUtils.registerBeanDefinition(component, this.getRegistry());
      this.registerComponent(component);
   }
}
