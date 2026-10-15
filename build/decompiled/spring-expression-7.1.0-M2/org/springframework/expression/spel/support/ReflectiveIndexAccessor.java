package org.springframework.expression.spel.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.CompilableIndexAccessor;
import org.springframework.expression.spel.SpelNode;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class ReflectiveIndexAccessor implements CompilableIndexAccessor {
   private final Class<?> targetType;
   private final Class<?> indexType;
   private final Method readMethod;
   private final Method readMethodToInvoke;
   private final @Nullable Method writeMethodToInvoke;

   public ReflectiveIndexAccessor(Class<?> targetType, Class<?> indexType, String readMethodName) {
      this(targetType, indexType, readMethodName, null);
   }

   public ReflectiveIndexAccessor(Class<?> targetType, Class<?> indexType, String readMethodName, @Nullable String writeMethodName) {
      this.targetType = targetType;
      this.indexType = indexType;

      try {
         this.readMethod = targetType.getMethod(readMethodName, indexType);
      } catch (Exception ex) {
         throw new IllegalArgumentException(
            "Failed to find public read-method '%s(%s)' in class '%s'."
               .formatted(readMethodName, ClassUtils.getCanonicalName(indexType), ClassUtils.getCanonicalName(targetType))
         );
      }

      this.readMethodToInvoke = ClassUtils.getPubliclyAccessibleMethodIfPossible(this.readMethod, targetType);
      ReflectionUtils.makeAccessible(this.readMethodToInvoke);
      if (writeMethodName != null) {
         Class<?> indexedValueType = this.readMethod.getReturnType();

         Method writeMethod;
         try {
            writeMethod = targetType.getMethod(writeMethodName, indexType, indexedValueType);
         } catch (Exception ex) {
            throw new IllegalArgumentException(
               "Failed to find public write-method '%s(%s, %s)' in class '%s'."
                  .formatted(
                     writeMethodName,
                     ClassUtils.getCanonicalName(indexType),
                     ClassUtils.getCanonicalName(indexedValueType),
                     ClassUtils.getCanonicalName(targetType)
                  )
            );
         }

         this.writeMethodToInvoke = ClassUtils.getPubliclyAccessibleMethodIfPossible(writeMethod, targetType);
         ReflectionUtils.makeAccessible(this.writeMethodToInvoke);
      } else {
         this.writeMethodToInvoke = null;
      }
   }

   @Override
   public Class<?>[] getSpecificTargetClasses() {
      return new Class[]{this.targetType};
   }

   @Override
   public boolean canRead(EvaluationContext context, Object target, Object index) {
      return ClassUtils.isAssignableValue(this.targetType, target) && ClassUtils.isAssignableValue(this.indexType, index);
   }

   @Override
   public TypedValue read(EvaluationContext context, Object target, Object index) {
      Object value = ReflectionUtils.invokeMethod(this.readMethodToInvoke, target, new Object[]{index});
      return new TypedValue(value);
   }

   @Override
   public boolean canWrite(EvaluationContext context, Object target, Object index) {
      return this.writeMethodToInvoke != null && this.canRead(context, target, index);
   }

   @Override
   public void write(EvaluationContext context, Object target, Object index, @Nullable Object newValue) {
      Assert.state(this.writeMethodToInvoke != null, "Write-method cannot be null");
      ReflectionUtils.invokeMethod(this.writeMethodToInvoke, target, new Object[]{index, newValue});
   }

   @Override
   public boolean isCompilable() {
      return true;
   }

   @Override
   public Class<?> getIndexedValueType() {
      return this.readMethod.getReturnType();
   }

   @Override
   public void generateCode(SpelNode index, MethodVisitor mv, CodeFlow cf) {
      Class<?> publicDeclaringClass = this.readMethodToInvoke.getDeclaringClass();
      Assert.state(Modifier.isPublic(publicDeclaringClass.getModifiers()), () -> "Failed to find public declaring class for read-method: " + this.readMethod);
      String classDesc = publicDeclaringClass.getName().replace('.', '/');
      String lastDesc = cf.lastDescriptor();
      if (lastDesc == null || !classDesc.equals(lastDesc.substring(1))) {
         mv.visitTypeInsn(192, classDesc);
      }

      cf.generateCodeForArgument(mv, index, this.indexType);
      String methodName = this.readMethod.getName();
      String methodDescr = CodeFlow.createSignatureDescriptor(this.readMethod);
      boolean isInterface = publicDeclaringClass.isInterface();
      int opcode = isInterface ? 185 : 182;
      mv.visitMethodInsn(opcode, classDesc, methodName, methodDescr, isInterface);
   }
}
