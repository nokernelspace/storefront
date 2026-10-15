package org.springframework.validation.beanvalidation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.NoProviderFoundException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.ExecutableDescriptor;
import jakarta.validation.metadata.MethodType;
import jakarta.validation.metadata.ParameterDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

class BeanValidationBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   private static final boolean BEAN_VALIDATION_PRESENT = ClassUtils.isPresent(
      "jakarta.validation.Validation", BeanValidationBeanRegistrationAotProcessor.class.getClassLoader()
   );
   private static final Log logger = LogFactory.getLog(BeanValidationBeanRegistrationAotProcessor.class);

   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      return BEAN_VALIDATION_PRESENT ? BeanValidationBeanRegistrationAotProcessor.BeanValidationDelegate.processAheadOfTime(registeredBean) : null;
   }

   private static class AotContribution implements BeanRegistrationAotContribution {
      private final Collection<Class<?>> validatedClasses;
      private final Collection<Class<? extends ConstraintValidator<?, ?>>> constraintValidatorClasses;

      public AotContribution(Collection<Class<?>> validatedClasses, Collection<Class<? extends ConstraintValidator<?, ?>>> constraintValidatorClasses) {
         this.validatedClasses = validatedClasses;
         this.constraintValidatorClasses = constraintValidatorClasses;
      }

      public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         ReflectionHints hints = generationContext.getRuntimeHints().reflection();

         for (Class<?> validatedClass : this.validatedClasses) {
            hints.registerType(validatedClass, new MemberCategory[]{MemberCategory.ACCESS_DECLARED_FIELDS});
         }

         for (Class<? extends ConstraintValidator<?, ?>> constraintValidatorClass : this.constraintValidatorClasses) {
            hints.registerType(constraintValidatorClass, new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS});
         }
      }
   }

   private static class BeanValidationDelegate {
      private static final @Nullable Validator validator = getValidatorIfAvailable();

      private static @Nullable Validator getValidatorIfAvailable() {
         try {
            ValidatorFactory validator = Validation.buildDefaultValidatorFactory();

            Validator var1;
            try {
               var1 = validator.getValidator();
            } catch (Throwable var4) {
               if (validator != null) {
                  try {
                     validator.close();
                  } catch (Throwable var3) {
                     var4.addSuppressed(var3);
                  }
               }

               throw var4;
            }

            if (validator != null) {
               validator.close();
            }

            return var1;
         } catch (NoProviderFoundException ex) {
            BeanValidationBeanRegistrationAotProcessor.logger.info("No Bean Validation provider available - skipping validation constraint hint inference");
            return null;
         }
      }

      public static @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
         if (validator == null) {
            return null;
         }

         Class<?> beanClass = registeredBean.getBeanClass();
         Set<Class<?>> validatedClasses = new HashSet<>();
         Set<Class<? extends ConstraintValidator<?, ?>>> constraintValidatorClasses = new HashSet<>();
         processAheadOfTime(beanClass, new HashSet<>(), validatedClasses, constraintValidatorClasses);
         return validatedClasses.isEmpty() && constraintValidatorClasses.isEmpty()
            ? null
            : new BeanValidationBeanRegistrationAotProcessor.AotContribution(validatedClasses, constraintValidatorClasses);
      }

      private static void processAheadOfTime(
         Class<?> clazz,
         Set<Class<?>> visitedClasses,
         Set<Class<?>> validatedClasses,
         Set<Class<? extends ConstraintValidator<?, ?>>> constraintValidatorClasses
      ) {
         Assert.notNull(validator, "Validator cannot be null");
         if (visitedClasses.add(clazz)) {
            BeanDescriptor descriptor;
            try {
               descriptor = validator.getConstraintsForClass(clazz);
            } catch (RuntimeException | LinkageError ex) {
               String className = clazz.getName();
               if (KotlinDetector.isKotlinType(clazz) && ex instanceof ArrayIndexOutOfBoundsException) {
                  if (BeanValidationBeanRegistrationAotProcessor.logger.isWarnEnabled()) {
                     BeanValidationBeanRegistrationAotProcessor.logger
                        .warn(
                           "Skipping validation constraint hint inference for class "
                              + className
                              + " due to an ArrayIndexOutOfBoundsException at validator level"
                        );
                  }
               } else if (!(ex instanceof TypeNotPresentException) && !(ex instanceof NoClassDefFoundError)) {
                  if (BeanValidationBeanRegistrationAotProcessor.logger.isWarnEnabled()) {
                     BeanValidationBeanRegistrationAotProcessor.logger.warn("Skipping validation constraint hint inference for class " + className, ex);
                  }
               } else if (BeanValidationBeanRegistrationAotProcessor.logger.isDebugEnabled()) {
                  BeanValidationBeanRegistrationAotProcessor.logger
                     .debug(
                        "Skipping validation constraint hint inference for class %s due to a %s for %s"
                           .formatted(className, ex.getClass().getSimpleName(), ex.getMessage())
                     );
               }

               return;
            }

            processExecutableDescriptor(
               descriptor.getConstrainedMethods(MethodType.NON_GETTER, new MethodType[]{MethodType.GETTER}), constraintValidatorClasses
            );
            processExecutableDescriptor(descriptor.getConstrainedConstructors(), constraintValidatorClasses);
            processPropertyDescriptors(descriptor.getConstrainedProperties(), constraintValidatorClasses);
            if (!constraintValidatorClasses.isEmpty() && shouldProcess(clazz)) {
               validatedClasses.add(clazz);
            }

            ReflectionUtils.doWithFields(clazz, field -> {
               Class<?> type = field.getType();
               if (Iterable.class.isAssignableFrom(type) || Optional.class.isAssignableFrom(type)) {
                  ResolvableType resolvableType = ResolvableType.forField(field);
                  Class<?> genericType = resolvableType.getGeneric(new int[]{0}).toClass();
                  if (shouldProcess(genericType)) {
                     validatedClasses.add(clazz);
                     processAheadOfTime(genericType, visitedClasses, validatedClasses, constraintValidatorClasses);
                  }
               }

               if (Map.class.isAssignableFrom(type)) {
                  ResolvableType resolvableType = ResolvableType.forField(field);
                  Class<?> keyGenericType = resolvableType.getGeneric(new int[]{0}).toClass();
                  Class<?> valueGenericType = resolvableType.getGeneric(new int[]{1}).toClass();
                  if (shouldProcess(keyGenericType)) {
                     validatedClasses.add(clazz);
                     processAheadOfTime(keyGenericType, visitedClasses, validatedClasses, constraintValidatorClasses);
                  }

                  if (shouldProcess(valueGenericType)) {
                     validatedClasses.add(clazz);
                     processAheadOfTime(valueGenericType, visitedClasses, validatedClasses, constraintValidatorClasses);
                  }
               }
            });
         }
      }

      private static boolean shouldProcess(Class<?> clazz) {
         return !clazz.getCanonicalName().startsWith("java.");
      }

      private static void processExecutableDescriptor(
         Set<? extends ExecutableDescriptor> executableDescriptors, Collection<Class<? extends ConstraintValidator<?, ?>>> constraintValidatorClasses
      ) {
         for (ExecutableDescriptor executableDescriptor : executableDescriptors) {
            for (ParameterDescriptor parameterDescriptor : executableDescriptor.getParameterDescriptors()) {
               for (ConstraintDescriptor<?> constraintDescriptor : parameterDescriptor.getConstraintDescriptors()) {
                  constraintValidatorClasses.addAll(constraintDescriptor.getConstraintValidatorClasses());
               }

               for (ContainerElementTypeDescriptor typeDescriptor : parameterDescriptor.getConstrainedContainerElementTypes()) {
                  for (ConstraintDescriptor<?> constraintDescriptor : typeDescriptor.getConstraintDescriptors()) {
                     constraintValidatorClasses.addAll(constraintDescriptor.getConstraintValidatorClasses());
                  }
               }
            }
         }
      }

      private static void processPropertyDescriptors(
         Set<PropertyDescriptor> propertyDescriptors, Collection<Class<? extends ConstraintValidator<?, ?>>> constraintValidatorClasses
      ) {
         for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            for (ConstraintDescriptor<?> constraintDescriptor : propertyDescriptor.getConstraintDescriptors()) {
               constraintValidatorClasses.addAll(constraintDescriptor.getConstraintValidatorClasses());
            }

            for (ContainerElementTypeDescriptor typeDescriptor : propertyDescriptor.getConstrainedContainerElementTypes()) {
               for (ConstraintDescriptor<?> constraintDescriptor : typeDescriptor.getConstraintDescriptors()) {
                  constraintValidatorClasses.addAll(constraintDescriptor.getConstraintValidatorClasses());
               }
            }
         }
      }
   }
}
