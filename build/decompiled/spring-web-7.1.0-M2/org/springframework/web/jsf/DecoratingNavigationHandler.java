package org.springframework.web.jsf;

import jakarta.faces.application.NavigationHandler;
import jakarta.faces.context.FacesContext;
import org.jspecify.annotations.Nullable;

public abstract class DecoratingNavigationHandler extends NavigationHandler {
   private @Nullable NavigationHandler decoratedNavigationHandler;

   protected DecoratingNavigationHandler() {
   }

   protected DecoratingNavigationHandler(NavigationHandler originalNavigationHandler) {
      this.decoratedNavigationHandler = originalNavigationHandler;
   }

   public final @Nullable NavigationHandler getDecoratedNavigationHandler() {
      return this.decoratedNavigationHandler;
   }

   public final void handleNavigation(FacesContext facesContext, String fromAction, String outcome) {
      this.handleNavigation(facesContext, fromAction, outcome, this.decoratedNavigationHandler);
   }

   public abstract void handleNavigation(
      FacesContext facesContext, @Nullable String fromAction, @Nullable String outcome, @Nullable NavigationHandler originalNavigationHandler
   );

   protected final void callNextHandlerInChain(
      FacesContext facesContext, @Nullable String fromAction, @Nullable String outcome, @Nullable NavigationHandler originalNavigationHandler
   ) {
      NavigationHandler decoratedNavigationHandler = this.getDecoratedNavigationHandler();
      if (decoratedNavigationHandler instanceof DecoratingNavigationHandler decHandler) {
         decHandler.handleNavigation(facesContext, fromAction, outcome, originalNavigationHandler);
      } else if (decoratedNavigationHandler != null) {
         decoratedNavigationHandler.handleNavigation(facesContext, fromAction, outcome);
      } else if (originalNavigationHandler != null) {
         originalNavigationHandler.handleNavigation(facesContext, fromAction, outcome);
      }
   }
}
