package org.springframework.validation.support;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BindingResult;

public class BindingAwareModelMap extends ExtendedModelMap {
   public Object put(String key, @Nullable Object value) {
      this.removeBindingResultIfNecessary(key, value);
      return super.put(key, value);
   }

   @Override
   public void putAll(Map<? extends String, ?> map) {
      map.forEach(this::removeBindingResultIfNecessary);
      super.putAll((Map<? extends String, ? extends Object>)map);
   }

   private void removeBindingResultIfNecessary(Object key, @Nullable Object value) {
      if (key instanceof String attributeName && !attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
         String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + attributeName;
         BindingResult bindingResult = (BindingResult)this.get(bindingResultKey);
         if (bindingResult != null && bindingResult.getTarget() != value) {
            this.remove(bindingResultKey);
         }
      }
   }
}
