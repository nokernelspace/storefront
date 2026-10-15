package org.springframework.beans.factory.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.GenericTypeAwareAutowireCandidateResolver;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public class QualifierAnnotationAutowireCandidateResolver extends GenericTypeAwareAutowireCandidateResolver {
   private final Set<Class<? extends Annotation>> qualifierTypes = CollectionUtils.newLinkedHashSet(2);
   private Class<? extends Annotation> valueAnnotationType = Value.class;

   public QualifierAnnotationAutowireCandidateResolver() {
      this.qualifierTypes.add(Qualifier.class);

      try {
         this.qualifierTypes.add(ClassUtils.forName("jakarta.inject.Qualifier", QualifierAnnotationAutowireCandidateResolver.class.getClassLoader()));
      } catch (ClassNotFoundException var2) {
      }
   }

   public QualifierAnnotationAutowireCandidateResolver(Class<? extends Annotation> qualifierType) {
      Assert.notNull(qualifierType, "'qualifierType' must not be null");
      this.qualifierTypes.add(qualifierType);
   }

   public QualifierAnnotationAutowireCandidateResolver(Set<Class<? extends Annotation>> qualifierTypes) {
      Assert.notNull(qualifierTypes, "'qualifierTypes' must not be null");
      this.qualifierTypes.addAll(qualifierTypes);
   }

   public void addQualifierType(Class<? extends Annotation> qualifierType) {
      this.qualifierTypes.add(qualifierType);
   }

   public void setValueAnnotationType(Class<? extends Annotation> valueAnnotationType) {
      this.valueAnnotationType = valueAnnotationType;
   }

   @Override
   public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
      if (!super.isAutowireCandidate(bdHolder, descriptor)) {
         return false;
      }

      Boolean checked = this.checkQualifiers(bdHolder, descriptor.getAnnotations());
      if (checked != Boolean.FALSE) {
         MethodParameter methodParam = descriptor.getMethodParameter();
         if (methodParam != null) {
            Method method = methodParam.getMethod();
            if (method == null || void.class == method.getReturnType()) {
               Boolean methodChecked = this.checkQualifiers(bdHolder, methodParam.getMethodAnnotations());
               if (methodChecked != null && checked == null) {
                  checked = methodChecked;
               }
            }
         }
      }

      return checked == Boolean.TRUE || checked == null && ((RootBeanDefinition)bdHolder.getBeanDefinition()).isDefaultCandidate();
   }

   protected @Nullable Boolean checkQualifiers(BeanDefinitionHolder bdHolder, Annotation[] annotationsToSearch) {
      boolean qualifierFound = false;
      if (!ObjectUtils.isEmpty(annotationsToSearch)) {
         SimpleTypeConverter typeConverter = new SimpleTypeConverter();

         for (Annotation annotation : annotationsToSearch) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (!this.isPlainJavaAnnotation(type)) {
               boolean checkMeta = true;
               boolean fallbackToMeta = false;
               if (this.isQualifier(type)) {
                  qualifierFound = true;
                  if (!this.checkQualifier(bdHolder, annotation, typeConverter)) {
                     fallbackToMeta = true;
                  } else {
                     checkMeta = false;
                  }
               }

               if (checkMeta) {
                  boolean foundMeta = false;

                  for (Annotation metaAnn : type.getAnnotations()) {
                     Class<? extends Annotation> metaType = metaAnn.annotationType();
                     if (!this.isPlainJavaAnnotation(metaType) && this.isQualifier(metaType)) {
                        qualifierFound = true;
                        foundMeta = true;
                        if (fallbackToMeta && ObjectUtils.isEmpty(AnnotationUtils.getValue(metaAnn)) || !this.checkQualifier(bdHolder, metaAnn, typeConverter)) {
                           return false;
                        }
                     }
                  }

                  if (fallbackToMeta && !foundMeta) {
                     return false;
                  }
               }
            }
         }
      }

      return qualifierFound ? true : null;
   }

   private boolean isPlainJavaAnnotation(Class<? extends Annotation> annotationType) {
      return annotationType.getName().startsWith("java.");
   }

   protected boolean isQualifier(Class<? extends Annotation> annotationType) {
      for (Class<? extends Annotation> qualifierType : this.qualifierTypes) {
         if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
            return true;
         }
      }

      return false;
   }

   protected boolean checkQualifier(BeanDefinitionHolder bdHolder, Annotation annotation, TypeConverter typeConverter) {
      Class<? extends Annotation> type = annotation.annotationType();
      RootBeanDefinition bd = (RootBeanDefinition)bdHolder.getBeanDefinition();
      AutowireCandidateQualifier qualifier = bd.getQualifier(type.getName());
      if (qualifier == null) {
         qualifier = bd.getQualifier(ClassUtils.getShortName(type));
      }

      if (qualifier == null) {
         Annotation targetAnnotation = this.getQualifiedElementAnnotation(bd, type);
         if (targetAnnotation == null) {
            targetAnnotation = this.getFactoryMethodAnnotation(bd, type);
         }

         if (targetAnnotation == null) {
            RootBeanDefinition dbd = this.getResolvedDecoratedDefinition(bd);
            if (dbd != null) {
               targetAnnotation = this.getFactoryMethodAnnotation(dbd, type);
            }
         }

         if (targetAnnotation == null) {
            BeanFactory beanFactory = this.getBeanFactory();
            if (beanFactory != null) {
               try {
                  Class<?> beanType = beanFactory.getType(bdHolder.getBeanName());
                  if (beanType != null) {
                     targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(beanType), type);
                  }
               } catch (NoSuchBeanDefinitionException var14) {
               }
            }

            if (targetAnnotation == null && bd.hasBeanClass()) {
               targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(bd.getBeanClass()), type);
            }
         }

         if (targetAnnotation != null && targetAnnotation.equals(annotation)) {
            return true;
         }
      }

      Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
      if (attributes.isEmpty() && qualifier == null) {
         return false;
      }

      for (Entry<String, Object> entry : attributes.entrySet()) {
         String attributeName = entry.getKey();
         Object expectedValue = entry.getValue();
         Object actualValue = null;
         if (qualifier != null) {
            actualValue = qualifier.getAttribute(attributeName);
         }

         if (actualValue == null) {
            actualValue = bd.getAttribute(attributeName);
         }

         if (actualValue != null || !(attributeName.equals("value") && expectedValue instanceof String name) || !bdHolder.matchesName(name)) {
            if (actualValue == null && qualifier != null) {
               actualValue = AnnotationUtils.getDefaultValue(annotation, attributeName);
            }

            if (actualValue != null) {
               actualValue = typeConverter.convertIfNecessary(actualValue, expectedValue.getClass());
            }

            if (!ObjectUtils.nullSafeEquals(expectedValue, actualValue)) {
               return false;
            }
         }
      }

      return true;
   }

   protected @Nullable Annotation getQualifiedElementAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
      AnnotatedElement qualifiedElement = bd.getQualifiedElement();
      return qualifiedElement != null ? AnnotationUtils.getAnnotation(qualifiedElement, type) : null;
   }

   protected @Nullable Annotation getFactoryMethodAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
      Method resolvedFactoryMethod = bd.getResolvedFactoryMethod();
      return resolvedFactoryMethod != null ? AnnotationUtils.getAnnotation(resolvedFactoryMethod, type) : null;
   }

   @Override
   public boolean isRequired(DependencyDescriptor descriptor) {
      if (!super.isRequired(descriptor)) {
         return false;
      }

      for (Annotation ann : descriptor.getAnnotations()) {
         if (ann instanceof Autowired autowired) {
            return autowired.required();
         }

         Autowired autowired = (Autowired)AnnotationUtils.findAnnotation(ann.annotationType(), Autowired.class);
         if (autowired != null) {
            return autowired.required();
         }
      }

      return true;
   }

   @Override
   public boolean hasQualifier(DependencyDescriptor descriptor) {
      for (Annotation annotation : descriptor.getAnnotations()) {
         if (this.isQualifier(annotation.annotationType())) {
            return true;
         }
      }

      MethodParameter methodParam = descriptor.getMethodParameter();
      if (methodParam != null) {
         Method method = methodParam.getMethod();
         if (method == null || void.class == method.getReturnType()) {
            for (Annotation annotation : methodParam.getMethodAnnotations()) {
               if (this.isQualifier(annotation.annotationType())) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public @Nullable String getSuggestedName(DependencyDescriptor descriptor) {
      for (Annotation annotation : descriptor.getAnnotations()) {
         if (this.isQualifier(annotation.annotationType()) && AnnotationUtils.getValue(annotation) instanceof String str) {
            return str;
         }
      }

      return null;
   }

   @Override
   public @Nullable Object getSuggestedValue(DependencyDescriptor descriptor) {
      Object value = this.findValue(descriptor.getAnnotations());
      if (value == null) {
         MethodParameter methodParam = descriptor.getMethodParameter();
         if (methodParam != null) {
            value = this.findValue(methodParam.getMethodAnnotations());
         }
      }

      return value;
   }

   protected @Nullable Object findValue(Annotation[] annotationsToSearch) {
      if (annotationsToSearch.length > 0) {
         AnnotationAttributes attr = AnnotatedElementUtils.getMergedAnnotationAttributes(
            AnnotatedElementUtils.forAnnotations(annotationsToSearch), this.valueAnnotationType
         );
         if (attr != null) {
            return this.extractValue(attr);
         }
      }

      return null;
   }

   protected Object extractValue(AnnotationAttributes attr) {
      Object value = attr.get("value");
      if (value == null) {
         throw new IllegalStateException("Value annotation must have a value attribute");
      } else {
         return value;
      }
   }
}
