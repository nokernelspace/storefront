package org.springframework.cglib.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.asm.Type;

public class TypeUtils {
   private static final Map transforms = new HashMap();
   private static final Map rtransforms = new HashMap();

   private TypeUtils() {
   }

   public static Type getType(String className) {
      return Type.getType("L" + className.replace('.', '/') + ";");
   }

   public static boolean isFinal(int access) {
      return (16 & access) != 0;
   }

   public static boolean isStatic(int access) {
      return (8 & access) != 0;
   }

   public static boolean isProtected(int access) {
      return (4 & access) != 0;
   }

   public static boolean isPublic(int access) {
      return (1 & access) != 0;
   }

   public static boolean isAbstract(int access) {
      return (1024 & access) != 0;
   }

   public static boolean isInterface(int access) {
      return (512 & access) != 0;
   }

   public static boolean isPrivate(int access) {
      return (2 & access) != 0;
   }

   public static boolean isSynthetic(int access) {
      return (4096 & access) != 0;
   }

   public static boolean isBridge(int access) {
      return (64 & access) != 0;
   }

   public static String getPackageName(Type type) {
      return getPackageName(getClassName(type));
   }

   public static String getPackageName(String className) {
      int idx = className.lastIndexOf(46);
      return idx < 0 ? "" : className.substring(0, idx);
   }

   public static String upperFirst(String s) {
      return s != null && !s.isEmpty() ? Character.toUpperCase(s.charAt(0)) + s.substring(1) : s;
   }

   public static String getClassName(Type type) {
      if (isPrimitive(type)) {
         return (String)rtransforms.get(type.getDescriptor());
      } else {
         return isArray(type) ? getClassName(getComponentType(type)) + "[]" : type.getClassName();
      }
   }

   public static Type[] add(Type[] types, Type extra) {
      if (types == null) {
         return new Type[]{extra};
      }

      List list = Arrays.asList(types);
      if (list.contains(extra)) {
         return types;
      }

      Type[] copy = new Type[types.length + 1];
      System.arraycopy(types, 0, copy, 0, types.length);
      copy[types.length] = extra;
      return copy;
   }

   public static Type[] add(Type[] t1, Type[] t2) {
      Type[] all = new Type[t1.length + t2.length];
      System.arraycopy(t1, 0, all, 0, t1.length);
      System.arraycopy(t2, 0, all, t1.length, t2.length);
      return all;
   }

   public static Type fromInternalName(String name) {
      return Type.getType("L" + name + ";");
   }

   public static Type[] fromInternalNames(String[] names) {
      if (names == null) {
         return null;
      }

      Type[] types = new Type[names.length];

      for (int i = 0; i < names.length; i++) {
         types[i] = fromInternalName(names[i]);
      }

      return types;
   }

   public static int getStackSize(Type[] types) {
      int size = 0;

      for (Type type : types) {
         size += type.getSize();
      }

      return size;
   }

   public static String[] toInternalNames(Type[] types) {
      if (types == null) {
         return null;
      }

      String[] names = new String[types.length];

      for (int i = 0; i < types.length; i++) {
         names[i] = types[i].getInternalName();
      }

      return names;
   }

   public static Signature parseSignature(String s) {
      int space = s.indexOf(32);
      int lparen = s.indexOf(40, space);
      int rparen = s.indexOf(41, lparen);
      String returnType = s.substring(0, space);
      String methodName = s.substring(space + 1, lparen);
      StringBuilder sb = new StringBuilder();
      sb.append('(');
      Iterator it = parseTypes(s, lparen + 1, rparen).iterator();

      while (it.hasNext()) {
         sb.append(it.next());
      }

      sb.append(')');
      sb.append(map(returnType));
      return new Signature(methodName, sb.toString());
   }

   public static Type parseType(String s) {
      return Type.getType(map(s));
   }

   public static Type[] parseTypes(String s) {
      List names = parseTypes(s, 0, s.length());
      Type[] types = new Type[names.size()];

      for (int i = 0; i < types.length; i++) {
         types[i] = Type.getType((String)names.get(i));
      }

      return types;
   }

   public static Signature parseConstructor(Type[] types) {
      StringBuilder sb = new StringBuilder();
      sb.append("(");

      for (Type type : types) {
         sb.append(type.getDescriptor());
      }

      sb.append(")");
      sb.append("V");
      return new Signature("<init>", sb.toString());
   }

   public static Signature parseConstructor(String sig) {
      return parseSignature("void <init>(" + sig + ")");
   }

   private static List parseTypes(String s, int mark, int end) {
      List types = new ArrayList(5);

      while (true) {
         int next = s.indexOf(44, mark);
         if (next < 0) {
            types.add(map(s.substring(mark, end).trim()));
            return types;
         }

         types.add(map(s.substring(mark, next).trim()));
         mark = next + 1;
      }
   }

