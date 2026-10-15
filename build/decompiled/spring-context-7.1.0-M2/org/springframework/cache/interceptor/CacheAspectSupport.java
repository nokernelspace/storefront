package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.SpringProperties;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.util.function.SupplierUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class CacheAspectSupport extends AbstractCacheInvoker implements BeanFactoryAware, InitializingBean, SmartInitializingSingleton {
   public static final String IGNORE_REACTIVESTREAMS_PROPERTY_NAME = "spring.cache.reactivestreams.ignore";
   private static final boolean SHOULD_IGNORE_REACTIVE_STREAMS = SpringProperties.getFlag("spring.cache.reactivestreams.ignore");
   private static final boolean REACTIVE_STREAMS_PRESENT = ClassUtils.isPresent("org.reactivestreams.Publisher", CacheAspectSupport.class.getClassLoader());
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final Map<CacheAspectSupport.CacheOperationCacheKey, CacheAspectSupport.CacheOperationMetadata> metadataCache = new ConcurrentHashMap<>(1024);
   private final StandardEvaluationContext originalEvaluationContext = new StandardEvaluationContext();
   private final CacheOperationExpressionEvaluator evaluator = new CacheOperationExpressionEvaluator(
      new CacheEvaluationContextFactory(this.originalEvaluationContext)
   );
   private final CacheAspectSupport.@Nullable ReactiveCachingHandler reactiveCachingHandler;
   private @Nullable CacheOperationSource cacheOperationSource;
   private SingletonSupplier<KeyGenerator> keyGenerator = SingletonSupplier.of(SimpleKeyGenerator::new);
   private @Nullable SingletonSupplier<CacheResolver> cacheResolver;
   private @Nullable BeanFactory beanFactory;
   private boolean initialized = false;

   protected CacheAspectSupport() {
      this.reactiveCachingHandler = REACTIVE_STREAMS_PRESENT && !SHOULD_IGNORE_REACTIVE_STREAMS ? new CacheAspectSupport.ReactiveCachingHandler() : null;
   }

   public void configure(
      @Nullable Supplier<? extends @Nullable CacheErrorHandler> errorHandler,
      @Nullable Supplier<? extends @Nullable KeyGenerator> keyGenerator,
      @Nullable Supplier<? extends @Nullable CacheResolver> cacheResolver,
      @Nullable Supplier<? extends @Nullable CacheManager> cacheManager
   ) {
      this.errorHandler = new SingletonSupplier(errorHandler, SimpleCacheErrorHandler::new);
      this.keyGenerator = new SingletonSupplier(keyGenerator, SimpleKeyGenerator::new);
      this.cacheResolver = new SingletonSupplier(cacheResolver, () -> SimpleCacheResolver.of((CacheManager)SupplierUtils.resolve(cacheManager)));
   }

   public void setCacheOperationSources(CacheOperationSource... cacheOperationSources) {
      Assert.notEmpty(cacheOperationSources, "At least 1 CacheOperationSource needs to be specified");
      this.cacheOperationSource = cacheOperationSources.length > 1 ? new CompositeCacheOperationSource(cacheOperationSources) : cacheOperationSources[0];
   }

   public void setCacheOperationSource(@Nullable CacheOperationSource cacheOperationSource) {
      this.cacheOperationSource = cacheOperationSource;
   }

   public @Nullable CacheOperationSource getCacheOperationSource() {
      return this.cacheOperationSource;
   }

   public void setKeyGenerator(KeyGenerator keyGenerator) {
      this.keyGenerator = SingletonSupplier.of(keyGenerator);
   }

   public KeyGenerator getKeyGenerator() {
      return (KeyGenerator)this.keyGenerator.obtain();
   }

   public void setCacheResolver(@Nullable CacheResolver cacheResolver) {
      this.cacheResolver = SingletonSupplier.ofNullable(cacheResolver);
   }

   public @Nullable CacheResolver getCacheResolver() {
      return (CacheResolver)SupplierUtils.resolve(this.cacheResolver);
   }

   public void setCacheManager(CacheManager cacheManager) {
      this.cacheResolver = SingletonSupplier.of(new SimpleCacheResolver(cacheManager));
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      this.originalEvaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
   }

   public void afterPropertiesSet() {
      Assert.state(
         this.getCacheOperationSource() != null,
         "The 'cacheOperationSources' property is required: If there are no cacheable methods, then don't use a cache aspect."
      );
   }

   public void afterSingletonsInstantiated() {
      if (this.getCacheResolver() == null) {
         Assert.state(this.beanFactory != null, "CacheResolver or BeanFactory must be set on cache aspect");

         try {
            this.setCacheManager((CacheManager)this.beanFactory.getBean(CacheManager.class));
         } catch (NoUniqueBeanDefinitionException ex) {
            int numberOfBeansFound = ex.getNumberOfBeansFound();
            Collection<String> beanNamesFound = ex.getBeanNamesFound();
            StringBuilder message = new StringBuilder("no CacheResolver specified and expected single matching CacheManager but found ");
            message.append(numberOfBeansFound);
            if (beanNamesFound != null) {
               message.append(": ").append(StringUtils.collectionToCommaDelimitedString(beanNamesFound));
            }

            String exceptionMessage = message.toString();
            if (beanNamesFound != null) {
               throw new NoUniqueBeanDefinitionException(CacheManager.class, beanNamesFound, exceptionMessage);
            }

            throw new NoUniqueBeanDefinitionException(CacheManager.class, numberOfBeansFound, exceptionMessage);
         } catch (NoSuchBeanDefinitionException ex) {
            throw new NoSuchBeanDefinitionException(
               CacheManager.class, "no CacheResolver specified - register a CacheManager bean or remove the @EnableCaching annotation from your configuration."
            );
         }
      }

      this.initialized = true;
   }

   protected Collection<? extends Cache> getCaches(CacheOperationInvocationContext<CacheOperation> context, CacheResolver cacheResolver) {
      Collection<? extends Cache> caches = cacheResolver.resolveCaches(context);
      if (caches.isEmpty()) {
         throw new IllegalStateException(
            "No cache could be resolved for '"
               + context.getOperation()
               + "' using resolver '"
               + cacheResolver
               + "'. At least one cache should be provided per cache operation."
         );
      } else {
         return caches;
      }
   }

   protected CacheAspectSupport.CacheOperationContext getOperationContext(
      CacheOperation operation, Method method, @Nullable Object[] args, Object target, Class<?> targetClass
   ) {
      CacheAspectSupport.CacheOperationMetadata metadata = this.getCacheOperationMetadata(operation, method, targetClass);
      return new CacheAspectSupport.CacheOperationContext(metadata, args, target);
   }

   protected CacheAspectSupport.CacheOperationMetadata getCacheOperationMetadata(CacheOperation operation, Method method, Class<?> targetClass) {
      CacheAspectSupport.CacheOperationCacheKey cacheKey = new CacheAspectSupport.CacheOperationCacheKey(operation, method, targetClass);
      CacheAspectSupport.CacheOperationMetadata metadata = this.metadataCache.get(cacheKey);
      if (metadata == null) {
         KeyGenerator operationKeyGenerator;
         if (StringUtils.hasText(operation.getKeyGenerator())) {
            operationKeyGenerator = this.getBean(operation.getKeyGenerator(), KeyGenerator.class);
         } else {
            operationKeyGenerator = this.getKeyGenerator();
         }

         CacheResolver operationCacheResolver;
         if (StringUtils.hasText(operation.getCacheResolver())) {
            operationCacheResolver = this.getBean(operation.getCacheResolver(), CacheResolver.class);
         } else if (StringUtils.hasText(operation.getCacheManager())) {
            CacheManager cacheManager = this.getBean(operation.getCacheManager(), CacheManager.class);
            operationCacheResolver = new SimpleCacheResolver(cacheManager);
         } else {
            operationCacheResolver = this.getCacheResolver();
            Assert.state(operationCacheResolver != null, "No CacheResolver/CacheManager set");
         }

         metadata = new CacheAspectSupport.CacheOperationMetadata(operation, method, targetClass, operationKeyGenerator, operationCacheResolver);
         this.metadataCache.put(cacheKey, metadata);
      }

      return metadata;
   }

   protected <T> T getBean(String name, Class<T> serviceType) {
      if (this.beanFactory == null) {
         throw new IllegalStateException("BeanFactory must be set on cache aspect for " + serviceType.getSimpleName() + " retrieval");
      } else {
         return (T)BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.beanFactory, serviceType, name);
      }
   }

   protected void clearMetadataCache() {
      this.metadataCache.clear();
      this.evaluator.clear();
   }

   protected @Nullable Object execute(CacheOperationInvoker invoker, Object target, Method method, @Nullable Object[] args) {
      if (this.initialized) {
         Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
         CacheOperationSource cacheOperationSource = this.getCacheOperationSource();
         if (cacheOperationSource != null) {
            Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(method, targetClass);
            if (!CollectionUtils.isEmpty(operations)) {
               return this.execute(invoker, method, new CacheAspectSupport.CacheOperationContexts(operations, method, args, target, targetClass));
            }
         }
      }

      return this.invokeOperation(invoker);
   }

   protected @Nullable Object invokeOperation(CacheOperationInvoker invoker) {
      return invoker.invoke();
   }

   private @Nullable Object execute(CacheOperationInvoker invoker, Method method, CacheAspectSupport.CacheOperationContexts contexts) {
      if (contexts.isSynchronized()) {
         return this.executeSynchronized(invoker, method, contexts);
      }

      this.processCacheEvicts(contexts.get(CacheEvictOperation.class), true, CacheOperationExpressionEvaluator.NO_RESULT);
      Object cacheHit = this.findCachedValue(invoker, method, contexts);
      return cacheHit != null && !(cacheHit instanceof Cache.ValueWrapper) ? cacheHit : this.evaluate(cacheHit, invoker, method, contexts);
   }

   private @Nullable Object executeSynchronized(CacheOperationInvoker invoker, Method method, CacheAspectSupport.CacheOperationContexts contexts) {
      CacheAspectSupport.CacheOperationContext context = contexts.get(CacheableOperation.class).iterator().next();
      if (this.isConditionPassing(context, CacheOperationExpressionEvaluator.NO_RESULT)) {
         Object key = this.generateKey(context, CacheOperationExpressionEvaluator.NO_RESULT);
         Cache cache = context.getCaches().iterator().next();
         if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            AtomicBoolean invokeFailure = new AtomicBoolean(false);
            CompletableFuture<?> result = this.doRetrieve(cache, key, () -> {
               CompletableFuture<?> invokeResult = (CompletableFuture<?>)this.invokeOperation(invoker);
               if (invokeResult == null) {
                  throw new IllegalStateException("Returned CompletableFuture must not be null: " + method);
               } else {
                  return invokeResult.exceptionallyCompose(exx -> {
                     invokeFailure.set(true);
                     return CompletableFuture.failedFuture(exx);
                  });
               }
            });
            return result.exceptionallyCompose(exx -> {
               if (exx instanceof RuntimeException rex) {
                  try {
                     this.getErrorHandler().handleCacheGetError(rex, cache, key);
                     return invokeFailure.get() ? CompletableFuture.failedFuture(exx) : (CompletableFuture)this.invokeOperation(invoker);
                  } catch (Throwable ex2) {
                     return CompletableFuture.failedFuture(ex2);
                  }
               } else {
                  return CompletableFuture.failedFuture(exx);
               }
            });
         }

         if (this.reactiveCachingHandler != null) {
            Object returnValue = this.reactiveCachingHandler.executeSynchronized(invoker, method, cache, key);
            if (returnValue != CacheAspectSupport.ReactiveCachingHandler.NOT_HANDLED) {
               return returnValue;
            }
         }

         try {
            return this.wrapCacheValue(method, this.doGet(cache, key, () -> this.unwrapReturnValue(this.invokeOperation(invoker))));
         } catch (Cache.ValueRetrievalException ex) {
            ReflectionUtils.rethrowRuntimeException(ex.getCause());
            return null;
         }
      } else {
         return this.invokeOperation(invoker);
      }
   }

   private @Nullable Object findCachedValue(CacheOperationInvoker invoker, Method method, CacheAspectSupport.CacheOperationContexts contexts) {
      for (CacheAspectSupport.CacheOperationContext context : contexts.get(CacheableOperation.class)) {
         if (this.isConditionPassing(context, CacheOperationExpressionEvaluator.NO_RESULT)) {
            Object key = this.generateKey(context, CacheOperationExpressionEvaluator.NO_RESULT);
            Object cached = this.findInCaches(context, key, invoker, method, contexts);
            if (cached != null) {
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Cache entry for key '" + key + "' found in cache(s) " + context.getCacheNames());
               }

               return cached;
            }

            if (this.logger.isTraceEnabled()) {
               this.logger.trace("No cache entry for key '" + key + "' in cache(s) " + context.getCacheNames());
            }
         }
      }

      return null;
   }

   private @Nullable Object findInCaches(
      CacheAspectSupport.CacheOperationContext context,
      Object key,
      CacheOperationInvoker invoker,
      Method method,
      CacheAspectSupport.CacheOperationContexts contexts
   ) {
      for (Cache cache : context.getCaches()) {
         if (CompletableFuture.class.isAssignableFrom(context.getMethod().getReturnType())) {
            CompletableFuture<?> result = this.doRetrieve(cache, key);
            if (result != null) {
               return result.exceptionallyCompose(ex -> {
                     if (ex instanceof RuntimeException rex) {
                        try {
                           this.getErrorHandler().handleCacheGetError(rex, cache, key);
                           return CompletableFuture.completedFuture(null);
                        } catch (Throwable ex2) {
                           return CompletableFuture.failedFuture(ex2);
                        }
                     } else {
                        return CompletableFuture.failedFuture(ex);
                     }
                  })
                  .thenCompose(
                     value -> (CompletableFuture)this.evaluate(
                        value != null ? CompletableFuture.completedFuture(this.unwrapCacheValue(value)) : null, invoker, method, contexts
                     )
                  );
            }
         } else {
            if (this.reactiveCachingHandler != null) {
               Object returnValue = this.reactiveCachingHandler.findInCaches(context, cache, key, invoker, method, contexts);
               if (returnValue != CacheAspectSupport.ReactiveCachingHandler.NOT_HANDLED) {
                  return returnValue;
               }
            }

            Cache.ValueWrapper result = this.doGet(cache, key);
            if (result != null) {
               return result;
            }
         }
      }

      return null;
   }

   private @Nullable Object evaluate(
      @Nullable Object cacheHit, CacheOperationInvoker invoker, Method method, CacheAspectSupport.CacheOperationContexts contexts
   ) {
      if (contexts.processed) {
         return cacheHit;
      }

      Object cacheValue;
      Object returnValue;
      if (cacheHit != null && !this.hasCachePut(contexts)) {
         cacheValue = this.unwrapCacheValue(cacheHit);
         returnValue = this.wrapCacheValue(method, cacheValue);
      } else {
         returnValue = this.invokeOperation(invoker);
         cacheValue = this.unwrapReturnValue(returnValue);
      }

      List<CacheAspectSupport.CachePutRequest> cachePutRequests = new ArrayList<>(1);
      if (cacheHit == null) {
         this.collectPutRequests(contexts.get(CacheableOperation.class), cacheValue, cachePutRequests);
      }

      this.collectPutRequests(contexts.get(CachePutOperation.class), cacheValue, cachePutRequests);

      for (CacheAspectSupport.CachePutRequest cachePutRequest : cachePutRequests) {
         Object returnOverride = cachePutRequest.apply(cacheValue);
         if (returnOverride != null) {
            returnValue = returnOverride;
         }
      }

      Object returnOverride = this.processCacheEvicts(contexts.get(CacheEvictOperation.class), false, returnValue);
      if (returnOverride != null) {
         returnValue = returnOverride;
      }

      contexts.processed = true;
      return returnValue;
   }

   private @Nullable Object unwrapCacheValue(@Nullable Object cacheValue) {
      return cacheValue instanceof Cache.ValueWrapper wrapper ? wrapper.get() : cacheValue;
   }

   private @Nullable Object wrapCacheValue(Method method, @Nullable Object cacheValue) {
      return method.getReturnType() != Optional.class || cacheValue != null && cacheValue.getClass() == Optional.class
         ? cacheValue
         : Optional.ofNullable(cacheValue);
   }

   private @Nullable Object unwrapReturnValue(@Nullable Object returnValue) {
      return ObjectUtils.unwrapOptional(returnValue);
   }

   private boolean hasCachePut(CacheAspectSupport.CacheOperationContexts contexts) {
      Collection<CacheAspectSupport.CacheOperationContext> cachePutContexts = contexts.get(CachePutOperation.class);
      Collection<CacheAspectSupport.CacheOperationContext> excluded = new ArrayList<>(1);

      for (CacheAspectSupport.CacheOperationContext context : cachePutContexts) {
         try {
            if (!context.isConditionPassing(CacheOperationExpressionEvaluator.RESULT_UNAVAILABLE)) {
               excluded.add(context);
            }
         } catch (VariableNotAvailableException var7) {
         }
      }

      return cachePutContexts.size() != excluded.size();
   }

   private @Nullable Object processCacheEvicts(Collection<CacheAspectSupport.CacheOperationContext> contexts, boolean beforeInvocation, @Nullable Object result) {
      if (contexts.isEmpty()) {
         return null;
      } else {
         List<CacheAspectSupport.CacheOperationContext> applicable = contexts.stream()
            .filter(context -> context.metadata.operation instanceof CacheEvictOperation evict && beforeInvocation == evict.isBeforeInvocation())
            .toList();
         if (applicable.isEmpty()) {
            return null;
         } else if (result instanceof CompletableFuture<?> future) {
            return future.whenComplete((value, ex) -> {
               if (ex == null) {
                  this.performCacheEvicts(applicable, value);
               }
            });
         } else {
            if (this.reactiveCachingHandler != null) {
               Object returnValue = this.reactiveCachingHandler.processCacheEvicts(applicable, result);
               if (returnValue != CacheAspectSupport.ReactiveCachingHandler.NOT_HANDLED) {
                  return returnValue;
               }
            }

            this.performCacheEvicts(applicable, result);
            return null;
         }
      }
   }

   private void performCacheEvicts(List<CacheAspectSupport.CacheOperationContext> contexts, @Nullable Object result) {
      for (CacheAspectSupport.CacheOperationContext context : contexts) {
         CacheEvictOperation operation = (CacheEvictOperation)context.metadata.operation;
         if (this.isConditionPassing(context, result)) {
            Object key = context.getGeneratedKey();

            for (Cache cache : context.getCaches()) {
               if (operation.isCacheWide()) {
                  this.logInvalidating(context, operation, null);
                  this.doClear(cache, operation.isBeforeInvocation());
               } else {
                  if (key == null) {
                     key = this.generateKey(context, result);
                  }

                  this.logInvalidating(context, operation, key);
                  this.doEvict(cache, key, operation.isBeforeInvocation());
               }
            }
         }
      }
   }

   private void logInvalidating(CacheAspectSupport.CacheOperationContext context, CacheEvictOperation operation, @Nullable Object key) {
      if (this.logger.isTraceEnabled()) {
         this.logger
            .trace(
               "Invalidating "
                  + (key != null ? "cache key [" + key + "]" : "entire cache")
                  + " for operation "
                  + operation
                  + " on method "
                  + context.metadata.method
            );
      }
   }

   private void collectPutRequests(
      Collection<CacheAspectSupport.CacheOperationContext> contexts, @Nullable Object result, Collection<CacheAspectSupport.CachePutRequest> putRequests
   ) {
      for (CacheAspectSupport.CacheOperationContext context : contexts) {
         if (this.isConditionPassing(context, result)) {
            putRequests.add(new CacheAspectSupport.CachePutRequest(context));
         }
      }
   }

   private boolean isConditionPassing(CacheAspectSupport.CacheOperationContext context, @Nullable Object result) {
      boolean passing = context.isConditionPassing(result);
      if (!passing && this.logger.isTraceEnabled()) {
         this.logger.trace("Cache condition failed on method " + context.metadata.method + " for operation " + context.metadata.operation);
      }

      return passing;
   }

   private Object generateKey(CacheAspectSupport.CacheOperationContext context, @Nullable Object result) {
      Object key = context.generateKey(result);
      if (key == null) {
         throw new IllegalArgumentException(
            "Null key returned for cache operation [%s]. If you are using named parameters, ensure that the compiler uses the '-parameters' flag."
               .formatted(context.metadata.operation)
         );
      }

      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Computed cache key '" + key + "' for operation " + context.metadata.operation);
      }

      return key;
   }

   private static final class CacheOperationCacheKey implements Comparable<CacheAspectSupport.CacheOperationCacheKey> {
      private final CacheOperation cacheOperation;
      private final AnnotatedElementKey methodCacheKey;

      private CacheOperationCacheKey(CacheOperation cacheOperation, Method method, Class<?> targetClass) {
         this.cacheOperation = cacheOperation;
         this.methodCacheKey = new AnnotatedElementKey(method, targetClass);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof CacheAspectSupport.CacheOperationCacheKey that
               && this.cacheOperation.equals(that.cacheOperation)
               && this.methodCacheKey.equals(that.methodCacheKey);
      }

      @Override
      public int hashCode() {
         return this.cacheOperation.hashCode() * 31 + this.methodCacheKey.hashCode();
      }

      @Override
      public String toString() {
         return this.cacheOperation + " on " + this.methodCacheKey;
      }

      public int compareTo(CacheAspectSupport.CacheOperationCacheKey other) {
         int result = this.cacheOperation.getName().compareTo(other.cacheOperation.getName());
         if (result == 0) {
            result = this.methodCacheKey.compareTo(other.methodCacheKey);
         }

         return result;
      }
   }

   protected class CacheOperationContext implements CacheOperationInvocationContext<CacheOperation> {
      private final CacheAspectSupport.CacheOperationMetadata metadata;
      private final @Nullable Object[] args;
      private final Object target;
      private final Collection<? extends Cache> caches;
      private final Collection<String> cacheNames;
      private @Nullable Boolean conditionPassing;
      private @Nullable Object key;

      public CacheOperationContext(CacheAspectSupport.CacheOperationMetadata metadata, @Nullable Object[] args, Object target) {
         this.metadata = metadata;
         this.args = this.extractArgs(metadata.method, args);
         this.target = target;
         this.caches = CacheAspectSupport.this.getCaches(this, metadata.cacheResolver);
         this.cacheNames = this.prepareCacheNames(this.caches);
      }

      public CacheOperation getOperation() {
         return this.metadata.operation;
      }

      @Override
      public Object getTarget() {
         return this.target;
      }

      @Override
      public Method getMethod() {
         return this.metadata.method;
      }

      @Override
      public @Nullable Object[] getArgs() {
         return this.args;
      }

      private @Nullable Object[] extractArgs(Method method, @Nullable Object[] args) {
         if (!method.isVarArgs()) {
            return args;
         }

         Object[] varArgs = ObjectUtils.toObjectArray(args[args.length - 1]);
         Object[] combinedArgs = new Object[args.length - 1 + varArgs.length];
         System.arraycopy(args, 0, combinedArgs, 0, args.length - 1);
         System.arraycopy(varArgs, 0, combinedArgs, args.length - 1, varArgs.length);
         return combinedArgs;
      }

      protected boolean isConditionPassing(@Nullable Object result) {
         if (this.conditionPassing == null) {
            if (StringUtils.hasText(this.metadata.operation.getCondition())) {
               EvaluationContext evaluationContext = this.createEvaluationContext(result);
               this.conditionPassing = CacheAspectSupport.this.evaluator
                  .condition(this.metadata.operation.getCondition(), this.metadata.methodKey, evaluationContext);
            } else {
               this.conditionPassing = true;
            }
         }

         return this.conditionPassing;
      }

      protected boolean canPutToCache(@Nullable Object value) {
         String unless = "";
         if (this.metadata.operation instanceof CacheableOperation cacheableOperation) {
            unless = cacheableOperation.getUnless();
         } else if (this.metadata.operation instanceof CachePutOperation cachePutOperation) {
            unless = cachePutOperation.getUnless();
         }

         if (StringUtils.hasText(unless)) {
            EvaluationContext evaluationContext = this.createEvaluationContext(value);
            return !CacheAspectSupport.this.evaluator.unless(unless, this.metadata.methodKey, evaluationContext);
         } else {
            return true;
         }
      }

      protected @Nullable Object generateKey(@Nullable Object result) {
         if (StringUtils.hasText(this.metadata.operation.getKey())) {
            EvaluationContext evaluationContext = this.createEvaluationContext(result);
            this.key = CacheAspectSupport.this.evaluator.key(this.metadata.operation.getKey(), this.metadata.methodKey, evaluationContext);
         } else {
            this.key = this.metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
         }

         return this.key;
      }

      protected @Nullable Object getGeneratedKey() {
         return this.key;
      }

      private EvaluationContext createEvaluationContext(@Nullable Object result) {
         return CacheAspectSupport.this.evaluator
            .createEvaluationContext(this.caches, this.metadata.method, this.args, this.target, this.metadata.targetClass, this.metadata.targetMethod, result);
      }

      protected Collection<? extends Cache> getCaches() {
         return this.caches;
      }

      protected Collection<String> getCacheNames() {
         return this.cacheNames;
      }

      private Collection<String> prepareCacheNames(Collection<? extends Cache> caches) {
         Collection<String> names = new ArrayList<>(caches.size());

         for (Cache cache : caches) {
            names.add(cache.getName());
         }

         return names;
      }
   }

   private class CacheOperationContexts {
      private final MultiValueMap<Class<? extends CacheOperation>, CacheAspectSupport.CacheOperationContext> contexts;
      private final boolean sync;
      boolean processed;

      public CacheOperationContexts(
         Collection<? extends CacheOperation> operations, Method method, @Nullable Object[] args, Object target, Class<?> targetClass
      ) {
         this.contexts = new LinkedMultiValueMap(operations.size());

         for (CacheOperation op : operations) {
            this.contexts.add(op.getClass(), CacheAspectSupport.this.getOperationContext(op, method, args, target, targetClass));
         }

         this.sync = this.determineSyncFlag(method);
      }

      public Collection<CacheAspectSupport.CacheOperationContext> get(Class<? extends CacheOperation> operationClass) {
         Collection<CacheAspectSupport.CacheOperationContext> result = (Collection<CacheAspectSupport.CacheOperationContext>)this.contexts.get(operationClass);
         return result != null ? result : Collections.emptyList();
      }

      public boolean isSynchronized() {
         return this.sync;
      }

      private boolean determineSyncFlag(Method method) {
         List<CacheAspectSupport.CacheOperationContext> cacheableContexts = (List<CacheAspectSupport.CacheOperationContext>)this.contexts
            .get(CacheableOperation.class);
         if (cacheableContexts == null) {
            return false;
         }

         boolean syncEnabled = false;

         for (CacheAspectSupport.CacheOperationContext context : cacheableContexts) {
            if (context.getOperation() instanceof CacheableOperation cacheable && cacheable.isSync()) {
               syncEnabled = true;
               break;
            }
         }

         if (syncEnabled) {
            if (this.contexts.size() > 1) {
               throw new IllegalStateException("A sync=true operation cannot be combined with other cache operations on '" + method + "'");
            } else if (cacheableContexts.size() > 1) {
               throw new IllegalStateException("Only one sync=true operation is allowed on '" + method + "'");
            } else {
               CacheAspectSupport.CacheOperationContext cacheableContext = cacheableContexts.iterator().next();
               CacheOperation operation = cacheableContext.getOperation();
               if (cacheableContext.getCaches().size() > 1) {
                  throw new IllegalStateException("A sync=true operation is restricted to a single cache on '" + operation + "'");
               } else if (operation instanceof CacheableOperation cacheable && StringUtils.hasText(cacheable.getUnless())) {
                  throw new IllegalStateException("A sync=true operation does not support the unless attribute on '" + operation + "'");
               } else {
                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   protected static class CacheOperationMetadata {
      private final CacheOperation operation;
      private final Method method;
      private final Class<?> targetClass;
      private final Method targetMethod;
      private final AnnotatedElementKey methodKey;
      private final KeyGenerator keyGenerator;
      private final CacheResolver cacheResolver;

      public CacheOperationMetadata(CacheOperation operation, Method method, Class<?> targetClass, KeyGenerator keyGenerator, CacheResolver cacheResolver) {
         this.operation = operation;
         this.method = BridgeMethodResolver.findBridgedMethod(method);
         this.targetClass = targetClass;
         this.targetMethod = !Proxy.isProxyClass(targetClass) ? AopUtils.getMostSpecificMethod(method, targetClass) : this.method;
         this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);
         this.keyGenerator = keyGenerator;
         this.cacheResolver = cacheResolver;
      }
   }

   private final class CachePutListSubscriber implements Subscriber<Object> {
      private final CacheAspectSupport.CachePutRequest request;
      private final List<Object> cacheValue = new ArrayList<>();

      public CachePutListSubscriber(CacheAspectSupport.CachePutRequest request) {
         this.request = request;
      }

      public void onSubscribe(Subscription s) {
         s.request(2147483647L);
      }

      public void onNext(Object o) {
         this.cacheValue.add(o);
      }

      public void onError(Throwable t) {
         this.cacheValue.clear();
      }

      public void onComplete() {
         this.request.performCachePut(this.cacheValue);
      }
   }

   private class CachePutRequest {
      private final CacheAspectSupport.CacheOperationContext context;

      public CachePutRequest(CacheAspectSupport.CacheOperationContext context) {
         this.context = context;
      }

      public @Nullable Object apply(@Nullable Object result) {
         if (result instanceof CompletableFuture<?> future) {
            return future.whenComplete((value, ex) -> {
               if (ex == null) {
                  this.performCachePut(value);
               }
            });
         } else {
            if (CacheAspectSupport.this.reactiveCachingHandler != null) {
               Object returnValue = CacheAspectSupport.this.reactiveCachingHandler.processPutRequest(this, result);
               if (returnValue != CacheAspectSupport.ReactiveCachingHandler.NOT_HANDLED) {
                  return returnValue;
               }
            }

            this.performCachePut(result);
            return null;
         }
      }

      public void performCachePut(@Nullable Object value) {
         if (this.context.canPutToCache(value)) {
            Object key = this.context.getGeneratedKey();
            if (key == null) {
               key = CacheAspectSupport.this.generateKey(this.context, value);
            }

            if (CacheAspectSupport.this.logger.isTraceEnabled()) {
               CacheAspectSupport.this.logger.trace("Creating cache entry for key '" + key + "' in cache(s) " + this.context.getCacheNames());
            }

            for (Cache cache : this.context.getCaches()) {
               CacheAspectSupport.this.doPut(cache, key, value);
            }
         }
      }
   }

   private class ReactiveCachingHandler {
      public static final Object NOT_HANDLED = new Object();
      private final ReactiveAdapterRegistry registry = ReactiveAdapterRegistry.getSharedInstance();

      public @Nullable Object executeSynchronized(CacheOperationInvoker invoker, Method method, Cache cache, Object key) {
         AtomicBoolean invokeFailure = new AtomicBoolean(false);
         ReactiveAdapter adapter = this.registry.getAdapter(method.getReturnType());
         if (adapter != null) {
            return adapter.isMultiValue()
               ? adapter.fromPublisher(
                  Flux.from(
                        Mono.fromFuture(
                           CacheAspectSupport.this.doRetrieve(
                              cache,
                              key,
                              () -> Flux.from(adapter.toPublisher(CacheAspectSupport.this.invokeOperation(invoker)))
                                 .collectList()
                                 .doOnError(ex -> invokeFailure.set(true))
                                 .toFuture()
                           )
                        )
                     )
                     .flatMap(Flux::fromIterable)
                     .onErrorResume(RuntimeException.class, ex -> {
                        try {
                           CacheAspectSupport.this.getErrorHandler().handleCacheGetError(ex, cache, key);
                           return invokeFailure.get() ? Flux.error(ex) : Flux.from(adapter.toPublisher(CacheAspectSupport.this.invokeOperation(invoker)));
                        } catch (RuntimeException exception) {
                           return Flux.error(exception);
                        }
                     })
               )
               : adapter.fromPublisher(
                  Mono.fromFuture(
                        CacheAspectSupport.this.doRetrieve(
                           cache,
                           key,
                           () -> Mono.from(adapter.toPublisher(CacheAspectSupport.this.invokeOperation(invoker)))
                              .doOnError(ex -> invokeFailure.set(true))
                              .toFuture()
                        )
                     )
                     .onErrorResume(RuntimeException.class, ex -> {
                        try {
                           CacheAspectSupport.this.getErrorHandler().handleCacheGetError(ex, cache, key);
                           return invokeFailure.get() ? Mono.error(ex) : Mono.from(adapter.toPublisher(CacheAspectSupport.this.invokeOperation(invoker)));
                        } catch (RuntimeException exception) {
                           return Mono.error(exception);
                        }
                     })
               );
         } else {
            return KotlinDetector.isSuspendingFunction(method) ? Mono.fromFuture(CacheAspectSupport.this.doRetrieve(cache, key, () -> {
               Mono<?> mono = (Mono<?>)CacheAspectSupport.this.invokeOperation(invoker);
               if (mono != null) {
                  mono = mono.doOnError(ex -> invokeFailure.set(true));
               } else {
                  mono = Mono.empty();
               }

               return mono.toFuture();
            })).onErrorResume(RuntimeException.class, ex -> {
               try {
                  CacheAspectSupport.this.getErrorHandler().handleCacheGetError(ex, cache, key);
                  return invokeFailure.get() ? Mono.error(ex) : Objects.requireNonNull((Mono)CacheAspectSupport.this.invokeOperation(invoker));
               } catch (RuntimeException exception) {
                  return Mono.error(exception);
               }
            }) : NOT_HANDLED;
         }
      }

      public @Nullable Object processCacheEvicts(List<CacheAspectSupport.CacheOperationContext> contexts, @Nullable Object result) {
         ReactiveAdapter adapter = result != null ? this.registry.getAdapter(result.getClass()) : null;
         return adapter != null
            ? adapter.fromPublisher(Mono.from(adapter.toPublisher(result)).doOnSuccess(value -> CacheAspectSupport.this.performCacheEvicts(contexts, value)))
            : NOT_HANDLED;
      }

      public @Nullable Object findInCaches(
         CacheAspectSupport.CacheOperationContext context,
         Cache cache,
         Object key,
         CacheOperationInvoker invoker,
         Method method,
         CacheAspectSupport.CacheOperationContexts contexts
      ) {
         ReactiveAdapter adapter = this.registry.getAdapter(context.getMethod().getReturnType());
         if (adapter != null) {
            CompletableFuture<?> cachedFuture = CacheAspectSupport.this.doRetrieve(cache, key);
            if (cachedFuture == null) {
               return null;
            } else {
               return adapter.isMultiValue()
                  ? adapter.fromPublisher(
                     Flux.from(Mono.fromFuture(cachedFuture))
                        .switchIfEmpty(
                           Flux.defer(() -> (Flux)Objects.requireNonNull((Publisher)CacheAspectSupport.this.evaluate(null, invoker, method, contexts)))
                        )
                        .flatMap(v -> Objects.requireNonNull(CacheAspectSupport.this.evaluate(this.valueToFlux(v, contexts), invoker, method, contexts)))
                        .onErrorResume(RuntimeException.class, ex -> {
                           try {
                              CacheAspectSupport.this.getErrorHandler().handleCacheGetError((RuntimeException)ex, cache, key);
                              Object e = CacheAspectSupport.this.evaluate(null, invoker, method, contexts);
                              return e != null ? e : Flux.error((RuntimeException)ex);
                           } catch (RuntimeException exception) {
                              return Flux.error(exception);
                           }
                        })
                  )
                  : adapter.fromPublisher(
                     Mono.fromFuture(cachedFuture)
                        .switchIfEmpty(Mono.defer(() -> Objects.requireNonNull((Mono)CacheAspectSupport.this.evaluate(null, invoker, method, contexts))))
                        .flatMap(
                           v -> Objects.requireNonNull(
                              CacheAspectSupport.this.evaluate(Mono.justOrEmpty(CacheAspectSupport.this.unwrapCacheValue(v)), invoker, method, contexts)
                           )
                        )
                        .onErrorResume(RuntimeException.class, ex -> {
                           try {
                              CacheAspectSupport.this.getErrorHandler().handleCacheGetError((RuntimeException)ex, cache, key);
                              Object e = CacheAspectSupport.this.evaluate(null, invoker, method, contexts);
                              return e != null ? e : Mono.error((RuntimeException)ex);
                           } catch (RuntimeException exception) {
                              return Mono.error(exception);
                           }
                        })
                  );
            }
         } else {
            return NOT_HANDLED;
         }
      }

      private Flux<?> valueToFlux(Object value, CacheAspectSupport.CacheOperationContexts contexts) {
         Object data = CacheAspectSupport.this.unwrapCacheValue(value);
         return !contexts.processed && data instanceof Iterable<?> iterable ? Flux.fromIterable(iterable) : (data != null ? Flux.just(data) : Flux.empty());
      }

      public @Nullable Object processPutRequest(CacheAspectSupport.CachePutRequest request, @Nullable Object result) {
         ReactiveAdapter adapter = result != null ? this.registry.getAdapter(result.getClass()) : null;
         if (adapter != null) {
            if (adapter.isMultiValue()) {
               Flux<?> source = Flux.from(adapter.toPublisher(result)).publish().refCount(2);
               source.subscribe(CacheAspectSupport.this.new CachePutListSubscriber(request));
               return adapter.fromPublisher(source);
            } else {
               return adapter.fromPublisher(Mono.from(adapter.toPublisher(result)).doOnSuccess(request::performCachePut));
            }
         } else {
            return NOT_HANDLED;
         }
      }
   }
}
