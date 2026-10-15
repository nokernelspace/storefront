package org.springframework.cglib.transform;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.Attribute;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.TypePath;
import org.springframework.cglib.core.Constants;

public class ClassVisitorTee extends ClassVisitor {
   private ClassVisitor cv1;
   private ClassVisitor cv2;

   public ClassVisitorTee(ClassVisitor cv1, ClassVisitor cv2) {
      super(Constants.ASM_API);
      this.cv1 = cv1;
      this.cv2 = cv2;
   }

   @Override
   public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      this.cv1.visit(version, access, name, signature, superName, interfaces);
      this.cv2.visit(version, access, name, signature, superName, interfaces);
   }

   @Override
   public void visitEnd() {
      this.cv1.visitEnd();
      this.cv2.visitEnd();
      this.cv1 = this.cv2 = null;
   }

   @Override
   public void visitInnerClass(String name, String outerName, String innerName, int access) {
      this.cv1.visitInnerClass(name, outerName, innerName, access);
      this.cv2.visitInnerClass(name, outerName, innerName, access);
   }

   @Override
   public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
      FieldVisitor fv1 = this.cv1.visitField(access, name, desc, signature, value);
      FieldVisitor fv2 = this.cv2.visitField(access, name, desc, signature, value);
      if (fv1 == null) {
         return fv2;
      } else {
         return fv2 == null ? fv1 : new FieldVisitorTee(fv1, fv2);
      }
   }

   @Override
   public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      MethodVisitor mv1 = this.cv1.visitMethod(access, name, desc, signature, exceptions);
      MethodVisitor mv2 = this.cv2.visitMethod(access, name, desc, signature, exceptions);
      if (mv1 == null) {
         return mv2;
      } else {
         return mv2 == null ? mv1 : new MethodVisitorTee(mv1, mv2);
      }
   }

   @Override
   public void visitSource(String source, String debug) {
      this.cv1.visitSource(source, debug);
      this.cv2.visitSource(source, debug);
   }

   @Override
   public void visitOuterClass(String owner, String name, String desc) {
      this.cv1.visitOuterClass(owner, name, desc);
      this.cv2.visitOuterClass(owner, name, desc);
   }

   @Override
   public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(this.cv1.visitAnnotation(desc, visible), this.cv2.visitAnnotation(desc, visible));
   }

   @Override
   public void visitAttribute(Attribute attrs) {
      this.cv1.visitAttribute(attrs);
      this.cv2.visitAttribute(attrs);
   }

   @Override
   public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(
         this.cv1.visitTypeAnnotation(typeRef, typePath, desc, visible), this.cv2.visitTypeAnnotation(typeRef, typePath, desc, visible)
      );
   }
}
