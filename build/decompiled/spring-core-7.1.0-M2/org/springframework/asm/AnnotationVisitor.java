package org.springframework.asm;

public abstract class AnnotationVisitor {
   protected final int api;
   protected AnnotationVisitor av;

   protected AnnotationVisitor(final int api) {
      this(api, null);
   }

   protected AnnotationVisitor(final int api, final AnnotationVisitor annotationVisitor) {
      if (api != 589824 && api != 524288 && api != 458752 && api != 393216 && api != 327680 && api != 262144 && api != 17432576) {
         throw new IllegalArgumentException("Unsupported api " + api);
      }

      this.api = api;
      this.av = annotationVisitor;
   }

   public AnnotationVisitor getDelegate() {
      return this.av;
   }

   public void visit(final String name, final Object value) {
      if (this.av != null) {
         this.av.visit(name, value);
      }
   }

   public void visitEnum(final String name, final String descriptor, final String value) {
      if (this.av != null) {
         this.av.visitEnum(name, descriptor, value);
      }
   }

   public AnnotationVisitor visitAnnotation(final String name, final String descriptor) {
      return this.av != null ? this.av.visitAnnotation(name, descriptor) : null;
   }

   public AnnotationVisitor visitArray(final String name) {
      return this.av != null ? this.av.visitArray(name) : null;
   }

   public void visitEnd() {
      if (this.av != null) {
         this.av.visitEnd();
      }
   }
}
