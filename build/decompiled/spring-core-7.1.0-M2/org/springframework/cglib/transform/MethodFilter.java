package org.springframework.cglib.transform;

public interface MethodFilter {
   boolean accept(int access, String name, String desc, String signature, String[] exceptions);
}
