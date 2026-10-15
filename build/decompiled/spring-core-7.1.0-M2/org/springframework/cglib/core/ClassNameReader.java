package org.springframework.cglib.core;

import java.util.ArrayList;
import java.util.List;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;

public class ClassNameReader {
   private static final ClassNameReader.EarlyExitException EARLY_EXIT = new ClassNameReader.EarlyExitException();

   private ClassNameReader() {
   }

   public static String getClassName(ClassReader r) {
      return r.getClassName().replace('/', '.');
   }

   public static String[] getClassInfo(ClassReader r) {
      final List<String> array = new ArrayList<>();

      try {
         r.accept(new ClassVisitor(Constants.ASM_API, null) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
               array.add(name.replace('/', '.'));
               if (superName != null) {
                  array.add(superName.replace('/', '.'));
               }

               for (String element : interfaces) {
                  array.add(element.replace('/', '.'));
               }

               throw ClassNameReader.EARLY_EXIT;
            }
         }, 6);
      } catch (ClassNameReader.EarlyExitException var3) {
      }

      return array.toArray(new String[0]);
   }

   private static class EarlyExitException extends RuntimeException {
   }
}
