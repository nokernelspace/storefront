package org.springframework.aop.framework;

public interface AdvisedSupportListener {
   void activated(AdvisedSupport advised);

   void adviceChanged(AdvisedSupport advised);
}
