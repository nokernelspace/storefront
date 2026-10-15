package org.springframework.expression.spel.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.IndexAccessor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;

public final class SimpleEvaluationContext implements EvaluationContext {
   private static final TypeLocator typeNotFoundTypeLocator = typeName -> {
      throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
   };
   private final TypedValue rootObject;
   private final List<PropertyAccessor> propertyAccessors;
   private final List<IndexAccessor> indexAccessors;
   private final List<MethodResolver> methodResolvers;
   private final TypeConverter typeConverter;
   private final TypeComparator typeComparator = StandardTypeComparator.INSTANCE;
   private final OperatorOverloader operatorOverloader = StandardOperatorOverloader.INSTANCE;
   private final Map<String, Object> variables = new HashMap<>();
   private final boolean assignmentEnabled;
   private final boolean compilationSupported;

   private SimpleEvaluationContext(
      List<PropertyAccessor> propertyAccessors,
      List<IndexAccessor> indexAccessors,
      List<MethodResolver> resolvers,
      @Nullable TypeConverter converter,
      @Nullable TypedValue rootObject,
      boolean assignmentEnabled,
      boolean compilationSupported
   ) {
      this.propertyAccessors = propertyAccessors;
      this.indexAccessors = indexAccessors;
      this.methodResolvers = resolvers;
      this.typeConverter = converter != null ? converter : new StandardTypeConverter();
      this.rootObject = rootObject != null ? rootObject : TypedValue.NULL;
      this.assignmentEnabled = assignmentEnabled;
      this.compilationSupported = compilationSupported;
   }

   @Override
   public TypedValue getRootObject() {
      return this.rootObject;
   }

   @Override
   public List<PropertyAccessor> getPropertyAccessors() {
      return this.propertyAccessors;
   }

   @Override
   public List<IndexAccessor> getIndexAccessors() {
      return this.indexAccessors;
   }

   @Override
   public List<MethodResolver> getMethodResolvers() {
      return this.methodResolvers;
   }

   @Override
   public @Nullable BeanResolver getBeanResolver() {
      return null;
   }

   @Override
   public TypeLocator getTypeLocator() {
      return typeNotFoundTypeLocator;
   }

   @Override
   public TypeConverter getTypeConverter() {
      return this.typeConverter;
   }

   @Override
   public TypeComparator getTypeComparator() {
      return this.typeComparator;
   }

   @Override
   public OperatorOverloader getOperatorOverloader() {
      return this.operatorOverloader;
   }

   @Override
   public TypedValue assignVariable(String name, Supplier<TypedValue> valueSupplier) {
      throw new SpelEvaluationException(SpelMessage.VARIABLE_ASSIGNMENT_NOT_SUPPORTED, "#" + name);
   }

   @Override
   public void setVariable(String name, @Nullable Object value) {
      this.variables.put(name, value);
   }

   @Override
   public @Nullable Object lookupVariable(String name) {
      return this.variables.get(name);
   }

   @Override
   public boolean isAssignmentEnabled() {
      return this.assignmentEnabled;
   }

   @Override
   public boolean isCompilationSupported() {
      return this.compilationSupported;
   }

   public static SimpleEvaluationContext.Builder forPropertyAccessors(PropertyAccessor... accessors) {
      for (PropertyAccessor accessor : accessors) {
         if (accessor.getClass() == ReflectivePropertyAccessor.class) {
            throw new IllegalArgumentException(
               "SimpleEvaluationContext is not designed for use with a plain ReflectivePropertyAccessor. Consider using DataBindingPropertyAccessor or a custom subclass."
            );
         }
      }

      return new SimpleEvaluationContext.Builder(accessors);
   }

   public static SimpleEvaluationContext.Builder forReadOnlyDataBinding() {
      return new SimpleEvaluationContext.Builder(DataBindingPropertyAccessor.forReadOnlyAccess()).withAssignmentDisabled();
   }

   public static SimpleEvaluationContext.Builder forReadWriteDataBinding() {
      return new SimpleEvaluationContext.Builder(DataBindingPropertyAccessor.forReadWriteAccess());
   }

   public static final class Builder {
      private final List<PropertyAccessor> propertyAccessors;
      private List<IndexAccessor> indexAccessors = Collections.emptyList();
      private List<MethodResolver> resolvers = Collections.emptyList();
      private @Nullable TypeConverter typeConverter;
      private @Nullable TypedValue rootObject;
      private boolean assignmentEnabled = true;
      private boolean compilationSupported = false;

      private Builder(PropertyAccessor... accessors) {
         this.propertyAccessors = Arrays.asList(accessors);
      }

      public SimpleEvaluationContext.Builder withAssignmentDisabled() {
         this.assignmentEnabled = false;
         return this;
      }

      public SimpleEvaluationContext.Builder withCompilationSupported() {
         this.compilationSupported = true;
         return this;
      }

      public SimpleEvaluationContext.Builder withIndexAccessors(IndexAccessor... indexAccessors) {
         this.indexAccessors = Arrays.asList(indexAccessors);
         return this;
      }

      public SimpleEvaluationContext.Builder withMethodResolvers(MethodResolver... resolvers) {
         for (MethodResolver resolver : resolvers) {
            if (resolver.getClass() == ReflectiveMethodResolver.class) {
               throw new IllegalArgumentException(
                  "SimpleEvaluationContext is not designed for use with a plain ReflectiveMethodResolver. Consider using DataBindingMethodResolver or a custom subclass."
               );
            }
         }

         this.resolvers = Arrays.asList(resolvers);
         return this;
      }

      public SimpleEvaluationContext.Builder withInstanceMethods() {
         this.resolvers = Collections.singletonList(DataBindingMethodResolver.forInstanceMethodInvocation());
         return this;
      }

      public SimpleEvaluationContext.Builder withConversionService(ConversionService conversionService) {
         this.typeConverter = new StandardTypeConverter(conversionService);
         return this;
      }

      public SimpleEvaluationContext.Builder withTypeConverter(TypeConverter converter) {
         this.typeConverter = converter;
         return this;
      }

      public SimpleEvaluationContext.Builder withRootObject(Object rootObject) {
         this.rootObject = new TypedValue(rootObject);
         return this;
      }

      public SimpleEvaluationContext.Builder withTypedRootObject(Object rootObject, TypeDescriptor typeDescriptor) {
         this.rootObject = new TypedValue(rootObject, typeDescriptor);
         return this;
      }

      public SimpleEvaluationContext build() {
         return new SimpleEvaluationContext(
            this.propertyAccessors, this.indexAccessors, this.resolvers, this.typeConverter, this.rootObject, this.assignmentEnabled, this.compilationSupported
         );
      }
   }
}
