package org.springframework.expression.spel.ast;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class Projection extends SpelNodeImpl {
   private final boolean nullSafe;

   public Projection(boolean nullSafe, int startPos, int endPos, SpelNodeImpl expression) {
      super(startPos, endPos, expression);
      this.nullSafe = nullSafe;
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
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.PROJECTION_NOT_SUPPORTED_ON_TYPE, "null");
      }

      if (operand instanceof Map<?, ?> mapData) {
         state.trackOperation();
         List<Object> result = new ArrayList<>();

         for (Entry<?, ?> entry : mapData.entrySet()) {
            try {
               state.pushActiveContextObject(new TypedValue(entry));
               state.enterScope();
               state.trackOperation();
               result.add(this.children[0].getValueInternal(state).getValue());
            } finally {
               state.popActiveContextObject();
               state.exitScope();
            }
         }

         return new ValueRef.TypedValueHolderValueRef(new TypedValue(result), this);
      } else {
         boolean operandIsArray = ObjectUtils.isArray(operand);
         if (!(operand instanceof Iterable) && !operandIsArray) {
            throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.PROJECTION_NOT_SUPPORTED_ON_TYPE, operand.getClass().getName());
         }

         Iterable<?> data = operand instanceof Iterable<?> iterable ? iterable : Arrays.asList(ObjectUtils.toObjectArray(operand));
         state.trackOperation();
         List<Object> result = new ArrayList<>();
         Class<?> arrayElementType = null;

         for (Object element : data) {
            try {
               state.pushActiveContextObject(new TypedValue(element));
               state.enterScope();
               Object value = this.children[0].getValueInternal(state).getValue();
               if (value != null && operandIsArray) {
                  arrayElementType = this.determineCommonType(arrayElementType, value.getClass());
               }

               state.trackOperation();
               result.add(value);
            } finally {
               state.exitScope();
               state.popActiveContextObject();
            }
         }

         if (operandIsArray) {
            if (arrayElementType == null) {
               arrayElementType = Object.class;
            }

            Object resultArray = Array.newInstance(arrayElementType, result.size());
            System.arraycopy(result.toArray(), 0, resultArray, 0, result.size());
            return new ValueRef.TypedValueHolderValueRef(new TypedValue(resultArray), this);
         } else {
            return new ValueRef.TypedValueHolderValueRef(new TypedValue(result), this);
         }
      }
   }

   @Override
   public String toStringAST() {
      return "![" + this.getChild(0).toStringAST() + "]";
   }

   private Class<?> determineCommonType(@Nullable Class<?> oldType, Class<?> newType) {
      if (oldType == null) {
         return newType;
      }

      if (oldType.isAssignableFrom(newType)) {
         return oldType;
      }

      for (Class<?> nextType = newType; nextType != Object.class; nextType = nextType.getSuperclass()) {
         if (nextType.isAssignableFrom(oldType)) {
            return nextType;
         }
      }

      for (Class<?> nextInterface : ClassUtils.getAllInterfacesForClassAsSet(newType)) {
         if (nextInterface.isAssignableFrom(oldType)) {
            return nextInterface;
         }
      }

      return Object.class;
   }
}
