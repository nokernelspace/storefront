package org.springframework.expression.spel.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;

public class InlineMap extends SpelNodeImpl {
   private final boolean isConstant = this.determineIfConstant();
   private volatile @Nullable TypedValue constant;

   public InlineMap(int startPos, int endPos, SpelNodeImpl... args) {
      super(startPos, endPos, args);
   }

   private boolean determineIfConstant() {
      int childCount = this.getChildCount();

      for (int c = 0; c < childCount; c++) {
         SpelNode child = this.getChild(c);
         if (!(child instanceof Literal)
            && !(child instanceof InlineList inlineList && inlineList.isConstant())
            && !(child instanceof InlineMap inlineMap && inlineMap.isConstant())
            && (c % 2 != 0 || !(child instanceof PropertyOrFieldReference))
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

      result = this.createMap(expressionState);
      if (this.isConstant) {
         this.constant = result;
      }

      return result;
   }

   private TypedValue createMap(ExpressionState expressionState) throws EvaluationException {
      expressionState.trackOperation();
      Map<Object, Object> map = new LinkedHashMap<>();
      int childCount = this.getChildCount();

      for (int c = 0; c < childCount; c++) {
         SpelNode keyChild = this.getChild(c++);
         Object key;
         if (keyChild instanceof PropertyOrFieldReference reference) {
            key = reference.getName();
         } else {
            key = keyChild.getValue(expressionState);
         }

         Object value = this.getChild(c).getValue(expressionState);
         expressionState.trackOperation();
         map.put(key, value);
      }

      return new TypedValue(this.isConstant ? Collections.unmodifiableMap(map) : map);
   }

   @Override
   public String toStringAST() {
      StringBuilder sb = new StringBuilder("{");

      for (int c = 0; c < this.getChildCount(); c++) {
         if (c > 0) {
            sb.append(',');
         }

         sb.append(this.getChild(c++).toStringAST());
         sb.append(':');
         sb.append(this.getChild(c).toStringAST());
      }

      sb.append('}');
      return sb.toString();
   }

   public boolean isConstant() {
      return this.isConstant;
   }

   @Deprecated(since = "6.2.19")
   public @Nullable Map<Object, Object> getConstantValue() {
      Assert.state(this.isConstant, "Not a constant");
      TypedValue result = this.constant;
      if (result == null) {
         result = this.createMap(new ExpressionState(SimpleEvaluationContext.forReadOnlyDataBinding().build()));
         this.constant = result;
      }

      return (Map<Object, Object>)result.getValue();
   }
}
