package org.springframework.javapoet;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;

public final class ArrayTypeName extends TypeName {
   private final TypeName componentType;

   private ArrayTypeName(TypeName componentType) {
      this(componentType, new ArrayList<>());
   }

   private ArrayTypeName(TypeName componentType, List<AnnotationSpec> annotations) {
      super(annotations);
      this.componentType = Util.checkNotNull(componentType, "rawType == null");
   }

   public TypeName componentType() {
      return this.componentType;
   }

   public ArrayTypeName annotated(AnnotationSpec... annotations) {
      return this.annotated(Arrays.asList(annotations));
   }

   public ArrayTypeName annotated(List<AnnotationSpec> annotations) {
      return new ArrayTypeName(this.componentType, this.concatAnnotations(annotations));
   }

   @Override
   public TypeName withoutAnnotations() {
      return new ArrayTypeName(this.componentType);
   }

   @Override
   CodeWriter emit(CodeWriter out) throws IOException {
      return this.emit(out, false);
   }

   CodeWriter emit(CodeWriter out, boolean varargs) throws IOException {
      this.emitLeafType(out);
      return this.emitBrackets(out, varargs);
   }

   private CodeWriter emitLeafType(CodeWriter out) throws IOException {
      return TypeName.asArray(this.componentType) != null ? TypeName.asArray(this.componentType).emitLeafType(out) : this.componentType.emit(out);
   }

   private CodeWriter emitBrackets(CodeWriter out, boolean varargs) throws IOException {
      if (this.isAnnotated()) {
         out.emit(" ");
         this.emitAnnotations(out);
      }

      if (TypeName.asArray(this.componentType) == null) {
         return out.emit(varargs ? "..." : "[]");
      }

      out.emit("[]");
      return TypeName.asArray(this.componentType).emitBrackets(out, varargs);
   }

   public static ArrayTypeName of(TypeName componentType) {
      return new ArrayTypeName(componentType);
   }

   public static ArrayTypeName of(Type componentType) {
      return of(TypeName.get(componentType));
   }

   public static ArrayTypeName get(ArrayType mirror) {
      return get(mirror, new LinkedHashMap<>());
   }

   static ArrayTypeName get(ArrayType mirror, Map<TypeParameterElement, TypeVariableName> typeVariables) {
      return new ArrayTypeName(get(mirror.getComponentType(), typeVariables));
   }

   public static ArrayTypeName get(GenericArrayType type) {
      return get(type, new LinkedHashMap<>());
   }

   static ArrayTypeName get(GenericArrayType type, Map<Type, TypeVariableName> map) {
      return of(get(type.getGenericComponentType(), map));
   }
}
