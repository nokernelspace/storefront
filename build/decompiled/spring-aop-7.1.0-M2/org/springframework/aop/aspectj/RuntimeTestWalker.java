package org.springframework.aop.aspectj;

import java.lang.reflect.Field;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.ast.And;
import org.aspectj.weaver.ast.Call;
import org.aspectj.weaver.ast.FieldGetCall;
import org.aspectj.weaver.ast.HasAnnotation;
import org.aspectj.weaver.ast.ITestVisitor;
import org.aspectj.weaver.ast.Instanceof;
import org.aspectj.weaver.ast.Literal;
import org.aspectj.weaver.ast.Not;
import org.aspectj.weaver.ast.Or;
import org.aspectj.weaver.ast.Test;
import org.aspectj.weaver.internal.tools.MatchingContextBasedTest;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionVar;
import org.aspectj.weaver.reflect.ShadowMatchImpl;
import org.aspectj.weaver.tools.ShadowMatch;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

class RuntimeTestWalker {
   private static final Field residualTestField;
   private static final Field varTypeField;
   private static final Field myClassField;
   private final @Nullable Test runtimeTest;

   public RuntimeTestWalker(ShadowMatch shadowMatch) {
      try {
         ReflectionUtils.makeAccessible(residualTestField);
         this.runtimeTest = (Test)residualTestField.get(shadowMatch);
      } catch (IllegalAccessException ex) {
         throw new IllegalStateException(ex);
      }
   }

   public boolean testsSubtypeSensitiveVars() {
      return this.runtimeTest != null && new RuntimeTestWalker.SubtypeSensitiveVarTypeTestVisitor().testsSubtypeSensitiveVars(this.runtimeTest);
   }

   public boolean testThisInstanceOfResidue(Class<?> thisClass) {
      return this.runtimeTest != null && new RuntimeTestWalker.ThisInstanceOfResidueTestVisitor(thisClass).thisInstanceOfMatches(this.runtimeTest);
   }

   public boolean testTargetInstanceOfResidue(Class<?> targetClass) {
      return this.runtimeTest != null && new RuntimeTestWalker.TargetInstanceOfResidueTestVisitor(targetClass).targetInstanceOfMatches(this.runtimeTest);
   }

   static {
      try {
         residualTestField = ShadowMatchImpl.class.getDeclaredField("residualTest");
         varTypeField = ReflectionVar.class.getDeclaredField("varType");
         myClassField = ReflectionBasedReferenceTypeDelegate.class.getDeclaredField("myClass");
      } catch (NoSuchFieldException ex) {
         throw new IllegalStateException(
            "The version of aspectjtools.jar / aspectjweaver.jar on the classpath is incompatible with this version of Spring: " + ex
         );
      }
   }

   private abstract static class InstanceOfResidueTestVisitor extends RuntimeTestWalker.TestVisitorAdapter {
      private final Class<?> matchClass;
      private boolean matches;
      private final int matchVarType;

      public InstanceOfResidueTestVisitor(Class<?> matchClass, boolean defaultMatches, int matchVarType) {
         this.matchClass = matchClass;
         this.matches = defaultMatches;
         this.matchVarType = matchVarType;
      }

      public boolean instanceOfMatches(Test test) {
         test.accept(this);
         return this.matches;
      }

      @Override
      public void visit(Instanceof i) {
         int varType = this.getVarType((ReflectionVar)i.getVar());
         if (varType == this.matchVarType) {
            Class<?> typeClass = null;
            ResolvedType type = (ResolvedType)i.getType();
            if (type instanceof ReferenceType referenceType) {
               ReferenceTypeDelegate delegate = referenceType.getDelegate();
               if (delegate instanceof ReflectionBasedReferenceTypeDelegate) {
                  try {
                     ReflectionUtils.makeAccessible(RuntimeTestWalker.myClassField);
                     typeClass = (Class<?>)RuntimeTestWalker.myClassField.get(delegate);
                  } catch (IllegalAccessException ex) {
                     throw new IllegalStateException(ex);
                  }
               }
            }

            try {
               if (typeClass == null) {
                  typeClass = ClassUtils.forName(type.getName(), this.matchClass.getClassLoader());
               }

               this.matches = typeClass.isAssignableFrom(this.matchClass);
            } catch (ClassNotFoundException ex) {
               this.matches = false;
            }
         }
      }
   }

   private static class SubtypeSensitiveVarTypeTestVisitor extends RuntimeTestWalker.TestVisitorAdapter {
      private final Object thisObj = new Object();
      private final Object targetObj = new Object();
      private final Object[] argsObjs = new Object[0];
      private boolean testsSubtypeSensitiveVars = false;

      public boolean testsSubtypeSensitiveVars(Test aTest) {
         aTest.accept(this);
         return this.testsSubtypeSensitiveVars;
      }

      @Override
      public void visit(Instanceof i) {
         ReflectionVar v = (ReflectionVar)i.getVar();
         Object varUnderTest = v.getBindingAtJoinPoint(this.thisObj, this.targetObj, this.argsObjs);
         if (varUnderTest == this.thisObj || varUnderTest == this.targetObj) {
            this.testsSubtypeSensitiveVars = true;
         }
      }

      @Override
      public void visit(HasAnnotation hasAnn) {
         ReflectionVar v = (ReflectionVar)hasAnn.getVar();
         int varType = this.getVarType(v);
         if (varType == 3 || varType == 4 || varType == 8) {
            this.testsSubtypeSensitiveVars = true;
         }
      }
   }

   private static class TargetInstanceOfResidueTestVisitor extends RuntimeTestWalker.InstanceOfResidueTestVisitor {
      public TargetInstanceOfResidueTestVisitor(Class<?> targetClass) {
         super(targetClass, false, 1);
      }

      public boolean targetInstanceOfMatches(Test test) {
         return this.instanceOfMatches(test);
      }
   }

   private static class TestVisitorAdapter implements ITestVisitor {
      protected static final int THIS_VAR = 0;
      protected static final int TARGET_VAR = 1;
      protected static final int AT_THIS_VAR = 3;
      protected static final int AT_TARGET_VAR = 4;
      protected static final int AT_ANNOTATION_VAR = 8;

      public void visit(And e) {
         e.getLeft().accept(this);
         e.getRight().accept(this);
      }

      public void visit(Or e) {
         e.getLeft().accept(this);
         e.getRight().accept(this);
      }

      public void visit(Not e) {
         e.getBody().accept(this);
      }

      public void visit(Instanceof i) {
      }

      public void visit(Literal literal) {
      }

      public void visit(Call call) {
      }

      public void visit(FieldGetCall fieldGetCall) {
      }

      public void visit(HasAnnotation hasAnnotation) {
      }

      public void visit(MatchingContextBasedTest matchingContextTest) {
      }

      protected int getVarType(ReflectionVar v) {
         try {
            ReflectionUtils.makeAccessible(RuntimeTestWalker.varTypeField);
            return (Integer)RuntimeTestWalker.varTypeField.get(v);
         } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
         }
      }
   }

   private static class ThisInstanceOfResidueTestVisitor extends RuntimeTestWalker.InstanceOfResidueTestVisitor {
      public ThisInstanceOfResidueTestVisitor(Class<?> thisClass) {
         super(thisClass, true, 0);
      }

      public boolean thisInstanceOfMatches(Test test) {
         return this.instanceOfMatches(test);
      }
   }
}
