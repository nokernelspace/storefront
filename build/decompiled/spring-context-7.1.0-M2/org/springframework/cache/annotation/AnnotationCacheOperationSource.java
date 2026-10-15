package org.springframework.cache.annotation;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.AbstractFallbackCacheOperationSource;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.util.Assert;

public class AnnotationCacheOperationSource extends AbstractFallbackCacheOperationSource implements Serializable {
   private final Set<CacheAnnotationParser> annotationParsers;
   private boolean publicMethodsOnly = true;

   public AnnotationCacheOperationSource() {
      this.annotationParsers = Collections.singleton(new SpringCacheAnnotationParser());
   }

   public AnnotationCacheOperationSource(boolean publicMethodsOnly) {
      this();
      this.publicMethodsOnly = publicMethodsOnly;
   }

   public AnnotationCacheOperationSource(CacheAnnotationParser annotationParser) {
      Assert.notNull(annotationParser, "CacheAnnotationParser must not be null");
      this.annotationParsers = Collections.singleton(annotationParser);
   }

   public AnnotationCacheOperationSource(CacheAnnotationParser... annotationParsers) {
      Assert.notEmpty(annotationParsers, "At least one CacheAnnotationParser needs to be specified");
      this.annotationParsers = Set.of(annotationParsers);
   }

   public AnnotationCacheOperationSource(Set<CacheAnnotationParser> annotationParsers) {
      Assert.notEmpty(annotationParsers, "At least one CacheAnnotationParser needs to be specified");
      this.annotationParsers = annotationParsers;
   }

   public void setPublicMethodsOnly(boolean publicMethodsOnly) {
      this.publicMethodsOnly = publicMethodsOnly;
   }

   @Override
   public boolean isCandidateClass(Class<?> targetClass) {
      for (CacheAnnotationParser parser : this.annotationParsers) {
         if (parser.isCandidateClass(targetClass)) {
            return true;
         }
      }

      return false;
   }

   @Override
   protected @Nullable Collection<CacheOperation> findCacheOperations(Class<?> clazz) {
      return this.determineCacheOperations(parser -> parser.parseCacheAnnotations(clazz));
   }

   @Override
   protected @Nullable Collection<CacheOperation> findCacheOperations(Method method) {
      return this.determineCacheOperations(parser -> parser.parseCacheAnnotations(method));
   }

   protected @Nullable Collection<CacheOperation> determineCacheOperations(AnnotationCacheOperationSource.CacheOperationProvider provider) {
      Collection<CacheOperation> ops = null;

      for (CacheAnnotationParser parser : this.annotationParsers) {
         Collection<CacheOperation> annOps = provider.getCacheOperations(parser);
         if (annOps != null) {
            if (ops == null) {
               ops = annOps;
            } else {
               Collection<CacheOperation> combined = new ArrayList<>(ops.size() + annOps.size());
               combined.addAll(ops);
               combined.addAll(annOps);
               ops = combined;
            }
         }
      }

      return ops;
   }

   @Override
   protected boolean allowPublicMethodsOnly() {
      return this.publicMethodsOnly;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof AnnotationCacheOperationSource otherCos
            && this.annotationParsers.equals(otherCos.annotationParsers)
            && this.publicMethodsOnly == otherCos.publicMethodsOnly;
   }

   @Override
   public int hashCode() {
      return this.annotationParsers.hashCode();
   }

   @FunctionalInterface
   protected interface CacheOperationProvider {
      @Nullable Collection<CacheOperation> getCacheOperations(CacheAnnotationParser parser);
   }
}
