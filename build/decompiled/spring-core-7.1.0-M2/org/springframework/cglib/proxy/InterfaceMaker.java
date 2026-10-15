package org.springframework.cglib.proxy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Type;
import org.springframework.cglib.core.AbstractClassGenerator;
import org.springframework.cglib.core.ClassEmitter;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.cglib.core.Signature;

public class InterfaceMaker extends AbstractClassGenerator {
   private static final AbstractClassGenerator.Source SOURCE = new AbstractClassGenerator.Source(InterfaceMaker.class.getName());
   private Map signatures = new HashMap();

   public InterfaceMaker() {
      super(SOURCE);
   }

   public void add(Signature sig, Type[] exceptions) {
      this.signatures.put(sig, exceptions);
   }

   public void add(Method method) {
      this.add(ReflectUtils.getSignature(method), ReflectUtils.getExceptionTypes(method));
   }

   public void add(Class clazz) {
      Method[] methods = clazz.getMethods();

      for (Method m : methods) {
         if (!m.getDeclaringClass().getName().equals("java.lang.Object")) {
            this.add(m);
         }
      }
   }

   public Class create() {
      this.setUseCache(false);
      return (Class)super.create(this);
   }

   @Override
   protected ClassLoader getDefaultClassLoader() {
      return null;
   }

   @Override
   protected Object firstInstance(Class type) {
      return type;
   }

   @Override
   protected Object nextInstance(Object instance) {
      throw new IllegalStateException("InterfaceMaker does not cache");
   }

   @Override
   public void generateClass(ClassVisitor v) throws Exception {
      ClassEmitter ce = new ClassEmitter(v);
      ce.begin_class(52, 1537, this.getClassName(), null, null, "<generated>");

      for (Signature sig : this.signatures.keySet()) {
         Type[] exceptions = (Type[])this.signatures.get(sig);
         ce.begin_method(1025, sig, exceptions).end_method();
      }

      ce.end_class();
   }
}
