package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

public class TypeName {
   public static final TypeName VOID = new TypeName("void");
   public static final TypeName BOOLEAN = new TypeName("boolean");
   public static final TypeName BYTE = new TypeName("byte");
   public static final TypeName SHORT = new TypeName("short");
   public static final TypeName INT = new TypeName("int");
   public static final TypeName LONG = new TypeName("long");
   public static final TypeName CHAR = new TypeName("char");
   public static final TypeName FLOAT = new TypeName("float");
   public static final TypeName DOUBLE = new TypeName("double");
   private final String keyword;
   private final List<AnnotationSpec> annotations;
   private String cachedString;

   private TypeName(String keyword) {
      this(keyword, new ArrayList<>());
   }

   private TypeName(String keyword, List<AnnotationSpec> annotations) {
      this.keyword = keyword;
      this.annotations = Util.immutableList(annotations);
   }

   TypeName(List<AnnotationSpec> annotations) {
      this(null, annotations);
   }

   public List<AnnotationSpec> annotations() {
      return this.annotations;
   }

   public TypeName annotated(AnnotationSpec... annotations) {
      return this.annotated(Arrays.asList(annotations));
   }

   public TypeName annotated(List<AnnotationSpec> annotations) {
      Util.checkNotNull(annotations, "annotations == null");
      return new TypeName(this.keyword, this.concatAnnotations(annotations));
   }

   public TypeName withoutAnnotations() {
      return this.annotations.isEmpty() ? this : new TypeName(this.keyword);
   }

   protected final List<AnnotationSpec> concatAnnotations(List<AnnotationSpec> annotations) {
      List<AnnotationSpec> allAnnotations = new ArrayList<>(this.annotations);
      allAnnotations.addAll(annotations);
      return allAnnotations;
   }

   public boolean isAnnotated() {
      return !this.annotations.isEmpty();
   }

   public boolean isPrimitive() {
      return this.keyword != null && this != VOID;
   }

   public boolean isBoxedPrimitive() {
      TypeName thisWithoutAnnotations = this.withoutAnnotations();
      return thisWithoutAnnotations.equals(ClassName.BOXED_BOOLEAN)
         || thisWithoutAnnotations.equals(ClassName.BOXED_BYTE)
         || thisWithoutAnnotations.equals(ClassName.BOXED_SHORT)
         || thisWithoutAnnotations.equals(ClassName.BOXED_INT)
         || thisWithoutAnnotations.equals(ClassName.BOXED_LONG)
         || thisWithoutAnnotations.equals(ClassName.BOXED_CHAR)
         || thisWithoutAnnotations.equals(ClassName.BOXED_FLOAT)
         || thisWithoutAnnotations.equals(ClassName.BOXED_DOUBLE);
   }

   public TypeName box() {
      if (this.keyword == null) {
         return this;
      }

      TypeName boxed;
      if (this.keyword.equals(VOID.keyword)) {
         boxed = ClassName.BOXED_VOID;
      } else if (this.keyword.equals(BOOLEAN.keyword)) {
         boxed = ClassName.BOXED_BOOLEAN;
      } else if (this.keyword.equals(BYTE.keyword)) {
         boxed = ClassName.BOXED_BYTE;
      } else if (this.keyword.equals(SHORT.keyword)) {
         boxed = ClassName.BOXED_SHORT;
      } else if (this.keyword.equals(INT.keyword)) {
         boxed = ClassName.BOXED_INT;
      } else if (this.keyword.equals(LONG.keyword)) {
         boxed = ClassName.BOXED_LONG;
      } else if (this.keyword.equals(CHAR.keyword)) {
         boxed = ClassName.BOXED_CHAR;
      } else if (this.keyword.equals(FLOAT.keyword)) {
         boxed = ClassName.BOXED_FLOAT;
      } else {
         if (!this.keyword.equals(DOUBLE.keyword)) {
            throw new IllegalStateException(this.keyword);
         }

         boxed = ClassName.BOXED_DOUBLE;
      }

      return this.annotations.isEmpty() ? boxed : boxed.annotated(this.annotations);
   }

