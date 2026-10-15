package org.springframework.aop.aspectj;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.StringUtils;

public class AspectJAdviceParameterNameDiscoverer implements ParameterNameDiscoverer {
   private static final String THIS_JOIN_POINT = "thisJoinPoint";
   private static final String THIS_JOIN_POINT_STATIC_PART = "thisJoinPointStaticPart";
   private static final int STEP_JOIN_POINT_BINDING = 1;
   private static final int STEP_THROWING_BINDING = 2;
   private static final int STEP_ANNOTATION_BINDING = 3;
   private static final int STEP_RETURNING_BINDING = 4;
   private static final int STEP_PRIMITIVE_ARGS_BINDING = 5;
   private static final int STEP_THIS_TARGET_ARGS_BINDING = 6;
   private static final int STEP_REFERENCE_PCUT_BINDING = 7;
   private static final int STEP_FINISHED = 8;
   private static final Set<String> singleValuedAnnotationPcds = Set.of("@this", "@target", "@within", "@withincode", "@annotation");
   private static final Set<String> nonReferencePointcutTokens = new HashSet<>();
   private final @Nullable String pointcutExpression;
   private boolean raiseExceptions;
   private @Nullable String returningName;
   private @Nullable String throwingName;
   private Class<?>[] argumentTypes = new Class[0];
   private @Nullable String[] parameterNameBindings = new String[0];
   private int numberOfRemainingUnboundArguments;

   public AspectJAdviceParameterNameDiscoverer(@Nullable String pointcutExpression) {
      this.pointcutExpression = pointcutExpression;
   }

   public void setRaiseExceptions(boolean raiseExceptions) {
      this.raiseExceptions = raiseExceptions;
   }

   public void setReturningName(@Nullable String returningName) {
      this.returningName = returningName;
   }

   public void setThrowingName(@Nullable String throwingName) {
      this.throwingName = throwingName;
   }

   public @Nullable String @Nullable [] getParameterNames(Method method) {
      this.argumentTypes = method.getParameterTypes();
      this.numberOfRemainingUnboundArguments = this.argumentTypes.length;
      this.parameterNameBindings = new String[this.numberOfRemainingUnboundArguments];
      int minimumNumberUnboundArgs = 0;
      if (this.returningName != null) {
         minimumNumberUnboundArgs++;
      }

      if (this.throwingName != null) {
         minimumNumberUnboundArgs++;
      }

      if (this.numberOfRemainingUnboundArguments < minimumNumberUnboundArgs) {
         throw new IllegalStateException("Not enough arguments in method to satisfy binding of returning and throwing variables");
      }

      try {
         int algorithmicStep = 1;

         while (this.numberOfRemainingUnboundArguments > 0 && algorithmicStep < 8) {
            switch (algorithmicStep++) {
               case 1:
                  if (!this.maybeBindThisJoinPoint()) {
                     this.maybeBindThisJoinPointStaticPart();
                  }
                  break;
               case 2:
                  this.maybeBindThrowingVariable();
                  break;
               case 3:
                  this.maybeBindAnnotationsFromPointcutExpression();
                  break;
               case 4:
                  this.maybeBindReturningVariable();
                  break;
               case 5:
                  this.maybeBindPrimitiveArgsFromPointcutExpression();
                  break;
               case 6:
                  this.maybeBindThisOrTargetOrArgsFromPointcutExpression();
                  break;
               case 7:
                  this.maybeBindReferencePointcutParameter();
                  break;
               default:
                  throw new IllegalStateException("Unknown algorithmic step: " + (algorithmicStep - 1));
            }
         }
      } catch (AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException | IllegalArgumentException ex) {
         if (this.raiseExceptions) {
            throw ex;
         }

         return null;
      }

      if (this.numberOfRemainingUnboundArguments == 0) {
         return this.parameterNameBindings;
      } else if (this.raiseExceptions) {
         throw new IllegalStateException("Failed to bind all argument names: " + this.numberOfRemainingUnboundArguments + " argument(s) could not be bound");
      } else {
         return null;
      }
   }

   public String @Nullable [] getParameterNames(Constructor<?> ctor) {
      if (this.raiseExceptions) {
         throw new UnsupportedOperationException("An advice method can never be a constructor");
      } else {
         return null;
      }
   }

   private void bindParameterName(int index, @Nullable String name) {
      this.parameterNameBindings[index] = name;
      this.numberOfRemainingUnboundArguments--;
   }

