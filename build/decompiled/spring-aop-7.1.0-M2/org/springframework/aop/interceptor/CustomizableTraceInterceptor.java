package org.springframework.aop.interceptor;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

public class CustomizableTraceInterceptor extends AbstractTraceInterceptor {
   public static final String PLACEHOLDER_METHOD_NAME = "$[methodName]";
   public static final String PLACEHOLDER_TARGET_CLASS_NAME = "$[targetClassName]";
   public static final String PLACEHOLDER_TARGET_CLASS_SHORT_NAME = "$[targetClassShortName]";
   public static final String PLACEHOLDER_RETURN_VALUE = "$[returnValue]";
   public static final String PLACEHOLDER_ARGUMENT_TYPES = "$[argumentTypes]";
   public static final String PLACEHOLDER_ARGUMENTS = "$[arguments]";
   public static final String PLACEHOLDER_EXCEPTION = "$[exception]";
   public static final String PLACEHOLDER_INVOCATION_TIME = "$[invocationTime]";
   private static final String DEFAULT_ENTER_MESSAGE = "Entering method '$[methodName]' of class [$[targetClassName]]";
   private static final String DEFAULT_EXIT_MESSAGE = "Exiting method '$[methodName]' of class [$[targetClassName]]";
   private static final String DEFAULT_EXCEPTION_MESSAGE = "Exception thrown in method '$[methodName]' of class [$[targetClassName]]";
   private static final Pattern PATTERN = Pattern.compile("\\$\\[\\p{Alpha}+]");
   static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
      "$[methodName]",
      "$[targetClassName]",
      "$[targetClassShortName]",
      "$[returnValue]",
      "$[argumentTypes]",
      "$[arguments]",
      "$[exception]",
      "$[invocationTime]"
   );
   private String enterMessage = "Entering method '$[methodName]' of class [$[targetClassName]]";
   private String exitMessage = "Exiting method '$[methodName]' of class [$[targetClassName]]";
   private String exceptionMessage = "Exception thrown in method '$[methodName]' of class [$[targetClassName]]";

   public void setEnterMessage(String enterMessage) throws IllegalArgumentException {
      Assert.hasText(enterMessage, "enterMessage must not be empty");
      checkForInvalidPlaceholders(enterMessage);
      Assert.doesNotContain(enterMessage, "$[returnValue]", "enterMessage cannot contain placeholder $[returnValue]");
      Assert.doesNotContain(enterMessage, "$[exception]", "enterMessage cannot contain placeholder $[exception]");
      Assert.doesNotContain(enterMessage, "$[invocationTime]", "enterMessage cannot contain placeholder $[invocationTime]");
      this.enterMessage = enterMessage;
   }

   public void setExitMessage(String exitMessage) {
      Assert.hasText(exitMessage, "exitMessage must not be empty");
      checkForInvalidPlaceholders(exitMessage);
      Assert.doesNotContain(exitMessage, "$[exception]", "exitMessage cannot contain placeholder$[exception]");
      this.exitMessage = exitMessage;
   }

   public void setExceptionMessage(String exceptionMessage) {
      Assert.hasText(exceptionMessage, "exceptionMessage must not be empty");
      checkForInvalidPlaceholders(exceptionMessage);
      Assert.doesNotContain(exceptionMessage, "$[returnValue]", "exceptionMessage cannot contain placeholder $[returnValue]");
      this.exceptionMessage = exceptionMessage;
   }

   @Override
   protected @Nullable Object invokeUnderTrace(MethodInvocation invocation, Log logger) throws Throwable {
      String name = ClassUtils.getQualifiedMethodName(invocation.getMethod());
      StopWatch stopWatch = new StopWatch(name);
      Object returnValue = null;
      boolean exitThroughException = false;

      try {
         stopWatch.start(name);
         this.writeToLog(logger, this.replacePlaceholders(this.enterMessage, invocation, null, null, -1L));
         return invocation.proceed();
      } catch (Throwable ex) {
         if (stopWatch.isRunning()) {
            stopWatch.stop();
         }

         exitThroughException = true;
         this.writeToLog(logger, this.replacePlaceholders(this.exceptionMessage, invocation, null, ex, stopWatch.getTotalTimeMillis()), ex);
         throw ex;
      } finally {
         if (!exitThroughException) {
            if (stopWatch.isRunning()) {
               stopWatch.stop();
            }

            this.writeToLog(logger, this.replacePlaceholders(this.exitMessage, invocation, returnValue, null, stopWatch.getTotalTimeMillis()));
         }
      }
   }

   protected String replacePlaceholders(
      String message, MethodInvocation methodInvocation, @Nullable Object returnValue, @Nullable Throwable throwable, long invocationTime
   ) {
      Object target = methodInvocation.getThis();
      Assert.state(target != null, "Target must not be null");
      StringBuilder output = new StringBuilder();
      Matcher matcher = PATTERN.matcher(message);

      while (matcher.find()) {
         String match = matcher.group();
         switch (match) {
            case "$[methodName]":
               matcher.appendReplacement(output, Matcher.quoteReplacement(methodInvocation.getMethod().getName()));
               break;
            case "$[targetClassName]":
               String className = this.getClassForLogging(target).getName();
               matcher.appendReplacement(output, Matcher.quoteReplacement(className));
               break;
            case "$[targetClassShortName]":
               String shortName = ClassUtils.getShortName(this.getClassForLogging(target));
               matcher.appendReplacement(output, Matcher.quoteReplacement(shortName));
               break;
            case "$[arguments]":
               matcher.appendReplacement(output, Matcher.quoteReplacement(StringUtils.arrayToCommaDelimitedString(methodInvocation.getArguments())));
               break;
            case "$[argumentTypes]":
               appendArgumentTypes(methodInvocation, matcher, output);
               break;
            case "$[returnValue]":
               appendReturnValue(methodInvocation, matcher, output, returnValue);
               break;
            case "$[exception]":
               if (throwable != null) {
                  matcher.appendReplacement(output, Matcher.quoteReplacement(throwable.toString()));
               }
               break;
            case "$[invocationTime]":
               matcher.appendReplacement(output, Long.toString(invocationTime));
               break;
            default:
               throw new IllegalArgumentException("Unknown placeholder [" + match + "]");
         }
      }

      matcher.appendTail(output);
      return output.toString();
   }

   private static void appendReturnValue(MethodInvocation methodInvocation, Matcher matcher, StringBuilder output, @Nullable Object returnValue) {
      if (methodInvocation.getMethod().getReturnType() == void.class) {
         matcher.appendReplacement(output, "void");
      } else if (returnValue == null) {
         matcher.appendReplacement(output, "null");
      } else {
         matcher.appendReplacement(output, Matcher.quoteReplacement(returnValue.toString()));
      }
   }

   private static void appendArgumentTypes(MethodInvocation methodInvocation, Matcher matcher, StringBuilder output) {
      Class<?>[] argumentTypes = methodInvocation.getMethod().getParameterTypes();
      String[] argumentTypeShortNames = new String[argumentTypes.length];

      for (int i = 0; i < argumentTypeShortNames.length; i++) {
         argumentTypeShortNames[i] = ClassUtils.getShortName(argumentTypes[i]);
      }

      matcher.appendReplacement(output, Matcher.quoteReplacement(StringUtils.arrayToCommaDelimitedString(argumentTypeShortNames)));
   }

   private static void checkForInvalidPlaceholders(String message) throws IllegalArgumentException {
      Matcher matcher = PATTERN.matcher(message);

      while (matcher.find()) {
         String match = matcher.group();
         if (!ALLOWED_PLACEHOLDERS.contains(match)) {
            throw new IllegalArgumentException("Placeholder [" + match + "] is not valid");
         }
      }
   }
}
