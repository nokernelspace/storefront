package org.springframework.javapoet;

import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

public final class TypeVariableName extends TypeName {
   private final String name;
   private final List<TypeName> bounds;

   private TypeVariableName(String name, List<TypeName> bounds) {
      this(name, bounds, new ArrayList<>());
   }

   private TypeVariableName(String name, List<TypeName> bounds, List<AnnotationSpec> annotations) {
      super(annotations);
      this.name = Util.checkNotNull(name, "name == null");
      this.bounds = Util.immutableList(bounds);

      for (TypeName bound : this.bounds) {
         Util.checkArgument(!bound.isPrimitive() && bound != VOID, "invalid bound: %s", bound);
      }
   }

   public String name() {
      return this.name;
   }

   public List<TypeName> bounds() {
      return this.bounds;
   }

   public TypeVariableName annotated(AnnotationSpec... annotations) {
      return this.annotated(Arrays.asList(annotations));
   }

   public TypeVariableName annotated(List<AnnotationSpec> annotations) {
      return new TypeVariableName(this.name, this.bounds, annotations);
   }

   @Override
   public TypeName withoutAnnotations() {
      return new TypeVariableName(this.name, this.bounds);
   }

   public TypeVariableName withBounds(Type... bounds) {
      return this.withBounds(TypeName.list(bounds));
   }

   public TypeVariableName withBounds(TypeName... bounds) {
      return this.withBounds(Arrays.asList(bounds));
   }

   public TypeVariableName withBounds(List<? extends TypeName> bounds) {
      List<TypeName> newBounds = new ArrayList<>();
      newBounds.addAll(this.bounds);
      newBounds.addAll(bounds);
      return new TypeVariableName(this.name, newBounds, this.annotations());
   }

   @Override
   CodeWriter emit(CodeWriter out) throws IOException {
      this.emitAnnotations(out);
      return out.emitAndIndent(this.name);
   }

   private static TypeVariableName of(String name, List<TypeName> bounds) {
      List<TypeName> boundsNoObject = new ArrayList<>(bounds);
      boundsNoObject.remove(ClassName.OBJECT);
      return new TypeVariableName(name, Collections.unmodifiableList(boundsNoObject));
   }

   public static TypeVariableName get(String name) {
      return of(name, Collections.emptyList());
   }

   public static TypeVariableName get(String name, TypeName... bounds) {
      return of(name, Arrays.asList(bounds));
   }

   public static TypeVariableName get(String name, Type... bounds) {
      return of(name, TypeName.list(bounds));
   }

   public static TypeVariableName get(javax.lang.model.type.TypeVariable mirror) {
      return get((TypeParameterElement)mirror.asElement());
   }

   static TypeVariableName get(javax.lang.model.type.TypeVariable mirror, Map<TypeParameterElement, TypeVariableName> typeVariables) {
      TypeParameterElement element = (TypeParameterElement)mirror.asElement();
      TypeVariableName typeVariableName = typeVariables.get(element);
      if (typeVariableName == null) {
         List<TypeName> bounds = new ArrayList<>();

         for (TypeMirror typeMirror : element.getBounds()) {
            typeVariableName = new TypeVariableName(element.getSimpleName().toString(), bounds);
            typeVariables.put(element, typeVariableName);
            bounds.add(TypeName.get(typeMirror, typeVariables));
         }

         bounds.remove(ClassName.OBJECT);
         typeVariableName = new TypeVariableName(element.getSimpleName().toString(), bounds);
         typeVariables.put(element, typeVariableName);
      }

      return typeVariableName;
   }

   public static TypeVariableName get(TypeParameterElement element) {
      String name = element.getSimpleName().toString();
      List<? extends TypeMirror> boundsMirrors = element.getBounds();
      List<TypeName> boundsTypeNames = new ArrayList<>();

      for (TypeMirror typeMirror : boundsMirrors) {
         boundsTypeNames.add(TypeName.get(typeMirror));
      }

      return of(name, boundsTypeNames);
   }

   public static TypeVariableName get(TypeVariable<?> type) {
      return get(type, new LinkedHashMap<>());
   }

   static TypeVariableName get(TypeVariable<?> type, Map<Type, TypeVariableName> map) {
      TypeVariableName result = map.get(type);
      if (result == null) {
         List<TypeName> bounds = new ArrayList<>();
         List<TypeName> visibleBounds = Collections.unmodifiableList(bounds);
         result = new TypeVariableName(type.getName(), visibleBounds);
         map.put(type, result);

         for (Type bound : type.getBounds()) {
            bounds.add(TypeName.get(bound, map));
         }

         bounds.remove(ClassName.OBJECT);
      }

      return result;
   }
}
