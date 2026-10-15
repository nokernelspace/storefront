package org.springframework.context.annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;

public class Jsr330ScopeMetadataResolver implements ScopeMetadataResolver {
   private final Map<String, String> scopeMap = new HashMap<>();

   public Jsr330ScopeMetadataResolver() {
      this.registerScope("jakarta.inject.Singleton", "singleton");
   }

   public final void registerScope(Class<?> annotationType, String scopeName) {
      this.scopeMap.put(annotationType.getName(), scopeName);
   }

   public final void registerScope(String annotationType, String scopeName) {
      this.scopeMap.put(annotationType, scopeName);
   }

   protected @Nullable String resolveScopeName(String annotationType) {
      return this.scopeMap.get(annotationType);
   }

   @Override
   public ScopeMetadata resolveScopeMetadata(BeanDefinition definition) {
      ScopeMetadata metadata = new ScopeMetadata();
      metadata.setScopeName("prototype");
      if (definition instanceof AnnotatedBeanDefinition annDef) {
         Set<String> annTypes = annDef.getMetadata().getAnnotationTypes();
         String found = null;

         for (String annType : annTypes) {
            Set<String> metaAnns = annDef.getMetadata().getMetaAnnotationTypes(annType);
            if (metaAnns.contains("jakarta.inject.Scope")) {
               if (found != null) {
                  throw new IllegalStateException(
                     "Found ambiguous scope annotations on bean class [" + definition.getBeanClassName() + "]: " + found + ", " + annType
                  );
               }

               found = annType;
               String scopeName = this.resolveScopeName(annType);
               if (scopeName == null) {
                  throw new IllegalStateException("Unsupported scope annotation - not mapped onto Spring scope name: " + annType);
               }

               metadata.setScopeName(scopeName);
            }
         }
      }

      return metadata;
   }
}
