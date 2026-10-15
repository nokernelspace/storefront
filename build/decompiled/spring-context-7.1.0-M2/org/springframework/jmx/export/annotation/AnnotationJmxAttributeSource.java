package org.springframework.jmx.export.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.jmx.export.metadata.InvalidMetadataException;
import org.springframework.jmx.export.metadata.JmxAttributeSource;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public class AnnotationJmxAttributeSource implements JmxAttributeSource, BeanFactoryAware {
   private @Nullable StringValueResolver embeddedValueResolver;

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableBeanFactory cbf) {
         this.embeddedValueResolver = new EmbeddedValueResolver(cbf);
      }
   }

   @Override
   public @Nullable ManagedResource getManagedResource(Class<?> beanClass) throws InvalidMetadataException {
      MergedAnnotation<ManagedResource> ann = MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY)
         .get(ManagedResource.class)
         .withNonMergedAttributes();
      if (!ann.isPresent()) {
         return null;
      }

      Class<?> declaringClass = (Class<?>)ann.getSource();
      Class<?> target = declaringClass != null && !declaringClass.isInterface() ? declaringClass : beanClass;
      if (!Modifier.isPublic(target.getModifiers())) {
         throw new InvalidMetadataException("@ManagedResource class '" + target.getName() + "' must be public");
      }

      org.springframework.jmx.export.metadata.ManagedResource bean = new org.springframework.jmx.export.metadata.ManagedResource();
      Map<String, Object> map = ann.asMap(new Adapt[0]);
      List<PropertyValue> list = new ArrayList<>(map.size());
      map.forEach((attrName, attrValue) -> {
         if (!"value".equals(attrName)) {
            Object value = attrValue;
            if (this.embeddedValueResolver != null && value instanceof String text) {
               value = this.embeddedValueResolver.resolveStringValue(text);
            }

            list.add(new PropertyValue(attrName, value));
         }
      });
      PropertyAccessorFactory.forBeanPropertyAccess(bean).setPropertyValues(new MutablePropertyValues(list));
      return bean;
   }

   @Override
   public @Nullable ManagedAttribute getManagedAttribute(Method method) throws InvalidMetadataException {
      MergedAnnotation<ManagedAttribute> ann = MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY)
         .get(ManagedAttribute.class)
         .withNonMergedAttributes();
      if (!ann.isPresent()) {
         return null;
      }

      org.springframework.jmx.export.metadata.ManagedAttribute bean = new org.springframework.jmx.export.metadata.ManagedAttribute();
      Map<String, Object> map = ann.asMap(new Adapt[0]);
      MutablePropertyValues pvs = new MutablePropertyValues(map);
      pvs.removePropertyValue("defaultValue");
      PropertyAccessorFactory.forBeanPropertyAccess(bean).setPropertyValues(pvs);
      String defaultValue = (String)map.get("defaultValue");
      if (StringUtils.hasLength(defaultValue)) {
         bean.setDefaultValue(defaultValue);
      }

      return bean;
   }

   @Override
   public @Nullable ManagedMetric getManagedMetric(Method method) throws InvalidMetadataException {
      MergedAnnotation<ManagedMetric> ann = MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY).get(ManagedMetric.class).withNonMergedAttributes();
      return copyPropertiesToBean(ann, org.springframework.jmx.export.metadata.ManagedMetric.class);
   }

   @Override
   public @Nullable ManagedOperation getManagedOperation(Method method) throws InvalidMetadataException {
      MergedAnnotation<ManagedOperation> ann = MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY)
         .get(ManagedOperation.class)
         .withNonMergedAttributes();
      return copyPropertiesToBean(ann, org.springframework.jmx.export.metadata.ManagedOperation.class);
   }

   @Override
   public @Nullable ManagedOperationParameter[] getManagedOperationParameters(Method method) throws InvalidMetadataException {
      List<MergedAnnotation<? extends Annotation>> anns = getRepeatableAnnotations(method, ManagedOperationParameter.class);
      return copyPropertiesToBeanArray(anns, org.springframework.jmx.export.metadata.ManagedOperationParameter.class);
   }

   @Override
   public @Nullable ManagedNotification[] getManagedNotifications(Class<?> clazz) throws InvalidMetadataException {
      List<MergedAnnotation<? extends Annotation>> anns = getRepeatableAnnotations(clazz, ManagedNotification.class);
      return copyPropertiesToBeanArray(anns, org.springframework.jmx.export.metadata.ManagedNotification.class);
   }

   private static List<MergedAnnotation<? extends Annotation>> getRepeatableAnnotations(
      AnnotatedElement annotatedElement, Class<? extends Annotation> annotationType
   ) {
      return MergedAnnotations.from(annotatedElement, SearchStrategy.TYPE_HIERARCHY)
         .stream(annotationType)
         .filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))
         .<MergedAnnotation<? extends Annotation>>map(MergedAnnotation::withNonMergedAttributes)
         .collect(Collectors.toList());
   }

   private static <T> T[] copyPropertiesToBeanArray(List<MergedAnnotation<? extends Annotation>> anns, Class<T> beanClass) {
      T[] beans = (T[])Array.newInstance(beanClass, anns.size());
      int i = 0;

      for (MergedAnnotation<? extends Annotation> ann : anns) {
         beans[i++] = copyPropertiesToBean(ann, beanClass);
      }

      return beans;
   }

   private static <T> @Nullable T copyPropertiesToBean(MergedAnnotation<? extends Annotation> ann, Class<T> beanClass) {
      if (!ann.isPresent()) {
         return null;
      }

      T bean = (T)BeanUtils.instantiateClass(beanClass);
      BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(bean);
      bw.setPropertyValues(new MutablePropertyValues(ann.asMap(new Adapt[0])));
      return bean;
   }
}
