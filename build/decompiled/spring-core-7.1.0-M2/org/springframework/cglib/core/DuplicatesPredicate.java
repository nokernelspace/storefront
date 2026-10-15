package org.springframework.cglib.core;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;

public class DuplicatesPredicate implements Predicate {
   private final Set unique;
   private final Set rejected;

   public DuplicatesPredicate() {
      this.unique = new HashSet();
      this.rejected = Collections.emptySet();
   }

   public DuplicatesPredicate(List allMethods) {
      this.rejected = new HashSet();
      this.unique = new HashSet();
      Map scanned = new HashMap();
      Map suspects = new HashMap();

      for (Object o : allMethods) {
         Method method = (Method)o;
         Object sig = MethodWrapper.create(method);
         Method existing = (Method)scanned.get(sig);
         if (existing == null) {
            scanned.put(sig, method);
         } else if (!suspects.containsKey(sig) && existing.isBridge() && !method.isBridge()) {
            suspects.put(sig, existing);
         }
      }

      if (!suspects.isEmpty()) {
         Set classes = new HashSet();
         DuplicatesPredicate.UnnecessaryBridgeFinder finder = new DuplicatesPredicate.UnnecessaryBridgeFinder(this.rejected);

         for (Object o : suspects.values()) {
            Method m = (Method)o;
            classes.add(m.getDeclaringClass());
            finder.addSuspectMethod(m);
         }

         for (Object o : classes) {
            Class c = (Class)o;

            try {
               ClassLoader cl = getClassLoader(c);
               if (cl != null) {
                  InputStream is = cl.getResourceAsStream(c.getName().replace('.', '/') + ".class");
                  if (is != null) {
                     try {
                        new ClassReader(is).accept(finder, 6);
                     } finally {
                        is.close();
                     }
                  }
               }
            } catch (IOException var15) {
            }
         }
      }
   }

   @Override
   public boolean evaluate(Object arg) {
      return !this.rejected.contains(arg) && this.unique.add(MethodWrapper.create((Method)arg));
   }

   private static ClassLoader getClassLoader(Class c) {
      ClassLoader cl = c.getClassLoader();
      if (cl == null) {
         cl = DuplicatesPredicate.class.getClassLoader();
      }

      if (cl == null) {
         cl = Thread.currentThread().getContextClassLoader();
      }

      return cl;
   }

   private static class UnnecessaryBridgeFinder extends ClassVisitor {
      private final Set rejected;
      private Signature currentMethodSig = null;
      private Map methods = new HashMap();

      UnnecessaryBridgeFinder(Set rejected) {
         super(Constants.ASM_API);
         this.rejected = rejected;
      }

      void addSuspectMethod(Method m) {
         this.methods.put(ReflectUtils.getSignature(m), m);
      }

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
         Signature sig = new Signature(name, desc);
         final Method currentMethod = (Method)this.methods.remove(sig);
         if (currentMethod != null) {
            this.currentMethodSig = sig;
            return new MethodVisitor(Constants.ASM_API) {
               @Override
               public void visitMethodInsn(int opcode, String owner, String namex, String descx, boolean itf) {
                  if (opcode == 183 && UnnecessaryBridgeFinder.this.currentMethodSig != null) {
                     Signature target = new Signature(namex, descx);
                     if (target.equals(UnnecessaryBridgeFinder.this.currentMethodSig)) {
                        UnnecessaryBridgeFinder.this.rejected.add(currentMethod);
                     }

                     UnnecessaryBridgeFinder.this.currentMethodSig = null;
                  }
               }
            };
         } else {
            return null;
         }
      }
   }
}