   private static String map(String type) {
      if (type.isEmpty()) {
         return type;
      }

      String t = (String)transforms.get(type);
      if (t != null) {
         return t;
      }

      if (type.indexOf(46) < 0) {
         return map("java.lang." + type);
      }

      StringBuilder sb = new StringBuilder();
      int index = 0;

      while ((index = type.indexOf("[]", index) + 1) > 0) {
         sb.append('[');
      }

      type = type.substring(0, type.length() - sb.length() * 2);
      sb.append('L').append(type.replace('.', '/')).append(';');
      return sb.toString();
   }

   public static Type getBoxedType(Type type) {
      return switch (type.getSort()) {
         case 1 -> Constants.TYPE_BOOLEAN;
         case 2 -> Constants.TYPE_CHARACTER;
         case 3 -> Constants.TYPE_BYTE;
         case 4 -> Constants.TYPE_SHORT;
         case 5 -> Constants.TYPE_INTEGER;
         case 6 -> Constants.TYPE_FLOAT;
         case 7 -> Constants.TYPE_LONG;
         case 8 -> Constants.TYPE_DOUBLE;
         default -> type;
      };
   }

   public static Type getUnboxedType(Type type) {
      if (Constants.TYPE_INTEGER.equals(type)) {
         return Type.INT_TYPE;
      } else if (Constants.TYPE_BOOLEAN.equals(type)) {
         return Type.BOOLEAN_TYPE;
      } else if (Constants.TYPE_DOUBLE.equals(type)) {
         return Type.DOUBLE_TYPE;
      } else if (Constants.TYPE_LONG.equals(type)) {
         return Type.LONG_TYPE;
      } else if (Constants.TYPE_CHARACTER.equals(type)) {
         return Type.CHAR_TYPE;
      } else if (Constants.TYPE_BYTE.equals(type)) {
         return Type.BYTE_TYPE;
      } else if (Constants.TYPE_FLOAT.equals(type)) {
         return Type.FLOAT_TYPE;
      } else {
         return Constants.TYPE_SHORT.equals(type) ? Type.SHORT_TYPE : type;
      }
   }

   public static boolean isArray(Type type) {
      return type.getSort() == 9;
   }

   public static Type getComponentType(Type type) {
      if (!isArray(type)) {
         throw new IllegalArgumentException("Type " + type + " is not an array");
      } else {
         return Type.getType(type.getDescriptor().substring(1));
      }
   }

   public static boolean isPrimitive(Type type) {
      return switch (type.getSort()) {
         case 9, 10 -> false;
         default -> true;
      };
   }

   public static String emulateClassGetName(Type type) {
      return isArray(type) ? type.getDescriptor().replace('/', '.') : getClassName(type);
   }

   public static boolean isConstructor(MethodInfo method) {
      return method.getSignature().getName().equals("<init>");
   }

   public static Type[] getTypes(Class[] classes) {
      if (classes == null) {
         return null;
      }

      Type[] types = new Type[classes.length];

      for (int i = 0; i < classes.length; i++) {
         types[i] = Type.getType(classes[i]);
      }

      return types;
   }

   public static int ICONST(int value) {
      return switch (value) {
         case -1 -> 2;
         case 0 -> 3;
         case 1 -> 4;
         case 2 -> 5;
         case 3 -> 6;
         case 4 -> 7;
         case 5 -> 8;
         default -> -1;
      };
   }

   public static int LCONST(long value) {
      if (value == 0L) {
         return 9;
      } else {
         return value == 1L ? 10 : -1;
      }
   }

   public static int FCONST(float value) {
      if (value == 0.0F) {
         return 11;
      } else if (value == 1.0F) {
         return 12;
      } else {
         return value == 2.0F ? 13 : -1;
      }
   }

   public static int DCONST(double value) {
      if (value == 0.0) {
         return 14;
      } else {
         return value == 1.0 ? 15 : -1;
      }
   }

   public static int NEWARRAY(Type type) {
      return switch (type.getSort()) {
         case 1 -> 4;
         case 2 -> 5;
         case 3 -> 8;
         case 4 -> 9;
         case 5 -> 10;
         case 6 -> 6;
         case 7 -> 11;
         case 8 -> 7;
         default -> -1;
      };
   }

   public static String escapeType(String s) {
      StringBuilder sb = new StringBuilder();
      int i = 0;

      for (int len = s.length(); i < len; i++) {
         char c = s.charAt(i);
         switch (c) {
            case '$':
               sb.append("$24");
               break;
            case '(':
               sb.append("$28");
               break;
            case ')':
               sb.append("$29");
               break;
            case '.':
               sb.append("$2E");
               break;
            case '/':
               sb.append("$2F");
               break;
            case ';':
               sb.append("$3B");
               break;
            case '[':
               sb.append("$5B");
               break;
            default:
               sb.append(c);
         }
      }

      return sb.toString();
   }

   static {
      transforms.put("void", "V");
      transforms.put("byte", "B");
      transforms.put("char", "C");
      transforms.put("double", "D");
      transforms.put("float", "F");
      transforms.put("int", "I");
      transforms.put("long", "J");
      transforms.put("short", "S");
      transforms.put("boolean", "Z");
      CollectionUtils.reverse(transforms, rtransforms);
   }
}
