package org.springframework.validation;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public abstract class BindingResultUtils {
   public static @Nullable BindingResult getBindingResult(Map<?, ?> model, String name) {
      Assert.notNull(model, "Model map must not be null");
      Assert.notNull(name, "Name must not be null");
      Object attr = model.get(BindingResult.MODEL_KEY_PREFIX + name);
      if (attr == null) {
         return null;
      } else if (attr instanceof BindingResult bindingResult) {
         return bindingResult;
      } else {
         throw new IllegalStateException("BindingResult attribute is not of type BindingResult: " + attr);
      }
   }

   public static BindingResult getRequiredBindingResult(Map<?, ?> model, String name) {
      BindingResult bindingResult = getBindingResult(model, name);
      if (bindingResult == null) {
         throw new IllegalStateException("No BindingResult attribute found for name '" + name + "'- have you exposed the correct model?");
      } else {
         return bindingResult;
      }
   }
}
