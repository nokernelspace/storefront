package org.springframework.expression.spel.ast;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.ConstructorExecutor;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.ExpressionUtils;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.ReflectiveConstructorExecutor;
import org.springframework.util.Assert;

public class ConstructorReference extends SpelNodeImpl {
   private static final int MAX_ARRAY_ELEMENTS = 262144;
   private final boolean isArrayConstructor;
   private final SpelNodeImpl @Nullable [] dimensions;
   private volatile @Nullable ConstructorExecutor cachedExecutor;

   public ConstructorReference(int startPos, int endPos, SpelNodeImpl... arguments) {
      super(startPos, endPos, arguments);
      this.isArrayConstructor = false;
      this.dimensions = null;
   }

   public ConstructorReference(int startPos, int endPos, SpelNodeImpl[] dimensions, SpelNodeImpl... arguments) {
      super(startPos, endPos, arguments);
      this.isArrayConstructor = true;
      this.dimensions = dimensions;
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      state.trackOperation();
      return this.isArrayConstructor ? this.createArray(state) : this.createNewInstance(state);
   }

   private TypedValue createNewInstance(ExpressionState state) throws EvaluationException {
      Object[] arguments = new Object[this.getChildCount() - 1];
      List<TypeDescriptor> argumentTypes = new ArrayList<>(this.getChildCount() - 1);

      for (int i = 0; i < arguments.length; i++) {
         TypedValue childValue = this.children[i + 1].getValueInternal(state);
         Object value = childValue.getValue();
         arguments[i] = value;
         argumentTypes.add(TypeDescriptor.forObject(value));
      }

      ConstructorExecutor executorToUse = this.cachedExecutor;
      if (executorToUse != null) {
         if (state.getEvaluationContext().getConstructorResolvers().isEmpty()) {
            this.cachedExecutor = null;
         } else {
            try {
               return executorToUse.execute(state.getEvaluationContext(), arguments);
            } catch (AccessException ex) {
               if (ex.getCause() instanceof InvocationTargetException cause) {
                  Throwable rootCause = cause.getCause();
                  if (rootCause instanceof RuntimeException runtimeException) {
                     throw runtimeException;
                  }

                  String typeName = (String)this.children[0].getValueInternal(state).getValue();
                  throw new SpelEvaluationException(
                     this.getStartPosition(),
                     rootCause,
                     SpelMessage.CONSTRUCTOR_INVOCATION_PROBLEM,
                     typeName,
                     FormatHelper.formatMethodForMessage("", argumentTypes)
                  );
               }
            }

            this.cachedExecutor = null;
         }
      }

      String typeName = (String)this.children[0].getValueInternal(state).getValue();
      Assert.state(typeName != null, "No type name");
      executorToUse = this.findExecutorForConstructor(typeName, argumentTypes, state);

      try {
         this.cachedExecutor = executorToUse;
         if (executorToUse instanceof ReflectiveConstructorExecutor reflectiveConstructorExecutor) {
            this.exitTypeDescriptor = CodeFlow.toDescriptor(reflectiveConstructorExecutor.getConstructor().getDeclaringClass());
         }

         return executorToUse.execute(state.getEvaluationContext(), arguments);
      } catch (AccessException ex) {
         throw new SpelEvaluationException(
            this.getStartPosition(), ex, SpelMessage.CONSTRUCTOR_INVOCATION_PROBLEM, typeName, FormatHelper.formatMethodForMessage("", argumentTypes)
         );
      }
   }

   private ConstructorExecutor findExecutorForConstructor(String typeName, List<TypeDescriptor> argumentTypes, ExpressionState state) throws SpelEvaluationException {
      EvaluationContext evalContext = state.getEvaluationContext();

      for (ConstructorResolver ctorResolver : evalContext.getConstructorResolvers()) {
         try {
            ConstructorExecutor ce = ctorResolver.resolve(state.getEvaluationContext(), typeName, argumentTypes);
            if (ce != null) {
               return ce;
            }
         } catch (AccessException ex) {
            throw new SpelEvaluationException(
               this.getStartPosition(), ex, SpelMessage.CONSTRUCTOR_INVOCATION_PROBLEM, typeName, FormatHelper.formatMethodForMessage("", argumentTypes)
            );
         }
      }

      throw new SpelEvaluationException(
         this.getStartPosition(), SpelMessage.CONSTRUCTOR_NOT_FOUND, typeName, FormatHelper.formatMethodForMessage("", argumentTypes)
      );
   }

