package org.springframework.context.event;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class ApplicationListenerMethodAdapter implements GenericApplicationListener {
   private static final boolean REACTIVE_STREAMS_PRESENT = ClassUtils.isPresent(
      "org.reactivestreams.Publisher", ApplicationListenerMethodAdapter.class.getClassLoader()
   );
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final String beanName;
   private final Method method;
   private final Method targetMethod;
   private final AnnotatedElementKey methodKey;
   private final List<ResolvableType> declaredEventTypes;
   private final @Nullable String condition;
   private final boolean defaultExecution;
   private final int order;
   private volatile @Nullable String listenerId;
   private @Nullable ApplicationContext applicationContext;
   private @Nullable EventExpressionEvaluator evaluator;

   public ApplicationListenerMethodAdapter(String beanName, Class<?> targetClass, Method method) {
      this.beanName = beanName;
      this.method = BridgeMethodResolver.findBridgedMethod(method);
      this.targetMethod = !Proxy.isProxyClass(targetClass) ? AopUtils.getMostSpecificMethod(method, targetClass) : this.method;
      this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);
      EventListener ann = (EventListener)AnnotatedElementUtils.findMergedAnnotation(this.targetMethod, EventListener.class);
      this.declaredEventTypes = resolveDeclaredEventTypes(method, ann);
      this.condition = ann != null ? ann.condition() : null;
      this.defaultExecution = ann == null || ann.defaultExecution();
      this.order = resolveOrder(this.targetMethod);
      String id = ann != null ? ann.id() : "";
      this.listenerId = !id.isEmpty() ? id : null;
   }

   private static List<ResolvableType> resolveDeclaredEventTypes(Method method, @Nullable EventListener ann) {
      int count = KotlinDetector.isSuspendingFunction(method) ? method.getParameterCount() - 1 : method.getParameterCount();
      if (count > 1) {
         throw new IllegalStateException("Maximum one parameter is allowed for event listener method: " + method);
      }

      if (ann != null) {
         Class<?>[] classes = ann.classes();
         if (classes.length > 0) {
            List<ResolvableType> types = new ArrayList<>(classes.length);

            for (Class<?> eventType : classes) {
               types.add(ResolvableType.forClass(eventType));
            }

            return types;
         }
      }

      if (count == 0) {
         throw new IllegalStateException("Event parameter is mandatory for event listener method: " + method);
      } else {
         return Collections.singletonList(ResolvableType.forMethodParameter(method, 0));
      }
   }

   private static int resolveOrder(Method method) {
      Order ann = (Order)AnnotatedElementUtils.findMergedAnnotation(method, Order.class);
      return ann != null ? ann.value() : Integer.MAX_VALUE;
   }

   public final Method getTargetMethod() {
      return this.targetMethod;
   }

   protected final @Nullable String getCondition() {
      return this.condition;
   }

   protected final boolean isDefaultExecution() {
      return this.defaultExecution;
   }

   void init(ApplicationContext applicationContext, @Nullable EventExpressionEvaluator evaluator) {
      this.applicationContext = applicationContext;
      this.evaluator = evaluator;
   }

   @Override
   public void onApplicationEvent(ApplicationEvent event) {
      if (this.defaultExecution) {
         this.processEvent(event);
      }
   }

   @Override
   public boolean supportsEventType(ResolvableType eventType) {
      for (ResolvableType declaredEventType : this.declaredEventTypes) {
         if (eventType.hasUnresolvableGenerics()
            ? declaredEventType.toClass().isAssignableFrom(eventType.toClass())
            : declaredEventType.isAssignableFrom(eventType)) {
            return true;
         }

         if (PayloadApplicationEvent.class.isAssignableFrom(eventType.toClass())) {
            ResolvableType payloadType = eventType.as(PayloadApplicationEvent.class).getGeneric(new int[0]);
            if (declaredEventType.isAssignableFrom(payloadType)) {
               return true;
            }

            if (payloadType.resolve() == null) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public boolean supportsSourceType(@Nullable Class<?> sourceType) {
      return true;
   }

   @Override
   public int getOrder() {
      return this.order;
   }

   @Override
   public String getListenerId() {
      String id = this.listenerId;
      if (id == null) {
         id = this.getDefaultListenerId();
         this.listenerId = id;
      }

      return id;
   }

   protected String getDefaultListenerId() {
      StringJoiner sj = new StringJoiner(",", "(", ")");

      for (Class<?> paramType : this.targetMethod.getParameterTypes()) {
         sj.add(paramType.getName());
      }

      return ClassUtils.getQualifiedMethodName(this.targetMethod) + sj;
   }

   public void processEvent(ApplicationEvent event) {
      Object[] args = this.resolveArguments(event);
      if (this.shouldHandle(event, args)) {
         Object result = this.doInvoke(args);
         if (result != null) {
            this.handleResult(result);
         } else {
            this.logger.trace("No result object given - no result to handle");
         }
      }
   }

   public boolean shouldHandle(ApplicationEvent event) {
      return this.shouldHandle(event, this.resolveArguments(event));
   }

   @Contract("_, null -> false")
   private boolean shouldHandle(ApplicationEvent event, @Nullable Object @Nullable [] args) {
      if (args == null) {
         return false;
      } else if (StringUtils.hasText(this.condition)) {
         Assert.notNull(this.evaluator, "EventExpressionEvaluator must not be null");
         return this.evaluator.condition(this.condition, event, this.targetMethod, this.methodKey, args);
      } else {
         return true;
      }
   }

   protected @Nullable Object @Nullable [] resolveArguments(ApplicationEvent event) {
      ResolvableType declaredEventType = this.getResolvableType(event);
      if (declaredEventType == null) {
         return null;
      }

      if (this.method.getParameterCount() == 0) {
         return new Object[0];
      }

      Class<?> declaredEventClass = declaredEventType.toClass();
      if (!ApplicationEvent.class.isAssignableFrom(declaredEventClass) && event instanceof PayloadApplicationEvent<?> payloadEvent) {
         Object payload = payloadEvent.getPayload();
         if (declaredEventClass.isInstance(payload)) {
            return new Object[]{payload};
         }
      }

      return new Object[]{event};
   }

   protected void handleResult(Object result) {
      if (REACTIVE_STREAMS_PRESENT && new ApplicationListenerMethodAdapter.ReactiveResultHandler().subscribeToPublisher(result)) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Adapted to reactive result: " + result);
         }
      } else if (result instanceof CompletionStage<?> completionStage) {
         completionStage.whenComplete((event, ex) -> {
            if (ex != null) {
               this.handleAsyncError(ex);
            } else if (event != null) {
               this.publishEvents(event);
            }
         });
      } else {
         this.publishEvents(result);
      }
   }

   private void publishEvents(@Nullable Object result) {
      if (result != null && result.getClass().isArray()) {
         Object[] events = ObjectUtils.toObjectArray(result);

         for (Object event : events) {
            this.publishEvent(event);
         }
      } else if (result instanceof Collection) {
         for (Object event : (Collection)result) {
            this.publishEvent(event);
         }
      } else {
         this.publishEvent(result);
      }
   }

   private void publishEvent(@Nullable Object event) {
      if (event != null) {
         Assert.notNull(this.applicationContext, "ApplicationContext must not be null");
         this.applicationContext.publishEvent(event);
      }
   }

   protected void handleAsyncError(Throwable t) {
      this.logger.error("Unexpected error occurred in asynchronous listener", t);
   }

   protected @Nullable Object doInvoke(@Nullable Object... args) {
      Object bean = this.getTargetBean();
      if (bean.equals(null)) {
         return null;
      }

      try {
         ReflectionUtils.makeAccessible(this.method);
         return KotlinDetector.isSuspendingFunction(this.method)
            ? CoroutinesUtils.invokeSuspendingFunction(this.method, bean, args)
            : this.method.invoke(bean, args);
      } catch (IllegalArgumentException ex) {
         this.assertTargetBean(this.method, bean, args);
         throw new IllegalStateException(this.getInvocationErrorMessage(bean, ex.getMessage(), args), ex);
      } catch (IllegalAccessException | InaccessibleObjectException ex) {
         throw new IllegalStateException(this.getInvocationErrorMessage(bean, ex.getMessage(), args), ex);
      } catch (InvocationTargetException ex) {
         Throwable targetException = ex.getTargetException();
         if (targetException instanceof RuntimeException runtimeException) {
            throw runtimeException;
         } else {
            String msg = this.getInvocationErrorMessage(bean, "Failed to invoke event listener method", args);
            throw new UndeclaredThrowableException(targetException, msg);
         }
      }
   }

   protected Object getTargetBean() {
      Assert.notNull(this.applicationContext, "ApplicationContext must not be null");
      return this.applicationContext.getBean(this.beanName);
   }

   protected String getDetailedErrorMessage(Object bean, @Nullable String message) {
      StringBuilder sb = StringUtils.hasLength(message) ? new StringBuilder(message).append('\n') : new StringBuilder();
      sb.append("ApplicationListenerMethodAdapter details: \n");
      sb.append("Bean [").append(bean.getClass().getName()).append("]\n");
      sb.append("Method [").append(this.method.toGenericString()).append("]\n");
      return sb.toString();
   }

   private void assertTargetBean(Method method, Object targetBean, @Nullable Object[] args) {
      Class<?> methodDeclaringClass = method.getDeclaringClass();
      Class<?> targetBeanClass = targetBean.getClass();
      if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
         String msg = "The event listener method class '"
            + methodDeclaringClass.getName()
            + "' is not an instance of the actual bean class '"
            + targetBeanClass.getName()
            + "'. If the bean requires proxying (for example, due to @Transactional), please use class-based proxying.";
         throw new IllegalStateException(this.getInvocationErrorMessage(targetBean, msg, args));
      }
   }

   private String getInvocationErrorMessage(Object bean, @Nullable String message, @Nullable Object[] resolvedArgs) {
      StringBuilder sb = new StringBuilder(this.getDetailedErrorMessage(bean, message));
      sb.append("Resolved arguments: \n");

      for (int i = 0; i < resolvedArgs.length; i++) {
         sb.append('[').append(i).append("] ");
         if (resolvedArgs[i] == null) {
            sb.append("[null] \n");
         } else {
            sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
            sb.append("[value=").append(resolvedArgs[i]).append("]\n");
         }
      }

      return sb.toString();
   }

   private @Nullable ResolvableType getResolvableType(ApplicationEvent event) {
      ResolvableType payloadType = null;
      if (event instanceof PayloadApplicationEvent<?> payloadEvent) {
         ResolvableType eventType = payloadEvent.getResolvableType();
         if (eventType != null) {
            payloadType = eventType.as(PayloadApplicationEvent.class).getGeneric(new int[0]);
         }
      }

      for (ResolvableType declaredEventType : this.declaredEventTypes) {
         Class<?> eventClass = declaredEventType.toClass();
         if (!ApplicationEvent.class.isAssignableFrom(eventClass) && payloadType != null && declaredEventType.isAssignableFrom(payloadType)) {
            return declaredEventType;
         }

         if (eventClass.isInstance(event)) {
            return declaredEventType;
         }
      }

      return null;
   }

   @Override
   public String toString() {
      return this.method.toGenericString();
   }

   private class EventPublicationSubscriber implements Subscriber<Object> {
      public void onSubscribe(Subscription s) {
         s.request(2147483647L);
      }

      public void onNext(Object o) {
         ApplicationListenerMethodAdapter.this.publishEvents(o);
      }

      public void onError(Throwable t) {
         ApplicationListenerMethodAdapter.this.handleAsyncError(t);
      }

      public void onComplete() {
      }
   }

   private class ReactiveResultHandler {
      public boolean subscribeToPublisher(Object result) {
         ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(result.getClass());
         if (adapter != null) {
            adapter.toPublisher(result).subscribe(ApplicationListenerMethodAdapter.this.new EventPublicationSubscriber());
            return true;
         } else {
            return false;
         }
      }
   }
}
