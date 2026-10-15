package org.springframework.javapoet;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParameterizedTypeName extends TypeName {
   private final ParameterizedTypeName enclosingType;
   private final ClassName rawType;
   private final List<TypeName> typeArguments;

   ParameterizedTypeName(ParameterizedTypeName enclosingType, ClassName rawType, List<TypeName> typeArguments) {
      super(rawType.annotations());
      this.rawType = rawType;
      this.enclosingType = enclosingType;
      this.typeArguments = Util.immutableList(typeArguments);
      Util.checkArgument(!this.typeArguments.isEmpty() || enclosingType != null, "no type arguments: %s", rawType);

      for (TypeName typeArgument : this.typeArguments) {
         Util.checkArgument(!typeArgument.isPrimitive() && typeArgument != VOID, "invalid type parameter: %s", typeArgument);
      }
   }

   public ParameterizedTypeName enclosingType() {
      return this.enclosingType;
   }

   public ClassName rawType() {
      return this.rawType;
   }

   public List<TypeName> typeArguments() {
      return this.typeArguments;
   }

   public ParameterizedTypeName annotated(AnnotationSpec... annotations) {
      return this.annotated(Arrays.asList(annotations));
   }

   public ParameterizedTypeName annotated(List<AnnotationSpec> annotations) {
      return new ParameterizedTypeName(this.enclosingType, this.rawType.annotated(annotations), this.typeArguments);
   }

   @Override
   public TypeName withoutAnnotations() {
      return new ParameterizedTypeName(this.enclosingType, this.rawType.withoutAnnotations(), this.typeArguments);
   }

   @Override
   CodeWriter emit(CodeWriter out) throws IOException {
      if (this.enclosingType != null) {
         this.enclosingType.emit(out);
         out.emit(".");
         if (this.isAnnotated()) {
            out.emit(" ");
            this.emitAnnotations(out);
         }

         out.emit(this.rawType.simpleName());
      } else {
         this.rawType.emit(out);
      }

      if (!this.typeArguments.isEmpty()) {
         out.emitAndIndent("<");
         boolean firstParameter = true;

         for (TypeName parameter : this.typeArguments) {
            if (!firstParameter) {
               out.emitAndIndent(", ");
            }

            parameter.emit(out);
            firstParameter = false;
         }

         out.emitAndIndent(">");
      }

      return out;
   }

   public ParameterizedTypeName nestedClass(String name) {
      Util.checkNotNull(name, "name == null");
      return new ParameterizedTypeName(this, this.rawType.nestedClass(name), new ArrayList<>());
   }

   public ParameterizedTypeName nestedClass(String name, List<TypeName> typeArguments) {
      Util.checkNotNull(name, "name == null");
      return new ParameterizedTypeName(this, this.rawType.nestedClass(name), typeArguments);
   }

   public static ParameterizedTypeName get(ClassName rawType, TypeName... typeArguments) {
      return new ParameterizedTypeName(null, rawType, Arrays.asList(typeArguments));
   }

   public static ParameterizedTypeName get(Class<?> rawType, Type... typeArguments) {
      return new ParameterizedTypeName(null, ClassName.get(rawType), list(typeArguments));
   }

   public static ParameterizedTypeName get(ParameterizedType type) {
      return get(type, new LinkedHashMap<>());
   }

   static ParameterizedTypeName get(ParameterizedType type, Map<Type, TypeVariableName> map) {
      ClassName rawType = ClassName.get((Class<?>)type.getRawType());
      List<TypeName> typeArguments = TypeName.list(type.getActualTypeArguments(), map);
      return type.getOwnerType() instanceof ParameterizedType parameterizedOwnerType && !Modifier.isStatic(((Class)type.getRawType()).getModifiers())
         ? get(parameterizedOwnerType, map).nestedClass(rawType.simpleName(), typeArguments)
         : new ParameterizedTypeName(null, rawType, typeArguments);
   }
}
