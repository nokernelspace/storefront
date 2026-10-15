package org.springframework.core.type.classreading;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Type;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

final class SimpleMethodMetadataReadingVisitor extends MethodVisitor {
   private final @Nullable ClassLoader classLoader;
   private final String declaringClassName;
   private final int access;
   private final String methodName;
   private final String descriptor;
   private final List<MergedAnnotation<?>> annotations = new ArrayList<>(4);
   private final Consumer<SimpleMethodMetadata> consumer;
   private SimpleMethodMetadataReadingVisitor.@Nullable Source source;

   SimpleMethodMetadataReadingVisitor(
      @Nullable ClassLoader classLoader, String declaringClassName, int access, String methodName, String descriptor, Consumer<SimpleMethodMetadata> consumer
   ) {
      super(17432576);
      this.classLoader = classLoader;
      this.declaringClassName = declaringClassName;
      this.access = access;
      this.methodName = methodName;
      this.descriptor = descriptor;
      this.consumer = consumer;
   }

   @Override
   public @Nullable AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return MergedAnnotationReadingVisitor.get(this.classLoader, this.getSource(), descriptor, visible, this.annotations::add);
   }

   @Override
   public void visitEnd() {
      String returnTypeName = Type.getReturnType(this.descriptor).getClassName();
      MergedAnnotations annotations = MergedAnnotations.of(this.annotations);
      SimpleMethodMetadata metadata = new SimpleMethodMetadata(
         this.methodName, this.access, this.declaringClassName, returnTypeName, this.getSource(), annotations
      );
      this.consumer.accept(metadata);
   }

   private Object getSource() {
      SimpleMethodMetadataReadingVisitor.Source source = this.source;
      if (source == null) {
         source = new SimpleMethodMetadataReadingVisitor.Source(this.declaringClassName, this.methodName, this.access, this.descriptor);
         this.source = source;
      }

      return source;
   }

   static final class Source {
      private final String declaringClassName;
      private final String methodName;
      private final int access;
      private final String descriptor;
      private @Nullable String toStringValue;

      Source(String declaringClassName, String methodName, int access, String descriptor) {
         this.declaringClassName = declaringClassName;
         this.methodName = methodName;
         this.access = access;
         this.descriptor = descriptor;
      }

      @Override
      public int hashCode() {
         int result = 1;
         result = 31 * result + this.declaringClassName.hashCode();
         result = 31 * result + this.methodName.hashCode();
         result = 31 * result + this.access;
         return 31 * result + this.descriptor.hashCode();
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         } else if (other != null && this.getClass() == other.getClass()) {
            SimpleMethodMetadataReadingVisitor.Source otherSource = (SimpleMethodMetadataReadingVisitor.Source)other;
            return this.declaringClassName.equals(otherSource.declaringClassName)
               && this.methodName.equals(otherSource.methodName)
               && this.access == otherSource.access
               && this.descriptor.equals(otherSource.descriptor);
         } else {
            return false;
         }
      }

      @Override
      public String toString() {
         String value = this.toStringValue;
         if (value == null) {
            StringBuilder builder = new StringBuilder();
            if ((this.access & 1) != 0) {
               builder.append("public ");
            }

            if ((this.access & 4) != 0) {
               builder.append("protected ");
            }

            if ((this.access & 2) != 0) {
               builder.append("private ");
            }

            if ((this.access & 1024) != 0) {
               builder.append("abstract ");
            }

            if ((this.access & 8) != 0) {
               builder.append("static ");
            }

            if ((this.access & 16) != 0) {
               builder.append("final ");
            }

            Type returnType = Type.getReturnType(this.descriptor);
            builder.append(returnType.getClassName());
            builder.append(' ');
            builder.append(this.declaringClassName);
            builder.append('.');
            builder.append(this.methodName);
            Type[] argumentTypes = Type.getArgumentTypes(this.descriptor);
            builder.append('(');

            for (int i = 0; i < argumentTypes.length; i++) {
               if (i != 0) {
                  builder.append(',');
               }

               builder.append(argumentTypes[i].getClassName());
            }

            builder.append(')');
            value = builder.toString();
            this.toStringValue = value;
         }

         return value;
      }
   }
}
