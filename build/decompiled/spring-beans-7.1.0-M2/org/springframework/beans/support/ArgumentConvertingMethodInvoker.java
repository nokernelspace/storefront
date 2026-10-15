package org.springframework.beans.support;

import java.beans.PropertyEditor;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.util.Assert;
import org.springframework.util.MethodInvoker;
import org.springframework.util.ReflectionUtils;

public class ArgumentConvertingMethodInvoker extends MethodInvoker {
   private @Nullable TypeConverter typeConverter;
   private boolean useDefaultConverter = true;

   public void setTypeConverter(@Nullable TypeConverter typeConverter) {
      this.typeConverter = typeConverter;
      this.useDefaultConverter = typeConverter == null;
   }

   public @Nullable TypeConverter getTypeConverter() {
      if (this.typeConverter == null && this.useDefaultConverter) {
         this.typeConverter = this.getDefaultTypeConverter();
      }

      return this.typeConverter;
   }

   protected TypeConverter getDefaultTypeConverter() {
      return new SimpleTypeConverter();
   }

   public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
      TypeConverter converter = this.getTypeConverter();
      if (converter instanceof PropertyEditorRegistry registry) {
         registry.registerCustomEditor(requiredType, propertyEditor);
      } else {
         throw new IllegalStateException("TypeConverter does not implement PropertyEditorRegistry interface: " + converter);
      }
   }

   protected @Nullable Method findMatchingMethod() {
      Method matchingMethod = super.findMatchingMethod();
      if (matchingMethod == null) {
         matchingMethod = this.doFindMatchingMethod(this.getArguments());
      }

      if (matchingMethod == null) {
         matchingMethod = this.doFindMatchingMethod(new Object[]{this.getArguments()});
      }

      return matchingMethod;
   }

   protected Method doFindMatchingMethod(Object[] arguments) {
      TypeConverter converter = this.getTypeConverter();
      if (converter != null) {
         String targetMethod = this.getTargetMethod();
         Method matchingMethod = null;
         int argCount = arguments.length;
         Class<?> targetClass = this.getTargetClass();
         Assert.state(targetClass != null, "No target class set");
         Method[] candidates = ReflectionUtils.getAllDeclaredMethods(targetClass);
         int minTypeDiffWeight = Integer.MAX_VALUE;
         Object[] argumentsToUse = null;

         for (Method candidate : candidates) {
            if (candidate.getName().equals(targetMethod)) {
               int parameterCount = candidate.getParameterCount();
               if (parameterCount == argCount) {
                  Class<?>[] paramTypes = candidate.getParameterTypes();
                  Object[] convertedArguments = new Object[argCount];
                  boolean match = true;

                  for (int j = 0; j < argCount && match; j++) {
                     try {
                        convertedArguments[j] = converter.convertIfNecessary(arguments[j], paramTypes[j]);
                     } catch (TypeMismatchException ex) {
                        match = false;
                     }
                  }

                  if (match) {
                     int typeDiffWeight = getTypeDifferenceWeight(paramTypes, convertedArguments);
                     if (typeDiffWeight < minTypeDiffWeight) {
                        minTypeDiffWeight = typeDiffWeight;
                        matchingMethod = candidate;
                        argumentsToUse = convertedArguments;
                     }
                  }
               }
            }
         }

         if (matchingMethod != null) {
            this.setArguments(argumentsToUse);
            return matchingMethod;
         }
      }

      return null;
   }
}
