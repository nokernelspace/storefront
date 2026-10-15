package org.springframework.expression.spel.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;

public class InlineList extends SpelNodeImpl {
   private final boolean isConstant = this.determineIfConstant();
   private volatile @Nullable TypedValue constant;

   public InlineList(int startPos, int endPos, SpelNodeImpl... args) {
      super(startPos, endPos, args);
   }

   private boolean determineIfConstant() {
      int c = 0;

      for (int max = this.getChildCount(); c < max; c++) {
         SpelNode child = this.getChild(c);
         if (!(child instanceof Literal)
            && !(child instanceof InlineList inlineList && inlineList.isConstant())
            && (!(child instanceof OpMinus opMinus) || !opMinus.isNegativeNumberLiteral())) {
            return false;
         }
      }

      return true;
   }

   @Override
   public TypedValue getValueInternal(ExpressionState expressionState) throws EvaluationException {
      TypedValue result = this.constant;
      if (result != null) {
         return result;
      }

      result = this.createList(expressionState);
      if (this.isConstant) {
         this.constant = result;
      }

      return result;
   }

   private TypedValue createList(ExpressionState expressionState) throws EvaluationException {
      int childCount = this.getChildCount();
      expressionState.trackOperation();
      List<Object> list = new ArrayList<>(childCount);

      for (int c = 0; c < childCount; c++) {
         expressionState.trackOperation();
         list.add(this.getChild(c).getValue(expressionState));
      }

      return new TypedValue(this.isConstant ? Collections.unmodifiableList(list) : list);
   }

   @Override
   public String toStringAST() {
      StringJoiner sj = new StringJoiner(",", "{", "}");

      for (int c = 0; c < this.getChildCount(); c++) {
         sj.add(this.getChild(c).toStringAST());
      }

      return sj.toString();
   }

   public boolean isConstant() {
      return this.isConstant;
   }

   @Deprecated(since = "6.2.19")
   public @Nullable List<Object> getConstantValue() {
      Assert.state(this.isConstant, "Not a constant");
      TypedValue result = this.constant;
      if (result == null) {
         result = this.createList(new ExpressionState(SimpleEvaluationContext.forReadOnlyDataBinding().build()));
         this.constant = result;
      }

      return (List<Object>)result.getValue();
   }

   @Override
   public boolean isCompilable() {
      return this.isConstant;
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow codeflow) {
      String constantFieldName = "inlineList$" + codeflow.nextFieldId();
      String className = codeflow.getClassName();
      codeflow.registerNewField((cw, cflow) -> cw.visitField(26, constantFieldName, "Ljava/util/List;", null, null));
      codeflow.registerNewClinit((mVisitor, cflow) -> this.generateClinitCode(className, constantFieldName, mVisitor, cflow, false));
      mv.visitFieldInsn(178, className, constantFieldName, "Ljava/util/List;");
      codeflow.pushDescriptor("Ljava/util/List");
   }

   void generateClinitCode(String clazzname, String constantFieldName, MethodVisitor mv, CodeFlow codeflow, boolean nested) {
      mv.visitTypeInsn(187, "java/util/ArrayList");
      mv.visitInsn(89);
      mv.visitMethodInsn(183, "java/util/ArrayList", "<init>", "()V", false);
      if (!nested) {
         mv.visitFieldInsn(179, clazzname, constantFieldName, "Ljava/util/List;");
      }

      int childCount = this.getChildCount();

      for (int c = 0; c < childCount; c++) {
         if (!nested) {
            mv.visitFieldInsn(178, clazzname, constantFieldName, "Ljava/util/List;");
         } else {
            mv.visitInsn(89);
         }

         if (this.children[c] instanceof InlineList inlineList) {
            inlineList.generateClinitCode(clazzname, constantFieldName, mv, codeflow, true);
         } else {
            this.children[c].generateCode(mv, codeflow);
            String lastDesc = codeflow.lastDescriptor();
            if (CodeFlow.isPrimitive(lastDesc)) {
               CodeFlow.insertBoxIfNecessary(mv, lastDesc.charAt(0));
            }
         }

         mv.visitMethodInsn(185, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
         mv.visitInsn(87);
      }

      if (!nested) {
         mv.visitFieldInsn(178, clazzname, constantFieldName, "Ljava/util/List;");
      }

      mv.visitMethodInsn(184, "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;", false);
      if (!nested) {
         mv.visitFieldInsn(179, clazzname, constantFieldName, "Ljava/util/List;");
      }
   }
}
