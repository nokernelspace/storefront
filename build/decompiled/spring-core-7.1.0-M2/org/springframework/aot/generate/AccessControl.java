package org.springframework.aot.generate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.ClassName;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public final class AccessControl {
   private final Class<?> target;
   private final AccessControl.Visibility visibility;

   AccessControl(Class<?> target, AccessControl.Visibility visibility) {
      this.target = target;
      this.visibility = visibility;
   }

   public static AccessControl forMember(Member member) {
      return new AccessControl(member.getDeclaringClass(), AccessControl.Visibility.forMember(member));
   }

   public static AccessControl forResolvableType(ResolvableType resolvableType) {
      return new AccessControl(resolvableType.toClass(), AccessControl.Visibility.forResolvableType(resolvableType));
   }

   public static AccessControl forClass(Class<?> type) {
      return new AccessControl(type, AccessControl.Visibility.forClass(type));
   }

   public static AccessControl lowest(AccessControl... candidates) {
      int index = AccessControl.Visibility.lowestIndex(Arrays.stream(candidates).map(AccessControl::getVisibility).toArray(AccessControl.Visibility[]::new));
      return candidates[index];
   }

   public AccessControl.Visibility getVisibility() {
      return this.visibility;
   }

   public boolean isPublic() {
      return this.visibility == AccessControl.Visibility.PUBLIC;
   }

   public boolean isAccessibleFrom(ClassName type) {
      if (this.visibility == AccessControl.Visibility.PRIVATE) {
         return false;
      } else {
         return this.visibility == AccessControl.Visibility.PUBLIC ? true : this.target.getPackageName().equals(type.packageName());
      }
   }

   public enum Visibility {
      PUBLIC,
      PROTECTED,
      PACKAGE_PRIVATE,
      PRIVATE;

      private static AccessControl.Visibility forMember(Member member) {
         Assert.notNull(member, "'member' must not be null");
         AccessControl.Visibility visibility = forModifiers(member.getModifiers());
         AccessControl.Visibility declaringClassVisibility = forClass(member.getDeclaringClass());
         visibility = lowest(visibility, declaringClassVisibility);
         if (visibility != PRIVATE) {
            if (member instanceof Field field) {
               AccessControl.Visibility fieldVisibility = forResolvableType(ResolvableType.forField(field));
               return lowest(visibility, fieldVisibility);
            }

            if (member instanceof Constructor<?> constructor) {
               AccessControl.Visibility parameterVisibility = forParameterTypes(constructor, i -> ResolvableType.forConstructorParameter(constructor, i));
               return lowest(visibility, parameterVisibility);
            }

            if (member instanceof Method method) {
               AccessControl.Visibility parameterVisibility = forParameterTypes(method, i -> ResolvableType.forMethodParameter(method, i));
               AccessControl.Visibility returnTypeVisibility = forResolvableType(ResolvableType.forMethodReturnType(method));
               return lowest(visibility, parameterVisibility, returnTypeVisibility);
            }
         }

         return PRIVATE;
      }

      private static AccessControl.Visibility forResolvableType(ResolvableType resolvableType) {
         return forResolvableType(resolvableType, new HashSet<>());
      }

      private static AccessControl.Visibility forResolvableType(ResolvableType resolvableType, Set<ResolvableType> seen) {
         if (!seen.add(resolvableType)) {
            return PUBLIC;
         }

         Class<?> userClass = ClassUtils.getUserClass(resolvableType.toClass());
         ResolvableType userType = resolvableType.as(userClass);
         AccessControl.Visibility visibility = forClass(userType.toClass());

         for (ResolvableType generic : userType.getGenerics()) {
            visibility = lowest(visibility, forResolvableType(generic, seen));
         }

         return visibility;
      }

      private static AccessControl.Visibility forParameterTypes(Executable executable, IntFunction<ResolvableType> resolvableTypeFactory) {
         AccessControl.Visibility visibility = PUBLIC;
         Class<?>[] parameterTypes = executable.getParameterTypes();

         for (int i = 0; i < parameterTypes.length; i++) {
            ResolvableType type = resolvableTypeFactory.apply(i);
            visibility = lowest(visibility, forResolvableType(type));
         }

         return visibility;
      }

      private static AccessControl.Visibility forClass(Class<?> clazz) {
         clazz = ClassUtils.getUserClass(clazz);
         AccessControl.Visibility visibility = forModifiers(clazz.getModifiers());
         if (clazz.isArray()) {
            visibility = lowest(visibility, forClass(clazz.componentType()));
         }

         Class<?> enclosingClass = clazz.getEnclosingClass();
         if (enclosingClass != null) {
            visibility = lowest(visibility, forClass(clazz.getEnclosingClass()));
         }

         return visibility;
      }

      private static AccessControl.Visibility forModifiers(int modifiers) {
         if (Modifier.isPublic(modifiers)) {
            return PUBLIC;
         } else if (Modifier.isProtected(modifiers)) {
            return PROTECTED;
         } else {
            return Modifier.isPrivate(modifiers) ? PRIVATE : PACKAGE_PRIVATE;
         }
      }

      static AccessControl.Visibility lowest(AccessControl.Visibility... candidates) {
         AccessControl.Visibility visibility = PUBLIC;

         for (AccessControl.Visibility candidate : candidates) {
            if (candidate.ordinal() > visibility.ordinal()) {
               visibility = candidate;
            }
         }

         return visibility;
      }

      static int lowestIndex(AccessControl.Visibility... candidates) {
         AccessControl.Visibility visibility = PUBLIC;
         int index = 0;

         for (int i = 0; i < candidates.length; i++) {
            AccessControl.Visibility candidate = candidates[i];
            if (candidate.ordinal() > visibility.ordinal()) {
               visibility = candidate;
               index = i;
            }
         }

         return index;
      }
   }
}
