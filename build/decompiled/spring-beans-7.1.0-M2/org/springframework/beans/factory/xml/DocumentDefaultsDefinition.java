package org.springframework.beans.factory.xml;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.parsing.DefaultsDefinition;

public class DocumentDefaultsDefinition implements DefaultsDefinition {
   private @Nullable String lazyInit;
   private @Nullable String merge;
   private @Nullable String autowire;
   private @Nullable String autowireCandidates;
   private @Nullable String initMethod;
   private @Nullable String destroyMethod;
   private @Nullable Object source;

   public void setLazyInit(@Nullable String lazyInit) {
      this.lazyInit = lazyInit;
   }

   public @Nullable String getLazyInit() {
      return this.lazyInit;
   }

   public void setMerge(@Nullable String merge) {
      this.merge = merge;
   }

   public @Nullable String getMerge() {
      return this.merge;
   }

   public void setAutowire(@Nullable String autowire) {
      this.autowire = autowire;
   }

   public @Nullable String getAutowire() {
      return this.autowire;
   }

   public void setAutowireCandidates(@Nullable String autowireCandidates) {
      this.autowireCandidates = autowireCandidates;
   }

   public @Nullable String getAutowireCandidates() {
      return this.autowireCandidates;
   }

   public void setInitMethod(@Nullable String initMethod) {
      this.initMethod = initMethod;
   }

   public @Nullable String getInitMethod() {
      return this.initMethod;
   }

   public void setDestroyMethod(@Nullable String destroyMethod) {
      this.destroyMethod = destroyMethod;
   }

   public @Nullable String getDestroyMethod() {
      return this.destroyMethod;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }
}
