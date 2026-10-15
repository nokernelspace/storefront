package org.springframework.expression.spel.ast;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.util.Assert;

public class Elvis extends SpelNodeImpl {
   private volatile @Nullable String unwrappedOptionalDescriptor;

   public Elvis(int startPos, int endPos, SpelNodeImpl... args) {
      super(startPos, endPos, args);
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      state.trackOperation();
      TypedValue leftHandTypedValue = this.children[0].getValueInternal(state);
      Object leftHandValue = leftHandTypedValue.getValue();
      TypedValue result;
      if (leftHandValue instanceof Optional<?> optional) {
         if (optional.isPresent()) {
            Object value = optional.get();
            this.unwrappedOptionalDescriptor = CodeFlow.toDescriptor(value.getClass());
            result = new TypedValue(value);
         } else {
            result = this.children[1].getValueInternal(state);
         }
      } else if (leftHandValue != null && !"".equals(leftHandValue)) {
         result = leftHandTypedValue;
      } else {
         result = this.children[1].getValueInternal(state);
      }

      this.computeExitTypeDescriptor();
      return result;
   }

   @Override
   public String toStringAST() {
      return "(" + this.getChild(0).toStringAST() + " ?: " + this.getChild(1).toStringAST() + ")";
   }

   @Override
   public boolean isCompilable() {
      SpelNodeImpl condition = this.children[0];
      SpelNodeImpl ifNullValue = this.children[1];
      String conditionDescriptor = this.unwrappedOptionalDescriptor != null ? this.unwrappedOptionalDescriptor : condition.exitTypeDescriptor;
      String ifNullValueDescriptor = ifNullValue.exitTypeDescriptor;
      return condition.isCompilable() && ifNullValue.isCompilable() && conditionDescriptor != null && ifNullValueDescriptor != null;
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      this.computeExitTypeDescriptor();
      Label elseTarget = new Label();
      Label endOfIf = new Label();
      cf.enterCompilationScope();
      this.children[0].generateCode(mv, cf);
      String lastDesc = cf.lastDescriptor();
      Assert.state(lastDesc != null, "No last descriptor");
      if ("Ljava/util/Optional".equals(lastDesc)) {
         CodeFlow.insertOptionalUnwrapIfNecessary(mv, lastDesc);
      } else {
         CodeFlow.insertBoxIfNecessary(mv, lastDesc.charAt(0));
      }

      cf.exitCompilationScope();
      mv.visitInsn(89);
      mv.visitJumpInsn(198, elseTarget);
      mv.visitInsn(89);
      mv.visitLdcInsn("");
      mv.visitInsn(95);
      mv.visitMethodInsn(182, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(153, endOfIf);
      mv.visitLabel(elseTarget);
      mv.visitInsn(87);
      cf.enterCompilationScope();
      this.children[1].generateCode(mv, cf);
      if (!CodeFlow.isPrimitive(this.exitTypeDescriptor)) {
         lastDesc = cf.lastDescriptor();
         Assert.state(lastDesc != null, "No last descriptor");
         CodeFlow.insertBoxIfNecessary(mv, lastDesc.charAt(0));
      }

      cf.exitCompilationScope();
      mv.visitLabel(endOfIf);
      cf.pushDescriptor(this.exitTypeDescriptor);
   }

   private void computeExitTypeDescriptor() {
      SpelNodeImpl condition = this.children[0];
      SpelNodeImpl ifNullValue = this.children[1];
      String conditionDescriptor = this.unwrappedOptionalDescriptor != null ? this.unwrappedOptionalDescriptor : condition.exitTypeDescriptor;
      String ifNullValueDescriptor = ifNullValue.exitTypeDescriptor;
      if (this.exitTypeDescriptor == null && conditionDescriptor != null && ifNullValueDescriptor != null) {
         if (Objects.equals(conditionDescriptor, ifNullValueDescriptor)) {
            this.exitTypeDescriptor = conditionDescriptor;
         } else {
            this.exitTypeDescriptor = "Ljava/lang/Object";
         }
      }
   }
}
