package org.springframework.cglib.proxy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.asm.Label;
import org.springframework.asm.Type;
import org.springframework.cglib.core.ClassEmitter;
import org.springframework.cglib.core.CodeEmitter;
import org.springframework.cglib.core.Constants;
import org.springframework.cglib.core.MethodInfo;
import org.springframework.cglib.core.Signature;
import org.springframework.cglib.core.TypeUtils;

class LazyLoaderGenerator implements CallbackGenerator {
   public static final LazyLoaderGenerator INSTANCE = new LazyLoaderGenerator();
   private static final Signature LOAD_OBJECT = TypeUtils.parseSignature("Object loadObject()");
   private static final Type LAZY_LOADER = TypeUtils.parseType("org.springframework.cglib.proxy.LazyLoader");

   @Override
   public void generate(ClassEmitter ce, CallbackGenerator.Context context, List methods) {
      Set indexes = new HashSet();

      for (MethodInfo method : methods) {
         if (!TypeUtils.isProtected(method.getModifiers())) {
            int index = context.getIndex(method);
            indexes.add(index);
            CodeEmitter e = context.beginMethod(ce, method);
            e.load_this();
            e.dup();
            e.invoke_virtual_this(this.loadMethod(index));
            e.checkcast(method.getClassInfo().getType());
            e.load_args();
            e.invoke(method);
            e.return_value();
            e.end_method();
         }
      }

      for (int index : indexes) {
         String delegate = "CGLIB$LAZY_LOADER_" + index;
         ce.declare_field(2, delegate, Constants.TYPE_OBJECT, null);
         CodeEmitter e = ce.begin_method(50, this.loadMethod(index), null);
         e.load_this();
         e.getfield(delegate);
         e.dup();
         Label end = e.make_label();
         e.ifnonnull(end);
         e.pop();
         e.load_this();
         context.emitCallback(e, index);
         e.invoke_interface(LAZY_LOADER, LOAD_OBJECT);
         e.dup_x1();
         e.putfield(delegate);
         e.mark(end);
         e.return_value();
         e.end_method();
      }
   }

   private Signature loadMethod(int index) {
      return new Signature("CGLIB$LOAD_PRIVATE_" + index, Constants.TYPE_OBJECT, Constants.TYPES_EMPTY);
   }

   @Override
   public void generateStatic(CodeEmitter e, CallbackGenerator.Context context, List methods) {
   }
}
