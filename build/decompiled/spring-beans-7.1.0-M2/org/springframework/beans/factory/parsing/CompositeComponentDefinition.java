package org.springframework.beans.factory.parsing;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class CompositeComponentDefinition extends AbstractComponentDefinition {
   private final String name;
   private final @Nullable Object source;
   private final List<ComponentDefinition> nestedComponents = new ArrayList<>();

   public CompositeComponentDefinition(String name, @Nullable Object source) {
      Assert.notNull(name, "Name must not be null");
      this.name = name;
      this.source = source;
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void addNestedComponent(ComponentDefinition component) {
      Assert.notNull(component, "ComponentDefinition must not be null");
      this.nestedComponents.add(component);
   }

   public ComponentDefinition[] getNestedComponents() {
      return this.nestedComponents.toArray(new ComponentDefinition[0]);
   }
}
