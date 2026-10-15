package org.springframework.expression.spel.ast;

import java.lang.reflect.Constructor;
import java.util.Collection;
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
import org.springframework.expression.IndexAccessor;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.CompilableIndexAccessor;
import org.springframework.expression.spel.CompilablePropertyAccessor;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class Indexer extends SpelNodeImpl {
   private final boolean nullSafe;
   private volatile Indexer.@Nullable IndexedType indexedType;
   private volatile @Nullable String originalPrimitiveExitTypeDescriptor;
   private volatile @Nullable String arrayTypeDescriptor;
   private volatile Indexer.@Nullable CachedPropertyState cachedPropertyReadState;
   private volatile Indexer.@Nullable CachedPropertyState cachedPropertyWriteState;
   private volatile Indexer.@Nullable CachedIndexState cachedIndexReadState;
   private volatile Indexer.@Nullable CachedIndexState cachedIndexWriteState;

   public Indexer(boolean nullSafe, int startPos, int endPos, SpelNodeImpl indexExpression) {
      super(startPos, endPos, indexExpression);
      this.nullSafe = nullSafe;
   }

   @Override
   public final boolean isNullSafe() {
      return this.nullSafe;
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      return this.getValueRef(state, Indexer.AccessMode.READ).getValue();
   }

   @Override
   public TypedValue setValueInternal(ExpressionState state, Supplier<TypedValue> valueSupplier) throws EvaluationException {
      TypedValue typedValue = valueSupplier.get();
      this.getValueRef(state, Indexer.AccessMode.WRITE).setValue(typedValue.getValue());
      return typedValue;
   }

   @Override
   public boolean isWritable(ExpressionState expressionState) throws SpelEvaluationException {
      return this.getValueRef(expressionState, Indexer.AccessMode.WRITE).isWritable();
   }

   @Override
   protected ValueRef getValueRef(ExpressionState state) throws EvaluationException {
      return this.getValueRef(state, Indexer.AccessMode.READ_WRITE);
   }

   private ValueRef getValueRef(ExpressionState state, Indexer.AccessMode accessMode) throws EvaluationException {
      TypedValue context = state.getActiveContextObject();
      Object target = context.getValue();
      if (this.isNullSafe()) {
         if (target == null) {
            return ValueRef.NullValueRef.INSTANCE;
         }

         if (target instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
               return ValueRef.NullValueRef.INSTANCE;
            }

            target = optional.get();
         }
      }

      if (target == null) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.CANNOT_INDEX_INTO_NULL_VALUE);
      }

      TypeDescriptor targetDescriptor = context.getTypeDescriptor();
      TypedValue indexValue;
      Object index;
      if (target instanceof Map && this.children[0] instanceof PropertyOrFieldReference reference) {
         index = reference.getName();
         indexValue = new TypedValue(index);
      } else {
         try {
            state.pushActiveContextObject(state.getRootContextObject());
            indexValue = this.children[0].getValueInternal(state);
            index = indexValue.getValue();
            Assert.state(index != null, "No index");
         } finally {
            state.popActiveContextObject();
         }
      }

      Assert.state(targetDescriptor != null, "No type descriptor");
      if (target.getClass().isArray()) {
         int intIndex = convertIndexToInt(state, index);
         this.indexedType = Indexer.IndexedType.ARRAY;
         return new Indexer.ArrayIndexingValueRef(state, state.getTypeConverter(), target, intIndex, targetDescriptor);
      } else if (target instanceof List<?> list) {
         int intIndex = convertIndexToInt(state, index);
         this.indexedType = Indexer.IndexedType.LIST;
         return new Indexer.CollectionIndexingValueRef(
            state,
            list,
            intIndex,
            targetDescriptor,
            state.getTypeConverter(),
            state.getConfiguration().isAutoGrowCollections(),
            state.getConfiguration().getMaximumAutoGrowSize()
         );
      } else if (target instanceof Map<?, ?> map) {
         Object key = index;
         TypeDescriptor mapKeyTypeDescriptor = targetDescriptor.getMapKeyTypeDescriptor();
         if (mapKeyTypeDescriptor != null) {
            key = state.convertValue(key, mapKeyTypeDescriptor);
         }

         this.indexedType = Indexer.IndexedType.MAP;
         return new Indexer.MapIndexingValueRef(state, state.getTypeConverter(), map, key, targetDescriptor);
      } else if (target instanceof String string) {
         int intIndex = convertIndexToInt(state, index);
         this.indexedType = Indexer.IndexedType.STRING;
         return new Indexer.StringIndexingValueRef(state, string, intIndex, targetDescriptor);
      } else {
         EvaluationContext evalContext = state.getEvaluationContext();
         List<IndexAccessor> accessorsToTry = AccessorUtils.getAccessorsToTry(target, evalContext.getIndexAccessors());
         if (accessMode.supportsReads) {
            try {
               for (IndexAccessor indexAccessor : accessorsToTry) {
                  if (indexAccessor.canRead(evalContext, target, index)) {
                     this.indexedType = Indexer.IndexedType.CUSTOM;
                     return new Indexer.IndexAccessorValueRef(state, target, index, evalContext, targetDescriptor);
                  }
               }
            } catch (Exception ex) {
               throw new SpelEvaluationException(this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_INDEX_READ, index, target.getClass().getTypeName());
            }
         }

         if (accessMode.supportsWrites) {
            try {
               for (IndexAccessor indexAccessor : accessorsToTry) {
                  if (indexAccessor.canWrite(evalContext, target, index)) {
                     this.indexedType = Indexer.IndexedType.CUSTOM;
                     return new Indexer.IndexAccessorValueRef(state, target, index, evalContext, targetDescriptor);
                  }
               }
            } catch (Exception ex) {
               throw new SpelEvaluationException(this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_INDEX_WRITE, index, target.getClass().getTypeName());
            }
         }

         if (target instanceof Collection<?> collection) {
            int intIndex = convertIndexToInt(state, index);
            return new Indexer.CollectionIndexingValueRef(
               state,
               collection,
               intIndex,
               targetDescriptor,
               state.getTypeConverter(),
               state.getConfiguration().isAutoGrowCollections(),
               state.getConfiguration().getMaximumAutoGrowSize()
            );
         } else {
            TypeDescriptor valueType = indexValue.getTypeDescriptor();
            if (valueType != null && String.class == valueType.getType()) {
               this.indexedType = Indexer.IndexedType.OBJECT;
               return new Indexer.PropertyAccessorValueRef(state, target, (String)index, state.getEvaluationContext(), targetDescriptor);
            } else {
               throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, targetDescriptor);
            }
         }
      }
   }

   @Override
   public boolean isCompilable() {
      if (this.exitTypeDescriptor == null) {
         return false;
      }

      if (this.indexedType == Indexer.IndexedType.ARRAY) {
         return this.arrayTypeDescriptor != null;
      }

      SpelNodeImpl index = this.children[0];
      if (this.indexedType == Indexer.IndexedType.LIST) {
         return index.isCompilable();
      }

      if (this.indexedType == Indexer.IndexedType.MAP) {
         return index instanceof PropertyOrFieldReference || index.isCompilable();
      }

      if (this.indexedType == Indexer.IndexedType.OBJECT) {
         Indexer.CachedPropertyState cachedPropertyReadState = this.cachedPropertyReadState;
         return index instanceof StringLiteral
            && cachedPropertyReadState != null
            && cachedPropertyReadState.accessor instanceof CompilablePropertyAccessor cpa
            && cpa.isCompilable();
      }

      if (this.indexedType != Indexer.IndexedType.CUSTOM) {
         return false;
      }

      Indexer.CachedIndexState cachedIndexReadState = this.cachedIndexReadState;
      return cachedIndexReadState != null && cachedIndexReadState.accessor instanceof CompilableIndexAccessor cia && cia.isCompilable() && index.isCompilable();
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      String exitTypeDescriptor = this.exitTypeDescriptor;
      String descriptor = cf.lastDescriptor();
      if (descriptor == null) {
         cf.loadTarget(mv);
      }

      Label skipIfNull = null;
      if (this.isNullSafe()) {
         CodeFlow.insertOptionalUnwrapIfNecessary(mv, descriptor);
         mv.visitInsn(89);
         skipIfNull = new Label();
         Label continueLabel = new Label();
         mv.visitJumpInsn(199, continueLabel);
         CodeFlow.insertCheckCast(mv, exitTypeDescriptor);
         mv.visitJumpInsn(167, skipIfNull);
         mv.visitLabel(continueLabel);
      }

      SpelNodeImpl index = this.children[0];
      if (this.indexedType == Indexer.IndexedType.ARRAY) {
         String arrayTypeDescriptor = this.arrayTypeDescriptor;
         Assert.state(exitTypeDescriptor != null && arrayTypeDescriptor != null, "Array not compilable without descriptors");
         CodeFlow.insertCheckCast(mv, arrayTypeDescriptor);

         int insn = switch (arrayTypeDescriptor) {
            case "[D" -> 49;
            case "[F" -> 48;
            case "[J" -> 47;
            case "[I" -> 46;
            case "[S" -> 53;
            case "[B", "[Z" -> 51;
            case "[C" -> 52;
            default -> 50;
         };
         this.generateIndexCode(mv, cf, index, int.class);
         mv.visitInsn(insn);
      } else if (this.indexedType == Indexer.IndexedType.LIST) {
         mv.visitTypeInsn(192, "java/util/List");
         this.generateIndexCode(mv, cf, index, int.class);
         mv.visitMethodInsn(185, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
      } else if (this.indexedType == Indexer.IndexedType.MAP) {
         mv.visitTypeInsn(192, "java/util/Map");
         if (index instanceof PropertyOrFieldReference reference) {
            String mapKeyName = reference.getName();
            mv.visitLdcInsn(mapKeyName);
         } else {
            this.generateIndexCode(mv, cf, index, Object.class);
         }

         mv.visitMethodInsn(185, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      } else if (this.indexedType == Indexer.IndexedType.OBJECT) {
         if (!(index instanceof StringLiteral stringLiteral)) {
            throw new IllegalStateException("Index expression must be a StringLiteral, but was: " + index.getClass().getName());
         }

         Indexer.CachedPropertyState cachedPropertyReadState = this.cachedPropertyReadState;
         Assert.state(cachedPropertyReadState != null, "No cached PropertyAccessor for reading");
         if (!(cachedPropertyReadState.accessor instanceof CompilablePropertyAccessor compilablePropertyAccessor)) {
            throw new IllegalStateException(
               "Cached PropertyAccessor must be a CompilablePropertyAccessor, but was: " + cachedPropertyReadState.accessor.getClass().getName()
            );
         }

         String propertyName = (String)stringLiteral.getLiteralValue().getValue();
         Assert.state(propertyName != null, "No property name");
         compilablePropertyAccessor.generateCode(propertyName, mv, cf);
      } else if (this.indexedType == Indexer.IndexedType.CUSTOM) {
         Indexer.CachedIndexState cachedIndexReadState = this.cachedIndexReadState;
         Assert.state(cachedIndexReadState != null, "No cached IndexAccessor for reading");
         if (!(cachedIndexReadState.accessor instanceof CompilableIndexAccessor compilableIndexAccessor)) {
            throw new IllegalStateException(
               "Cached IndexAccessor must be a CompilableIndexAccessor, but was: " + cachedIndexReadState.accessor.getClass().getName()
            );
         }

         compilableIndexAccessor.generateCode(index, mv, cf);
      }

      cf.pushDescriptor(exitTypeDescriptor);
      if (skipIfNull != null) {
         if (this.originalPrimitiveExitTypeDescriptor != null) {
            CodeFlow.insertBoxIfNecessary(mv, this.originalPrimitiveExitTypeDescriptor);
         }

         mv.visitLabel(skipIfNull);
      }
   }

   private void generateIndexCode(MethodVisitor mv, CodeFlow cf, SpelNodeImpl indexNode, Class<?> indexType) {
      cf.generateCodeForArgument(mv, indexNode, indexType);
   }

   @Override
   public String toStringAST() {
      return "[" + this.getChild(0).toStringAST() + "]";
   }

   private void setExitTypeDescriptor(String descriptor) {
      if (this.isNullSafe() && CodeFlow.isPrimitive(descriptor)) {
         this.originalPrimitiveExitTypeDescriptor = descriptor;
         this.exitTypeDescriptor = CodeFlow.toBoxedDescriptor(descriptor);
      } else {
         this.exitTypeDescriptor = descriptor;
      }
   }

   private static int convertIndexToInt(ExpressionState state, Object index) {
      return (Integer)state.convertValue(index, TypeDescriptor.valueOf(Integer.class));
   }

   private static Class<?> getObjectType(Object obj) {
      return obj instanceof Class<?> clazz ? clazz : obj.getClass();
   }

   private enum AccessMode {
      READ(true, false),
      WRITE(false, true),
      READ_WRITE(true, true);

      private final boolean supportsReads;
      private final boolean supportsWrites;

      AccessMode(boolean supportsReads, boolean supportsWrites) {
         this.supportsReads = supportsReads;
         this.supportsWrites = supportsWrites;
      }
   }

   private class ArrayIndexingValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final TypeConverter typeConverter;
      private final Object array;
      private final int index;
      private final TypeDescriptor typeDescriptor;

      ArrayIndexingValueRef(ExpressionState expressionState, TypeConverter typeConverter, Object array, int index, TypeDescriptor typeDescriptor) {
         this.expressionState = expressionState;
         this.typeConverter = typeConverter;
         this.array = array;
         this.index = index;
         this.typeDescriptor = typeDescriptor;
      }

      @Override
      public TypedValue getValue() {
         Object arrayElement = this.getArrayElement(this.array, this.index);
         return new TypedValue(arrayElement, this.typeDescriptor.elementTypeDescriptor(arrayElement));
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         TypeDescriptor elementType = this.typeDescriptor.getElementTypeDescriptor();
         Assert.state(elementType != null, "No element type");
         this.setArrayElement(this.typeConverter, this.array, this.index, newValue, elementType.getType());
      }

      @Override
      public boolean isWritable() {
         return true;
      }

      private Object getArrayElement(Object ctx, int idx) throws SpelEvaluationException {
         this.expressionState.trackOperation();
         Class<?> arrayComponentType = ctx.getClass().componentType();
         if (arrayComponentType == boolean.class) {
            boolean[] array = (boolean[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("Z");
            Indexer.this.arrayTypeDescriptor = "[Z";
            return array[idx];
         } else if (arrayComponentType == byte.class) {
            byte[] array = (byte[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("B");
            Indexer.this.arrayTypeDescriptor = "[B";
            return array[idx];
         } else if (arrayComponentType == char.class) {
            char[] array = (char[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("C");
            Indexer.this.arrayTypeDescriptor = "[C";
            return array[idx];
         } else if (arrayComponentType == double.class) {
            double[] array = (double[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("D");
            Indexer.this.arrayTypeDescriptor = "[D";
            return array[idx];
         } else if (arrayComponentType == float.class) {
            float[] array = (float[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("F");
            Indexer.this.arrayTypeDescriptor = "[F";
            return array[idx];
         } else if (arrayComponentType == int.class) {
            int[] array = (int[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("I");
            Indexer.this.arrayTypeDescriptor = "[I";
            return array[idx];
         } else if (arrayComponentType == long.class) {
            long[] array = (long[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("J");
            Indexer.this.arrayTypeDescriptor = "[J";
            return array[idx];
         } else if (arrayComponentType == short.class) {
            short[] array = (short[])ctx;
            this.checkAccess(array.length, idx);
            Indexer.this.setExitTypeDescriptor("S");
            Indexer.this.arrayTypeDescriptor = "[S";
            return array[idx];
         } else {
            Object[] array = (Object[])ctx;
            this.checkAccess(array.length, idx);
            Object retValue = array[idx];
            Indexer.this.exitTypeDescriptor = CodeFlow.toDescriptor(arrayComponentType);
            Indexer.this.arrayTypeDescriptor = CodeFlow.toDescriptor(array.getClass());
            return retValue;
         }
      }

      private void setArrayElement(TypeConverter converter, Object ctx, int idx, @Nullable Object newValue, Class<?> arrayComponentType) throws EvaluationException {
         this.expressionState.trackOperation();
         if (arrayComponentType == boolean.class) {
            boolean[] array = (boolean[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, boolean.class);
         } else if (arrayComponentType == byte.class) {
            byte[] array = (byte[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, byte.class);
         } else if (arrayComponentType == char.class) {
            char[] array = (char[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, char.class);
         } else if (arrayComponentType == double.class) {
            double[] array = (double[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, double.class);
         } else if (arrayComponentType == float.class) {
            float[] array = (float[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, float.class);
         } else if (arrayComponentType == int.class) {
            int[] array = (int[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, int.class);
         } else if (arrayComponentType == long.class) {
            long[] array = (long[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, long.class);
         } else if (arrayComponentType == short.class) {
            short[] array = (short[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, short.class);
         } else {
            Object[] array = (Object[])ctx;
            this.checkAccess(array.length, idx);
            array[idx] = convertValue(converter, newValue, arrayComponentType);
         }
      }

      private void checkAccess(int arrayLength, int index) throws SpelEvaluationException {
         if (index >= arrayLength) {
            throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.ARRAY_INDEX_OUT_OF_BOUNDS, arrayLength, index);
         }
      }

      private static <T> T convertValue(TypeConverter converter, @Nullable Object value, Class<T> targetType) {
         T result = (T)converter.convertValue(value, TypeDescriptor.forObject(value), TypeDescriptor.valueOf(targetType));
         if (result == null) {
            throw new IllegalStateException("Null conversion result for index [" + value + "]");
         } else {
            return result;
         }
      }
   }

   private record CachedIndexState(IndexAccessor accessor, Class<?> targetType, Object index) {
   }

   private record CachedPropertyState(PropertyAccessor accessor, Class<?> targetType, String name) {
   }

   private class CollectionIndexingValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final Collection collection;
      private final int index;
      private final TypeDescriptor collectionEntryDescriptor;
      private final TypeConverter typeConverter;
      private final boolean growCollection;
      private final int maximumSize;

      public CollectionIndexingValueRef(
         ExpressionState expressionState,
         Collection collection,
         int index,
         TypeDescriptor collectionEntryDescriptor,
         TypeConverter typeConverter,
         boolean growCollection,
         int maximumSize
      ) {
         this.expressionState = expressionState;
         this.collection = collection;
         this.index = index;
         this.collectionEntryDescriptor = collectionEntryDescriptor;
         this.typeConverter = typeConverter;
         this.growCollection = growCollection;
         this.maximumSize = maximumSize;
      }

      @Override
      public TypedValue getValue() {
         this.growCollectionIfNecessary();
         if (this.collection instanceof List list) {
            this.expressionState.trackOperation();
            Object o = list.get(this.index);
            Indexer.this.exitTypeDescriptor = CodeFlow.toDescriptor(Object.class);
            return new TypedValue(o, this.collectionEntryDescriptor.elementTypeDescriptor(o));
         } else {
            int pos = 0;

            for (Object o : this.collection) {
               this.expressionState.trackOperation();
               if (pos == this.index) {
                  return new TypedValue(o, this.collectionEntryDescriptor.elementTypeDescriptor(o));
               }

               pos++;
            }

            throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.COLLECTION_INDEX_OUT_OF_BOUNDS, this.collection.size(), this.index);
         }
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         if (this.collection instanceof List list) {
            this.growCollectionIfNecessary();
            if (this.collectionEntryDescriptor.getElementTypeDescriptor() != null) {
               newValue = this.typeConverter
                  .convertValue(newValue, TypeDescriptor.forObject(newValue), this.collectionEntryDescriptor.getElementTypeDescriptor());
            }

            this.expressionState.trackOperation();
            list.set(this.index, newValue);
         } else {
            throw new SpelEvaluationException(
               Indexer.this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.collectionEntryDescriptor.toString()
            );
         }
      }

      private void growCollectionIfNecessary() {
         if (this.index >= this.collection.size()) {
            if (!this.growCollection) {
               throw new SpelEvaluationException(
                  Indexer.this.getStartPosition(), SpelMessage.COLLECTION_INDEX_OUT_OF_BOUNDS, this.collection.size(), this.index
               );
            }

            if (this.index >= this.maximumSize) {
               throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.UNABLE_TO_GROW_COLLECTION);
            }

            if (this.collectionEntryDescriptor.getElementTypeDescriptor() == null) {
               throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.UNABLE_TO_GROW_COLLECTION_UNKNOWN_ELEMENT_TYPE);
            }

            TypeDescriptor elementType = this.collectionEntryDescriptor.getElementTypeDescriptor();

            try {
               Constructor<?> ctor = getDefaultConstructor(elementType.getType());

               for (int newElements = this.index - this.collection.size(); newElements >= 0; newElements--) {
                  this.expressionState.trackOperation();
                  this.collection.add(ctor != null ? ctor.newInstance() : null);
               }
            } catch (Throwable ex) {
               throw new SpelEvaluationException(Indexer.this.getStartPosition(), ex, SpelMessage.UNABLE_TO_GROW_COLLECTION);
            }
         }
      }

      @Override
      public boolean isWritable() {
         return this.collection instanceof List;
      }

      private static @Nullable Constructor<?> getDefaultConstructor(Class<?> type) {
         try {
            return ReflectionUtils.accessibleConstructor(type, new Class[0]);
         } catch (Throwable ex) {
            return null;
         }
      }
   }

   private class IndexAccessorValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final Object target;
      private final Object index;
      private final EvaluationContext evaluationContext;
      private final TypeDescriptor typeDescriptor;

      IndexAccessorValueRef(ExpressionState expressionState, Object target, Object index, EvaluationContext evaluationContext, TypeDescriptor typeDescriptor) {
         this.expressionState = expressionState;
         this.target = target;
         this.index = index;
         this.evaluationContext = evaluationContext;
         this.typeDescriptor = typeDescriptor;
      }

      @Override
      public TypedValue getValue() {
         Class<?> targetType = Indexer.getObjectType(this.target);
         Exception exception = null;

         try {
            Indexer.CachedIndexState cachedIndexReadState = Indexer.this.cachedIndexReadState;
            if (cachedIndexReadState != null) {
               Object cachedIndex = cachedIndexReadState.index;
               Class<?> cachedTargetType = cachedIndexReadState.targetType;
               if (cachedIndex.equals(this.index) && cachedTargetType.equals(targetType)) {
                  IndexAccessor accessor = cachedIndexReadState.accessor;
                  if (this.evaluationContext.getIndexAccessors().contains(accessor)) {
                     try {
                        this.expressionState.trackOperation();
                        return accessor.read(this.evaluationContext, this.target, this.index);
                     } catch (Exception ex) {
                        exception = ex;
                     }
                  }
               }

               Indexer.this.cachedIndexReadState = null;
            }

            for (IndexAccessor indexAccessor : AccessorUtils.getAccessorsToTry(this.target, this.evaluationContext.getIndexAccessors())) {
               if (indexAccessor.canRead(this.evaluationContext, this.target, this.index)) {
                  this.expressionState.trackOperation();
                  TypedValue result = indexAccessor.read(this.evaluationContext, this.target, this.index);
                  Indexer.this.cachedIndexReadState = new Indexer.CachedIndexState(indexAccessor, targetType, this.index);
                  if (indexAccessor instanceof CompilableIndexAccessor compilableIndexAccessor) {
                     Indexer.this.setExitTypeDescriptor(CodeFlow.toDescriptor(compilableIndexAccessor.getIndexedValueType()));
                  }

                  return result;
               }
            }
         } catch (Exception ex) {
            exception = ex;
         }

         if (exception != null) {
            throw new SpelEvaluationException(
               Indexer.this.getStartPosition(), exception, SpelMessage.EXCEPTION_DURING_INDEX_READ, this.index, this.typeDescriptor.toString()
            );
         } else {
            throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.typeDescriptor.toString());
         }
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         Class<?> targetType = Indexer.getObjectType(this.target);
         Exception exception = null;

         try {
            Indexer.CachedIndexState cachedIndexWriteState = Indexer.this.cachedIndexWriteState;
            if (cachedIndexWriteState != null) {
               Object cachedIndex = cachedIndexWriteState.index;
               Class<?> cachedTargetType = cachedIndexWriteState.targetType;
               if (cachedIndex.equals(this.index) && cachedTargetType.equals(targetType)) {
                  IndexAccessor accessor = cachedIndexWriteState.accessor;
                  if (this.evaluationContext.getIndexAccessors().contains(accessor)) {
                     try {
                        this.expressionState.trackOperation();
                        accessor.write(this.evaluationContext, this.target, this.index, newValue);
                        return;
                     } catch (Exception ex) {
                        exception = ex;
                     }
                  }
               }

               Indexer.this.cachedIndexWriteState = null;
            }

            for (IndexAccessor indexAccessor : AccessorUtils.getAccessorsToTry(this.target, this.evaluationContext.getIndexAccessors())) {
               if (indexAccessor.canWrite(this.evaluationContext, this.target, this.index)) {
                  this.expressionState.trackOperation();
                  indexAccessor.write(this.evaluationContext, this.target, this.index, newValue);
                  Indexer.this.cachedIndexWriteState = new Indexer.CachedIndexState(indexAccessor, targetType, this.index);
                  return;
               }
            }
         } catch (Exception ex) {
            exception = ex;
         }

         if (exception != null) {
            throw new SpelEvaluationException(
               Indexer.this.getStartPosition(), exception, SpelMessage.EXCEPTION_DURING_INDEX_WRITE, this.index, this.typeDescriptor.toString()
            );
         } else {
            throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.typeDescriptor.toString());
         }
      }

      @Override
      public boolean isWritable() {
         return true;
      }
   }

   private enum IndexedType {
      ARRAY,
      LIST,
      MAP,
      STRING,
      OBJECT,
      CUSTOM;
   }

   private class MapIndexingValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final TypeConverter typeConverter;
      private final Map map;
      private final @Nullable Object key;
      private final TypeDescriptor mapEntryDescriptor;

      public MapIndexingValueRef(ExpressionState expressionState, TypeConverter typeConverter, Map map, @Nullable Object key, TypeDescriptor mapEntryDescriptor) {
         this.expressionState = expressionState;
         this.typeConverter = typeConverter;
         this.map = map;
         this.key = key;
         this.mapEntryDescriptor = mapEntryDescriptor;
      }

      @Override
      public TypedValue getValue() {
         this.expressionState.trackOperation();
         Object value = this.map.get(this.key);
         Indexer.this.exitTypeDescriptor = CodeFlow.toDescriptor(Object.class);
         return new TypedValue(value, this.mapEntryDescriptor.getMapValueTypeDescriptor(value));
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         if (this.mapEntryDescriptor.getMapValueTypeDescriptor() != null) {
            newValue = this.typeConverter.convertValue(newValue, TypeDescriptor.forObject(newValue), this.mapEntryDescriptor.getMapValueTypeDescriptor());
         }

         this.expressionState.trackOperation();
         this.map.put(this.key, newValue);
      }

      @Override
      public boolean isWritable() {
         return true;
      }
   }

   private class PropertyAccessorValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final Object targetObject;
      private final String name;
      private final EvaluationContext evaluationContext;
      private final TypeDescriptor targetObjectTypeDescriptor;

      public PropertyAccessorValueRef(
         ExpressionState expressionState, Object targetObject, String name, EvaluationContext evaluationContext, TypeDescriptor targetObjectTypeDescriptor
      ) {
         this.expressionState = expressionState;
         this.targetObject = targetObject;
         this.name = name;
         this.evaluationContext = evaluationContext;
         this.targetObjectTypeDescriptor = targetObjectTypeDescriptor;
      }

      @Override
      public TypedValue getValue() {
         Class<?> targetType = Indexer.getObjectType(this.targetObject);

         try {
            Indexer.CachedPropertyState cachedPropertyReadState = Indexer.this.cachedPropertyReadState;
            if (cachedPropertyReadState != null) {
               String cachedPropertyName = cachedPropertyReadState.name;
               Class<?> cachedTargetType = cachedPropertyReadState.targetType;
               PropertyAccessor accessor = cachedPropertyReadState.accessor;
               if (cachedPropertyName.equals(this.name)
                  && cachedTargetType.equals(targetType)
                  && this.evaluationContext.getPropertyAccessors().contains(accessor)) {
                  this.expressionState.trackOperation();
                  return accessor.read(this.evaluationContext, this.targetObject, this.name);
               }

               Indexer.this.cachedPropertyReadState = null;
            }

            for (PropertyAccessor accessor : AccessorUtils.getAccessorsToTry(targetType, this.evaluationContext.getPropertyAccessors())) {
               if (accessor.canRead(this.evaluationContext, this.targetObject, this.name)) {
                  if (accessor instanceof ReflectivePropertyAccessor reflectivePropertyAccessor) {
                     accessor = reflectivePropertyAccessor.createOptimalAccessor(this.evaluationContext, this.targetObject, this.name);
                  }

                  this.expressionState.trackOperation();
                  TypedValue result = accessor.read(this.evaluationContext, this.targetObject, this.name);
                  Indexer.this.cachedPropertyReadState = new Indexer.CachedPropertyState(accessor, targetType, this.name);
                  if (accessor instanceof CompilablePropertyAccessor compilablePropertyAccessor) {
                     Indexer.this.setExitTypeDescriptor(CodeFlow.toDescriptor(compilablePropertyAccessor.getPropertyType()));
                  }

                  return result;
               }
            }
         } catch (AccessException ex) {
            throw new SpelEvaluationException(
               Indexer.this.getStartPosition(), ex, SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.targetObjectTypeDescriptor.toString()
            );
         }

         throw new SpelEvaluationException(
            Indexer.this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.targetObjectTypeDescriptor.toString()
         );
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         Class<?> targetType = Indexer.getObjectType(this.targetObject);

         try {
            Indexer.CachedPropertyState cachedPropertyWriteState = Indexer.this.cachedPropertyWriteState;
            if (cachedPropertyWriteState != null) {
               String cachedPropertyName = cachedPropertyWriteState.name;
               Class<?> cachedTargetType = cachedPropertyWriteState.targetType;
               PropertyAccessor accessor = cachedPropertyWriteState.accessor;
               if (cachedPropertyName.equals(this.name)
                  && cachedTargetType.equals(targetType)
                  && this.evaluationContext.getPropertyAccessors().contains(accessor)) {
                  this.expressionState.trackOperation();
                  accessor.write(this.evaluationContext, this.targetObject, this.name, newValue);
                  return;
               }

               Indexer.this.cachedPropertyWriteState = null;
            }

            for (PropertyAccessor accessor : AccessorUtils.getAccessorsToTry(targetType, this.evaluationContext.getPropertyAccessors())) {
               if (accessor.canWrite(this.evaluationContext, this.targetObject, this.name)) {
                  this.expressionState.trackOperation();
                  accessor.write(this.evaluationContext, this.targetObject, this.name, newValue);
                  Indexer.this.cachedPropertyWriteState = new Indexer.CachedPropertyState(accessor, targetType, this.name);
                  return;
               }
            }
         } catch (AccessException ex) {
            throw new SpelEvaluationException(Indexer.this.getStartPosition(), ex, SpelMessage.EXCEPTION_DURING_PROPERTY_WRITE, this.name, ex.getMessage());
         }

         throw new SpelEvaluationException(
            Indexer.this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.targetObjectTypeDescriptor.toString()
         );
      }

      @Override
      public boolean isWritable() {
         return true;
      }
   }

   private class StringIndexingValueRef implements ValueRef {
      private final ExpressionState expressionState;
      private final String target;
      private final int index;
      private final TypeDescriptor typeDescriptor;

      public StringIndexingValueRef(ExpressionState expressionState, String target, int index, TypeDescriptor typeDescriptor) {
         this.expressionState = expressionState;
         this.target = target;
         this.index = index;
         this.typeDescriptor = typeDescriptor;
      }

      @Override
      public TypedValue getValue() {
         if (this.index >= this.target.length()) {
            throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.STRING_INDEX_OUT_OF_BOUNDS, this.target.length(), this.index);
         }

         this.expressionState.trackOperation();
         return new TypedValue(String.valueOf(this.target.charAt(this.index)));
      }

      @Override
      public void setValue(@Nullable Object newValue) {
         throw new SpelEvaluationException(Indexer.this.getStartPosition(), SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE, this.typeDescriptor.toString());
      }

      @Override
      public boolean isWritable() {
         return false;
      }
   }
}