   @Override
   public String toStringAST() {
      StringBuilder sb = new StringBuilder("new ");
      sb.append(this.getChild(0).toStringAST());
      if (this.isArrayConstructor) {
         if (this.hasInitializer()) {
            InlineList initializer = (InlineList)this.getChild(1);
            sb.append("[] ").append(initializer.toStringAST());
         } else if (this.dimensions != null) {
            for (SpelNodeImpl dimension : this.dimensions) {
               sb.append('[').append(dimension.toStringAST()).append(']');
            }
         }
      } else {
         StringJoiner sj = new StringJoiner(",", "(", ")");
         int count = this.getChildCount();

         for (int i = 1; i < count; i++) {
            sj.add(this.getChild(i).toStringAST());
         }

         sb.append(sj.toString());
      }

      return sb.toString();
   }

   private TypedValue createArray(ExpressionState state) throws EvaluationException {
      Object intendedArrayType = this.getChild(0).getValue(state);
      if (!(intendedArrayType instanceof String type)) {
         throw new SpelEvaluationException(
            this.getChild(0).getStartPosition(),
            SpelMessage.TYPE_NAME_EXPECTED_FOR_ARRAY_CONSTRUCTION,
            FormatHelper.formatClassNameForMessage(intendedArrayType != null ? intendedArrayType.getClass() : null)
         );
      } else {
         if (state.getEvaluationContext().getConstructorResolvers().isEmpty()) {
            throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.CONSTRUCTOR_NOT_FOUND, type + "[]", "[]");
         }

         TypeCode arrayTypeCode = TypeCode.forName(type);
         Class<?> componentType;
         if (arrayTypeCode == TypeCode.OBJECT) {
            componentType = state.findType(type);
         } else {
            componentType = arrayTypeCode.getType();
         }

         Object newArray = null;
         if (!this.hasInitializer()) {
            if (this.dimensions != null) {
               for (SpelNodeImpl dimension : this.dimensions) {
                  if (dimension == null) {
                     throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.MISSING_ARRAY_DIMENSION);
                  }
               }

               TypeConverter typeConverter = state.getEvaluationContext().getTypeConverter();
               if (this.dimensions.length == 1) {
                  TypedValue o = this.dimensions[0].getTypedValue(state);
                  int arraySize = ExpressionUtils.toInt(typeConverter, o);
                  this.checkNumElements(arraySize);
                  newArray = Array.newInstance(componentType, arraySize);
               } else {
                  int[] dims = new int[this.dimensions.length];
                  long numElements = 1L;
                  long totalArrayObjects = 0L;

                  for (int d = 0; d < this.dimensions.length; d++) {
                     TypedValue o = this.dimensions[d].getTypedValue(state);
                     int arraySize = ExpressionUtils.toInt(typeConverter, o);
                     dims[d] = arraySize;
                     totalArrayObjects += numElements;
                     numElements *= arraySize;
                     this.checkNumElements(numElements);
                     this.checkNumElements(totalArrayObjects);
                  }

                  newArray = Array.newInstance(componentType, dims);
               }
            }
         } else {
            if (this.dimensions == null || this.dimensions.length > 1) {
               throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.MULTIDIM_ARRAY_INITIALIZER_NOT_SUPPORTED);
            }

