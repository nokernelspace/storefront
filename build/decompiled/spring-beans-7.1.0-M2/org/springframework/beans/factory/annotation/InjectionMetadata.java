package org.springframework.beans.factory.annotation;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lang.Contract;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

public class InjectionMetadata {
   public static final InjectionMetadata EMPTY = new InjectionMetadata(Object.class, Collections.emptyList()) {
      @Override
      protected boolean needsRefresh(Class<?> clazz) {
         return false;
      }

      @Override
      public void checkConfigMembers(RootBeanDefinition beanDefinition) {
      }

      @Override
      public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) {
      }

      @Override
      public void clear(@Nullable PropertyValues pvs) {
      }
   };
   private final Class<?> targetClass;
   private final Collection<InjectionMetadata.InjectedElement> injectedElements;
   private volatile @Nullable Set<InjectionMetadata.InjectedElement> checkedElements;

   public InjectionMetadata(Class<?> targetClass, Collection<InjectionMetadata.InjectedElement> elements) {
      this.targetClass = targetClass;
      this.injectedElements = elements;
   }

   public Collection<InjectionMetadata.InjectedElement> getInjectedElements() {
      return Collections.unmodifiableCollection(this.injectedElements);
   }

   public Collection<InjectionMetadata.InjectedElement> getInjectedElements(@Nullable PropertyValues pvs) {
      return this.injectedElements.stream().filter(candidate -> candidate.shouldInject(pvs)).toList();
   }

   protected boolean needsRefresh(Class<?> clazz) {
      return this.targetClass != clazz;
   }

   public void checkConfigMembers(RootBeanDefinition beanDefinition) {
      if (this.injectedElements.isEmpty()) {
         this.checkedElements = Collections.emptySet();
      } else {
         Set<InjectionMetadata.InjectedElement> checkedElements = CollectionUtils.newLinkedHashSet(this.injectedElements.size());

         for (InjectionMetadata.InjectedElement element : this.injectedElements) {
            Member member = element.getMember();
            if (!beanDefinition.isExternallyManagedConfigMember(member)) {
               beanDefinition.registerExternallyManagedConfigMember(member);
               checkedElements.add(element);
            }
         }

         this.checkedElements = checkedElements;
      }
   }

   public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
      Collection<InjectionMetadata.InjectedElement> checkedElements = this.checkedElements;
      Collection<InjectionMetadata.InjectedElement> elementsToIterate = checkedElements != null ? checkedElements : this.injectedElements;
      if (!elementsToIterate.isEmpty()) {
         for (InjectionMetadata.InjectedElement element : elementsToIterate) {
            element.inject(target, beanName, pvs);
         }
      }
   }

   public void clear(@Nullable PropertyValues pvs) {
      Collection<InjectionMetadata.InjectedElement> checkedElements = this.checkedElements;
      Collection<InjectionMetadata.InjectedElement> elementsToIterate = checkedElements != null ? checkedElements : this.injectedElements;
      if (!elementsToIterate.isEmpty()) {
         for (InjectionMetadata.InjectedElement element : elementsToIterate) {
            element.clearPropertySkipping(pvs);
         }
      }
   }

   public static InjectionMetadata forElements(Collection<InjectionMetadata.InjectedElement> elements, Class<?> clazz) {
      return elements.isEmpty() ? new InjectionMetadata(clazz, Collections.emptyList()) : new InjectionMetadata(clazz, elements);
   }

   @Contract("null, _ -> true")
   public static boolean needsRefresh(@Nullable InjectionMetadata metadata, Class<?> clazz) {
      return metadata == null || metadata.needsRefresh(clazz);
   }

   public abstract static class InjectedElement {
      protected final Member member;
      protected final boolean isField;
      protected final @Nullable PropertyDescriptor pd;
      protected volatile @Nullable Boolean skip;

      protected InjectedElement(Member member, @Nullable PropertyDescriptor pd) {
         this.member = member;
         this.isField = member instanceof Field;
         this.pd = pd;
      }

      public final Member getMember() {
         return this.member;
      }

      protected final Class<?> getResourceType() {
         if (this.isField) {
            return ((Field)this.member).getType();
         } else {
            return this.pd != null ? this.pd.getPropertyType() : ((Method)this.member).getParameterTypes()[0];
         }
      }

      protected final void checkResourceType(Class<?> resourceType) {
         if (this.isField) {
            Class<?> fieldType = ((Field)this.member).getType();
            if (!resourceType.isAssignableFrom(fieldType) && !fieldType.isAssignableFrom(resourceType)) {
               throw new IllegalStateException("Specified field type [" + fieldType + "] is incompatible with resource type [" + resourceType.getName() + "]");
            }
         } else {
            Class<?> paramType = this.pd != null ? this.pd.getPropertyType() : ((Method)this.member).getParameterTypes()[0];
            if (!resourceType.isAssignableFrom(paramType) && !paramType.isAssignableFrom(resourceType)) {
               throw new IllegalStateException(
                  "Specified parameter type [" + paramType + "] is incompatible with resource type [" + resourceType.getName() + "]"
               );
            }
         }
      }

      protected boolean shouldInject(@Nullable PropertyValues pvs) {
         return this.isField ? true : !this.checkPropertySkipping(pvs);
      }

      protected void inject(Object target, @Nullable String requestingBeanName, @Nullable PropertyValues pvs) throws Throwable {
         if (this.shouldInject(pvs)) {
            if (this.isField) {
               Field field = (Field)this.member;
               ReflectionUtils.makeAccessible(field);
               field.set(target, this.getResourceToInject(target, requestingBeanName));
            } else {
               try {
                  Method method = (Method)this.member;
                  ReflectionUtils.makeAccessible(method);
                  method.invoke(target, this.getResourceToInject(target, requestingBeanName));
               } catch (InvocationTargetException ex) {
                  throw ex.getTargetException();
               }
            }
         }
      }

      protected boolean checkPropertySkipping(@Nullable PropertyValues pvs) {
         Boolean skip = this.skip;
         if (skip != null) {
            return skip;
         }

         if (pvs == null) {
            this.skip = false;
            return false;
         }

         synchronized (pvs) {
            skip = this.skip;
            if (skip != null) {
               return skip;
            }

            if (this.pd != null) {
               if (pvs.contains(this.pd.getName())) {
                  this.skip = true;
                  return true;
               }

               if (pvs instanceof MutablePropertyValues mpvs) {
                  mpvs.registerProcessedProperty(this.pd.getName());
               }
            }

            this.skip = false;
            return false;
         }
      }

      protected void clearPropertySkipping(@Nullable PropertyValues pvs) {
         if (pvs != null) {
            synchronized (pvs) {
               if (Boolean.FALSE.equals(this.skip) && this.pd != null && pvs instanceof MutablePropertyValues mpvs) {
                  mpvs.clearProcessedProperty(this.pd.getName());
               }
            }
         }
      }

      protected @Nullable Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
         return null;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof InjectionMetadata.InjectedElement that && this.member.equals(that.member);
      }

      @Override
      public int hashCode() {
         return this.member.getClass().hashCode() * 29 + this.member.getName().hashCode();
      }

      @Override
      public String toString() {
         return this.getClass().getSimpleName() + " for " + this.member;
      }
   }
}
