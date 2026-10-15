package org.springframework.asm;

public abstract class FieldVisitor {
   protected final int api;
   protected FieldVisitor fv;

   protected FieldVisitor(final int api) {
      this(api, null);
   }

   protected FieldVisitor(final int api, final FieldVisitor fieldVisitor) {
      if (api != 589824 && api != 524288 && api != 458752 && api != 393216 && api != 327680 && api != 262144 && api != 17432576) {
         throw new IllegalArgumentException("Unsupported api " + api);
      }

      this.api = api;
      this.fv = fieldVisitor;
   }

   public FieldVisitor getDelegate() {
      return this.fv;
   }

   public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
      return this.fv != null ? this.fv.visitAnnotation(descriptor, visible) : null;
   }

   public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
      if (this.api < 327680) {
         throw new UnsupportedOperationException("This feature requires ASM5");
      } else {
         return this.fv != null ? this.fv.visitTypeAnnotation(typeRef, typePath, descriptor, visible) : null;
      }
   }

   public void visitAttribute(final Attribute attribute) {
      if (this.fv != null) {
         this.fv.visitAttribute(attribute);
      }
   }

   public void visitEnd() {
      if (this.fv != null) {
         this.fv.visitEnd();
      }
   }
}
