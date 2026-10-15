package org.springframework.expression.spel.ast;

import java.lang.reflect.Modifier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;

public class VariableReference extends SpelNodeImpl {
   private static final String THIS = "this";
   private static final String ROOT = "root";
   private final String name;

   public VariableReference(String variableName, int startPos, int endPos) {
      super(startPos, endPos);
      this.name = variableName;
   }

   @Override
   public ValueRef getValueRef(ExpressionState state) throws SpelEvaluationException {
      if ("this".equals(this.name)) {
         return new ValueRef.TypedValueHolderValueRef(state.getActiveContextObject(), this);
      }

      if ("root".equals(this.name)) {
         return new ValueRef.TypedValueHolderValueRef(state.getRootContextObject(), this);
      }

      TypedValue result = state.lookupVariable(this.name);
      return new VariableReference.VariableRef(this.name, result, state.getEvaluationContext());
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws SpelEvaluationException {
      TypedValue result;
      if ("this".equals(this.name)) {
         result = state.getActiveContextObject();
         if (result != state.getRootContextObject()) {
            return result;
         }
      } else if ("root".equals(this.name)) {
         result = state.getRootContextObject();
      } else {
         result = state.lookupVariable(this.name);
      }

      this.setExitTypeDescriptor(result.getValue());
      return result;
   }

   private void setExitTypeDescriptor(@Nullable Object value) {
      if (value != null && Modifier.isPublic(value.getClass().getModifiers())) {
         this.exitTypeDescriptor = CodeFlow.toDescriptorFromObject(value);
      } else {
         this.exitTypeDescriptor = "Ljava/lang/Object";
      }
   }

   @Override
   public TypedValue setValueInternal(ExpressionState state, Supplier<TypedValue> valueSupplier) throws EvaluationException {
      return state.assignVariable(this.name, valueSupplier);
   }

   @Override
   public String toStringAST() {
      return "#" + this.name;
   }

   @Override
   public boolean isWritable(ExpressionState expressionState) throws SpelEvaluationException {
      return !"this".equals(this.name) && !"root".equals(this.name);
   }

   @Override
   public boolean isCompilable() {
      return this.exitTypeDescriptor != null;
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      if (!"this".equals(this.name) && !"root".equals(this.name)) {
         mv.visitVarInsn(25, 2);
         mv.visitLdcInsn(this.name);
         mv.visitMethodInsn(185, "org/springframework/expression/EvaluationContext", "lookupVariable", "(Ljava/lang/String;)Ljava/lang/Object;", true);
      } else {
         mv.visitVarInsn(25, 1);
      }

      CodeFlow.insertCheckCast(mv, this.exitTypeDescriptor);
      cf.pushDescriptor(this.exitTypeDescriptor);
   }

   private static class VariableRef implements ValueRef {
      private final String name;
      private final TypedValue value;
      private final EvaluationContext evaluationContext;

      public VariableRef(String name, TypedValue value, EvaluationContext evaluationContext) {
         this.name = name;
         this.value = value;
         this.evaluationContext = evaluationContext;
      }

      @Override
      public TypedValue getValue() {
         return this.value;
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         this.evaluationContext.setVariable(this.name, newValue);
      }

      @Override
      public boolean isWritable() {
         return true;
      }
   }
}
