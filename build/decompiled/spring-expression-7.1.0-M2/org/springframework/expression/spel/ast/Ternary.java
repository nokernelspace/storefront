package org.springframework.expression.spel.ast;

import java.util.Objects;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.Assert;

public class Ternary extends SpelNodeImpl {
   public Ternary(int startPos, int endPos, SpelNodeImpl... args) {
      super(startPos, endPos, args);
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      Boolean condition = this.children[0].getValue(state, Boolean.class);
      if (condition == null) {
         throw new SpelEvaluationException(this.getChild(0).getStartPosition(), SpelMessage.TYPE_CONVERSION_ERROR, "null", "boolean");
      }

      state.trackOperation();
      TypedValue result = this.children[condition ? 1 : 2].getValueInternal(state);
      this.computeExitTypeDescriptor();
      return result;
   }

   @Override
   public String toStringAST() {
      return "(" + this.getChild(0).toStringAST() + " ? " + this.getChild(1).toStringAST() + " : " + this.getChild(2).toStringAST() + ")";
   }

   @Override
   public boolean isCompilable() {
      SpelNodeImpl condition = this.children[0];
      SpelNodeImpl left = this.children[1];
      SpelNodeImpl right = this.children[2];
      String conditionDescriptor = condition.exitTypeDescriptor;
      String leftDescriptor = left.exitTypeDescriptor;
      String rightDescriptor = right.exitTypeDescriptor;
      return condition.isCompilable()
         && left.isCompilable()
         && right.isCompilable()
         && CodeFlow.isBooleanCompatible(conditionDescriptor)
         && leftDescriptor != null
         && rightDescriptor != null;
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      this.computeExitTypeDescriptor();
      cf.enterCompilationScope();
      this.children[0].generateCode(mv, cf);
      String lastDesc = cf.lastDescriptor();
      Assert.state(lastDesc != null, "No last descriptor");
      if (!CodeFlow.isPrimitive(lastDesc)) {
         CodeFlow.insertUnboxInsns(mv, 'Z', lastDesc);
      }

      cf.exitCompilationScope();
      Label elseTarget = new Label();
      Label endOfIf = new Label();
      mv.visitJumpInsn(153, elseTarget);
      cf.enterCompilationScope();
      this.children[1].generateCode(mv, cf);
      if (!CodeFlow.isPrimitive(this.exitTypeDescriptor)) {
         lastDesc = cf.lastDescriptor();
         Assert.state(lastDesc != null, "No last descriptor");
         CodeFlow.insertBoxIfNecessary(mv, lastDesc.charAt(0));
      }

      cf.exitCompilationScope();
      mv.visitJumpInsn(167, endOfIf);
      mv.visitLabel(elseTarget);
      cf.enterCompilationScope();
      this.children[2].generateCode(mv, cf);
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
      String leftDescriptor = this.children[1].exitTypeDescriptor;
      String rightDescriptor = this.children[2].exitTypeDescriptor;
      if (this.exitTypeDescriptor == null && leftDescriptor != null && rightDescriptor != null) {
         if (Objects.equals(leftDescriptor, rightDescriptor)) {
            this.exitTypeDescriptor = leftDescriptor;
         } else {
            this.exitTypeDescriptor = "Ljava/lang/Object";
         }
      }
   }
}
