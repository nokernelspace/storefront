package org.springframework.expression.spel.ast;

import java.util.List;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.BooleanTypedValue;

public class OperatorBetween extends Operator {
   public OperatorBetween(int startPos, int endPos, SpelNodeImpl... operands) {
      super("between", startPos, endPos, operands);
   }

   public BooleanTypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      Object left = this.getLeftOperand().getValueInternal(state).getValue();
      if (this.getRightOperand().getValueInternal(state).getValue() instanceof List<?> list && list.size() == 2) {
         Object low = list.get(0);
         Object high = list.get(1);
         TypeComparator comp = state.getTypeComparator();

         try {
            state.trackOperation();
            return BooleanTypedValue.forValue(comp.compare(left, low) >= 0 && comp.compare(left, high) <= 0);
         } catch (SpelEvaluationException ex) {
            ex.setPosition(this.getStartPosition());
            throw ex;
         }
      } else {
         throw new SpelEvaluationException(this.getRightOperand().getStartPosition(), SpelMessage.BETWEEN_RIGHT_OPERAND_MUST_BE_TWO_ELEMENT_LIST);
      }
   }
}
