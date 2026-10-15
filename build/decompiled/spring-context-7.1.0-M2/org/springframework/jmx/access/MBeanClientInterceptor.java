package org.springframework.jmx.access;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXServiceURL;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.jmx.support.JmxUtils;
import org.springframework.jmx.support.ObjectNameManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class MBeanClientInterceptor implements MethodInterceptor, BeanClassLoaderAware, InitializingBean, DisposableBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable MBeanServerConnection server;
   private @Nullable JMXServiceURL serviceUrl;
   private @Nullable Map<String, ?> environment;
   private @Nullable String agentId;
   private boolean connectOnStartup = true;
   private boolean refreshOnConnectFailure = false;
   private @Nullable ObjectName objectName;
   private boolean useStrictCasing = true;
   private @Nullable Class<?> managementInterface;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private final ConnectorDelegate connector = new ConnectorDelegate();
   private @Nullable MBeanServerConnection serverToUse;
   private @Nullable MBeanServerInvocationHandler invocationHandler;
   private Map<String, MBeanAttributeInfo> allowedAttributes = Collections.emptyMap();
   private Map<MBeanClientInterceptor.MethodCacheKey, MBeanOperationInfo> allowedOperations = Collections.emptyMap();
   private final Map<Method, String[]> signatureCache = new HashMap<>();
   private final Object preparationMonitor = new Object();

   public void setServer(MBeanServerConnection server) {
      this.server = server;
   }

   public void setServiceUrl(String url) throws MalformedURLException {
      this.serviceUrl = new JMXServiceURL(url);
   }

   public void setEnvironment(@Nullable Map<String, ?> environment) {
      this.environment = environment;
   }

   public @Nullable Map<String, ?> getEnvironment() {
      return this.environment;
   }

   public void setAgentId(String agentId) {
      this.agentId = agentId;
   }

   public void setConnectOnStartup(boolean connectOnStartup) {
      this.connectOnStartup = connectOnStartup;
   }

   public void setRefreshOnConnectFailure(boolean refreshOnConnectFailure) {
      this.refreshOnConnectFailure = refreshOnConnectFailure;
   }

   public void setObjectName(Object objectName) throws MalformedObjectNameException {
      this.objectName = ObjectNameManager.getInstance(objectName);
   }

   public void setUseStrictCasing(boolean useStrictCasing) {
      this.useStrictCasing = useStrictCasing;
   }

   public void setManagementInterface(@Nullable Class<?> managementInterface) {
      this.managementInterface = managementInterface;
   }

   protected final @Nullable Class<?> getManagementInterface() {
      return this.managementInterface;
   }

   public void setBeanClassLoader(ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   public void afterPropertiesSet() {
      if (this.server != null && this.refreshOnConnectFailure) {
         throw new IllegalArgumentException("'refreshOnConnectFailure' does not work when setting a 'server' reference. Prefer 'serviceUrl' etc instead.");
      }

      if (this.connectOnStartup) {
         this.prepare();
      }
   }

   public void prepare() {
      synchronized (this.preparationMonitor) {
         if (this.server != null) {
            this.serverToUse = this.server;
         } else {
            this.serverToUse = null;
            this.serverToUse = this.connector.connect(this.serviceUrl, this.environment, this.agentId);
         }

         this.invocationHandler = null;
         if (this.useStrictCasing) {
            Assert.state(this.objectName != null, "No ObjectName set");
            this.invocationHandler = new MBeanServerInvocationHandler(
               this.serverToUse, this.objectName, this.managementInterface != null && JMX.isMXBeanInterface(this.managementInterface)
            );
         } else {
            this.retrieveMBeanInfo(this.serverToUse);
         }
      }
   }

   private void retrieveMBeanInfo(MBeanServerConnection server) throws MBeanInfoRetrievalException {
      try {
         MBeanInfo info = server.getMBeanInfo(this.objectName);
         MBeanAttributeInfo[] attributeInfo = info.getAttributes();
         this.allowedAttributes = CollectionUtils.newHashMap(attributeInfo.length);

         for (MBeanAttributeInfo infoEle : attributeInfo) {
            this.allowedAttributes.put(infoEle.getName(), infoEle);
         }

         MBeanOperationInfo[] operationInfo = info.getOperations();
         this.allowedOperations = CollectionUtils.newHashMap(operationInfo.length);

         for (MBeanOperationInfo infoEle : operationInfo) {
            Class<?>[] paramTypes = JmxUtils.parameterInfoToTypes(infoEle.getSignature(), this.beanClassLoader);
            this.allowedOperations.put(new MBeanClientInterceptor.MethodCacheKey(infoEle.getName(), paramTypes), infoEle);
         }
      } catch (ClassNotFoundException ex) {
         throw new MBeanInfoRetrievalException("Unable to locate class specified in method signature", ex);
      } catch (IntrospectionException ex) {
         throw new MBeanInfoRetrievalException("Unable to obtain MBean info for bean [" + this.objectName + "]", ex);
      } catch (InstanceNotFoundException ex) {
         throw new MBeanInfoRetrievalException(
            "Unable to obtain MBean info for bean [" + this.objectName + "]: it is likely that this bean was unregistered during the proxy creation process",
            ex
         );
      } catch (ReflectionException ex) {
         throw new MBeanInfoRetrievalException("Unable to read MBean info for bean [ " + this.objectName + "]", ex);
      } catch (IOException ex) {
         throw new MBeanInfoRetrievalException(
            "An IOException occurred when communicating with the MBeanServer. It is likely that you are communicating with a remote MBeanServer. Check the inner exception for exact details.",
            ex
         );
      }
   }

   protected boolean isPrepared() {
      synchronized (this.preparationMonitor) {
         return this.serverToUse != null;
      }
   }

   public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
      synchronized (this.preparationMonitor) {
         if (!this.isPrepared()) {
            this.prepare();
         }
      }

      try {
         return this.doInvoke(invocation);
      } catch (MBeanConnectFailureException | IOException ex) {
         return this.handleConnectFailure(invocation, ex);
      }
   }

   protected @Nullable Object handleConnectFailure(MethodInvocation invocation, Exception ex) throws Throwable {
      if (this.refreshOnConnectFailure) {
         String msg = "Could not connect to JMX server - retrying";
         if (this.logger.isDebugEnabled()) {
            this.logger.warn(msg, ex);
         } else if (this.logger.isWarnEnabled()) {
            this.logger.warn(msg);
         }

         this.prepare();
         return this.doInvoke(invocation);
      } else {
         throw ex;
      }
   }

   protected @Nullable Object doInvoke(MethodInvocation invocation) throws Throwable {
      Method method = invocation.getMethod();

      try {
         Object result;
         if (this.invocationHandler != null) {
            result = this.invocationHandler.invoke(invocation.getThis(), method, invocation.getArguments());
         } else {
            PropertyDescriptor pd = BeanUtils.findPropertyForMethod(method);
            if (pd != null) {
               result = this.invokeAttribute(pd, invocation);
            } else {
               result = this.invokeOperation(method, invocation.getArguments());
            }
         }

         return this.convertResultValueIfNecessary(result, new MethodParameter(method, -1));
      } catch (MBeanException ex) {
         throw ex.getTargetException();
      } catch (RuntimeMBeanException ex) {
         throw ex.getTargetException();
      } catch (RuntimeErrorException ex) {
         throw ex.getTargetError();
      } catch (RuntimeOperationsException ex) {
         RuntimeException rex = ex.getTargetException();
         if (rex instanceof RuntimeMBeanException runtimeMBeanException) {
            throw runtimeMBeanException.getTargetException();
         } else if (rex instanceof RuntimeErrorException runtimeErrorException) {
            throw runtimeErrorException.getTargetError();
         } else {
            throw rex;
         }
      } catch (OperationsException ex) {
         if (ReflectionUtils.declaresException(method, ex.getClass())) {
            throw ex;
         } else {
            throw new InvalidInvocationException(ex.getMessage());
         }
      } catch (JMException ex) {
         if (ReflectionUtils.declaresException(method, ex.getClass())) {
            throw ex;
         } else {
            throw new InvocationFailureException("JMX access failed", ex);
         }
      } catch (IOException ex) {
         if (ReflectionUtils.declaresException(method, ex.getClass())) {
            throw ex;
         } else {
            throw new MBeanConnectFailureException("I/O failure during JMX access", ex);
         }
      }
   }

   private @Nullable Object invokeAttribute(PropertyDescriptor pd, MethodInvocation invocation) throws JMException, IOException {
      Assert.state(this.serverToUse != null, "No MBeanServerConnection available");
      String attributeName = JmxUtils.getAttributeName(pd, this.useStrictCasing);
      MBeanAttributeInfo inf = this.allowedAttributes.get(attributeName);
      if (inf == null) {
         throw new InvalidInvocationException("Attribute '" + pd.getName() + "' is not exposed on the management interface");
      }

      if (invocation.getMethod().equals(pd.getReadMethod())) {
         if (inf.isReadable()) {
            return this.serverToUse.getAttribute(this.objectName, attributeName);
         } else {
            throw new InvalidInvocationException("Attribute '" + attributeName + "' is not readable");
         }
      } else if (invocation.getMethod().equals(pd.getWriteMethod())) {
         if (inf.isWritable()) {
            this.serverToUse.setAttribute(this.objectName, new Attribute(attributeName, invocation.getArguments()[0]));
            return null;
         } else {
            throw new InvalidInvocationException("Attribute '" + attributeName + "' is not writable");
         }
      } else {
         throw new IllegalStateException("Method [" + invocation.getMethod() + "] is neither a bean property getter nor a setter");
      }
   }

   private Object invokeOperation(Method method, @Nullable Object[] args) throws JMException, IOException {
      Assert.state(this.serverToUse != null, "No MBeanServerConnection available");
      MBeanClientInterceptor.MethodCacheKey key = new MBeanClientInterceptor.MethodCacheKey(method.getName(), method.getParameterTypes());
      MBeanOperationInfo info = this.allowedOperations.get(key);
      if (info == null) {
         throw new InvalidInvocationException("Operation '" + method.getName() + "' is not exposed on the management interface");
      }

      String[] signature;
      synchronized (this.signatureCache) {
         signature = this.signatureCache.get(method);
         if (signature == null) {
            signature = JmxUtils.getMethodSignature(method);
            this.signatureCache.put(method, signature);
         }
      }

      return this.serverToUse.invoke(this.objectName, method.getName(), args, signature);
   }

   protected @Nullable Object convertResultValueIfNecessary(@Nullable Object result, MethodParameter parameter) {
      Class<?> targetClass = parameter.getParameterType();

      try {
         if (result == null) {
            return null;
         }

         if (ClassUtils.isAssignableValue(targetClass, result)) {
            return result;
         }

         if (result instanceof CompositeData) {
            Method fromMethod = targetClass.getMethod("from", CompositeData.class);
            return ReflectionUtils.invokeMethod(fromMethod, null, new Object[]{result});
         }

         if (result instanceof CompositeData[] array) {
            if (targetClass.isArray()) {
               return this.convertDataArrayToTargetArray(array, targetClass);
            }

            if (Collection.class.isAssignableFrom(targetClass)) {
               Class<?> elementType = ResolvableType.forMethodParameter(parameter).asCollection().resolveGeneric(new int[0]);
               if (elementType != null) {
                  return this.convertDataArrayToTargetCollection(array, targetClass, elementType);
               }
            }
         } else {
            if (result instanceof TabularData) {
               Method fromMethod = targetClass.getMethod("from", TabularData.class);
               return ReflectionUtils.invokeMethod(fromMethod, null, new Object[]{result});
            }

            if (result instanceof TabularData[] array) {
               if (targetClass.isArray()) {
                  return this.convertDataArrayToTargetArray(array, targetClass);
               }

               if (Collection.class.isAssignableFrom(targetClass)) {
                  Class<?> elementType = ResolvableType.forMethodParameter(parameter).asCollection().resolveGeneric(new int[0]);
                  if (elementType != null) {
                     return this.convertDataArrayToTargetCollection(array, targetClass, elementType);
                  }
               }
            }
         }

         throw new InvocationFailureException("Incompatible result value [" + result + "] for target type [" + targetClass.getName() + "]");
      } catch (NoSuchMethodException ex) {
         throw new InvocationFailureException(
            "Could not obtain 'from(CompositeData)' / 'from(TabularData)' method on target type ["
               + targetClass.getName()
               + "] for conversion of MXBean data structure ["
               + result
               + "]"
         );
      }
   }

   private Object convertDataArrayToTargetArray(Object[] array, Class<?> targetClass) throws NoSuchMethodException {
      Class<?> targetType = targetClass.componentType();
      Method fromMethod = targetType.getMethod("from", array.getClass().componentType());
      Object resultArray = Array.newInstance(targetType, array.length);

      for (int i = 0; i < array.length; i++) {
         Array.set(resultArray, i, ReflectionUtils.invokeMethod(fromMethod, null, new Object[]{array[i]}));
      }

      return resultArray;
   }

   private Collection<?> convertDataArrayToTargetCollection(Object[] array, Class<?> collectionType, Class<?> elementType) throws NoSuchMethodException {
      Method fromMethod = elementType.getMethod("from", array.getClass().componentType());
      Collection<Object> resultColl = CollectionFactory.createCollection(collectionType, Array.getLength(array));

      for (Object element : array) {
         resultColl.add(ReflectionUtils.invokeMethod(fromMethod, null, new Object[]{element}));
      }

      return resultColl;
   }

   public void destroy() {
      this.connector.close();
   }

   private static final class MethodCacheKey implements Comparable<MBeanClientInterceptor.MethodCacheKey> {
      private final String name;
      private final Class<?>[] parameterTypes;

      public MethodCacheKey(String name, Class<?> @Nullable [] parameterTypes) {
         this.name = name;
         this.parameterTypes = parameterTypes != null ? parameterTypes : new Class[0];
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof MBeanClientInterceptor.MethodCacheKey that
               && this.name.equals(that.name)
               && Arrays.equals(this.parameterTypes, that.parameterTypes);
      }

      @Override
      public int hashCode() {
         return this.name.hashCode();
      }

      @Override
      public String toString() {
         return this.name + "(" + StringUtils.arrayToCommaDelimitedString(this.parameterTypes) + ")";
      }

      public int compareTo(MBeanClientInterceptor.MethodCacheKey other) {
         int result = this.name.compareTo(other.name);
         if (result != 0) {
            return result;
         }

         if (this.parameterTypes.length < other.parameterTypes.length) {
            return -1;
         }

         if (this.parameterTypes.length > other.parameterTypes.length) {
            return 1;
         }

         for (int i = 0; i < this.parameterTypes.length; i++) {
            result = this.parameterTypes[i].getName().compareTo(other.parameterTypes[i].getName());
            if (result != 0) {
               return result;
            }
         }

         return 0;
      }
   }
}
