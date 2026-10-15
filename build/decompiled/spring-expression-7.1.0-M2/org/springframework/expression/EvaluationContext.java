package org.springframework.expression;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface EvaluationContext {
   TypedValue getRootObject();

   default List<PropertyAccessor> getPropertyAccessors() {
      return Collections.emptyList();
   }

   default List<IndexAccessor> getIndexAccessors() {
      return Collections.emptyList();
   }

   default List<ConstructorResolver> getConstructorResolvers() {
      return Collections.emptyList();
   }

   default List<MethodResolver> getMethodResolvers() {
      return Collections.emptyList();
   }

   @Nullable BeanResolver getBeanResolver();

   TypeLocator getTypeLocator();

   TypeConverter getTypeConverter();

   TypeComparator getTypeComparator();

   OperatorOverloader getOperatorOverloader();

   default TypedValue assignVariable(String name, Supplier<TypedValue> valueSupplier) {
      TypedValue typedValue = valueSupplier.get();
      this.setVariable(name, typedValue.getValue());
      return typedValue;
   }

   void setVariable(String name, @Nullable Object value);

   @Nullable Object lookupVariable(String name);

   default boolean isAssignmentEnabled() {
      return true;
   }

   default boolean isCompilationSupported() {
      return true;
   }
}
