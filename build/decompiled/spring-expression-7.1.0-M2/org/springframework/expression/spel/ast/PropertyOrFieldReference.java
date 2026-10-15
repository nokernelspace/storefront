package org.springframework.expression.spel.ast;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.CompilablePropertyAccessor;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class PropertyOrFieldReference extends SpelNodeImpl {
   private final boolean nullSafe;
   private final String name;
   private volatile @Nullable String originalPrimitiveExitTypeDescriptor;
   private volatile boolean unwrapOptional;
   private volatile @Nullable PropertyAccessor cachedReadAccessor;
   private volatile @Nullable PropertyAccessor cachedWriteAccessor;

   public PropertyOrFieldReference(boolean nullSafe, String propertyOrFieldName, int startPos, int endPos) {
      super(startPos, endPos);
      this.nullSafe = nullSafe;
      this.name = propertyOrFieldName;
   }

   @Override
   public boolean isNullSafe() {
      return this.nullSafe;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public ValueRef getValueRef(ExpressionState state) throws EvaluationException {
      return new PropertyOrFieldReference.AccessorValueRef(
         this, state.getActiveContextObject(), state, state.getEvaluationContext(), state.getConfiguration().isAutoGrowNullReferences()
      );
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      TypedValue tv = this.getValueInternal(
         state, state.getActiveContextObject(), state.getEvaluationContext(), state.getConfiguration().isAutoGrowNullReferences()
      );
      if (this.cachedReadAccessor instanceof CompilablePropertyAccessor compilablePropertyAccessor) {
         this.setExitTypeDescriptor(CodeFlow.toDescriptor(compilablePropertyAccessor.getPropertyType()));
      }

      return tv;
   }

   private TypedValue getValueInternal(ExpressionState state, TypedValue contextObject, EvaluationContext evalContext, boolean isAutoGrowNullReferences) throws EvaluationException {
      TypedValue result = this.readProperty(state, contextObject, evalContext, this.name);
      if (result.getValue() == null && isAutoGrowNullReferences && this.nextChildIs(Indexer.class, PropertyOrFieldReference.class)) {
         TypeDescriptor resultDescriptor = result.getTypeDescriptor();
         Assert.state(resultDescriptor != null, "No result type");
         if (List.class == resultDescriptor.getType()) {
            if (this.isWritableProperty(this.name, contextObject, evalContext)) {
               state.trackOperation();
               List<?> newList = new ArrayList();
               this.writeProperty(state, contextObject, evalContext, this.name, newList);
               result = this.readProperty(state, contextObject, evalContext, this.name);
            }
         } else if (Map.class == resultDescriptor.getType()) {
            if (this.isWritableProperty(this.name, contextObject, evalContext)) {
               state.trackOperation();
               Map<?, ?> newMap = new HashMap();
               this.writeProperty(state, contextObject, evalContext, this.name, newMap);
               result = this.readProperty(state, contextObject, evalContext, this.name);
            }
         } else {
            try {
               if (this.isWritableProperty(this.name, contextObject, evalContext)) {
                  Class<?> clazz = resultDescriptor.getType();
                  state.trackOperation();
                  Object newObject = ReflectionUtils.accessibleConstructor(clazz, new Class[0]).newInstance();
                  this.writeProperty(state, contextObject, evalContext, this.name, newObject);
                  result = this.readProperty(state, contextObject, evalContext, this.name);
               }
            } catch (InvocationTargetException ex) {
               throw new SpelEvaluationException(
                  this.getStartPosition(), ex.getTargetException(), SpelMessage.UNABLE_TO_DYNAMICALLY_CREATE_OBJECT, resultDescriptor.getType()
               );
            } catch (Throwable ex) {
               throw new SpelEvaluationException(this.getStartPosition(), ex, SpelMessage.UNABLE_TO_DYNAMICALLY_CREATE_OBJECT, resultDescriptor.getType());
            }
         }
      }

      return result;
   }

   @Override
   public TypedValue setValueInternal(ExpressionState state, Supplier<TypedValue> valueSupplier) throws EvaluationException {
      TypedValue typedValue = valueSupplier.get();
      this.writeProperty(state, state.getActiveContextObject(), state.getEvaluationContext(), this.name, typedValue.getValue());
      return typedValue;
   }

   @Override
   public boolean isWritable(ExpressionState state) throws EvaluationException {
      return this.isWritableProperty(this.name, state.getActiveContextObject(), state.getEvaluationContext());
   }

   @Override
   public String toStringAST() {
      return this.name;
   }

   private TypedValue readProperty(ExpressionState state, TypedValue contextObject, EvaluationContext evalContext, String name) throws EvaluationException {
      Object originalTarget = contextObject.getValue();
      Object target = originalTarget;
      Optional<?> fallbackOptionalTarget = null;
      boolean isEmptyOptional = false;
      this.unwrapOptional = false;
      if (this.isNullSafe()) {
         if (target == null) {
            return TypedValue.NULL;
         }

         if (target instanceof Optional<?> optional) {
            if (optional.isPresent()) {
               target = optional.get();
               fallbackOptionalTarget = optional;
               this.unwrapOptional = true;
            } else {
               isEmptyOptional = true;
            }
         }
      }

      PropertyAccessor accessorToUse = this.cachedReadAccessor;
      if (accessorToUse != null) {
         if (evalContext.getPropertyAccessors().contains(accessorToUse)) {
            try {
               state.trackOperation();
               return accessorToUse.read(evalContext, target, name);
            } catch (Exception var15) {
            }
         }

         this.cachedReadAccessor = null;
      }

      List<PropertyAccessor> accessorsToTry = AccessorUtils.getAccessorsToTry(target, evalContext.getPropertyAccessors());

      try {
         for (PropertyAccessor accessor : accessorsToTry) {
            if (accessor.canRead(evalContext, target, name)) {
               if (accessor instanceof ReflectivePropertyAccessor reflectivePropertyAccessor) {
                  accessor = reflectivePropertyAccessor.createOptimalAccessor(evalContext, target, name);
               }

               this.cachedReadAccessor = accessor;
               state.trackOperation();
               return accessor.read(evalContext, target, name);
            }

            if (fallbackOptionalTarget != null && accessor.canRead(evalContext, fallbackOptionalTarget, name)) {
               if (accessor instanceof ReflectivePropertyAccessor reflectivePropertyAccessor) {
                  accessor = reflectivePropertyAccessor.createOptimalAccessor(evalContext, fallbackOptionalTarget, name);
               }

               this.cachedReadAccessor = accessor;
               this.unwrapOptional = false;
               state.trackOperation();
               return accessor.read(evalContext, fallbackOptionalTarget, name);
            }
         }
      } catch (Exception ex) {
         if (ex instanceof SpelEvaluationException see) {
            throw see;
         }

         throw new SpelEvaluationException(ex, SpelMessage.EXCEPTION_DURING_PROPERTY_READ, name, ex.getMessage());
      }

      if (isEmptyOptional) {
         return TypedValue.NULL;
      } else if (originalTarget == null) {
         throw new SpelEvaluationException(SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE_ON_NULL, name);
      } else {
         throw new SpelEvaluationException(
            this.getStartPosition(),
            SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE,
            name,
            FormatHelper.formatClassNameForMessage(this.getObjectClass(originalTarget))
         );
      }
   }

   private void writeProperty(ExpressionState state, TypedValue contextObject, EvaluationContext evalContext, String name, @Nullable Object newValue) throws EvaluationException {
      Object target = contextObject.getValue();
      if (target == null) {
         if (!this.isNullSafe()) {
            throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.PROPERTY_OR_FIELD_NOT_WRITABLE_ON_NULL, name);
         }
      } else {
         PropertyAccessor accessorToUse = this.cachedWriteAccessor;
         if (accessorToUse != null) {
            if (evalContext.getPropertyAccessors().contains(accessorToUse)) {
               try {
                  state.trackOperation();
                  accessorToUse.write(evalContext, target, name, newValue);
                  return;
               } catch (Exception var12) {
               }
            }

            this.cachedWriteAccessor = null;
         }

         List<PropertyAccessor> accessorsToTry = AccessorUtils.getAccessorsToTry(target, evalContext.getPropertyAccessors());

         try {
            for (PropertyAccessor accessor : accessorsToTry) {
               if (accessor.canWrite(evalContext, target, name)) {
                  this.cachedWriteAccessor = accessor;
                  state.trackOperation();
                  accessor.write(evalContext, target, name, newValue);
                  return;
               }
            }
         } catch (AccessException ex) {
            throw new SpelEvaluationException(this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_PROPERTY_WRITE, name, ex.getMessage());
         }

         throw new SpelEvaluationException(
            this.getStartPosition(), SpelMessage.PROPERTY_OR_FIELD_NOT_WRITABLE, name, FormatHelper.formatClassNameForMessage(this.getObjectClass(target))
         );
      }
   }

   public boolean isWritableProperty(String name, TypedValue contextObject, EvaluationContext evalContext) throws EvaluationException {
      Object target = contextObject.getValue();
      if (target != null) {
         for (PropertyAccessor accessor : AccessorUtils.getAccessorsToTry(target, evalContext.getPropertyAccessors())) {
            try {
               if (accessor.canWrite(evalContext, target, name)) {
                  return true;
               }
            } catch (AccessException var9) {
            }
         }
      }

      return false;
   }

   @Override
   public boolean isCompilable() {
      return this.cachedReadAccessor instanceof CompilablePropertyAccessor compilablePropertyAccessor && compilablePropertyAccessor.isCompilable();
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      PropertyAccessor accessorToUse = this.cachedReadAccessor;
      if (accessorToUse instanceof CompilablePropertyAccessor compilablePropertyAccessor) {
         Label skipIfNull = null;
         if (this.isNullSafe()) {
            if (this.unwrapOptional) {
               CodeFlow.insertOptionalUnwrapIfNecessary(mv, cf.lastDescriptor());
            }

            mv.visitInsn(89);
            skipIfNull = new Label();
            Label continueLabel = new Label();
            mv.visitJumpInsn(199, continueLabel);
            CodeFlow.insertCheckCast(mv, this.exitTypeDescriptor);
            mv.visitJumpInsn(167, skipIfNull);
            mv.visitLabel(continueLabel);
         }

         compilablePropertyAccessor.generateCode(this.name, mv, cf);
         cf.pushDescriptor(this.exitTypeDescriptor);
         if (this.originalPrimitiveExitTypeDescriptor != null) {
            CodeFlow.insertBoxIfNecessary(mv, this.originalPrimitiveExitTypeDescriptor);
         }

         if (skipIfNull != null) {
            mv.visitLabel(skipIfNull);
         }
      } else {
         throw new IllegalStateException("Property accessor is not compilable: " + accessorToUse);
      }
   }

   void setExitTypeDescriptor(String descriptor) {
      if (this.isNullSafe() && CodeFlow.isPrimitive(descriptor)) {
         this.originalPrimitiveExitTypeDescriptor = descriptor;
         this.exitTypeDescriptor = CodeFlow.toBoxedDescriptor(descriptor);
      } else {
         this.exitTypeDescriptor = descriptor;
      }
   }

   private static class AccessorValueRef implements ValueRef {
      private final PropertyOrFieldReference ref;
      private final TypedValue contextObject;
      private final ExpressionState expressionState;
      private final EvaluationContext evalContext;
      private final boolean autoGrowNullReferences;

      public AccessorValueRef(
         PropertyOrFieldReference propertyOrFieldReference,
         TypedValue activeContextObject,
         ExpressionState expressionState,
         EvaluationContext evalContext,
         boolean autoGrowNullReferences
      ) {
         this.ref = propertyOrFieldReference;
         this.contextObject = activeContextObject;
         this.expressionState = expressionState;
         this.evalContext = evalContext;
         this.autoGrowNullReferences = autoGrowNullReferences;
      }

      @Override
      public TypedValue getValue() {
         TypedValue value = this.ref.getValueInternal(this.expressionState, this.contextObject, this.evalContext, this.autoGrowNullReferences);
         if (this.ref.cachedReadAccessor instanceof CompilablePropertyAccessor compilablePropertyAccessor) {
            this.ref.setExitTypeDescriptor(CodeFlow.toDescriptor(compilablePropertyAccessor.getPropertyType()));
         }

         return value;
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         this.ref.writeProperty(this.expressionState, this.contextObject, this.evalContext, this.ref.name, newValue);
      }

      @Override
      public boolean isWritable() {
         return this.ref.isWritableProperty(this.ref.name, this.contextObject, this.evalContext);
      }
   }
}
