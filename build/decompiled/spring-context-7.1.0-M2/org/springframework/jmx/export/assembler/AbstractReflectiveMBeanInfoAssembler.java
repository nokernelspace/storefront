package org.springframework.jmx.export.assembler;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.management.Descriptor;
import javax.management.JMException;
import javax.management.MBeanParameterInfo;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.jmx.support.JmxUtils;

public abstract class AbstractReflectiveMBeanInfoAssembler extends AbstractMBeanInfoAssembler {
   protected static final String FIELD_GET_METHOD = "getMethod";
   protected static final String FIELD_SET_METHOD = "setMethod";
   protected static final String FIELD_ROLE = "role";
   protected static final String ROLE_GETTER = "getter";
   protected static final String ROLE_SETTER = "setter";
   protected static final String ROLE_OPERATION = "operation";
   protected static final String FIELD_VISIBILITY = "visibility";
   protected static final int ATTRIBUTE_OPERATION_VISIBILITY = 4;
   protected static final String FIELD_CLASS = "class";
   protected static final String FIELD_LOG = "log";
   protected static final String FIELD_LOG_FILE = "logFile";
   protected static final String FIELD_CURRENCY_TIME_LIMIT = "currencyTimeLimit";
   protected static final String FIELD_DEFAULT = "default";
   protected static final String FIELD_PERSIST_POLICY = "persistPolicy";
   protected static final String FIELD_PERSIST_PERIOD = "persistPeriod";
   protected static final String FIELD_PERSIST_LOCATION = "persistLocation";
   protected static final String FIELD_PERSIST_NAME = "persistName";
   protected static final String FIELD_DISPLAY_NAME = "displayName";
   protected static final String FIELD_UNITS = "units";
   protected static final String FIELD_METRIC_TYPE = "metricType";
   protected static final String FIELD_METRIC_CATEGORY = "metricCategory";
   private @Nullable Integer defaultCurrencyTimeLimit;
   private boolean useStrictCasing = true;
   private boolean exposeClassDescriptor = false;
   private @Nullable ParameterNameDiscoverer parameterNameDiscoverer = DefaultParameterNameDiscoverer.getSharedInstance();

   public void setDefaultCurrencyTimeLimit(@Nullable Integer defaultCurrencyTimeLimit) {
      this.defaultCurrencyTimeLimit = defaultCurrencyTimeLimit;
   }

   protected @Nullable Integer getDefaultCurrencyTimeLimit() {
      return this.defaultCurrencyTimeLimit;
   }

   public void setUseStrictCasing(boolean useStrictCasing) {
      this.useStrictCasing = useStrictCasing;
   }

   protected boolean isUseStrictCasing() {
      return this.useStrictCasing;
   }

   public void setExposeClassDescriptor(boolean exposeClassDescriptor) {
      this.exposeClassDescriptor = exposeClassDescriptor;
   }

   protected boolean isExposeClassDescriptor() {
      return this.exposeClassDescriptor;
   }

   public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   protected @Nullable ParameterNameDiscoverer getParameterNameDiscoverer() {
      return this.parameterNameDiscoverer;
   }

   @Override
   protected ModelMBeanAttributeInfo[] getAttributeInfo(Object managedBean, String beanKey) throws JMException {
      PropertyDescriptor[] props = BeanUtils.getPropertyDescriptors(this.getClassToExpose(managedBean));
      List<ModelMBeanAttributeInfo> infos = new ArrayList<>();

      for (PropertyDescriptor prop : props) {
         Method getter = prop.getReadMethod();
         if (getter == null || getter.getDeclaringClass() != Object.class) {
            if (getter != null && !this.includeReadAttribute(getter, beanKey)) {
               getter = null;
            }

            Method setter = prop.getWriteMethod();
            if (setter != null && !this.includeWriteAttribute(setter, beanKey)) {
               setter = null;
            }

            if (getter != null || setter != null) {
               String attrName = JmxUtils.getAttributeName(prop, this.isUseStrictCasing());
               String description = this.getAttributeDescription(prop, beanKey);
               ModelMBeanAttributeInfo info = new ModelMBeanAttributeInfo(attrName, description, getter, setter);
               Descriptor desc = info.getDescriptor();
               if (getter != null) {
                  desc.setField("getMethod", getter.getName());
               }

               if (setter != null) {
                  desc.setField("setMethod", setter.getName());
               }

               this.populateAttributeDescriptor(desc, getter, setter, beanKey);
               info.setDescriptor(desc);
               infos.add(info);
            }
         }
      }

      return infos.toArray(new ModelMBeanAttributeInfo[0]);
   }

