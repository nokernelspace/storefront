package org.springframework.expression.spel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class CodeFlow implements Opcodes {
   private final String className;
   private final ClassWriter classWriter;
   private final Deque<List<String>> compilationScopes;
   private @Nullable List<CodeFlow.FieldAdder> fieldAdders;
   private @Nullable List<CodeFlow.ClinitAdder> clinitAdders;
   private int nextFieldId = 1;
   private int nextFreeVariableId = 1;

   public CodeFlow(String className, ClassWriter classWriter) {
      this.className = className;
      this.classWriter = classWriter;
      this.compilationScopes = new ArrayDeque<>();
      this.compilationScopes.add(new ArrayList<>());
   }

   public void loadTarget(MethodVisitor mv) {
      mv.visitVarInsn(25, 1);
   }

   public void loadEvaluationContext(MethodVisitor mv) {
      mv.visitVarInsn(25, 2);
   }

   public void pushDescriptor(@Nullable String descriptor) {
      if (descriptor != null) {
         this.compilationScopes.element().add(descriptor);
      }
   }

   public void enterCompilationScope() {
      this.compilationScopes.push(new ArrayList<>());
   }

   public void exitCompilationScope() {
      this.compilationScopes.pop();
   }

   public @Nullable String lastDescriptor() {
      return (String)CollectionUtils.lastElement(this.compilationScopes.peek());
   }

   public void unboxBooleanIfNecessary(MethodVisitor mv) {
      if ("Ljava/lang/Boolean".equals(this.lastDescriptor())) {
         mv.visitMethodInsn(182, "java/lang/Boolean", "booleanValue", "()Z", false);
      }
   }

   public void finish() {
      if (this.fieldAdders != null) {
         for (CodeFlow.FieldAdder fieldAdder : this.fieldAdders) {
            fieldAdder.generateField(this.classWriter, this);
         }
      }

      if (this.clinitAdders != null) {
         MethodVisitor mv = this.classWriter.visitMethod(9, "<clinit>", "()V", null, null);
         mv.visitCode();
         this.nextFreeVariableId = 0;

         for (CodeFlow.ClinitAdder clinitAdder : this.clinitAdders) {
            clinitAdder.generateCode(mv, this);
         }

         mv.visitInsn(177);
         mv.visitMaxs(0, 0);
         mv.visitEnd();
      }
   }

   public void registerNewField(CodeFlow.FieldAdder fieldAdder) {
      if (this.fieldAdders == null) {
         this.fieldAdders = new ArrayList<>();
      }

      this.fieldAdders.add(fieldAdder);
   }

   public void registerNewClinit(CodeFlow.ClinitAdder clinitAdder) {
      if (this.clinitAdders == null) {
         this.clinitAdders = new ArrayList<>();
      }

      this.clinitAdders.add(clinitAdder);
   }

   public int nextFieldId() {
      return this.nextFieldId++;
   }

   public int nextFreeVariableId() {
      return this.nextFreeVariableId++;
   }

   public String getClassName() {
      return this.className;
   }

   public void generateCodeForArgument(MethodVisitor methodVisitor, SpelNode argument, Class<?> requiredType) {
      this.generateCodeForArgument(methodVisitor, argument, toDescriptor(requiredType));
   }

   public void generateCodeForArgument(MethodVisitor methodVisitor, SpelNode argument, String requiredTypeDesc) {
      this.enterCompilationScope();
      argument.generateCode(methodVisitor, this);
      String lastDesc = this.lastDescriptor();
      Assert.state(lastDesc != null, "No last descriptor");
      boolean primitiveOnStack = isPrimitive(lastDesc);
      if (primitiveOnStack && requiredTypeDesc.charAt(0) == 'L') {
         insertBoxIfNecessary(methodVisitor, lastDesc.charAt(0));
      } else if (requiredTypeDesc.length() == 1 && !primitiveOnStack) {
         insertUnboxInsns(methodVisitor, requiredTypeDesc.charAt(0), lastDesc);
      } else if (!requiredTypeDesc.equals(lastDesc)) {
         insertCheckCast(methodVisitor, requiredTypeDesc);
      }

      this.exitCompilationScope();
   }

   public static void insertUnboxInsns(MethodVisitor mv, char ch, @Nullable String stackDescriptor) {
      if (stackDescriptor != null) {
         switch (ch) {
            case 'B':
               if (!stackDescriptor.equals("Ljava/lang/Byte")) {
                  mv.visitTypeInsn(192, "java/lang/Byte");
               }

               mv.visitMethodInsn(182, "java/lang/Byte", "byteValue", "()B", false);
               break;
            case 'C':
               if (!stackDescriptor.equals("Ljava/lang/Character")) {
                  mv.visitTypeInsn(192, "java/lang/Character");
               }

               mv.visitMethodInsn(182, "java/lang/Character", "charValue", "()C", false);
               break;
            case 'D':
               if (!stackDescriptor.equals("Ljava/lang/Double")) {
                  mv.visitTypeInsn(192, "java/lang/Double");
               }

               mv.visitMethodInsn(182, "java/lang/Double", "doubleValue", "()D", false);
               break;
            case 'E':
            case 'G':
            case 'H':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            default:
               throw new IllegalArgumentException("Unboxing should not be attempted for descriptor '" + ch + "'");
            case 'F':
               if (!stackDescriptor.equals("Ljava/lang/Float")) {
                  mv.visitTypeInsn(192, "java/lang/Float");
               }

               mv.visitMethodInsn(182, "java/lang/Float", "floatValue", "()F", false);
               break;
            case 'I':
               if (!stackDescriptor.equals("Ljava/lang/Integer")) {
                  mv.visitTypeInsn(192, "java/lang/Integer");
               }

               mv.visitMethodInsn(182, "java/lang/Integer", "intValue", "()I", false);
               break;
            case 'J':
               if (!stackDescriptor.equals("Ljava/lang/Long")) {
                  mv.visitTypeInsn(192, "java/lang/Long");
               }

               mv.visitMethodInsn(182, "java/lang/Long", "longValue", "()J", false);
               break;
            case 'S':
               if (!stackDescriptor.equals("Ljava/lang/Short")) {
                  mv.visitTypeInsn(192, "java/lang/Short");
               }

               mv.visitMethodInsn(182, "java/lang/Short", "shortValue", "()S", false);
               break;
            case 'Z':
               if (!stackDescriptor.equals("Ljava/lang/Boolean")) {
                  mv.visitTypeInsn(192, "java/lang/Boolean");
               }

               mv.visitMethodInsn(182, "java/lang/Boolean", "booleanValue", "()Z", false);
         }
      }
   }

   public static void insertUnboxNumberInsns(MethodVisitor mv, char targetDescriptor, @Nullable String stackDescriptor) {
      if (stackDescriptor != null) {
         switch (targetDescriptor) {
            case 'D':
               if (stackDescriptor.equals("Ljava/lang/Object")) {
                  mv.visitTypeInsn(192, "java/lang/Number");
               }

               mv.visitMethodInsn(182, "java/lang/Number", "doubleValue", "()D", false);
               break;
            case 'E':
            case 'G':
            case 'H':
            default:
               throw new IllegalArgumentException("Unboxing should not be attempted for descriptor '" + targetDescriptor + "'");
            case 'F':
               if (stackDescriptor.equals("Ljava/lang/Object")) {
                  mv.visitTypeInsn(192, "java/lang/Number");
               }

               mv.visitMethodInsn(182, "java/lang/Number", "floatValue", "()F", false);
               break;
            case 'I':
               if (stackDescriptor.equals("Ljava/lang/Object")) {
                  mv.visitTypeInsn(192, "java/lang/Number");
               }

               mv.visitMethodInsn(182, "java/lang/Number", "intValue", "()I", false);
               break;
            case 'J':
               if (stackDescriptor.equals("Ljava/lang/Object")) {
                  mv.visitTypeInsn(192, "java/lang/Number");
               }

               mv.visitMethodInsn(182, "java/lang/Number", "longValue", "()J", false);
         }
      }
   }

   public static void insertAnyNecessaryTypeConversionBytecodes(MethodVisitor mv, char targetDescriptor, String stackDescriptor) {
      if (isPrimitive(stackDescriptor)) {
         char stackTop = stackDescriptor.charAt(0);
         switch (stackTop) {
            case 'B':
            case 'C':
            case 'I':
            case 'S':
               switch (targetDescriptor) {
                  case 'D':
                     mv.visitInsn(135);
                     return;
                  case 'E':
                  case 'G':
                  case 'H':
                  default:
                     throw new IllegalStateException("Cannot get from " + stackTop + " to " + targetDescriptor);
                  case 'F':
                     mv.visitInsn(134);
                     return;
                  case 'I':
                     return;
                  case 'J':
                     mv.visitInsn(133);
                     return;
               }
            case 'D':
               switch (targetDescriptor) {
                  case 'D':
                     break;
                  case 'E':
                  case 'G':
                  case 'H':
                  default:
                     throw new IllegalStateException("Cannot get from " + stackDescriptor + " to " + targetDescriptor);
                  case 'F':
                     mv.visitInsn(144);
                     break;
                  case 'I':
                     mv.visitInsn(142);
                     break;
                  case 'J':
                     mv.visitInsn(143);
               }
            case 'E':
            case 'G':
            case 'H':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            default:
               break;
            case 'F':
               switch (targetDescriptor) {
                  case 'D':
                     mv.visitInsn(141);
                     return;
                  case 'E':
                  case 'G':
                  case 'H':
                  default:
                     throw new IllegalStateException("Cannot get from " + stackTop + " to " + targetDescriptor);
                  case 'F':
                     return;
                  case 'I':
                     mv.visitInsn(139);
                     return;
                  case 'J':
                     mv.visitInsn(140);
                     return;
               }
            case 'J':
               switch (targetDescriptor) {
                  case 'D':
                     mv.visitInsn(138);
                     break;
                  case 'E':
                  case 'G':
                  case 'H':
                  default:
                     throw new IllegalStateException("Cannot get from " + stackTop + " to " + targetDescriptor);
                  case 'F':
                     mv.visitInsn(137);
                     break;
                  case 'I':
                     mv.visitInsn(136);
                  case 'J':
               }
         }
      }
   }

   public static String createSignatureDescriptor(Method method) {
      Class<?>[] params = method.getParameterTypes();
      StringBuilder sb = new StringBuilder();
      sb.append('(');

      for (Class<?> param : params) {
         sb.append(toJvmDescriptor(param));
      }

      sb.append(')');
      sb.append(toJvmDescriptor(method.getReturnType()));
      return sb.toString();
   }

   public static String createSignatureDescriptor(Constructor<?> ctor) {
      Class<?>[] params = ctor.getParameterTypes();
      StringBuilder sb = new StringBuilder();
      sb.append('(');

      for (Class<?> param : params) {
         sb.append(toJvmDescriptor(param));
      }

      sb.append(")V");
      return sb.toString();
   }

   public static String toJvmDescriptor(Class<?> clazz) {
      StringBuilder sb = new StringBuilder();

      while (clazz.isArray()) {
         sb.append('[');
         clazz = clazz.componentType();
      }

      if (clazz.isPrimitive()) {
         if (clazz == boolean.class) {
            sb.append('Z');
         } else if (clazz == byte.class) {
            sb.append('B');
         } else if (clazz == char.class) {
            sb.append('C');
         } else if (clazz == double.class) {
            sb.append('D');
         } else if (clazz == float.class) {
            sb.append('F');
         } else if (clazz == int.class) {
            sb.append('I');
         } else if (clazz == long.class) {
            sb.append('J');
         } else if (clazz == short.class) {
            sb.append('S');
         } else if (clazz == void.class) {
            sb.append('V');
         }
      } else {
         sb.append('L');
         sb.append(clazz.getName().replace('.', '/'));
         sb.append(';');
      }

      return sb.toString();
   }

   public static String toDescriptorFromObject(@Nullable Object value) {
      return value == null ? "Ljava/lang/Object" : toDescriptor(value.getClass());
   }

   @Contract("null -> false")
   public static boolean isBooleanCompatible(@Nullable String descriptor) {
      return descriptor != null && (descriptor.equals("Z") || descriptor.equals("Ljava/lang/Boolean"));
   }

   @Contract("null -> false")
   public static boolean isPrimitive(@Nullable String descriptor) {
      return descriptor != null && descriptor.length() == 1;
   }

   @Contract("null -> false")
   public static boolean isPrimitiveArray(@Nullable String descriptor) {
      if (descriptor == null) {
         return false;
      }

      boolean primitive = true;
      int i = 0;

      for (int max = descriptor.length(); i < max; i++) {
         char ch = descriptor.charAt(i);
         if (ch != '[') {
            primitive = ch != 'L';
            break;
         }
      }

      return primitive;
   }

   public static boolean areBoxingCompatible(String desc1, String desc2) {
      if (desc1.equals(desc2)) {
         return true;
      } else if (desc1.length() == 1) {
         return checkPairs(desc1, desc2);
      } else {
         return desc2.length() == 1 ? checkPairs(desc2, desc1) : false;
      }
   }

   private static boolean checkPairs(String desc1, String desc2) {
      return switch (desc1) {
         case "Z" -> desc2.equals("Ljava/lang/Boolean");
         case "D" -> desc2.equals("Ljava/lang/Double");
         case "F" -> desc2.equals("Ljava/lang/Float");
         case "I" -> desc2.equals("Ljava/lang/Integer");
         case "J" -> desc2.equals("Ljava/lang/Long");
         default -> false;
      };
   }

   @Contract("null -> false")
   public static boolean isPrimitiveOrUnboxableSupportedNumberOrBoolean(@Nullable String descriptor) {
      if (descriptor == null) {
         return false;
      } else {
         return isPrimitiveOrUnboxableSupportedNumber(descriptor) ? true : "Z".equals(descriptor) || descriptor.equals("Ljava/lang/Boolean");
      }
   }

   @Contract("null -> false")
   public static boolean isPrimitiveOrUnboxableSupportedNumber(@Nullable String descriptor) {
      if (descriptor == null) {
         return false;
      }

      if (descriptor.length() == 1) {
         return "DFIJ".contains(descriptor);
      }

      if (!descriptor.startsWith("Ljava/lang/")) {
         return false;
      }

      String name = descriptor.substring("Ljava/lang/".length());
      return name.equals("Double") || name.equals("Float") || name.equals("Integer") || name.equals("Long");
   }

   public static boolean isIntegerForNumericOp(Number number) {
      return number instanceof Integer || number instanceof Short || number instanceof Byte;
   }

   public static char toPrimitiveTargetDesc(String descriptor) {
      if (descriptor.length() == 1) {
         return descriptor.charAt(0);
      }

      return switch (descriptor) {
         case "Ljava/lang/Double" -> 'D';
         case "Ljava/lang/Float" -> 'F';
         case "Ljava/lang/Integer" -> 'I';
         case "Ljava/lang/Long" -> 'J';
         case "Ljava/lang/Boolean" -> 'Z';
         case "Ljava/lang/Character" -> 'C';
         case "Ljava/lang/Byte" -> 'B';
         case "Ljava/lang/Short" -> 'S';
         default -> throw new IllegalStateException("No primitive for '" + descriptor + "'");
      };
   }

   public static void insertCheckCast(MethodVisitor mv, @Nullable String descriptor) {
      if (descriptor != null && descriptor.length() != 1) {
         if (descriptor.charAt(0) == '[') {
            if (isPrimitiveArray(descriptor)) {
               mv.visitTypeInsn(192, descriptor);
            } else {
               mv.visitTypeInsn(192, descriptor + ";");
            }
         } else if (!descriptor.equals("Ljava/lang/Object")) {
            mv.visitTypeInsn(192, descriptor.substring(1));
         }
      }
   }

   public static void insertBoxIfNecessary(MethodVisitor mv, @Nullable String descriptor) {
      if (descriptor != null && descriptor.length() == 1) {
         insertBoxIfNecessary(mv, descriptor.charAt(0));
      }
   }

   public static void insertBoxIfNecessary(MethodVisitor mv, char ch) {
      switch (ch) {
         case 'B':
            mv.visitMethodInsn(184, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            break;
         case 'C':
            mv.visitMethodInsn(184, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            break;
         case 'D':
            mv.visitMethodInsn(184, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            break;
         case 'E':
         case 'G':
         case 'H':
         case 'K':
         case 'M':
         case 'N':
         case 'O':
         case 'P':
         case 'Q':
         case 'R':
         case 'T':
         case 'U':
         case 'W':
         case 'X':
         case 'Y':
         default:
            throw new IllegalArgumentException("Boxing should not be attempted for descriptor '" + ch + "'");
         case 'F':
            mv.visitMethodInsn(184, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            break;
         case 'I':
            mv.visitMethodInsn(184, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            break;
         case 'J':
            mv.visitMethodInsn(184, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
         case 'L':
         case 'V':
         case '[':
            break;
         case 'S':
            mv.visitMethodInsn(184, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            break;
         case 'Z':
            mv.visitMethodInsn(184, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
      }
   }

   public static String toDescriptor(Class<?> type) {
      String name = type.getName();
      if (type.isPrimitive()) {
         switch (name.length()) {
            case 3:
               return "I";
            case 4:
               return switch (name) {
                  case "byte" -> "B";
                  case "char" -> "C";
                  case "long" -> "J";
                  case "void" -> "V";
                  default -> throw new IllegalArgumentException("Unknown primitive type: " + name);
               };
            case 5:
               if (name.equals("float")) {
                  return "F";
               }

               if (name.equals("short")) {
                  return "S";
               }
               break;
            case 6:
               if (name.equals("double")) {
                  return "D";
               }
               break;
            case 7:
               if (name.equals("boolean")) {
                  return "Z";
               }
               break;
            default:
               throw new IllegalArgumentException("Unknown primitive type: " + name);
         }

         return "";
      } else if (name.charAt(0) != '[') {
         return "L" + type.getName().replace('.', '/');
      } else {
         return name.endsWith(";") ? name.substring(0, name.length() - 1).replace('.', '/') : name;
      }
   }

   public static String[] toParamDescriptors(Method method) {
      return toDescriptors(method.getParameterTypes());
   }

   public static String[] toParamDescriptors(Constructor<?> ctor) {
      return toDescriptors(ctor.getParameterTypes());
   }

   public static String[] toDescriptors(Class<?>[] types) {
      int typesCount = types.length;
      String[] descriptors = new String[typesCount];

      for (int p = 0; p < typesCount; p++) {
         descriptors[p] = toDescriptor(types[p]);
      }

      return descriptors;
   }

   public static void insertOptimalLoad(MethodVisitor mv, int value) {
      if (value < 6) {
         mv.visitInsn(3 + value);
      } else if (value < 127) {
         mv.visitIntInsn(16, value);
      } else if (value < 32767) {
         mv.visitIntInsn(17, value);
      } else {
         mv.visitLdcInsn(value);
      }
   }

   public static void insertArrayStore(MethodVisitor mv, String arrayComponentType) {
      if (arrayComponentType.length() == 1) {
         char componentType = arrayComponentType.charAt(0);
         switch (componentType) {
            case 'B':
            case 'Z':
               mv.visitInsn(84);
               break;
            case 'C':
               mv.visitInsn(85);
               break;
            case 'D':
               mv.visitInsn(82);
               break;
            case 'E':
            case 'G':
            case 'H':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            default:
               throw new IllegalArgumentException("Unexpected array component type " + componentType);
            case 'F':
               mv.visitInsn(81);
               break;
            case 'I':
               mv.visitInsn(79);
               break;
            case 'J':
               mv.visitInsn(80);
               break;
            case 'S':
               mv.visitInsn(86);
         }
      } else {
         mv.visitInsn(83);
      }
   }

   public static int arrayCodeFor(String arrayType) {
      return switch (arrayType.charAt(0)) {
         case 'B' -> 8;
         case 'C' -> 5;
         case 'D' -> 7;
         default -> throw new IllegalArgumentException("Unexpected array type " + arrayType.charAt(0));
         case 'F' -> 6;
         case 'I' -> 10;
         case 'J' -> 11;
         case 'S' -> 9;
         case 'Z' -> 4;
      };
   }

   public static boolean isReferenceTypeArray(String arrayType) {
      int length = arrayType.length();

      for (int i = 0; i < length; i++) {
         char ch = arrayType.charAt(i);
         if (ch != '[') {
            return ch == 'L';
         }
      }

      return false;
   }

   public static void insertNewArrayCode(MethodVisitor mv, int size, String arrayType) {
      insertOptimalLoad(mv, size);
      if (arrayType.length() == 1) {
         mv.visitIntInsn(188, arrayCodeFor(arrayType));
      } else if (arrayType.charAt(0) == '[') {
         if (isReferenceTypeArray(arrayType)) {
            mv.visitTypeInsn(189, arrayType + ";");
         } else {
            mv.visitTypeInsn(189, arrayType);
         }
      } else {
         mv.visitTypeInsn(189, arrayType.substring(1));
      }
   }

   public static void insertNumericUnboxOrPrimitiveTypeCoercion(MethodVisitor mv, @Nullable String stackDescriptor, char targetDescriptor) {
      if (!isPrimitive(stackDescriptor)) {
         insertUnboxNumberInsns(mv, targetDescriptor, stackDescriptor);
      } else {
         insertAnyNecessaryTypeConversionBytecodes(mv, targetDescriptor, stackDescriptor);
      }
   }

   public static String toBoxedDescriptor(String primitiveDescriptor) {
      return switch (primitiveDescriptor.charAt(0)) {
         case 'B' -> "Ljava/lang/Byte";
         case 'C' -> "Ljava/lang/Character";
         case 'D' -> "Ljava/lang/Double";
         default -> throw new IllegalArgumentException("Unexpected non primitive descriptor " + primitiveDescriptor);
         case 'F' -> "Ljava/lang/Float";
         case 'I' -> "Ljava/lang/Integer";
         case 'J' -> "Ljava/lang/Long";
         case 'S' -> "Ljava/lang/Short";
         case 'Z' -> "Ljava/lang/Boolean";
      };
   }

   public static void insertOptionalUnwrapIfNecessary(MethodVisitor mv, @Nullable String descriptor) {
      if ("Ljava/util/Optional".equals(descriptor)) {
         mv.visitInsn(1);
         mv.visitMethodInsn(182, "java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
      }
   }

   @FunctionalInterface
   public interface ClinitAdder {
      void generateCode(MethodVisitor mv, CodeFlow codeflow);
   }

   @FunctionalInterface
   public interface FieldAdder {
      void generateField(ClassWriter cw, CodeFlow codeflow);
   }
}
