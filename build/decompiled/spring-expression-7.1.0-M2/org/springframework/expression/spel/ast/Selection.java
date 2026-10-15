package org.springframework.expression.spel.ast;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public class Selection extends SpelNodeImpl {
   public static final int ALL = 0;
   public static final int FIRST = 1;
   public static final int LAST = 2;
   private final int variant;
   private final boolean nullSafe;

   public Selection(boolean nullSafe, int variant, int startPos, int endPos, SpelNodeImpl expression) {
      super(startPos, endPos, expression);
      this.nullSafe = nullSafe;
      this.variant = variant;
   }

   @Override
   public final boolean isNullSafe() {
      return this.nullSafe;
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      return this.getValueRef(state).getValue();
   }

   @Override
   protected ValueRef getValueRef(ExpressionState state) throws EvaluationException {
      TypedValue contextObject = state.getActiveContextObject();
      Object operand = contextObject.getValue();
      if (this.isNullSafe()) {
         if (operand == null) {
            return ValueRef.NullValueRef.INSTANCE;
         }

         if (operand instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
               return ValueRef.NullValueRef.INSTANCE;
            }

            operand = optional.get();
         }
      }

      if (operand == null) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.INVALID_TYPE_FOR_SELECTION, "null");
      }

      SpelNodeImpl selectionCriteria = this.children[0];
      if (operand instanceof Map<?, ?> mapdata) {
         state.trackOperation();
         Map<Object, Object> result = new HashMap<>();
         Object lastKey = null;

         for (Entry<?, ?> entry : mapdata.entrySet()) {
            try {
               TypedValue kvPair = new TypedValue(entry);
               state.pushActiveContextObject(kvPair);
               state.enterScope();
               if (!(selectionCriteria.getValueInternal(state).getValue() instanceof Boolean b)) {
                  throw new SpelEvaluationException(selectionCriteria.getStartPosition(), SpelMessage.RESULT_OF_SELECTION_CRITERIA_IS_NOT_BOOLEAN);
               }

               if (b) {
                  state.trackOperation();
                  result.put(entry.getKey(), entry.getValue());
                  if (this.variant == 1) {
                     return new ValueRef.TypedValueHolderValueRef(new TypedValue(result), this);
                  }

                  lastKey = entry.getKey();
               }
            } finally {
               state.popActiveContextObject();
               state.exitScope();
            }
         }

         if ((this.variant == 1 || this.variant == 2) && result.isEmpty()) {
            return new ValueRef.TypedValueHolderValueRef(TypedValue.NULL, this);
         } else if (this.variant == 2) {
            Map<Object, Object> resultMap = new HashMap<>();
            Object lastValue = result.get(lastKey);
            resultMap.put(lastKey, lastValue);
            return new ValueRef.TypedValueHolderValueRef(new TypedValue(resultMap), this);
         } else {
            return new ValueRef.TypedValueHolderValueRef(new TypedValue(result), this);
         }
      } else {
         if (!(operand instanceof Iterable) && !ObjectUtils.isArray(operand)) {
            throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.INVALID_TYPE_FOR_SELECTION, operand.getClass().getName());
         }

         Iterable<?> data = operand instanceof Iterable<?> iterable ? iterable : Arrays.asList(ObjectUtils.toObjectArray(operand));
         state.trackOperation();
         List<Object> result = new ArrayList<>();

         for (Object element : data) {
            try {
               state.pushActiveContextObject(new TypedValue(element));
               state.enterScope();
               if (!(selectionCriteria.getValueInternal(state).getValue() instanceof Boolean match)) {
                  throw new SpelEvaluationException(selectionCriteria.getStartPosition(), SpelMessage.RESULT_OF_SELECTION_CRITERIA_IS_NOT_BOOLEAN);
               }

               if (match) {
                  if (this.variant == 1) {
                     return new ValueRef.TypedValueHolderValueRef(new TypedValue(element), this);
                  }

                  state.trackOperation();
                  result.add(element);
               }
            } finally {
               state.exitScope();
               state.popActiveContextObject();
            }
         }

         if ((this.variant == 1 || this.variant == 2) && result.isEmpty()) {
            return ValueRef.NullValueRef.INSTANCE;
         }

         if (this.variant == 2) {
            return new ValueRef.TypedValueHolderValueRef(new TypedValue(CollectionUtils.lastElement(result)), this);
         }

         if (operand instanceof Iterable) {
            return new ValueRef.TypedValueHolderValueRef(new TypedValue(result), this);
         }

         Class<?> elementType = null;
         TypeDescriptor typeDesc = contextObject.getTypeDescriptor();
         if (typeDesc != null) {
            TypeDescriptor elementTypeDesc = typeDesc.getElementTypeDescriptor();
            if (elementTypeDesc != null) {
               elementType = ClassUtils.resolvePrimitiveIfNecessary(elementTypeDesc.getType());
            }
         }

         Assert.state(elementType != null, "Unresolvable element type");
         Object resultArray = Array.newInstance(elementType, result.size());
         System.arraycopy(result.toArray(), 0, resultArray, 0, result.size());
         return new ValueRef.TypedValueHolderValueRef(new TypedValue(resultArray), this);
      }
   }

   @Override
   public String toStringAST() {
      return this.prefix() + this.getChild(0).toStringAST() + "]";
   }

   private String prefix() {
      return switch (this.variant) {
         case 0 -> "?[";
         case 1 -> "^[";
         case 2 -> "$[";
         default -> "";
      };
   }
}
