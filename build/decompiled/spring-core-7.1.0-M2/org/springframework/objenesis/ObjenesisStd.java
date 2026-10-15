package org.springframework.objenesis;

import org.springframework.objenesis.strategy.StdInstantiatorStrategy;

public class ObjenesisStd extends ObjenesisBase {
   public ObjenesisStd() {
      super(new StdInstantiatorStrategy());
   }

   public ObjenesisStd(boolean useCache) {
      super(new StdInstantiatorStrategy(), useCache);
   }
}
