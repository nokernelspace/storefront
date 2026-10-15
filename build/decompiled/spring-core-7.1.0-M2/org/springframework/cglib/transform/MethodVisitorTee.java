package org.springframework.cglib.transform;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.Attribute;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.TypePath;
import org.springframework.cglib.core.Constants;

public class MethodVisitorTee extends MethodVisitor {
   private final MethodVisitor mv1;
   private final MethodVisitor mv2;

   public MethodVisitorTee(MethodVisitor mv1, MethodVisitor mv2) {
      super(Constants.ASM_API);
      this.mv1 = mv1;
      this.mv2 = mv2;
   }

   @Override
   public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
      this.mv1.visitFrame(type, nLocal, local, nStack, stack);
      this.mv2.visitFrame(type, nLocal, local, nStack, stack);
   }

   @Override
   public AnnotationVisitor visitAnnotationDefault() {
      return AnnotationVisitorTee.getInstance(this.mv1.visitAnnotationDefault(), this.mv2.visitAnnotationDefault());
   }

   @Override
   public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(this.mv1.visitAnnotation(desc, visible), this.mv2.visitAnnotation(desc, visible));
   }

   @Override
   public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(
         this.mv1.visitParameterAnnotation(parameter, desc, visible), this.mv2.visitParameterAnnotation(parameter, desc, visible)
      );
   }

   @Override
   public void visitAttribute(Attribute attr) {
      this.mv1.visitAttribute(attr);
      this.mv2.visitAttribute(attr);
   }

   @Override
   public void visitCode() {
      this.mv1.visitCode();
      this.mv2.visitCode();
   }

   @Override
   public void visitInsn(int opcode) {
      this.mv1.visitInsn(opcode);
      this.mv2.visitInsn(opcode);
   }

   @Override
   public void visitIntInsn(int opcode, int operand) {
      this.mv1.visitIntInsn(opcode, operand);
      this.mv2.visitIntInsn(opcode, operand);
   }

   @Override
   public void visitVarInsn(int opcode, int var) {
      this.mv1.visitVarInsn(opcode, var);
      this.mv2.visitVarInsn(opcode, var);
   }

   @Override
   public void visitTypeInsn(int opcode, String desc) {
      this.mv1.visitTypeInsn(opcode, desc);
      this.mv2.visitTypeInsn(opcode, desc);
   }

   @Override
   public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      this.mv1.visitFieldInsn(opcode, owner, name, desc);
      this.mv2.visitFieldInsn(opcode, owner, name, desc);
   }

   @Override
   public void visitMethodInsn(int opcode, String owner, String name, String desc) {
      this.mv1.visitMethodInsn(opcode, owner, name, desc);
      this.mv2.visitMethodInsn(opcode, owner, name, desc);
   }

   @Override
   public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      this.mv1.visitMethodInsn(opcode, owner, name, desc, itf);
      this.mv2.visitMethodInsn(opcode, owner, name, desc, itf);
   }

   @Override
   public void visitJumpInsn(int opcode, Label label) {
      this.mv1.visitJumpInsn(opcode, label);
      this.mv2.visitJumpInsn(opcode, label);
   }

   @Override
   public void visitLabel(Label label) {
      this.mv1.visitLabel(label);
      this.mv2.visitLabel(label);
   }

   @Override
   public void visitLdcInsn(Object cst) {
      this.mv1.visitLdcInsn(cst);
      this.mv2.visitLdcInsn(cst);
   }

   @Override
   public void visitIincInsn(int var, int increment) {
      this.mv1.visitIincInsn(var, increment);
      this.mv2.visitIincInsn(var, increment);
   }

   @Override
   public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      this.mv1.visitTableSwitchInsn(min, max, dflt, labels);
      this.mv2.visitTableSwitchInsn(min, max, dflt, labels);
   }

   @Override
   public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      this.mv1.visitLookupSwitchInsn(dflt, keys, labels);
      this.mv2.visitLookupSwitchInsn(dflt, keys, labels);
   }

   @Override
   public void visitMultiANewArrayInsn(String desc, int dims) {
      this.mv1.visitMultiANewArrayInsn(desc, dims);
      this.mv2.visitMultiANewArrayInsn(desc, dims);
   }

   @Override
   public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      this.mv1.visitTryCatchBlock(start, end, handler, type);
      this.mv2.visitTryCatchBlock(start, end, handler, type);
   }

   @Override
   public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      this.mv1.visitLocalVariable(name, desc, signature, start, end, index);
      this.mv2.visitLocalVariable(name, desc, signature, start, end, index);
   }

   @Override
   public void visitLineNumber(int line, Label start) {
      this.mv1.visitLineNumber(line, start);
      this.mv2.visitLineNumber(line, start);
   }

   @Override
   public void visitMaxs(int maxStack, int maxLocals) {
      this.mv1.visitMaxs(maxStack, maxLocals);
      this.mv2.visitMaxs(maxStack, maxLocals);
   }

   @Override
   public void visitEnd() {
      this.mv1.visitEnd();
      this.mv2.visitEnd();
   }

   @Override
   public void visitParameter(String name, int access) {
      this.mv1.visitParameter(name, access);
      this.mv2.visitParameter(name, access);
   }

   @Override
   public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(
         this.mv1.visitTypeAnnotation(typeRef, typePath, desc, visible), this.mv2.visitTypeAnnotation(typeRef, typePath, desc, visible)
      );
   }

   @Override
   public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      this.mv1.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
      this.mv2.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
   }

   @Override
   public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(
         this.mv1.visitInsnAnnotation(typeRef, typePath, desc, visible), this.mv2.visitInsnAnnotation(typeRef, typePath, desc, visible)
      );
   }

   @Override
   public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(
         this.mv1.visitTryCatchAnnotation(typeRef, typePath, desc, visible), this.mv2.visitTryCatchAnnotation(typeRef, typePath, desc, visible)
      );
   }

   @Override
   public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
      return AnnotationVisitorTee.getInstance(
         this.mv1.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible),
         this.mv2.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible)
      );
   }
}
