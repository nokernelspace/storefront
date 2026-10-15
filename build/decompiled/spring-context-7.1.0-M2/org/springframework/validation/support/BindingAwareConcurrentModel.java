package org.springframework.validation.support;

import org.jspecify.annotations.Nullable;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BindingResult;

public class BindingAwareConcurrentModel extends ConcurrentModel {
   @Override
   public @Nullable Object put(String key, @Nullable Object value) {
      this.removeBindingResultIfNecessary(key, value);
      return super.put(key, value);
   }

   private void removeBindingResultIfNecessary(String key, @Nullable Object value) {
      if (!key.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
         String resultKey = BindingResult.MODEL_KEY_PREFIX + key;
         BindingResult result = (BindingResult)this.get(resultKey);
         if (result != null && result.getTarget() != value) {
            this.remove(resultKey);
         }
      }
   }
}