   @Override
   protected ModelMBeanOperationInfo[] getOperationInfo(Object managedBean, String beanKey) {
      Method[] methods = this.getClassToExpose(managedBean).getMethods();
      List<ModelMBeanOperationInfo> infos = new ArrayList<>();

      for (Method method : methods) {
         if (!method.isSynthetic() && Object.class != method.getDeclaringClass()) {
            ModelMBeanOperationInfo info = null;
            PropertyDescriptor pd = BeanUtils.findPropertyForMethod(method);
            if (pd != null
               && (
                  method.equals(pd.getReadMethod()) && this.includeReadAttribute(method, beanKey)
                     || method.equals(pd.getWriteMethod()) && this.includeWriteAttribute(method, beanKey)
               )) {
               info = this.createModelMBeanOperationInfo(method, pd.getName(), beanKey);
               Descriptor desc = info.getDescriptor();
               if (method.equals(pd.getReadMethod())) {
                  desc.setField("role", "getter");
               } else {
                  desc.setField("role", "setter");
               }

               desc.setField("visibility", 4);
               if (this.isExposeClassDescriptor()) {
                  desc.setField("class", this.getClassForDescriptor(managedBean).getName());
               }

               info.setDescriptor(desc);
            }

            if (info == null && this.includeOperation(method, beanKey)) {
               info = this.createModelMBeanOperationInfo(method, method.getName(), beanKey);
               Descriptor desc = info.getDescriptor();
               desc.setField("role", "operation");
               if (this.isExposeClassDescriptor()) {
                  desc.setField("class", this.getClassForDescriptor(managedBean).getName());
               }

               this.populateOperationDescriptor(desc, method, beanKey);
               info.setDescriptor(desc);
            }

            if (info != null) {
               infos.add(info);
            }
         }
      }

      return infos.toArray(new ModelMBeanOperationInfo[0]);
   }

   protected ModelMBeanOperationInfo createModelMBeanOperationInfo(Method method, String name, String beanKey) {
      MBeanParameterInfo[] params = this.getOperationParameters(method, beanKey);
      return params.length == 0
         ? new ModelMBeanOperationInfo(this.getOperationDescription(method, beanKey), method)
         : new ModelMBeanOperationInfo(
            method.getName(), this.getOperationDescription(method, beanKey), this.getOperationParameters(method, beanKey), method.getReturnType().getName(), 3
         );
   }

   protected Class<?> getClassForDescriptor(Object managedBean) {
      return AopUtils.isJdkDynamicProxy(managedBean) ? AopProxyUtils.proxiedUserInterfaces(managedBean)[0] : this.getClassToExpose(managedBean);
   }

   protected abstract boolean includeReadAttribute(Method method, String beanKey);

   protected abstract boolean includeWriteAttribute(Method method, String beanKey);

   protected abstract boolean includeOperation(Method method, String beanKey);

   protected String getAttributeDescription(PropertyDescriptor propertyDescriptor, String beanKey) {
      return propertyDescriptor.getDisplayName();
   }

   protected String getOperationDescription(Method method, String beanKey) {
      return method.getName();
   }

   protected MBeanParameterInfo[] getOperationParameters(Method method, String beanKey) {
      ParameterNameDiscoverer pnd = this.getParameterNameDiscoverer();
      String[] paramNames = pnd != null ? pnd.getParameterNames(method) : null;
      if (paramNames == null) {
         return new MBeanParameterInfo[0];
      }

      MBeanParameterInfo[] info = new MBeanParameterInfo[paramNames.length];
      Class<?>[] typeParameters = method.getParameterTypes();

      for (int i = 0; i < info.length; i++) {
         info[i] = new MBeanParameterInfo(paramNames[i], typeParameters[i].getName(), paramNames[i]);
      }

      return info;
   }

   @Override
   protected void populateMBeanDescriptor(Descriptor descriptor, Object managedBean, String beanKey) {
      this.applyDefaultCurrencyTimeLimit(descriptor);
   }

   protected void populateAttributeDescriptor(Descriptor desc, @Nullable Method getter, @Nullable Method setter, String beanKey) {
      this.applyDefaultCurrencyTimeLimit(desc);
   }

   protected void populateOperationDescriptor(Descriptor desc, Method method, String beanKey) {
      this.applyDefaultCurrencyTimeLimit(desc);
   }

   protected final void applyDefaultCurrencyTimeLimit(Descriptor desc) {
      if (this.getDefaultCurrencyTimeLimit() != null) {
         desc.setField("currencyTimeLimit", this.getDefaultCurrencyTimeLimit().toString());
      }
   }

   protected void applyCurrencyTimeLimit(Descriptor desc, int currencyTimeLimit) {
      if (currencyTimeLimit > 0) {
         desc.setField("currencyTimeLimit", Integer.toString(currencyTimeLimit));
      } else if (currencyTimeLimit == 0) {
         desc.setField("currencyTimeLimit", Integer.toString(Integer.MAX_VALUE));
      } else {
         this.applyDefaultCurrencyTimeLimit(desc);
      }
   }
}
