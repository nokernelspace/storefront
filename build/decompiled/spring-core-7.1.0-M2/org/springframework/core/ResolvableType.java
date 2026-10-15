package org.springframework.core;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class ResolvableType implements Serializable {
   public static final ResolvableType NONE = new ResolvableType(ResolvableType.EmptyType.INSTANCE, null, null, 0);
   private static final ResolvableType[] EMPTY_TYPES_ARRAY = new ResolvableType[0];
   private static final ConcurrentReferenceHashMap<ResolvableType, ResolvableType> cache = new ConcurrentReferenceHashMap<>(256);
   private final Type type;
   private final @Nullable ResolvableType componentType;
   private final SerializableTypeWrapper.@Nullable TypeProvider typeProvider;
   private final ResolvableType.@Nullable VariableResolver variableResolver;
   private final @Nullable Integer hash;
   private @Nullable Class<?> resolved;
   private transient volatile @Nullable ResolvableType superType;
   private transient volatile ResolvableType @Nullable [] interfaces;
   private transient volatile ResolvableType @Nullable [] generics;
   private transient volatile @Nullable Boolean unresolvableGenerics;

   private ResolvableType(Type type, SerializableTypeWrapper.@Nullable TypeProvider typeProvider, ResolvableType.@Nullable VariableResolver variableResolver) {
      this.type = type;
      this.componentType = null;
      this.typeProvider = typeProvider;
      this.variableResolver = variableResolver;
      this.hash = this.calculateHashCode();
      this.resolved = null;
   }

   private ResolvableType(
      Type type,
      SerializableTypeWrapper.@Nullable TypeProvider typeProvider,
      ResolvableType.@Nullable VariableResolver variableResolver,
      @Nullable Integer hash
   ) {
      this.type = type;
      this.componentType = null;
      this.typeProvider = typeProvider;
      this.variableResolver = variableResolver;
      this.hash = hash;
      this.resolved = this.resolveClass();
   }

   private ResolvableType(
      Type type,
      @Nullable ResolvableType componentType,
      SerializableTypeWrapper.@Nullable TypeProvider typeProvider,
      ResolvableType.@Nullable VariableResolver variableResolver
   ) {
      this.type = type;
      this.componentType = componentType;
      this.typeProvider = typeProvider;
      this.variableResolver = variableResolver;
      this.hash = null;
      this.resolved = this.resolveClass();
   }

   private ResolvableType(@Nullable Class<?> clazz) {
      this.resolved = clazz != null ? clazz : Object.class;
      this.type = this.resolved;
      this.componentType = null;
      this.typeProvider = null;
      this.variableResolver = null;
      this.hash = null;
   }

   public Type getType() {
      return SerializableTypeWrapper.unwrap(this.type);
   }

   public @Nullable Class<?> getRawClass() {
      if (this.type == this.resolved) {
         return this.resolved;
      }

      Type rawType = this.type;
      if (rawType instanceof ParameterizedType parameterizedType) {
         rawType = parameterizedType.getRawType();
      }

      return rawType instanceof Class<?> rawClass ? rawClass : null;
   }

   public Object getSource() {
      Object source = this.typeProvider != null ? this.typeProvider.getSource() : null;
      return source != null ? source : this.type;
   }

   public Class<?> toClass() {
      return this.resolve(Object.class);
   }

   public boolean isInstance(@Nullable Object obj) {
      return obj != null && this.isAssignableFrom(obj.getClass());
   }

   public boolean isAssignableFrom(Class<?> other) {
      return this.type instanceof Class<?> clazz ? ClassUtils.isAssignable(clazz, other) : this.isAssignableFrom(forClass(other), false, null, false);
   }

   public boolean isAssignableFrom(ResolvableType other) {
      return this.isAssignableFrom(other, false, null, false);
   }

   public boolean isAssignableFromResolvedPart(ResolvableType other) {
      return this.isAssignableFrom(other, false, null, true);
   }

   private boolean isAssignableFrom(ResolvableType other, boolean strict, @Nullable Map<Type, Type> matchedBefore, boolean upUntilUnresolvable) {
      Assert.notNull(other, "ResolvableType must not be null");
      if (this != NONE && other != NONE) {
         if (matchedBefore != null) {
            if (matchedBefore.get(this.type) == other.type) {
               return true;
            }
         } else if (this.type instanceof Class<?> clazz && other.type instanceof Class<?> otherClazz) {
            return strict ? clazz.isAssignableFrom(otherClazz) : ClassUtils.isAssignable(clazz, otherClazz);
         }

         if (!upUntilUnresolvable || !other.isUnresolvableTypeVariable() && !other.isWildcardWithoutBounds()) {
            if (this.isArray()) {
               return other.isArray() && this.getComponentType().isAssignableFrom(other.getComponentType(), true, matchedBefore, upUntilUnresolvable);
            }

            ResolvableType.WildcardBounds ourBounds = ResolvableType.WildcardBounds.get(this);
            ResolvableType.WildcardBounds otherBounds = ResolvableType.WildcardBounds.get(other);
            if (otherBounds != null) {
               if (ourBounds != null) {
                  return ourBounds.isSameKind(otherBounds) && ourBounds.isAssignableFrom(otherBounds.getBounds(), matchedBefore);
               } else if (upUntilUnresolvable) {
                  return otherBounds.isAssignableFrom(this, matchedBefore);
               } else if (!strict) {
                  return matchedBefore != null ? otherBounds.equalsType(this, matchedBefore) : otherBounds.isAssignableTo(this, matchedBefore);
               } else {
                  return false;
               }
            } else {
               if (ourBounds != null) {
                  return ourBounds.isAssignableFrom(other, matchedBefore);
               }

               boolean exactMatch = strict && matchedBefore != null;
               boolean checkGenerics = true;
               Class<?> ourResolved = null;
               if (this.type instanceof TypeVariable<?> variable) {
                  if (this.variableResolver != null) {
                     ResolvableType resolved = this.variableResolver.resolveVariable(variable);
                     if (resolved != null) {
                        ourResolved = resolved.resolve();
                     }
                  }

                  if (ourResolved == null && other.variableResolver != null) {
                     ResolvableType resolved = other.variableResolver.resolveVariable(variable);
                     if (resolved != null) {
                        ourResolved = resolved.resolve();
                        checkGenerics = false;
                     }
                  }

                  if (ourResolved == null) {
                     exactMatch = false;
                  }
               }

               if (ourResolved == null) {
                  ourResolved = this.toClass();
               }

               Class<?> otherResolved = other.toClass();
               if (exactMatch
                  ? ourResolved.equals(otherResolved)
                  : (strict ? ourResolved.isAssignableFrom(otherResolved) : ClassUtils.isAssignable(ourResolved, otherResolved))) {
                  if (checkGenerics) {
                     ResolvableType[] ourGenerics = this.getGenerics();
                     ResolvableType[] otherGenerics = other.as(ourResolved).getGenerics();
                     if (ourGenerics.length != otherGenerics.length) {
                        return false;
                     }

                     if (ourGenerics.length > 0) {
                        if (matchedBefore == null) {
                           matchedBefore = new IdentityHashMap<>(1);
                        }

                        matchedBefore.put(this.type, other.type);

                        for (int i = 0; i < ourGenerics.length; i++) {
                           ResolvableType otherGeneric = otherGenerics[i];
                           if (!ourGenerics[i].isAssignableFrom(otherGeneric, !otherGeneric.isUnresolvableTypeVariable(), matchedBefore, upUntilUnresolvable)) {
                              return false;
                           }
                        }
                     }
                  }

                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean isArray() {
      return this == NONE
         ? false
         : this.type instanceof Class<?> clazz && clazz.isArray() || this.type instanceof GenericArrayType || this.resolveType().isArray();
   }

   public ResolvableType getComponentType() {
      if (this == NONE) {
         return NONE;
      } else if (this.componentType != null) {
         return this.componentType;
      } else if (this.type instanceof Class<?> clazz) {
         Class<?> componentType = clazz.componentType();
         return forType(componentType, this.variableResolver);
      } else {
         return this.type instanceof GenericArrayType genericArrayType
            ? forType(genericArrayType.getGenericComponentType(), this.variableResolver)
            : this.resolveType().getComponentType();
      }
   }

   public ResolvableType asCollection() {
      return this.as(Collection.class);
   }

   public ResolvableType asMap() {
      return this.as(Map.class);
   }

   public ResolvableType as(Class<?> type) {
      if (this == NONE) {
         return NONE;
      }

      Class<?> resolved = this.resolve();
      if (resolved != null && resolved != type) {
         for (ResolvableType interfaceType : this.getInterfaces()) {
            ResolvableType interfaceAsType = interfaceType.as(type);
            if (interfaceAsType != NONE) {
               return interfaceAsType;
            }
         }

         return this.getSuperType().as(type);
      } else {
         return this;
      }
   }

   public ResolvableType getSuperType() {
      Class<?> resolved = this.resolve();
      if (resolved == null) {
         return NONE;
      }

      try {
         Type superclass = resolved.getGenericSuperclass();
         if (superclass == null) {
            return NONE;
         }

         ResolvableType superType = this.superType;
         if (superType == null) {
            superType = forType(superclass, this);
            this.superType = superType;
         }

         return superType;
      } catch (TypeNotPresentException ex) {
         return NONE;
      }
   }

   public ResolvableType[] getInterfaces() {
      Class<?> resolved = this.resolve();
      if (resolved == null) {
         return EMPTY_TYPES_ARRAY;
      }

      ResolvableType[] interfaces = this.interfaces;
      if (interfaces == null) {
         Type[] genericIfcs = resolved.getGenericInterfaces();
         if (genericIfcs.length > 0) {
            interfaces = new ResolvableType[genericIfcs.length];

            for (int i = 0; i < genericIfcs.length; i++) {
               interfaces[i] = forType(genericIfcs[i], this);
            }
         } else {
            interfaces = EMPTY_TYPES_ARRAY;
         }

         this.interfaces = interfaces;
      }

      return interfaces;
   }

   public boolean hasGenerics() {
      return this.getGenerics().length > 0;
   }

   public boolean hasResolvableGenerics() {
      if (this == NONE) {
         return false;
      }

      ResolvableType[] generics = this.getGenerics();

      for (ResolvableType generic : generics) {
         if (!generic.isUnresolvableTypeVariable() && !generic.isWildcardWithoutBounds()) {
            return true;
         }
      }

      return false;
   }

   public boolean hasUnresolvableGenerics() {
      return this == NONE ? false : this.hasUnresolvableGenerics(null);
   }

   private boolean hasUnresolvableGenerics(@Nullable Set<Type> alreadySeen) {
      Boolean unresolvableGenerics = this.unresolvableGenerics;
      if (unresolvableGenerics == null) {
         unresolvableGenerics = this.determineUnresolvableGenerics(alreadySeen);
         this.unresolvableGenerics = unresolvableGenerics;
      }

      return unresolvableGenerics;
   }

   private boolean determineUnresolvableGenerics(@Nullable Set<Type> alreadySeen) {
      if (alreadySeen != null && alreadySeen.contains(this.type)) {
         return false;
      }

      ResolvableType[] generics = this.getGenerics();

      for (ResolvableType generic : generics) {
         if (generic.isUnresolvableTypeVariable() || generic.isWildcardWithoutBounds() || generic.hasUnresolvableGenerics(this.currentTypeSeen(alreadySeen))) {
            return true;
         }
      }

      Class<?> resolved = this.resolve();
      if (resolved != null) {
         try {
            for (Type genericInterface : resolved.getGenericInterfaces()) {
               if (genericInterface instanceof Class<?> clazz && clazz.getTypeParameters().length > 0) {
                  return true;
               }
            }
         } catch (TypeNotPresentException var9) {
         }

         Class<?> superclass = resolved.getSuperclass();
         if (superclass != null && superclass != Object.class) {
            return this.getSuperType().hasUnresolvableGenerics(this.currentTypeSeen(alreadySeen));
         }
      }

      return false;
   }

   private Set<Type> currentTypeSeen(@Nullable Set<Type> alreadySeen) {
      if (alreadySeen == null) {
         alreadySeen = new HashSet<>(4);
      }

      alreadySeen.add(this.type);
      return alreadySeen;
   }

   private boolean isUnresolvableTypeVariable() {
      if (this.type instanceof TypeVariable<?> variable) {
         if (this.variableResolver == null) {
            return true;
         }

         ResolvableType resolved = this.variableResolver.resolveVariable(variable);
         if (resolved == null || resolved.isUnresolvableTypeVariable() || resolved.isWildcardWithoutBounds()) {
            return true;
         }
      }

      return false;
   }

   private boolean isWildcardWithoutBounds() {
      if (this.type instanceof WildcardType wildcardType && wildcardType.getLowerBounds().length == 0) {
         Type[] upperBounds = wildcardType.getUpperBounds();
         if (upperBounds.length == 0 || upperBounds.length == 1 && Object.class == upperBounds[0]) {
            return true;
         }
      }

      return false;
   }

   public ResolvableType getNested(int nestingLevel) {
      return this.getNested(nestingLevel, null);
   }

   public ResolvableType getNested(int nestingLevel, @Nullable Map<Integer, Integer> typeIndexesPerLevel) {
      ResolvableType result = this;

      for (int i = 2; i <= nestingLevel; i++) {
         if (result.isArray()) {
            result = result.getComponentType();
         } else {
            while (result != NONE && !result.hasGenerics()) {
               result = result.getSuperType();
            }

            Integer index = typeIndexesPerLevel != null ? typeIndexesPerLevel.get(i) : null;
            index = index == null ? result.getGenerics().length - 1 : index;
            result = result.getGeneric(index);
         }
      }

      return result;
   }

   public ResolvableType getGeneric(int @Nullable ... indexes) {
      ResolvableType[] generics = this.getGenerics();
      if (indexes != null && indexes.length != 0) {
         ResolvableType generic = this;

         for (int index : indexes) {
            generics = generic.getGenerics();
            if (index < 0 || index >= generics.length) {
               return NONE;
            }

            generic = generics[index];
         }

         return generic;
      } else {
         return generics.length == 0 ? NONE : generics[0];
      }
   }

   public ResolvableType[] getGenerics() {
      if (this == NONE) {
         return EMPTY_TYPES_ARRAY;
      }

      ResolvableType[] generics = this.generics;
      if (generics == null) {
         if (this.type instanceof Class<?> clazz) {
            Type[] typeParams = clazz.getTypeParameters();
            if (typeParams.length > 0) {
               generics = new ResolvableType[typeParams.length];

               for (int i = 0; i < generics.length; i++) {
                  generics[i] = forType(typeParams[i], this);
               }
            } else {
               generics = EMPTY_TYPES_ARRAY;
            }
         } else if (this.type instanceof ParameterizedType parameterizedType) {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length > 0) {
               generics = new ResolvableType[actualTypeArguments.length];

               for (int i = 0; i < actualTypeArguments.length; i++) {
                  generics[i] = forType(actualTypeArguments[i], this.variableResolver);
               }
            } else {
               generics = EMPTY_TYPES_ARRAY;
            }
         } else {
            generics = this.resolveType().getGenerics();
         }

         this.generics = generics;
      }

      return generics;
   }

   public Class<?>[] resolveGenerics() {
      ResolvableType[] generics = this.getGenerics();
      Class<?>[] resolvedGenerics = new Class[generics.length];

      for (int i = 0; i < generics.length; i++) {
         resolvedGenerics[i] = generics[i].resolve();
      }

      return resolvedGenerics;
   }

   public Class<?>[] resolveGenerics(Class<?> fallback) {
      ResolvableType[] generics = this.getGenerics();
      Class<?>[] resolvedGenerics = new Class[generics.length];

      for (int i = 0; i < generics.length; i++) {
         resolvedGenerics[i] = generics[i].resolve(fallback);
      }

      return resolvedGenerics;
   }

   public @Nullable Class<?> resolveGeneric(int... indexes) {
      return this.getGeneric(indexes).resolve();
   }

   public @Nullable Class<?> resolve() {
      return this.resolved;
   }

   public Class<?> resolve(Class<?> fallback) {
      return this.resolved != null ? this.resolved : fallback;
   }

   private @Nullable Class<?> resolveClass() {
      if (this.type == ResolvableType.EmptyType.INSTANCE) {
         return null;
      } else if (this.type instanceof Class<?> clazz) {
         return clazz;
      } else if (this.type instanceof GenericArrayType) {
         Class<?> resolvedComponent = this.getComponentType().resolve();
         return resolvedComponent != null ? Array.newInstance(resolvedComponent, 0).getClass() : null;
      } else {
         return this.resolveType().resolve();
      }
   }

   ResolvableType resolveType() {
      if (this.type instanceof ParameterizedType parameterizedType) {
         return forType(parameterizedType.getRawType(), this.variableResolver);
      } else if (this.type instanceof WildcardType wildcardType) {
         Type resolved = resolveBounds(wildcardType.getUpperBounds());
         if (resolved == null) {
            resolved = resolveBounds(wildcardType.getLowerBounds());
         }

         return forType(resolved, this.variableResolver);
      } else if (this.type instanceof TypeVariable<?> variable) {
         if (this.variableResolver != null) {
            ResolvableType resolved = this.variableResolver.resolveVariable(variable);
            if (resolved != null) {
               return resolved;
            }
         }

         return forType(resolveBounds(variable.getBounds()), this.variableResolver);
      } else {
         return NONE;
      }
   }

   private @Nullable ResolvableType resolveVariable(TypeVariable<?> variable) {
      TypeVariable<?> variableToCompare = SerializableTypeWrapper.unwrap(variable);
      if (this.type instanceof TypeVariable) {
         return this.resolveType().resolveVariable(variableToCompare);
      }

      if (this.type instanceof ParameterizedType parameterizedType) {
         Class<?> resolved = this.resolve();
         if (resolved == null) {
            return null;
         }

         TypeVariable<?>[] variables = resolved.getTypeParameters();
         Type[] typeArguments = parameterizedType.getActualTypeArguments();

         for (int i = 0; i < variables.length; i++) {
            if (ObjectUtils.nullSafeEquals(variables[i], variableToCompare)) {
               return forType(typeArguments[i], this.variableResolver);
            }
         }

         Type ownerType = parameterizedType.getOwnerType();
         if (ownerType != null) {
            return forType(ownerType, this.variableResolver).resolveVariable(variableToCompare);
         }

         for (int i = 0; i < variables.length; i++) {
            if (ObjectUtils.nullSafeEquals(variables[i].getName(), variableToCompare.getName())) {
               return forType(typeArguments[i], this.variableResolver);
            }
         }
      }

      if (this.type instanceof WildcardType) {
         ResolvableType resolved = this.resolveType().resolveVariable(variableToCompare);
         if (resolved != null) {
            return resolved;
         }
      }

      return this.variableResolver != null ? this.variableResolver.resolveVariable(variableToCompare) : null;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      }

      if (other != null && other.getClass() == this.getClass()) {
         ResolvableType otherType = (ResolvableType)other;
         if (!this.equalsType(otherType)) {
            return false;
         } else {
            return this.typeProvider == otherType.typeProvider
                  || this.typeProvider != null
                     && otherType.typeProvider != null
                     && ObjectUtils.nullSafeEquals(this.typeProvider.getType(), otherType.typeProvider.getType())
               ? this.variableResolver == otherType.variableResolver
                  || this.variableResolver != null
                     && otherType.variableResolver != null
                     && ObjectUtils.nullSafeEquals(this.variableResolver.getSource(), otherType.variableResolver.getSource())
               : false;
         }
      } else {
         return false;
      }
   }

   public boolean equalsType(ResolvableType otherType) {
      return ObjectUtils.nullSafeEquals(this.type, otherType.type) && ObjectUtils.nullSafeEquals(this.componentType, otherType.componentType);
   }

   @Override
   public int hashCode() {
      return this.hash != null ? this.hash : this.calculateHashCode();
   }

   private int calculateHashCode() {
      int hashCode = ObjectUtils.nullSafeHashCode(this.type);
      if (this.componentType != null) {
         hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.componentType);
      }

      if (this.typeProvider != null) {
         hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.typeProvider.getType());
      }

      if (this.variableResolver != null) {
         hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.variableResolver.getSource());
      }

      return hashCode;
   }

   ResolvableType.@Nullable VariableResolver asVariableResolver() {
      return this == NONE ? null : new ResolvableType.DefaultVariableResolver(this);
   }

   private Object readResolve() {
      return this.type == ResolvableType.EmptyType.INSTANCE ? NONE : this;
   }

   @Override
   public String toString() {
      if (this.isArray()) {
         return this.getComponentType() + "[]";
      } else if (this.resolved == null) {
         return "?";
      } else if (this.type instanceof TypeVariable<?> variable && (this.variableResolver == null || this.variableResolver.resolveVariable(variable) == null)) {
         return "?";
      } else {
         return this.hasGenerics()
            ? this.resolved.getName() + "<" + StringUtils.arrayToDelimitedString(this.getGenerics(), ", ") + ">"
            : this.resolved.getName();
      }
   }

   public static ResolvableType forClass(@Nullable Class<?> clazz) {
      return new ResolvableType(clazz);
   }

   public static ResolvableType forRawClass(@Nullable Class<?> clazz) {
      return new ResolvableType(clazz) {
         @Override
         public ResolvableType[] getGenerics() {
            return ResolvableType.EMPTY_TYPES_ARRAY;
         }

         @Override
         public boolean isAssignableFrom(Class<?> other) {
            return clazz == null || ClassUtils.isAssignable(clazz, other);
         }

         @Override
         public boolean isAssignableFrom(ResolvableType other) {
            Class<?> otherClass = other.resolve();
            return otherClass != null && (clazz == null || ClassUtils.isAssignable(clazz, otherClass));
         }
      };
   }

   public static ResolvableType forClass(Class<?> baseType, Class<?> implementationClass) {
      Assert.notNull(baseType, "Base type must not be null");
      ResolvableType asType = forType(implementationClass).as(baseType);
      return asType == NONE ? forType(baseType) : asType;
   }

   public static ResolvableType forClassWithGenerics(Class<?> clazz, Class<?>... generics) {
      Assert.notNull(generics, "Generics array must not be null");
      ResolvableType[] resolvableGenerics = new ResolvableType[generics.length];

      for (int i = 0; i < generics.length; i++) {
         resolvableGenerics[i] = forClass(generics[i]);
      }

      return forClassWithGenerics(clazz, resolvableGenerics);
   }

   public static ResolvableType forClassWithGenerics(Class<?> clazz, @Nullable ResolvableType @Nullable ... generics) {
      Assert.notNull(clazz, "Class must not be null");
      TypeVariable<?>[] variables = clazz.getTypeParameters();
      if (generics != null) {
         Assert.isTrue(variables.length == generics.length, () -> "Mismatched number of generics specified for " + clazz.toGenericString());
      }

      Type[] arguments = new Type[variables.length];

      for (int i = 0; i < variables.length; i++) {
         ResolvableType generic = generics != null ? generics[i] : null;
         Type argument = generic != null ? generic.getType() : null;
         arguments[i] = argument != null && !(argument instanceof TypeVariable) ? argument : variables[i];
      }

      return forType(
         new ResolvableType.SyntheticParameterizedType(clazz, arguments),
         generics != null ? new ResolvableType.TypeVariablesVariableResolver(variables, generics) : null
      );
   }

   public static ResolvableType forInstance(@Nullable Object instance) {
      if (instance instanceof ResolvableTypeProvider resolvableTypeProvider) {
         ResolvableType type = resolvableTypeProvider.getResolvableType();
         if (type != null) {
            return type;
         }
      }

      return instance != null ? forClass(instance.getClass()) : NONE;
   }

   public static ResolvableType forField(Field field) {
      Assert.notNull(field, "Field must not be null");
      return forType(null, new SerializableTypeWrapper.FieldTypeProvider(field), null);
   }

   public static ResolvableType forField(Field field, Class<?> implementationClass) {
      Assert.notNull(field, "Field must not be null");
      ResolvableType owner = forType(implementationClass).as(field.getDeclaringClass());
      return forType(null, new SerializableTypeWrapper.FieldTypeProvider(field), owner.asVariableResolver());
   }

   public static ResolvableType forField(Field field, @Nullable ResolvableType implementationType) {
      Assert.notNull(field, "Field must not be null");
      ResolvableType owner = implementationType != null ? implementationType : NONE;
      owner = owner.as(field.getDeclaringClass());
      return forType(null, new SerializableTypeWrapper.FieldTypeProvider(field), owner.asVariableResolver());
   }

   public static ResolvableType forField(Field field, int nestingLevel) {
      Assert.notNull(field, "Field must not be null");
      return forType(null, new SerializableTypeWrapper.FieldTypeProvider(field), null).getNested(nestingLevel);
   }

   public static ResolvableType forField(Field field, int nestingLevel, @Nullable Class<?> implementationClass) {
      Assert.notNull(field, "Field must not be null");
      ResolvableType owner = forType(implementationClass).as(field.getDeclaringClass());
      return forType(null, new SerializableTypeWrapper.FieldTypeProvider(field), owner.asVariableResolver()).getNested(nestingLevel);
   }

   public static ResolvableType forParameter(Parameter parameter) {
      return forMethodParameter(MethodParameter.forParameter(parameter));
   }

   public static ResolvableType forConstructorParameter(Constructor<?> constructor, int parameterIndex) {
      return forMethodParameter(new MethodParameter(constructor, parameterIndex));
   }

   public static ResolvableType forConstructorParameter(Constructor<?> constructor, int parameterIndex, Class<?> implementationClass) {
      MethodParameter methodParameter = new MethodParameter(constructor, parameterIndex, implementationClass);
      return forMethodParameter(methodParameter);
   }

   public static ResolvableType forMethodReturnType(Method method) {
      return forMethodParameter(new MethodParameter(method, -1));
   }

   public static ResolvableType forMethodReturnType(Method method, Class<?> implementationClass) {
      MethodParameter methodParameter = new MethodParameter(method, -1, implementationClass);
      return forMethodParameter(methodParameter);
   }

   public static ResolvableType forMethodParameter(Method method, int parameterIndex) {
      return forMethodParameter(new MethodParameter(method, parameterIndex));
   }

   public static ResolvableType forMethodParameter(Method method, int parameterIndex, Class<?> implementationClass) {
      MethodParameter methodParameter = new MethodParameter(method, parameterIndex, implementationClass);
      return forMethodParameter(methodParameter);
   }

   public static ResolvableType forMethodParameter(MethodParameter methodParameter) {
      return forMethodParameter(methodParameter, (Type)null);
   }

   public static ResolvableType forMethodParameter(MethodParameter methodParameter, @Nullable ResolvableType implementationType) {
      Assert.notNull(methodParameter, "MethodParameter must not be null");
      implementationType = implementationType != null ? implementationType : forType(methodParameter.getContainingClass());
      ResolvableType owner = implementationType.as(methodParameter.getDeclaringClass());
      return forType(null, new SerializableTypeWrapper.MethodParameterTypeProvider(methodParameter), owner.asVariableResolver())
         .getNested(methodParameter.getNestingLevel(), methodParameter.typeIndexesPerLevel);
   }

   public static ResolvableType forMethodParameter(MethodParameter methodParameter, @Nullable Type targetType) {
      Assert.notNull(methodParameter, "MethodParameter must not be null");
      return forMethodParameter(methodParameter, targetType, methodParameter.getNestingLevel());
   }

   static ResolvableType forMethodParameter(MethodParameter methodParameter, @Nullable Type targetType, int nestingLevel) {
      ResolvableType owner = forType(methodParameter.getContainingClass()).as(methodParameter.getDeclaringClass());
      return forType(targetType, new SerializableTypeWrapper.MethodParameterTypeProvider(methodParameter), owner.asVariableResolver())
         .getNested(nestingLevel, methodParameter.typeIndexesPerLevel);
   }

   public static ResolvableType forArrayComponent(ResolvableType componentType) {
      Assert.notNull(componentType, "Component type must not be null");
      Class<?> arrayType = componentType.toClass().arrayType();
      return new ResolvableType(arrayType, componentType, null, null);
   }

   static ResolvableType forVariableBounds(TypeVariable<?> typeVariable) {
      return forType(resolveBounds(typeVariable.getBounds()));
   }

   private static @Nullable Type resolveBounds(Type[] bounds) {
      return bounds.length != 0 && bounds[0] != Object.class ? bounds[0] : null;
   }

   public static ResolvableType forType(@Nullable Type type) {
      return forType(type, null, null);
   }

   public static ResolvableType forType(@Nullable Type type, @Nullable ResolvableType owner) {
      ResolvableType.VariableResolver variableResolver = null;
      if (owner != null) {
         variableResolver = owner.asVariableResolver();
      }

      return forType(type, variableResolver);
   }

   public static ResolvableType forType(ParameterizedTypeReference<?> typeReference) {
      return forType(typeReference.getType(), null, null);
   }

   static ResolvableType forType(@Nullable Type type, ResolvableType.@Nullable VariableResolver variableResolver) {
      return forType(type, null, variableResolver);
   }

   static ResolvableType forType(
      @Nullable Type type, SerializableTypeWrapper.@Nullable TypeProvider typeProvider, ResolvableType.@Nullable VariableResolver variableResolver
   ) {
      if (type == null && typeProvider != null) {
         type = SerializableTypeWrapper.forTypeProvider(typeProvider);
      }

      if (type == null) {
         return NONE;
      }

      if (type instanceof Class) {
         return new ResolvableType(type, null, typeProvider, variableResolver);
      }

      ResolvableType resultType = new ResolvableType(type, typeProvider, variableResolver);
      ResolvableType cachedType = cache.get(resultType);
      if (cachedType == null) {
         cachedType = new ResolvableType(type, typeProvider, variableResolver, resultType.hash);
         cache.put(cachedType, cachedType);
      }

      resultType.resolved = cachedType.resolved;
      return resultType;
   }

   public static void clearCache() {
      cache.clear();
      SerializableTypeWrapper.cache.clear();
   }

   private static class DefaultVariableResolver implements ResolvableType.VariableResolver {
      private final ResolvableType source;

      DefaultVariableResolver(ResolvableType resolvableType) {
         this.source = resolvableType;
      }

      @Override
      public @Nullable ResolvableType resolveVariable(TypeVariable<?> variable) {
         return this.source.resolveVariable(variable);
      }

      @Override
      public Object getSource() {
         return this.source;
      }
   }

   static class EmptyType implements Type, Serializable {
      static final Type INSTANCE = new ResolvableType.EmptyType();

      Object readResolve() {
         return INSTANCE;
      }
   }

   private static final class SyntheticParameterizedType implements ParameterizedType, Serializable {
      private final Type rawType;
      private final Type[] typeArguments;

      public SyntheticParameterizedType(Type rawType, Type[] typeArguments) {
         this.rawType = rawType;
         this.typeArguments = typeArguments;
      }

      @Override
      public String getTypeName() {
         String typeName = this.rawType.getTypeName();
         if (this.typeArguments.length <= 0) {
            return typeName;
         }

         StringJoiner stringJoiner = new StringJoiner(", ", "<", ">");

         for (Type argument : this.typeArguments) {
            stringJoiner.add(argument.getTypeName());
         }

         return typeName + stringJoiner;
      }

      @Override
      public @Nullable Type getOwnerType() {
         return null;
      }

      @Override
      public Type getRawType() {
         return this.rawType;
      }

      @Override
      public Type[] getActualTypeArguments() {
         return this.typeArguments;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof ParameterizedType that
               && that.getOwnerType() == null
               && this.rawType.equals(that.getRawType())
               && Arrays.equals(this.typeArguments, that.getActualTypeArguments());
      }

      @Override
      public int hashCode() {
         return this.rawType.hashCode() * 31 + Arrays.hashCode(this.typeArguments);
      }

      @Override
      public String toString() {
         return this.getTypeName();
      }
   }

   private static class TypeVariablesVariableResolver implements ResolvableType.VariableResolver {
      private final TypeVariable<?>[] variables;
      private final @Nullable ResolvableType[] generics;

      public TypeVariablesVariableResolver(TypeVariable<?>[] variables, @Nullable ResolvableType[] generics) {
         this.variables = variables;
         this.generics = generics;
      }

      @Override
      public @Nullable ResolvableType resolveVariable(TypeVariable<?> variable) {
         TypeVariable<?> variableToCompare = SerializableTypeWrapper.unwrap(variable);

         for (int i = 0; i < this.variables.length; i++) {
            if (ObjectUtils.nullSafeEquals(this.variables[i], variableToCompare)) {
               return this.generics[i];
            }
         }

         return null;
      }

      @Override
      public Object getSource() {
         return this.generics;
      }
   }

   interface VariableResolver extends Serializable {
      Object getSource();

      @Nullable ResolvableType resolveVariable(TypeVariable<?> variable);
   }

   private static class WildcardBounds {
      private final ResolvableType.WildcardBounds.Kind kind;
      private final ResolvableType[] bounds;

      public WildcardBounds(ResolvableType.WildcardBounds.Kind kind, ResolvableType[] bounds) {
         this.kind = kind;
         this.bounds = bounds;
      }

      public boolean isSameKind(ResolvableType.WildcardBounds bounds) {
         return this.kind == bounds.kind;
      }

      public boolean isAssignableFrom(ResolvableType[] types, @Nullable Map<Type, Type> matchedBefore) {
         for (ResolvableType bound : this.bounds) {
            boolean matched = false;

            for (ResolvableType type : types) {
               if (this.kind == ResolvableType.WildcardBounds.Kind.UPPER
                  ? bound.isAssignableFrom(type, false, matchedBefore, false)
                  : type.isAssignableFrom(bound, false, matchedBefore, false)) {
                  matched = true;
                  break;
               }
            }

            if (!matched) {
               return false;
            }
         }

         return true;
      }

      public boolean isAssignableFrom(ResolvableType type, @Nullable Map<Type, Type> matchedBefore) {
         for (ResolvableType bound : this.bounds) {
            if (this.kind == ResolvableType.WildcardBounds.Kind.UPPER
               ? !bound.isAssignableFrom(type, false, matchedBefore, false)
               : !type.isAssignableFrom(bound, false, matchedBefore, false)) {
               return false;
            }
         }

         return true;
      }

      public boolean isAssignableTo(ResolvableType type, @Nullable Map<Type, Type> matchedBefore) {
         if (this.kind == ResolvableType.WildcardBounds.Kind.UPPER) {
            for (ResolvableType bound : this.bounds) {
               if (type.isAssignableFrom(bound, false, matchedBefore, false)) {
                  return true;
               }
            }

            return false;
         } else {
            return type.resolve() == Object.class;
         }
      }

      public boolean equalsType(ResolvableType type, @Nullable Map<Type, Type> matchedBefore) {
         for (ResolvableType bound : this.bounds) {
            if (this.kind == ResolvableType.WildcardBounds.Kind.UPPER && bound.hasUnresolvableGenerics()
               ? !type.isAssignableFrom(bound, true, matchedBefore, false)
               : !type.equalsType(bound)) {
               return false;
            }
         }

         return true;
      }

      public ResolvableType[] getBounds() {
         return this.bounds;
      }

      public static ResolvableType.@Nullable WildcardBounds get(ResolvableType type) {
         ResolvableType candidate;
         for (candidate = type; !(candidate.getType() instanceof WildcardType) && !candidate.isUnresolvableTypeVariable(); candidate = candidate.resolveType()) {
            if (candidate == ResolvableType.NONE) {
               return null;
            }
         }

         ResolvableType.WildcardBounds.Kind boundsType;
         Type[] bounds;
         if (candidate.getType() instanceof WildcardType wildcardType) {
            boundsType = wildcardType.getLowerBounds().length > 0 ? ResolvableType.WildcardBounds.Kind.LOWER : ResolvableType.WildcardBounds.Kind.UPPER;
            bounds = boundsType == ResolvableType.WildcardBounds.Kind.UPPER ? wildcardType.getUpperBounds() : wildcardType.getLowerBounds();
         } else {
            boundsType = ResolvableType.WildcardBounds.Kind.UPPER;
            bounds = ((TypeVariable)candidate.getType()).getBounds();
         }

         ResolvableType[] resolvableBounds = new ResolvableType[bounds.length];

         for (int i = 0; i < bounds.length; i++) {
            resolvableBounds[i] = ResolvableType.forType(bounds[i], type.variableResolver);
         }

         return new ResolvableType.WildcardBounds(boundsType, resolvableBounds);
      }

      enum Kind {
         UPPER,
         LOWER;
      }
   }
}
