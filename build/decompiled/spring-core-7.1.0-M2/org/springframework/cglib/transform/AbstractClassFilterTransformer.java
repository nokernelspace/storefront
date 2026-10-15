package org.springframework.cglib.transform;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.Attribute;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.cglib.core.ClassTransformer;

public abstract class AbstractClassFilterTransformer extends AbstractClassTransformer {
   private ClassTransformer pass;
   private ClassVisitor target;

   @Override
   public void setTarget(ClassVisitor target) {
      super.setTarget(target);
      this.pass.setTarget(target);
   }

   protected AbstractClassFilterTransformer(ClassTransformer pass) {
      this.pass = pass;
   }

   protected abstract boolean accept(int version, int access, String name, String signature, String superName, String[] interfaces);

   @Override
   public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      this.target = this.accept(version, access, name, signature, superName, interfaces) ? this.pass : this.cv;
      this.target.visit(version, access, name, signature, superName, interfaces);
   }

   @Override
   public void visitSource(String source, String debug) {
      this.target.visitSource(source, debug);
   }

   @Override
   public void visitOuterClass(String owner, String name, String desc) {
      this.target.visitOuterClass(owner, name, desc);
   }

   @Override
   public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return this.target.visitAnnotation(desc, visible);
   }

   @Override
   public void visitAttribute(Attribute attr) {
      this.target.visitAttribute(attr);
   }

   @Override
   public void visitInnerClass(String name, String outerName, String innerName, int access) {
      this.target.visitInnerClass(name, outerName, innerName, access);
   }

   @Override
   public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
      return this.target.visitField(access, name, desc, signature, value);
   }

   @Override
   public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      return this.target.visitMethod(access, name, desc, signature, exceptions);
   }

   @Override
   public void visitEnd() {
      this.target.visitEnd();
      this.target = null;
   }
}
