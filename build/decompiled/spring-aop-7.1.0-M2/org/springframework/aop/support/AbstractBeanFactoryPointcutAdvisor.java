package org.springframework.aop.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.util.Assert;

public abstract class AbstractBeanFactoryPointcutAdvisor extends AbstractPointcutAdvisor implements BeanFactoryAware {
   private @Nullable String adviceBeanName;
   private @Nullable BeanFactory beanFactory;
   private transient volatile @Nullable Advice advice;
   private transient Object adviceMonitor = new Object();

   public void setAdviceBeanName(@Nullable String adviceBeanName) {
      this.adviceBeanName = adviceBeanName;
   }

   public @Nullable String getAdviceBeanName() {
      return this.adviceBeanName;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   public void setAdvice(Advice advice) {
      synchronized (this.adviceMonitor) {
         this.advice = advice;
      }
   }

   @Override
   public Advice getAdvice() {
      Advice advice = this.advice;
      if (advice != null) {
         return advice;
      }

      Assert.state(this.adviceBeanName != null, "'adviceBeanName' must be specified");
      Assert.state(this.beanFactory != null, "BeanFactory must be set to resolve 'adviceBeanName'");
      if (this.beanFactory.isSingleton(this.adviceBeanName)) {
         advice = (Advice)this.beanFactory.getBean(this.adviceBeanName, Advice.class);
         this.advice = advice;
         return advice;
      }

      synchronized (this.adviceMonitor) {
         advice = this.advice;
         if (advice == null) {
            advice = (Advice)this.beanFactory.getBean(this.adviceBeanName, Advice.class);
            this.advice = advice;
         }

         return advice;
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(this.getClass().getName());
      sb.append(": advice ");
      if (this.adviceBeanName != null) {
         sb.append("bean '").append(this.adviceBeanName).append('\'');
      } else {
         sb.append(this.advice);
      }

      return sb.toString();
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.adviceMonitor = new Object();
   }
}