            TypeConverter typeConverter = state.getEvaluationContext().getTypeConverter();
            InlineList initializer = (InlineList)this.getChild(1);
            if (this.dimensions[0] != null) {
               TypedValue dValue = this.dimensions[0].getTypedValue(state);
               int i = ExpressionUtils.toInt(typeConverter, dValue);
               if (i != initializer.getChildCount()) {
                  throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.INITIALIZER_LENGTH_INCORRECT);
               }
            }
            newArray = switch (arrayTypeCode) {
               case OBJECT -> this.createReferenceTypeArray(state, typeConverter, initializer.children, componentType);
               case BOOLEAN -> this.createBooleanArray(state, typeConverter, initializer.children);
               case CHAR -> this.createCharArray(state, typeConverter, initializer.children);
               case BYTE -> this.createByteArray(state, typeConverter, initializer.children);
               case SHORT -> this.createShortArray(state, typeConverter, initializer.children);
               case INT -> this.createIntArray(state, typeConverter, initializer.children);
               case LONG -> this.createLongArray(state, typeConverter, initializer.children);
               case FLOAT -> this.createFloatArray(state, typeConverter, initializer.children);
               case DOUBLE -> this.createDoubleArray(state, typeConverter, initializer.children);
               default -> throw new IllegalStateException("Unsupported TypeCode: " + arrayTypeCode);
            };
         }

         return new TypedValue(newArray);
      }
   }

   private void checkNumElements(long numElements) {
      if (numElements >= 262144L) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.MAX_ARRAY_ELEMENTS_THRESHOLD_EXCEEDED, 262144);
      }
   }

   private Object createReferenceTypeArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children, Class<?> componentType) {
      Object[] array = (Object[])Array.newInstance(componentType, children.length);
      TypeDescriptor targetType = TypeDescriptor.valueOf(componentType);

      for (int i = 0; i < array.length; i++) {
         Object value = children[i].getValue(state);
         array[i] = typeConverter.convertValue(value, TypeDescriptor.forObject(value), targetType);
      }

      return array;
   }

   private boolean[] createBooleanArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children) {
      boolean[] array = new boolean[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toBoolean(typeConverter, typedValue);
      }

      return array;
   }

   private char[] createCharArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children) {
      char[] array = new char[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toChar(typeConverter, typedValue);
      }

      return array;
   }

   private byte[] createByteArray(ExpressionState state, TypeConverter converter, SpelNodeImpl[] children) {
      byte[] array = new byte[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toByte(converter, typedValue);
      }

      return array;
   }

   private short[] createShortArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children) {
      short[] array = new short[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toShort(typeConverter, typedValue);
      }

      return array;
   }

   private int[] createIntArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children) {
      int[] array = new int[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toInt(typeConverter, typedValue);
      }

      return array;
   }

   private long[] createLongArray(ExpressionState state, TypeConverter converter, SpelNodeImpl[] children) {
      long[] array = new long[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toLong(converter, typedValue);
      }

      return array;
   }

   private float[] createFloatArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children) {
      float[] array = new float[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toFloat(typeConverter, typedValue);
      }

      return array;
   }

   private double[] createDoubleArray(ExpressionState state, TypeConverter typeConverter, SpelNodeImpl[] children) {
      double[] array = new double[children.length];

      for (int i = 0; i < array.length; i++) {
         TypedValue typedValue = children[i].getTypedValue(state);
         array[i] = ExpressionUtils.toDouble(typeConverter, typedValue);
      }

      return array;
   }

   private boolean hasInitializer() {
      return this.getChildCount() > 1;
   }

   @Override
   public boolean isCompilable() {
      if (this.cachedExecutor instanceof ReflectiveConstructorExecutor executor && this.exitTypeDescriptor != null) {
         for (int i = 1; i < this.children.length; i++) {
            if (!this.children[i].isCompilable()) {
               return false;
            }
         }

         Constructor<?> constructor = executor.getConstructor();
         return Modifier.isPublic(constructor.getModifiers()) && Modifier.isPublic(constructor.getDeclaringClass().getModifiers());
      } else {
         return false;
      }
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      ReflectiveConstructorExecutor executor = (ReflectiveConstructorExecutor)this.cachedExecutor;
      Assert.state(executor != null, "No cached executor");
      Constructor<?> constructor = executor.getConstructor();
      String classDesc = constructor.getDeclaringClass().getName().replace('.', '/');
      mv.visitTypeInsn(187, classDesc);
      mv.visitInsn(89);
      SpelNodeImpl[] arguments = new SpelNodeImpl[this.children.length - 1];
      System.arraycopy(this.children, 1, arguments, 0, this.children.length - 1);
      generateCodeForArguments(mv, cf, constructor, arguments);
      mv.visitMethodInsn(183, classDesc, "<init>", CodeFlow.createSignatureDescriptor(constructor), false);
      cf.pushDescriptor(this.exitTypeDescriptor);
   }
}
