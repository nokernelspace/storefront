package org.springframework.util;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class TypeUtils {
   private static final @Nullable Type[] IMPLICIT_LOWER_BOUNDS = new Type[]{null};
   private static final Type[] IMPLICIT_UPPER_BOUNDS = new Type[]{Object.class};

   public static boolean isAssignable(Type lhsType, Type rhsType) {
      Assert.notNull(lhsType, "Left-hand side type must not be null");
      Assert.notNull(rhsType, "Right-hand side type must not be null");
      if (!lhsType.equals(rhsType) && Object.class != lhsType) {
         if (lhsType instanceof Class<?> lhsClass) {
            if (rhsType instanceof Class<?> rhsClass) {
               return ClassUtils.isAssignable(lhsClass, rhsClass);
            }

            if (rhsType instanceof ParameterizedType rhsParameterizedType) {
               if (rhsParameterizedType.getRawType() instanceof Class<?> rhRawClass) {
                  return ClassUtils.isAssignable(lhsClass, rhRawClass);
               }
            } else if (lhsClass.isArray() && rhsType instanceof GenericArrayType rhsGenericArrayType) {
               Type rhsComponent = rhsGenericArrayType.getGenericComponentType();
               return isAssignable(lhsClass.componentType(), rhsComponent);
            }
         }

         if (lhsType instanceof ParameterizedType lhsParameterizedType) {
            if (rhsType instanceof Class<?> rhsClass) {
               if (lhsParameterizedType.getRawType() instanceof Class<?> lhsClass) {
                  return ClassUtils.isAssignable(lhsClass, rhsClass);
               }
            } else if (rhsType instanceof ParameterizedType rhsParameterizedType) {
               return isAssignable(lhsParameterizedType, rhsParameterizedType);
            }
         }

         if (lhsType instanceof GenericArrayType lhsGenericArrayType) {
            Type lhsComponent = lhsGenericArrayType.getGenericComponentType();
            if (rhsType instanceof Class<?> rhsClass && rhsClass.isArray()) {
               return isAssignable(lhsComponent, rhsClass.componentType());
            }

            if (rhsType instanceof GenericArrayType rhsGenericArrayType) {
               Type rhsComponent = rhsGenericArrayType.getGenericComponentType();
               return isAssignable(lhsComponent, rhsComponent);
            }
         }

         return lhsType instanceof WildcardType lhsWildcardType ? isAssignable(lhsWildcardType, rhsType) : false;
      } else {
         return true;
      }
   }

   private static boolean isAssignable(ParameterizedType lhsType, ParameterizedType rhsType) {
      if (lhsType.equals(rhsType)) {
         return true;
      }

      Type[] lhsTypeArguments = lhsType.getActualTypeArguments();
      Type[] rhsTypeArguments = rhsType.getActualTypeArguments();
      if (lhsTypeArguments.length != rhsTypeArguments.length) {
         return false;
      }

      int size = lhsTypeArguments.length;

      for (int i = 0; i < size; i++) {
         Type lhsArg = lhsTypeArguments[i];
         Type rhsArg = rhsTypeArguments[i];
         if (!lhsArg.equals(rhsArg) && (!(lhsArg instanceof WildcardType wildcardType) || !isAssignable(wildcardType, rhsArg))) {
            return false;
         }
      }

      return true;
   }

   private static boolean isAssignable(WildcardType lhsType, Type rhsType) {
      Type[] lUpperBounds = getUpperBounds(lhsType);
      Type[] lLowerBounds = getLowerBounds(lhsType);
      if (rhsType instanceof WildcardType rhsWcType) {
         Type[] rUpperBounds = getUpperBounds(rhsWcType);
         Type[] rLowerBounds = getLowerBounds(rhsWcType);

         for (Type lBound : lUpperBounds) {
            for (Type rBound : rUpperBounds) {
               if (!isAssignableBound(lBound, rBound)) {
                  return false;
               }
            }

            for (Type rBound : rLowerBounds) {
               if (!isAssignableBound(lBound, rBound)) {
                  return false;
               }
            }
         }

         for (Type lBound : lLowerBounds) {
            for (Type rBound : rUpperBounds) {
               if (!isAssignableBound(rBound, lBound)) {
                  return false;
               }
            }

            for (Type rBound : rLowerBounds) {
               if (!isAssignableBound(rBound, lBound)) {
                  return false;
               }
            }
         }
      } else {
         for (Type lBound : lUpperBounds) {
            if (!isAssignableBound(lBound, rhsType)) {
               return false;
            }
         }

         for (Type lBound : lLowerBounds) {
            if (!isAssignableBound(rhsType, lBound)) {
               return false;
            }
         }
      }

      return true;
   }

   private static @Nullable Type[] getLowerBounds(WildcardType wildcardType) {
      Type[] lowerBounds = wildcardType.getLowerBounds();
      return lowerBounds.length == 0 ? IMPLICIT_LOWER_BOUNDS : lowerBounds;
   }

   private static Type[] getUpperBounds(WildcardType wildcardType) {
      Type[] upperBounds = wildcardType.getUpperBounds();
      return upperBounds.length == 0 ? IMPLICIT_UPPER_BOUNDS : upperBounds;
   }

   @Contract("_, null -> true; null, _ -> false")
   public static boolean isAssignableBound(@Nullable Type lhsType, @Nullable Type rhsType) {
      if (rhsType == null) {
         return true;
      } else {
         return lhsType == null ? false : isAssignable(lhsType, rhsType);
      }
   }
}
