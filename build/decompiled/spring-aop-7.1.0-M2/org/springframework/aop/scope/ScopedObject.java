package org.springframework.aop.scope;

import org.springframework.aop.RawTargetAccess;

public interface ScopedObject extends RawTargetAccess {
   Object getTargetObject();

   void removeFromScope();
}
