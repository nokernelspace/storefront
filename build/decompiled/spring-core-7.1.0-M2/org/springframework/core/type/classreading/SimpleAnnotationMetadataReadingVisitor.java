package org.springframework.core.type.classreading;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

final class SimpleAnnotationMetadataReadingVisitor extends ClassVisitor {
   private final @Nullable ClassLoader classLoader;
   private String className = "";
   private int access;
   private @Nullable String superClassName;
   private @Nullable String enclosingClassName;
   private boolean independentInnerClass;
   private final Set<String> interfaceNames = new LinkedHashSet<>(4);
   private final Set<String> memberClassNames = new LinkedHashSet<>(4);
   private final List<MergedAnnotation<?>> annotations = new ArrayList<>(4);
   private final Set<MethodMetadata> declaredMethods = new LinkedHashSet<>(4);
   private @Nullable SimpleAnnotationMetadata metadata;
   private SimpleAnnotationMetadataReadingVisitor.@Nullable Source source;

   SimpleAnnotationMetadataReadingVisitor(@Nullable ClassLoader classLoader) {
      super(17432576);
      this.classLoader = classLoader;
   }

   @Override
   public void visit(int version, int access, String name, String signature, @Nullable String supername, String[] interfaces) {
      this.className = this.toClassName(name);
      this.access = access;
      if (supername != null && !this.isInterface(access)) {
         this.superClassName = this.toClassName(supername);
      }

      for (String element : interfaces) {
         this.interfaceNames.add(this.toClassName(element));
      }
   }

   @Override
   public void visitOuterClass(String owner, String name, String desc) {
      this.enclosingClassName = this.toClassName(owner);
   }

   @Override
   public void visitInnerClass(String name, @Nullable String outerName, String innerName, int access) {
      if (outerName != null) {
         String className = this.toClassName(name);
         String outerClassName = this.toClassName(outerName);
         if (this.className.equals(className)) {
            this.enclosingClassName = outerClassName;
            this.independentInnerClass = (access & 8) != 0;
         } else if (this.className.equals(outerClassName)) {
            this.memberClassNames.add(className);
         }
      }
   }

   @Override
   public @Nullable AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return MergedAnnotationReadingVisitor.get(this.classLoader, this.getSource(), descriptor, visible, this.annotations::add);
   }

   @Override
   public @Nullable MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      return !this.isBridge(access) && !name.equals("<init>")
         ? new SimpleMethodMetadataReadingVisitor(this.classLoader, this.className, access, name, descriptor, this.declaredMethods::add)
         : null;
   }

   @Override
   public void visitEnd() {
      MergedAnnotations annotations = MergedAnnotations.of(this.annotations);
      this.metadata = new SimpleAnnotationMetadata(
         this.className,
         this.access,
         this.enclosingClassName,
         this.superClassName,
         this.independentInnerClass,
         this.interfaceNames,
         this.memberClassNames,
         this.declaredMethods,
         annotations
      );
   }

   public SimpleAnnotationMetadata getMetadata() {
      Assert.state(this.metadata != null, "AnnotationMetadata not initialized");
      return this.metadata;
   }

   private SimpleAnnotationMetadataReadingVisitor.Source getSource() {
      SimpleAnnotationMetadataReadingVisitor.Source source = this.source;
      if (source == null) {
         source = new SimpleAnnotationMetadataReadingVisitor.Source(this.className);
         this.source = source;
      }

      return source;
   }

   private String toClassName(String name) {
      return ClassUtils.convertResourcePathToClassName(name);
   }

   private boolean isBridge(int access) {
      return (access & 64) != 0;
   }

   private boolean isInterface(int access) {
      return (access & 512) != 0;
   }

   private static final class Source {
      private final String className;

      Source(String className) {
         this.className = className;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof SimpleAnnotationMetadataReadingVisitor.Source that && this.className.equals(that.className);
      }

      @Override
      public int hashCode() {
         return this.className.hashCode();
      }

      @Override
      public String toString() {
         return this.className;
      }
   }
}
