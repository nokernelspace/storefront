package org.springframework.javapoet;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

public final class WildcardTypeName extends TypeName {
   private final List<TypeName> upperBounds;
   private final List<TypeName> lowerBounds;

   private WildcardTypeName(List<TypeName> upperBounds, List<TypeName> lowerBounds) {
      this(upperBounds, lowerBounds, new ArrayList<>());
   }

   private WildcardTypeName(List<TypeName> upperBounds, List<TypeName> lowerBounds, List<AnnotationSpec> annotations) {
      super(annotations);
      this.upperBounds = Util.immutableList(upperBounds);
      this.lowerBounds = Util.immutableList(lowerBounds);
      Util.checkArgument(this.upperBounds.size() == 1, "unexpected extends bounds: %s", upperBounds);

      for (TypeName upperBound : this.upperBounds) {
         Util.checkArgument(!upperBound.isPrimitive() && upperBound != VOID, "invalid upper bound: %s", upperBound);
      }

      for (TypeName lowerBound : this.lowerBounds) {
         Util.checkArgument(!lowerBound.isPrimitive() && lowerBound != VOID, "invalid lower bound: %s", lowerBound);
      }
   }

   public List<TypeName> upperBounds() {
      return this.upperBounds;
   }

   public List<TypeName> lowerBounds() {
      return this.lowerBounds;
   }

   public WildcardTypeName annotated(AnnotationSpec... annotations) {
      return this.annotated(Arrays.asList(annotations));
   }

   public WildcardTypeName annotated(List<AnnotationSpec> annotations) {
      return new WildcardTypeName(this.upperBounds, this.lowerBounds, this.concatAnnotations(annotations));
   }

   @Override
   public TypeName withoutAnnotations() {
      return new WildcardTypeName(this.upperBounds, this.lowerBounds);
   }

   @Override
   CodeWriter emit(CodeWriter out) throws IOException {
      if (this.lowerBounds.size() == 1) {
         return out.emit("? super $T", this.lowerBounds.get(0));
      } else {
         return this.upperBounds.get(0).equals(ClassName.OBJECT) ? out.emit("?") : out.emit("? extends $T", this.upperBounds.get(0));
      }
   }

   public static WildcardTypeName subtypeOf(TypeName upperBound) {
      return new WildcardTypeName(Collections.singletonList(upperBound), Collections.emptyList());
   }

   public static WildcardTypeName subtypeOf(Type upperBound) {
      return subtypeOf(TypeName.get(upperBound));
   }

   public static WildcardTypeName supertypeOf(TypeName lowerBound) {
      return new WildcardTypeName(Collections.singletonList(ClassName.OBJECT), Collections.singletonList(lowerBound));
   }

   public static WildcardTypeName supertypeOf(Type lowerBound) {
      return supertypeOf(TypeName.get(lowerBound));
   }

   public static TypeName get(WildcardType mirror) {
      return get(mirror, new LinkedHashMap<>());
   }

   static TypeName get(WildcardType mirror, Map<TypeParameterElement, TypeVariableName> typeVariables) {
      TypeMirror extendsBound = mirror.getExtendsBound();
      if (extendsBound == null) {
         TypeMirror superBound = mirror.getSuperBound();
         return superBound == null ? subtypeOf(Object.class) : supertypeOf(TypeName.get(superBound, typeVariables));
      } else {
         return subtypeOf(TypeName.get(extendsBound, typeVariables));
      }
   }

   public static TypeName get(java.lang.reflect.WildcardType wildcardName) {
      return get(wildcardName, new LinkedHashMap<>());
   }

   static TypeName get(java.lang.reflect.WildcardType wildcardName, Map<Type, TypeVariableName> map) {
      return new WildcardTypeName(list(wildcardName.getUpperBounds(), map), list(wildcardName.getLowerBounds(), map));
   }
}