   public TypeName unbox() {
      if (this.keyword != null) {
         return this;
      }

      TypeName thisWithoutAnnotations = this.withoutAnnotations();
      TypeName unboxed;
      if (thisWithoutAnnotations.equals(ClassName.BOXED_VOID)) {
         unboxed = VOID;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_BOOLEAN)) {
         unboxed = BOOLEAN;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_BYTE)) {
         unboxed = BYTE;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_SHORT)) {
         unboxed = SHORT;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_INT)) {
         unboxed = INT;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_LONG)) {
         unboxed = LONG;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_CHAR)) {
         unboxed = CHAR;
      } else if (thisWithoutAnnotations.equals(ClassName.BOXED_FLOAT)) {
         unboxed = FLOAT;
      } else {
         if (!thisWithoutAnnotations.equals(ClassName.BOXED_DOUBLE)) {
            throw new UnsupportedOperationException("cannot unbox " + this);
         }

         unboxed = DOUBLE;
      }

      return this.annotations.isEmpty() ? unboxed : unboxed.annotated(this.annotations);
   }

   @Override
   public final boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o == null) {
         return false;
      } else {
         return this.getClass() != o.getClass() ? false : this.toString().equals(o.toString());
      }
   }

   @Override
   public final int hashCode() {
      return this.toString().hashCode();
   }

   @Override
   public final String toString() {
      String result = this.cachedString;
      if (result == null) {
         try {
            StringBuilder resultBuilder = new StringBuilder();
            CodeWriter codeWriter = new CodeWriter(resultBuilder);
            this.emit(codeWriter);
            result = resultBuilder.toString();
            this.cachedString = result;
         } catch (IOException e) {
            throw new UncheckedIOException(e);
         }
      }

      return result;
   }

   CodeWriter emit(CodeWriter out) throws IOException {
      if (this.keyword == null) {
         throw new IllegalStateException();
      }

      if (this.isAnnotated()) {
         out.emit("");
         this.emitAnnotations(out);
      }

      return out.emitAndIndent(this.keyword);
   }

   CodeWriter emitAnnotations(CodeWriter out) throws IOException {
      for (AnnotationSpec annotation : this.annotations) {
         annotation.emit(out, true);
         out.emit(" ");
      }

      return out;
   }

   public static TypeName get(TypeMirror mirror) {
      return get(mirror, new LinkedHashMap<>());
   }

   static TypeName get(TypeMirror mirror, final Map<TypeParameterElement, TypeVariableName> typeVariables) {
      return mirror.accept(
         new SimpleTypeVisitor8<TypeName, Void>() {
            public TypeName visitPrimitive(PrimitiveType t, Void _p) {
               return switch (t.getKind()) {
                  case BOOLEAN -> TypeName.BOOLEAN;
                  case BYTE -> TypeName.BYTE;
                  case SHORT -> TypeName.SHORT;
                  case INT -> TypeName.INT;
                  case LONG -> TypeName.LONG;
                  case CHAR -> TypeName.CHAR;
                  case FLOAT -> TypeName.FLOAT;
                  case DOUBLE -> TypeName.DOUBLE;
                  default -> throw new IllegalStateException();
               };
            }

            public TypeName visitDeclared(DeclaredType t, Void _p) {
               ClassName rawType = ClassName.get((TypeElement)t.asElement());
               TypeMirror enclosingType = t.getEnclosingType();
               TypeName enclosing = enclosingType.getKind() != TypeKind.NONE && !t.asElement().getModifiers().contains(Modifier.STATIC)
                  ? enclosingType.accept(this, null)
                  : null;
               if (t.getTypeArguments().isEmpty() && !(enclosing instanceof ParameterizedTypeName)) {
                  return rawType;
               }

               List<TypeName> typeArgumentNames = new ArrayList<>();

               for (TypeMirror mirrorx : t.getTypeArguments()) {
                  typeArgumentNames.add(TypeName.get(mirrorx, typeVariables));
               }

               return enclosing instanceof ParameterizedTypeName parameterizedTypeName
                  ? parameterizedTypeName.nestedClass(rawType.simpleName(), typeArgumentNames)
                  : new ParameterizedTypeName(null, rawType, typeArgumentNames);
            }

            public TypeName visitError(ErrorType t, Void _p) {
               return this.visitDeclared(t, null);
            }

            public ArrayTypeName visitArray(ArrayType t, Void _p) {
               return ArrayTypeName.get(t, typeVariables);
            }

            public TypeName visitTypeVariable(javax.lang.model.type.TypeVariable t, Void _p) {
               return TypeVariableName.get(t, typeVariables);
            }

            public TypeName visitWildcard(WildcardType t, Void _p) {
               return WildcardTypeName.get(t, typeVariables);
            }

            public TypeName visitNoType(NoType t, Void _p) {
               return t.getKind() == TypeKind.VOID ? TypeName.VOID : (TypeName)super.visitUnknown(t, null);
            }

            protected TypeName defaultAction(TypeMirror e, Void _p) {
               throw new IllegalArgumentException("Unexpected type mirror: " + e);
            }
         },
         null
      );
   }

   public static TypeName get(Type type) {
      return get(type, new LinkedHashMap<>());
   }

   static TypeName get(Type type, Map<Type, TypeVariableName> map) {
      if (type instanceof Class<?> classType) {
         if (type == void.class) {
            return VOID;
         } else if (type == boolean.class) {
            return BOOLEAN;
         } else if (type == byte.class) {
            return BYTE;
         } else if (type == short.class) {
            return SHORT;
         } else if (type == int.class) {
            return INT;
         } else if (type == long.class) {
            return LONG;
         } else if (type == char.class) {
            return CHAR;
         } else if (type == float.class) {
            return FLOAT;
         } else if (type == double.class) {
            return DOUBLE;
         } else {
            return classType.isArray() ? ArrayTypeName.of(get(classType.getComponentType(), map)) : ClassName.get(classType);
         }
      } else if (type instanceof ParameterizedType parameterizedType) {
         return ParameterizedTypeName.get(parameterizedType, map);
      } else if (type instanceof java.lang.reflect.WildcardType wildcardType) {
         return WildcardTypeName.get(wildcardType, map);
      } else if (type instanceof TypeVariable<?> typeVariable) {
         return TypeVariableName.get(typeVariable, map);
      } else if (type instanceof GenericArrayType genericArrayType) {
         return ArrayTypeName.get(genericArrayType, map);
      } else {
         throw new IllegalArgumentException("unexpected type: " + type);
      }
   }

   static List<TypeName> list(Type[] types) {
      return list(types, new LinkedHashMap<>());
   }

   static List<TypeName> list(Type[] types, Map<Type, TypeVariableName> map) {
      List<TypeName> result = new ArrayList<>(types.length);

      for (Type type : types) {
         result.add(get(type, map));
      }

      return result;
   }

   static TypeName arrayComponent(TypeName type) {
      return type instanceof ArrayTypeName arrayTypeName ? arrayTypeName.componentType() : null;
   }

   static ArrayTypeName asArray(TypeName type) {
      return type instanceof ArrayTypeName arrayTypeName ? arrayTypeName : null;
   }
}
