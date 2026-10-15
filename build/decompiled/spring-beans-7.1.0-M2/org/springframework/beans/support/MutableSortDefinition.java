package org.springframework.beans.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

@Deprecated(since = "7.0.3", forRemoval = true)
public class MutableSortDefinition implements SortDefinition, Serializable {
   private String property = "";
   private boolean ignoreCase = true;
   private boolean ascending = true;
   private boolean toggleAscendingOnProperty = false;

   public MutableSortDefinition() {
   }

   public MutableSortDefinition(SortDefinition source) {
      this.property = source.getProperty();
      this.ignoreCase = source.isIgnoreCase();
      this.ascending = source.isAscending();
   }

   public MutableSortDefinition(String property, boolean ignoreCase, boolean ascending) {
      this.property = property;
      this.ignoreCase = ignoreCase;
      this.ascending = ascending;
   }

   public MutableSortDefinition(boolean toggleAscendingOnSameProperty) {
      this.toggleAscendingOnProperty = toggleAscendingOnSameProperty;
   }

   public void setProperty(String property) {
      if (!StringUtils.hasLength(property)) {
         this.property = "";
      } else {
         if (this.isToggleAscendingOnProperty()) {
            this.ascending = !property.equals(this.property) || !this.ascending;
         }

         this.property = property;
      }
   }

   @Override
   public String getProperty() {
      return this.property;
   }

   public void setIgnoreCase(boolean ignoreCase) {
      this.ignoreCase = ignoreCase;
   }

   @Override
   public boolean isIgnoreCase() {
      return this.ignoreCase;
   }

   public void setAscending(boolean ascending) {
      this.ascending = ascending;
   }

   @Override
   public boolean isAscending() {
      return this.ascending;
   }

   public void setToggleAscendingOnProperty(boolean toggleAscendingOnProperty) {
      this.toggleAscendingOnProperty = toggleAscendingOnProperty;
   }

   public boolean isToggleAscendingOnProperty() {
      return this.toggleAscendingOnProperty;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof SortDefinition that
            && this.getProperty().equals(that.getProperty())
            && this.isAscending() == that.isAscending()
            && this.isIgnoreCase() == that.isIgnoreCase();
   }

   @Override
   public int hashCode() {
      int hashCode = this.getProperty().hashCode();
      hashCode = 29 * hashCode + (this.isIgnoreCase() ? 1 : 0);
      return 29 * hashCode + (this.isAscending() ? 1 : 0);
   }
}
