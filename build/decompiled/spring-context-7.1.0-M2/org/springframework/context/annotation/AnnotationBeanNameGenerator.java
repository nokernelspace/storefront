package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class AnnotationBeanNameGenerator implements BeanNameGenerator {
   public static final AnnotationBeanNameGenerator INSTANCE = new AnnotationBeanNameGenerator();
   private static final String COMPONENT_ANNOTATION_CLASSNAME = "org.springframework.stereotype.Component";
   private static final Adapt[] ADAPTATIONS = Adapt.values(false, true);
   private static final Log logger = LogFactory.getLog(AnnotationBeanNameGenerator.class);
   private static final Set<String> conventionBasedStereotypeCheckCache = ConcurrentHashMap.newKeySet();
   private final Map<String, Set<String>> metaAnnotationTypesCache = new ConcurrentHashMap<>();

   public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
      if (definition instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
         String beanName = this.determineBeanNameFromAnnotation(annotatedBeanDefinition);
         if (StringUtils.hasText(beanName)) {
            return beanName;
         }
      }

      return this.buildDefaultBeanName(definition, registry);
   }

   protected @Nullable String determineBeanNameFromAnnotation(AnnotatedBeanDefinition annotatedDef) {
      AnnotationMetadata metadata = annotatedDef.getMetadata();
      String beanName = this.getExplicitBeanName(metadata);
      if (beanName != null) {
         return beanName;
      }

      List<MergedAnnotation<Annotation>> mergedAnnotations = metadata.getAnnotations().stream().filter(MergedAnnotation::isDirectlyPresent).toList();
      Set<AnnotationAttributes> visited = new HashSet<>();

      for (MergedAnnotation<Annotation> mergedAnnotation : mergedAnnotations) {
         AnnotationAttributes attributes = mergedAnnotation.asAnnotationAttributes(ADAPTATIONS);
         if (visited.add(attributes)) {
            String annotationType = mergedAnnotation.getType().getName();
            Set<String> metaAnnotationTypes = this.metaAnnotationTypesCache
               .computeIfAbsent(annotationType, key -> this.getMetaAnnotationTypes(mergedAnnotation));
            if (this.isStereotypeWithNameValue(annotationType, metaAnnotationTypes, attributes)
               && attributes.get("value") instanceof String currentName
               && !currentName.isBlank()
               && !hasExplicitlyAliasedValueAttribute(mergedAnnotation.getType())) {
               if (conventionBasedStereotypeCheckCache.add(annotationType)
                  && metaAnnotationTypes.contains("org.springframework.stereotype.Component")
                  && logger.isWarnEnabled()) {
                  logger.warn(
                     "Support for convention-based @Component names is deprecated and will be removed in a future version of the framework. Please annotate the 'value' attribute in @%s with @AliasFor(annotation=Component.class) to declare an explicit alias for @Component's 'value' attribute."
                        .formatted(annotationType)
                  );
               }

               if (beanName != null && !currentName.equals(beanName)) {
                  throw new IllegalStateException(
                     "Stereotype annotations suggest inconsistent component names: '" + beanName + "' versus '" + currentName + "'"
                  );
               }

               beanName = currentName;
            }
         }
      }

      return beanName;
   }

   private Set<String> getMetaAnnotationTypes(MergedAnnotation<Annotation> mergedAnnotation) {
      Set<String> result = MergedAnnotations.from(mergedAnnotation.getType())
         .stream()
         .map(metaAnnotation -> metaAnnotation.getType().getName())
         .collect(Collectors.toCollection(LinkedHashSet::new));
      return result.isEmpty() ? Collections.emptySet() : result;
   }

   private @Nullable String getExplicitBeanName(AnnotationMetadata metadata) {
      List<String> names = metadata.getAnnotations()
         .stream("org.springframework.stereotype.Component")
         .map(annotation -> annotation.getString("value"))
         .filter(StringUtils::hasText)
         .map(String::trim)
         .distinct()
         .toList();
      if (names.size() == 1) {
         return names.get(0);
      } else if (names.size() > 1) {
         throw new IllegalStateException("Stereotype annotations suggest inconsistent component names: " + names);
      } else {
         return null;
      }
   }

   protected boolean isStereotypeWithNameValue(String annotationType, Set<String> metaAnnotationTypes, Map<String, @Nullable Object> attributes) {
      boolean isStereotype = metaAnnotationTypes.contains("org.springframework.stereotype.Component") || annotationType.equals("jakarta.inject.Named");
      return isStereotype && attributes.containsKey("value");
   }

   protected String buildDefaultBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
      return this.buildDefaultBeanName(definition);
   }

   protected String buildDefaultBeanName(BeanDefinition definition) {
      String beanClassName = definition.getBeanClassName();
      Assert.state(beanClassName != null, "No bean class name set");
      String shortClassName = ClassUtils.getShortName(beanClassName);
      return StringUtils.uncapitalizeAsProperty(shortClassName);
   }

   private static boolean hasExplicitlyAliasedValueAttribute(Class<? extends Annotation> annotationType) {
      Method valueAttribute = ReflectionUtils.findMethod(annotationType, "value");
      return valueAttribute != null && valueAttribute.isAnnotationPresent(AliasFor.class);
   }
}
