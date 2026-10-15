package org.springframework.boot.diagnostics.analyzer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.util.ClassUtils;

class NoSuchMethodFailureAnalyzer extends AbstractFailureAnalyzer<NoSuchMethodError> {
   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, NoSuchMethodError cause) {
      NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor callerDescriptor = this.getCallerMethodDescriptor(cause);
      if (callerDescriptor == null) {
         return null;
      }

      String message = cause.getMessage();
      NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor calledDescriptor = this.getNoSuchMethodDescriptor(message != null ? message : "");
      if (calledDescriptor == null) {
         return null;
      }

      String description = this.getDescription(callerDescriptor, calledDescriptor);
      String action = this.getAction(callerDescriptor, calledDescriptor);
      return new FailureAnalysis(description, action, cause);
   }

   private NoSuchMethodFailureAnalyzer.@Nullable NoSuchMethodDescriptor getCallerMethodDescriptor(NoSuchMethodError cause) {
      StackTraceElement firstStackTraceElement = cause.getStackTrace()[0];
      String message = firstStackTraceElement.toString();
      String className = firstStackTraceElement.getClassName();
      return this.getDescriptorForClass(message, className);
   }

   protected NoSuchMethodFailureAnalyzer.@Nullable NoSuchMethodDescriptor getNoSuchMethodDescriptor(String cause) {
      String message = this.cleanMessage(cause);
      String className = this.extractClassName(message);
      return this.getDescriptorForClass(message, className);
   }

   private NoSuchMethodFailureAnalyzer.@Nullable NoSuchMethodDescriptor getDescriptorForClass(String message, @Nullable String className) {
      if (className == null) {
         return null;
      }

      List<URL> candidates = this.findCandidates(className);
      if (candidates == null) {
         return null;
      }

      Class<?> type = this.load(className);
      if (type == null) {
         return null;
      }

      List<NoSuchMethodFailureAnalyzer.ClassDescriptor> typeHierarchy = this.getTypeHierarchy(type);
      return typeHierarchy == null ? null : new NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor(message, className, candidates, typeHierarchy);
   }

   private String cleanMessage(String message) {
      int loadedFromIndex = message.indexOf(" (loaded from");
      return loadedFromIndex == -1 ? message : message.substring(0, loadedFromIndex);
   }

   private @Nullable String extractClassName(String message) {
      if (message.startsWith("'") && message.endsWith("'")) {
         int splitIndex = message.indexOf(32);
         if (splitIndex == -1) {
            return null;
         }

         message = message.substring(splitIndex + 1);
      }

      int descriptorIndex = message.indexOf(40);
      if (descriptorIndex == -1) {
         return null;
      }

      String classAndMethodName = message.substring(0, descriptorIndex);
      int methodNameIndex = classAndMethodName.lastIndexOf(46);
      if (methodNameIndex == -1) {
         return null;
      }

      String className = classAndMethodName.substring(0, methodNameIndex);
      return className.replace('/', '.');
   }

   private @Nullable List<URL> findCandidates(String className) {
      try {
         return Collections.list(
            NoSuchMethodFailureAnalyzer.class.getClassLoader().getResources(ClassUtils.convertClassNameToResourcePath(className) + ".class")
         );
      } catch (Throwable ex) {
         return null;
      }
   }

   private @Nullable Class<?> load(String className) {
      try {
         return Class.forName(className, false, this.getClass().getClassLoader());
      } catch (Throwable ex) {
         return null;
      }
   }

   private @Nullable List<NoSuchMethodFailureAnalyzer.ClassDescriptor> getTypeHierarchy(Class<?> type) {
      try {
         List<NoSuchMethodFailureAnalyzer.ClassDescriptor> typeHierarchy = new ArrayList<>();

         while (type != null && !type.equals(Object.class)) {
            typeHierarchy.add(
               new NoSuchMethodFailureAnalyzer.ClassDescriptor(type.getCanonicalName(), type.getProtectionDomain().getCodeSource().getLocation())
            );
            type = type.getSuperclass();
         }

         return typeHierarchy;
      } catch (Throwable ex) {
         return null;
      }
   }

   private String getDescription(
      NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor callerDescriptor, NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor calledDescriptor
   ) {
      StringWriter description = new StringWriter();
      PrintWriter writer = new PrintWriter(description);
      writer.println("An attempt was made to call a method that does not exist. The attempt was made from the following location:");
      writer.println();
      writer.printf("    %s%n", callerDescriptor.getErrorMessage());
      writer.println();
      writer.println("The following method did not exist:");
      writer.println();
      writer.printf("    %s%n", calledDescriptor.getErrorMessage());
      writer.println();
      if (callerDescriptor.getCandidateLocations().size() > 1) {
         writer.printf("The calling method's class, %s, is available from the following locations:%n", callerDescriptor.getClassName());
         writer.println();

         for (URL candidate : callerDescriptor.getCandidateLocations()) {
            writer.printf("    %s%n", candidate);
         }

         writer.println();
         writer.println("The calling method's class was loaded from the following location:");
         writer.println();
         writer.printf("    %s%n", callerDescriptor.getTypeHierarchy().get(0).getLocation());
      } else {
         writer.printf("The calling method's class, %s, was loaded from the following location:%n", callerDescriptor.getClassName());
         writer.println();
         writer.printf("    %s%n", callerDescriptor.getCandidateLocations().get(0));
      }

      writer.println();
      writer.printf("The called method's class, %s, is available from the following locations:%n", calledDescriptor.getClassName());
      writer.println();

      for (URL candidate : calledDescriptor.getCandidateLocations()) {
         writer.printf("    %s%n", candidate);
      }

      writer.println();
      writer.println("The called method's class hierarchy was loaded from the following locations:");
      writer.println();

      for (NoSuchMethodFailureAnalyzer.ClassDescriptor type : calledDescriptor.getTypeHierarchy()) {
         writer.printf("    %s: %s%n", type.getName(), type.getLocation());
      }

      return description.toString();
   }

   private String getAction(
      NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor callerDescriptor, NoSuchMethodFailureAnalyzer.NoSuchMethodDescriptor calledDescriptor
   ) {
      return callerDescriptor.getClassName().equals(calledDescriptor.getClassName())
         ? "Correct the classpath of your application so that it contains a single, compatible version of " + calledDescriptor.getClassName()
         : "Correct the classpath of your application so that it contains compatible versions of the classes "
            + callerDescriptor.getClassName()
            + " and "
            + calledDescriptor.getClassName();
   }

   protected static class ClassDescriptor {
      private final String name;
      private final URL location;

      public ClassDescriptor(String name, URL location) {
         this.name = name;
         this.location = location;
      }

      public String getName() {
         return this.name;
      }

      public URL getLocation() {
         return this.location;
      }
   }

   protected static class NoSuchMethodDescriptor {
      private final String errorMessage;
      private final String className;
      private final List<URL> candidateLocations;
      private final List<NoSuchMethodFailureAnalyzer.ClassDescriptor> typeHierarchy;

      public NoSuchMethodDescriptor(
         String errorMessage, String className, List<URL> candidateLocations, List<NoSuchMethodFailureAnalyzer.ClassDescriptor> typeHierarchy
      ) {
         this.errorMessage = errorMessage;
         this.className = className;
         this.candidateLocations = candidateLocations;
         this.typeHierarchy = typeHierarchy;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }

      public String getClassName() {
         return this.className;
      }

      public List<URL> getCandidateLocations() {
         return this.candidateLocations;
      }

      public List<NoSuchMethodFailureAnalyzer.ClassDescriptor> getTypeHierarchy() {
         return this.typeHierarchy;
      }
   }
}
