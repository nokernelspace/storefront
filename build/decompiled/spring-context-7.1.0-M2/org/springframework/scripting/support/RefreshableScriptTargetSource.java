package org.springframework.scripting.support;

import org.springframework.aop.target.dynamic.BeanFactoryRefreshableTargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.scripting.ScriptFactory;
import org.springframework.scripting.ScriptSource;
import org.springframework.util.Assert;

public class RefreshableScriptTargetSource extends BeanFactoryRefreshableTargetSource {
   private final ScriptFactory scriptFactory;
   private final ScriptSource scriptSource;
   private final boolean isFactoryBean;

   public RefreshableScriptTargetSource(BeanFactory beanFactory, String beanName, ScriptFactory scriptFactory, ScriptSource scriptSource, boolean isFactoryBean) {
      super(beanFactory, beanName);
      Assert.notNull(scriptFactory, "ScriptFactory must not be null");
      Assert.notNull(scriptSource, "ScriptSource must not be null");
      this.scriptFactory = scriptFactory;
      this.scriptSource = scriptSource;
      this.isFactoryBean = isFactoryBean;
   }

   protected boolean requiresRefresh() {
      return this.scriptFactory.requiresScriptedObjectRefresh(this.scriptSource);
   }

   protected Object obtainFreshBean(BeanFactory beanFactory, String beanName) {
      return super.obtainFreshBean(beanFactory, this.isFactoryBean ? "&" + beanName : beanName);
   }
}