   private boolean maybeBindThisJoinPoint() {
      if (this.argumentTypes[0] != JoinPoint.class && this.argumentTypes[0] != ProceedingJoinPoint.class) {
         return false;
      }

      this.bindParameterName(0, "thisJoinPoint");
      return true;
   }

   private void maybeBindThisJoinPointStaticPart() {
      if (this.argumentTypes[0] == StaticPart.class) {
         this.bindParameterName(0, "thisJoinPointStaticPart");
      }
   }

   private void maybeBindThrowingVariable() {
      if (this.throwingName != null) {
         int throwableIndex = -1;

         for (int i = 0; i < this.argumentTypes.length; i++) {
            if (this.isUnbound(i) && this.isSubtypeOf(Throwable.class, i)) {
               if (throwableIndex != -1) {
                  throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
                     "Binding of throwing parameter '" + this.throwingName + "' is ambiguous: could be bound to argument " + throwableIndex + " or " + i
                  );
               }

               throwableIndex = i;
            }
         }

         if (throwableIndex == -1) {
            throw new IllegalStateException(
               "Binding of throwing parameter '" + this.throwingName + "' could not be completed as no available arguments are a subtype of Throwable"
            );
         }

         this.bindParameterName(throwableIndex, this.throwingName);
      }
   }

   private void maybeBindReturningVariable() {
      if (this.numberOfRemainingUnboundArguments == 0) {
         throw new IllegalStateException("Algorithm assumes that there must be at least one unbound parameter on entry to this method");
      }

      if (this.returningName != null) {
         if (this.numberOfRemainingUnboundArguments > 1) {
            throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
               "Binding of returning parameter '"
                  + this.returningName
                  + "' is ambiguous: there are "
                  + this.numberOfRemainingUnboundArguments
                  + " candidates. Consider compiling with -parameters in order to make declared parameter names available."
            );
         }

         for (int i = 0; i < this.parameterNameBindings.length; i++) {
            if (this.parameterNameBindings[i] == null) {
               this.bindParameterName(i, this.returningName);
               break;
            }
         }
      }
   }

   private void maybeBindAnnotationsFromPointcutExpression() {
      List<String> varNames = new ArrayList<>();
      String[] tokens = StringUtils.tokenizeToStringArray(this.pointcutExpression, " ");

      for (int i = 0; i < tokens.length; i++) {
         String toMatch = tokens[i];
         int firstParenIndex = toMatch.indexOf(40);
         if (firstParenIndex != -1) {
            toMatch = toMatch.substring(0, firstParenIndex);
         }

         if (singleValuedAnnotationPcds.contains(toMatch)) {
            AspectJAdviceParameterNameDiscoverer.PointcutBody body = this.getPointcutBody(tokens, i);
            i += body.numTokensConsumed;
            String varName = this.maybeExtractVariableName(body.text);
            if (varName != null) {
               varNames.add(varName);
            }
         } else if (tokens[i].startsWith("@args(") || tokens[i].equals("@args")) {
            AspectJAdviceParameterNameDiscoverer.PointcutBody body = this.getPointcutBody(tokens, i);
            i += body.numTokensConsumed;
            this.maybeExtractVariableNamesFromArgs(body.text, varNames);
         }
      }

      this.bindAnnotationsFromVarNames(varNames);
   }

   private void bindAnnotationsFromVarNames(List<String> varNames) {
      if (!varNames.isEmpty()) {
         int numAnnotationSlots = this.countNumberOfUnboundAnnotationArguments();
         if (numAnnotationSlots > 1) {
            throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
               "Found " + varNames.size() + " potential annotation variable(s) and " + numAnnotationSlots + " potential argument slots"
            );
         }

         if (numAnnotationSlots == 1) {
            if (varNames.size() != 1) {
               throw new IllegalArgumentException(
                  "Found " + varNames.size() + " candidate annotation binding variables but only one potential argument binding slot"
               );
            }

            this.findAndBind(Annotation.class, varNames.get(0));
         }
      }
   }

   private @Nullable String maybeExtractVariableName(@Nullable String candidateToken) {
      return AspectJProxyUtils.isVariableName(candidateToken) ? candidateToken : null;
   }

   private void maybeExtractVariableNamesFromArgs(@Nullable String argsSpec, List<String> varNames) {
      if (argsSpec != null) {
         String[] tokens = StringUtils.tokenizeToStringArray(argsSpec, ",");

         for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].strip();
            String varName = this.maybeExtractVariableName(tokens[i]);
            if (varName != null) {
               varNames.add(varName);
            }
         }
      }
   }

   private void maybeBindThisOrTargetOrArgsFromPointcutExpression() {
      if (this.numberOfRemainingUnboundArguments > 1) {
         throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
            "Still " + this.numberOfRemainingUnboundArguments + " unbound args at this()/target()/args() binding stage, with no way to determine between them"
         );
      }

      List<String> varNames = new ArrayList<>();
      String[] tokens = StringUtils.tokenizeToStringArray(this.pointcutExpression, " ");

      for (int i = 0; i < tokens.length; i++) {
         if (!tokens[i].equals("this") && !tokens[i].startsWith("this(") && !tokens[i].equals("target") && !tokens[i].startsWith("target(")) {
            if (tokens[i].equals("args") || tokens[i].startsWith("args(")) {
               AspectJAdviceParameterNameDiscoverer.PointcutBody body = this.getPointcutBody(tokens, i);
               i += body.numTokensConsumed;
               List<String> candidateVarNames = new ArrayList<>();
               this.maybeExtractVariableNamesFromArgs(body.text, candidateVarNames);

               for (String varName : candidateVarNames) {
                  if (!this.alreadyBound(varName)) {
                     varNames.add(varName);
                  }
               }
            }
         } else {
            AspectJAdviceParameterNameDiscoverer.PointcutBody body = this.getPointcutBody(tokens, i);
            i += body.numTokensConsumed;
            String varName = this.maybeExtractVariableName(body.text);
            if (varName != null) {
               varNames.add(varName);
            }
         }
      }

      if (varNames.size() > 1) {
         throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
            "Found " + varNames.size() + " candidate this(), target(), or args() variables but only one unbound argument slot"
         );
      }

      if (varNames.size() == 1) {
         for (int j = 0; j < this.parameterNameBindings.length; j++) {
            if (this.isUnbound(j)) {
               this.bindParameterName(j, varNames.get(0));
               break;
            }
         }
      }
   }

   private void maybeBindReferencePointcutParameter() {
      if (this.numberOfRemainingUnboundArguments > 1) {
         throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
            "Still " + this.numberOfRemainingUnboundArguments + " unbound args at reference pointcut binding stage, with no way to determine between them"
         );
      }

      List<String> varNames = new ArrayList<>();
      String[] tokens = StringUtils.tokenizeToStringArray(this.pointcutExpression, " ");

      for (int i = 0; i < tokens.length; i++) {
         String toMatch = tokens[i];
         if (toMatch.startsWith("!")) {
            toMatch = toMatch.substring(1);
         }

         int firstParenIndex = toMatch.indexOf(40);
         if (firstParenIndex != -1) {
            toMatch = toMatch.substring(0, firstParenIndex);
         } else {
            if (tokens.length < i + 2) {
               continue;
            }

            String nextToken = tokens[i + 1];
            if (nextToken.charAt(0) != '(') {
               continue;
            }
         }

         AspectJAdviceParameterNameDiscoverer.PointcutBody body = this.getPointcutBody(tokens, i);
         i += body.numTokensConsumed;
         if (!nonReferencePointcutTokens.contains(toMatch)) {
            String varName = this.maybeExtractVariableName(body.text);
            if (varName != null) {
               varNames.add(varName);
            }
         }
      }

      if (varNames.size() > 1) {
         throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
            "Found " + varNames.size() + " candidate reference pointcut variables but only one unbound argument slot"
         );
      }

      if (varNames.size() == 1) {
         for (int j = 0; j < this.parameterNameBindings.length; j++) {
            if (this.isUnbound(j)) {
               this.bindParameterName(j, varNames.get(0));
               break;
            }
         }
      }
   }

   private AspectJAdviceParameterNameDiscoverer.PointcutBody getPointcutBody(String[] tokens, int startIndex) {
      int numTokensConsumed = 0;
      String currentToken = tokens[startIndex];
      int bodyStart = currentToken.indexOf(40);
      if (currentToken.charAt(currentToken.length() - 1) == ')') {
         return new AspectJAdviceParameterNameDiscoverer.PointcutBody(0, currentToken.substring(bodyStart + 1, currentToken.length() - 1));
      }

      StringBuilder sb = new StringBuilder();
      if (bodyStart >= 0 && bodyStart != currentToken.length() - 1) {
         sb.append(currentToken.substring(bodyStart + 1));
         sb.append(' ');
      }

      int currentIndex = startIndex + ++numTokensConsumed;

      while (currentIndex < tokens.length) {
         if (tokens[currentIndex].equals("(")) {
            currentIndex++;
         } else {
            if (tokens[currentIndex].endsWith(")")) {
               sb.append(tokens[currentIndex], 0, tokens[currentIndex].length() - 1);
               return new AspectJAdviceParameterNameDiscoverer.PointcutBody(numTokensConsumed, sb.toString().trim());
            }

            String toAppend = tokens[currentIndex];
            if (toAppend.startsWith("(")) {
               toAppend = toAppend.substring(1);
            }

            sb.append(toAppend);
            sb.append(' ');
            currentIndex++;
            numTokensConsumed++;
         }
      }

      return new AspectJAdviceParameterNameDiscoverer.PointcutBody(numTokensConsumed, null);
   }

   private void maybeBindPrimitiveArgsFromPointcutExpression() {
      int numUnboundPrimitives = this.countNumberOfUnboundPrimitiveArguments();
      if (numUnboundPrimitives > 1) {
         throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
            "Found " + numUnboundPrimitives + " unbound primitive arguments with no way to distinguish between them."
         );
      }

      if (numUnboundPrimitives == 1) {
         List<String> varNames = new ArrayList<>();
         String[] tokens = StringUtils.tokenizeToStringArray(this.pointcutExpression, " ");

         for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals("args") || tokens[i].startsWith("args(")) {
               AspectJAdviceParameterNameDiscoverer.PointcutBody body = this.getPointcutBody(tokens, i);
               i += body.numTokensConsumed;
               this.maybeExtractVariableNamesFromArgs(body.text, varNames);
            }
         }

         if (varNames.size() > 1) {
            throw new AspectJAdviceParameterNameDiscoverer.AmbiguousBindingException(
               "Found " + varNames.size() + " candidate variable names but only one candidate binding slot when matching primitive args"
            );
         }

         if (varNames.size() == 1) {
            for (int i = 0; i < this.argumentTypes.length; i++) {
               if (this.isUnbound(i) && this.argumentTypes[i].isPrimitive()) {
                  this.bindParameterName(i, varNames.get(0));
                  break;
               }
            }
         }
      }
   }

   private boolean isUnbound(int i) {
      return this.parameterNameBindings[i] == null;
   }

   private boolean alreadyBound(String varName) {
      for (int i = 0; i < this.parameterNameBindings.length; i++) {
         if (!this.isUnbound(i) && varName.equals(this.parameterNameBindings[i])) {
            return true;
         }
      }

      return false;
   }

   private boolean isSubtypeOf(Class<?> supertype, int argumentNumber) {
      return supertype.isAssignableFrom(this.argumentTypes[argumentNumber]);
   }

   private int countNumberOfUnboundAnnotationArguments() {
      int count = 0;

      for (int i = 0; i < this.argumentTypes.length; i++) {
         if (this.isUnbound(i) && this.isSubtypeOf(Annotation.class, i)) {
            count++;
         }
      }

      return count;
   }

   private int countNumberOfUnboundPrimitiveArguments() {
      int count = 0;

      for (int i = 0; i < this.argumentTypes.length; i++) {
         if (this.isUnbound(i) && this.argumentTypes[i].isPrimitive()) {
            count++;
         }
      }

      return count;
   }

   private void findAndBind(Class<?> argumentType, String varName) {
      for (int i = 0; i < this.argumentTypes.length; i++) {
         if (this.isUnbound(i) && this.isSubtypeOf(argumentType, i)) {
            this.bindParameterName(i, varName);
            return;
         }
      }

      throw new IllegalStateException("Expected to find an unbound argument of type '" + argumentType.getName() + "'");
   }

   static {
      for (PointcutPrimitive primitive : PointcutParser.getAllSupportedPointcutPrimitives()) {
         nonReferencePointcutTokens.add(primitive.getName());
      }

      nonReferencePointcutTokens.add("&&");
      nonReferencePointcutTokens.add("!");
      nonReferencePointcutTokens.add("||");
      nonReferencePointcutTokens.add("and");
      nonReferencePointcutTokens.add("or");
      nonReferencePointcutTokens.add("not");
   }

   public static class AmbiguousBindingException extends RuntimeException {
      public AmbiguousBindingException(String msg) {
         super(msg);
      }
   }

   private record PointcutBody(int numTokensConsumed, @Nullable String text) {
   }
}
