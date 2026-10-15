package org.springframework.cglib.transform;

import org.springframework.cglib.core.ClassTransformer;

public interface ClassTransformerFactory {
   ClassTransformer newInstance();
}
