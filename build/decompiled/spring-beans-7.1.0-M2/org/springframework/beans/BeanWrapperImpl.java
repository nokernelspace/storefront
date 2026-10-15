package org.springframework.beans;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Objects;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class BeanWrapperImpl extends AbstractNestablePropertyAccessor implements BeanWrapper {
   private @Nullable CachedIntrospectionResults cachedIntrospectionResults;

   public BeanWrapperImpl() {
      this(true);
   }

   public BeanWrapperImpl(boolean registerDefaultEditors) {
      super(registerDefaultEditors);
   }

   public BeanWrapperImpl(Object object) {
      super(object);
   }

   public BeanWrapperImpl(Class<?> clazz) {
      super(clazz);
   }

   public BeanWrapperImpl(Object object, String nestedPath, Object rootObject) {
      super(object, nestedPath, rootObject);
   }

   private BeanWrapperImpl(Object object, String nestedPath, BeanWrapperImpl parent) {
      super(object, nestedPath, parent);
   }

   public void setBeanInstance(Object object) {
      this.wrappedObject = object;
      this.rootObject = object;
      this.typeConverterDelegate = new TypeConverterDelegate(this, this.wrappedObject);
      this.setIntrospectionClass(object.getClass());
   }

   @Override
   public void setWrappedInstance(Object object, @Nullable String nestedPath, @Nullable Object rootObject) {
      super.setWrappedInstance(object, nestedPath, rootObject);
      this.setIntrospectionClass(this.getWrappedClass());
   }

   protected void setIntrospectionClass(Class<?> clazz) {
      if (this.cachedIntrospectionResults != null && this.cachedIntrospectionResults.getBeanClass() != clazz) {
         this.cachedIntrospectionResults = null;
      }
   }

   private CachedIntrospectionResults getCachedIntrospectionResults() {
      if (this.cachedIntrospectionResults == null) {
         this.cachedIntrospectionResults = CachedIntrospectionResults.forClass(this.getWrappedClass());
      }

      return this.cachedIntrospectionResults;
   }

   public @Nullable Object convertForProperty(@Nullable Object value, String propertyName) throws TypeMismatchException {
      CachedIntrospectionResults cachedIntrospectionResults = this.getCachedIntrospectionResults();
      PropertyDescriptor pd = cachedIntrospectionResults.getPropertyDescriptor(propertyName);
      if (pd == null) {
         throw new InvalidPropertyException(this.getRootClass(), this.getNestedPath() + propertyName, "No property '" + propertyName + "' found");
      }

      TypeDescriptor td = ((GenericTypeAwarePropertyDescriptor)pd).getTypeDescriptor();
      return this.convertForProperty(propertyName, null, value, td);
   }

   @Override
   protected AbstractNestablePropertyAccessor.@Nullable PropertyHandler getLocalPropertyHandler(String propertyName) {
      PropertyDescriptor pd = this.getCachedIntrospectionResults().getPropertyDescriptor(propertyName);
      return pd != null ? new BeanWrapperImpl.BeanPropertyHandler((GenericTypeAwarePropertyDescriptor)pd) : null;
   }

   protected BeanWrapperImpl newNestedPropertyAccessor(Object object, String nestedPath) {
      return new BeanWrapperImpl(object, nestedPath, this);
   }

   @Override
   protected NotWritablePropertyException createNotWritablePropertyException(String propertyName) {
      PropertyMatches matches = PropertyMatches.forProperty(propertyName, this.getRootClass());
      throw new NotWritablePropertyException(
         this.getRootClass(), this.getNestedPath() + propertyName, matches.buildErrorMessage(), matches.getPossibleMatches()
      );
   }

   @Override
   public PropertyDescriptor[] getPropertyDescriptors() {
      return this.getCachedIntrospectionResults().getPropertyDescriptors();
   }

   @Override
   public PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException {
      AbstractNestablePropertyAccessor.ResolvedProperty resolved;
      try {
         resolved = this.resolvePropertyPath(propertyName);
      } catch (InvalidPropertyPathException ex) {
         throw new InvalidPropertyException(this.getRootClass(), this.getNestedPath() + propertyName, Objects.requireNonNull(ex.getMessage()), ex);
      }

      BeanWrapperImpl nestedBw = (BeanWrapperImpl)resolved.accessor();
      PropertyDescriptor pd = nestedBw.getCachedIntrospectionResults().getPropertyDescriptor(resolved.segment().toCanonicalName());
      if (pd == null) {
         throw new InvalidPropertyException(this.getRootClass(), this.getNestedPath() + propertyName, "No property '" + propertyName + "' found");
      } else {
         return pd;
      }
   }

   private class BeanPropertyHandler extends AbstractNestablePropertyAccessor.PropertyHandler {
      private final GenericTypeAwarePropertyDescriptor pd;

      public BeanPropertyHandler(GenericTypeAwarePropertyDescriptor pd) {
         super(pd.getPropertyType(), pd.getReadMethod() != null, pd.getWriteMethod() != null);
         this.pd = pd;
      }

      @Override
      public TypeDescriptor toTypeDescriptor() {
         return this.pd.getTypeDescriptor();
      }

      @Override
      public ResolvableType getResolvableType() {
         return this.pd.getReadMethodType();
      }

      @Override
      public TypeDescriptor getMapValueType(int nestingLevel) {
         return new TypeDescriptor(
            this.pd.getReadMethodType().getNested(nestingLevel).asMap().getGeneric(new int[]{1}), null, this.pd.getTypeDescriptor().getAnnotations()
         );
      }

      @Override
      public TypeDescriptor getCollectionType(int nestingLevel) {
         return new TypeDescriptor(
            this.pd.getReadMethodType().getNested(nestingLevel).asCollection().getGeneric(new int[0]), null, this.pd.getTypeDescriptor().getAnnotations()
         );
      }

      @Override
      public @Nullable TypeDescriptor nested(int level) {
         return this.pd.getTypeDescriptor().nested(level);
      }

      @Override
      public @Nullable Object getValue() throws Exception {
         Method readMethod = this.pd.getReadMethod();
         Assert.state(readMethod != null, "No read method available");
         ReflectionUtils.makeAccessible(readMethod);
         return readMethod.invoke(BeanWrapperImpl.this.getWrappedInstance(), (Object[])null);
      }

      @Override
      public void setValue(@Nullable Object value) throws Exception {
         Method writeMethod = this.pd.getWriteMethodForActualAccess();
         ReflectionUtils.makeAccessible(writeMethod);
         writeMethod.invoke(BeanWrapperImpl.this.getWrappedInstance(), value);
      }

      @Override
      public boolean setValueFallbackIfPossible(@Nullable Object value) {
         try {
            Method writeMethod = this.pd.getWriteMethodFallback(value != null ? value.getClass() : null);
            if (writeMethod == null) {
               writeMethod = this.pd.getUniqueWriteMethodFallback();
               if (writeMethod != null) {
                  value = BeanWrapperImpl.this.convertForProperty(this.pd.getName(), null, value, new TypeDescriptor(new MethodParameter(writeMethod, 0)));
               }
            }

            if (writeMethod != null) {
               ReflectionUtils.makeAccessible(writeMethod);
               writeMethod.invoke(BeanWrapperImpl.this.getWrappedInstance(), value);
               return true;
            }
         } catch (Exception ex) {
            LogFactory.getLog(BeanWrapperImpl.BeanPropertyHandler.class).debug("Write method fallback failed", ex);
         }

         return false;
      }
   }
}
