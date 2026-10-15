package org.springframework.expression.spel.ast;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.ExpressionUtils;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.SpelNode;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class SpelNodeImpl implements SpelNode, Opcodes {
   private static final SpelNodeImpl[] NO_CHILDREN = new SpelNodeImpl[0];
   private final int startPos;
   private final int endPos;
   protected SpelNodeImpl[] children = NO_CHILDREN;
   private @Nullable SpelNodeImpl parent;
   protected volatile @Nullable String exitTypeDescriptor;

   public SpelNodeImpl(int startPos, int endPos, SpelNodeImpl @Nullable ... operands) {
      this.startPos = startPos;
      this.endPos = endPos;
      if (!ObjectUtils.isEmpty(operands)) {
         this.children = operands;

         for (SpelNodeImpl operand : operands) {
            Assert.notNull(operand, "Operand must not be null");
            operand.parent = this;
         }
      }
   }

   protected boolean nextChildIs(Class<?>... classes) {
      if (this.parent != null) {
         SpelNodeImpl[] peers = this.parent.children;
         int i = 0;

         for (int max = peers.length; i < max; i++) {
            if (this == peers[i]) {
               if (i + 1 >= max) {
                  return false;
               }

               Class<?> peerClass = peers[i + 1].getClass();

               for (Class<?> desiredClass : classes) {
                  if (peerClass == desiredClass) {
                     return true;
                  }
               }

               return false;
            }
         }
      }

      return false;
   }

   @Override
   public final @Nullable Object getValue(ExpressionState expressionState) throws EvaluationException {
      return this.getValueInternal(expressionState).getValue();
   }

   @Override
   public final TypedValue getTypedValue(ExpressionState expressionState) throws EvaluationException {
      return this.getValueInternal(expressionState);
   }

   @Override
   public boolean isWritable(ExpressionState expressionState) throws EvaluationException {
      return false;
   }

   @Override
   public void setValue(ExpressionState expressionState, @Nullable Object newValue) throws EvaluationException {
      this.setValueInternal(expressionState, () -> new TypedValue(newValue));
   }

   public TypedValue setValueInternal(ExpressionState expressionState, Supplier<TypedValue> valueSupplier) throws EvaluationException {
      throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.SETVALUE_NOT_SUPPORTED, this.getClass().getName());
   }

   @Override
   public SpelNode getChild(int index) {
      return this.children[index];
   }

   @Override
   public int getChildCount() {
      return this.children.length;
   }

   @Override
   public @Nullable Class<?> getObjectClass(@Nullable Object obj) {
      if (obj == null) {
         return null;
      } else {
         return obj instanceof Class<?> clazz ? clazz : obj.getClass();
      }
   }

   @Override
   public int getStartPosition() {
      return this.startPos;
   }

   @Override
   public int getEndPosition() {
      return this.endPos;
   }

   public boolean isNullSafe() {
      return false;
   }

   public @Nullable String getExitDescriptor() {
      return this.exitTypeDescriptor;
   }

   protected final <T> @Nullable T getValue(ExpressionState state, Class<T> desiredReturnType) throws EvaluationException {
      return ExpressionUtils.convertTypedValue(state.getEvaluationContext(), this.getValueInternal(state), desiredReturnType);
   }

   protected ValueRef getValueRef(ExpressionState state) throws EvaluationException {
      throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.NOT_ASSIGNABLE, this.toStringAST());
   }

   public abstract TypedValue getValueInternal(ExpressionState expressionState) throws EvaluationException;

   @Deprecated(since = "6.2")
   protected static void generateCodeForArguments(MethodVisitor mv, CodeFlow cf, Member member, SpelNodeImpl[] arguments) {
      if (member instanceof Executable executable) {
         generateCodeForArguments(mv, cf, executable, arguments);
      }

      throw new IllegalArgumentException("The supplied member must be an instance of java.lang.reflect.Executable: " + member);
   }

   protected static void generateCodeForArguments(MethodVisitor mv, CodeFlow cf, Executable executable, SpelNodeImpl[] arguments) {
      Class<?>[] parameterTypes = executable.getParameterTypes();
      String[] parameterDescriptors = CodeFlow.toDescriptors(parameterTypes);
      int parameterCount = parameterTypes.length;
      if (executable.isVarArgs()) {
         int varargsIndex = parameterCount - 1;
         int argumentCount = arguments.length;
         int p = 0;

         for (p = 0; p < varargsIndex; p++) {
            cf.generateCodeForArgument(mv, arguments[p], parameterDescriptors[p]);
         }

         SpelNodeImpl lastArgument = argumentCount != 0 ? arguments[argumentCount - 1] : null;
         ClassLoader classLoader = executable.getDeclaringClass().getClassLoader();
         Class<?> lastArgumentType = lastArgument != null ? loadClassForExitDescriptor(lastArgument.getExitDescriptor(), classLoader) : null;
         Class<?> lastParameterType = parameterTypes[varargsIndex];
         if (lastArgument != null && lastArgumentType != null && lastParameterType.isAssignableFrom(lastArgumentType)) {
            cf.generateCodeForArgument(mv, lastArgument, parameterDescriptors[p]);
         } else {
            String arrayComponentType = parameterDescriptors[varargsIndex];
            arrayComponentType = arrayComponentType.substring(1);
            CodeFlow.insertNewArrayCode(mv, argumentCount - p, arrayComponentType);
            int arrayIndex = 0;

            while (p < argumentCount) {
               mv.visitInsn(89);
               CodeFlow.insertOptimalLoad(mv, arrayIndex++);
               cf.generateCodeForArgument(mv, arguments[p++], arrayComponentType);
               CodeFlow.insertArrayStore(mv, arrayComponentType);
            }
         }
      } else {
         for (int i = 0; i < parameterCount; i++) {
            cf.generateCodeForArgument(mv, arguments[i], parameterDescriptors[i]);
         }
      }
   }

   private static @Nullable Class<?> loadClassForExitDescriptor(@Nullable String exitDescriptor, ClassLoader classLoader) {
      if (!StringUtils.hasText(exitDescriptor)) {
         return null;
      }

      String typeDescriptor = exitDescriptor;
      if (typeDescriptor.length() > 1) {
         typeDescriptor = typeDescriptor + ";";
      }

      String className = Type.getType(typeDescriptor).getClassName();
      return ClassUtils.resolveClassName(className, classLoader);
   }

   @Deprecated(since = "6.2")
   protected static void generateCodeForArgument(MethodVisitor mv, CodeFlow cf, SpelNodeImpl argument, String paramDesc) {
      cf.generateCodeForArgument(mv, argument, paramDesc);
   }
}
