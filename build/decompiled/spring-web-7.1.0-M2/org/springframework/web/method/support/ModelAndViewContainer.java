package org.springframework.web.method.support;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.ui.ModelMap;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.SimpleSessionStatus;

public class ModelAndViewContainer {
   private @Nullable Object view;
   private final ModelMap defaultModel = new BindingAwareModelMap();
   private @Nullable ModelMap redirectModel;
   private boolean redirectModelScenario = false;
   private @Nullable HttpStatusCode status;
   private final Set<String> noBinding = new HashSet<>(4);
   private final Set<String> bindingDisabled = new HashSet<>(4);
   private final SessionStatus sessionStatus = new SimpleSessionStatus();
   private boolean requestHandled = false;

   public void setViewName(@Nullable String viewName) {
      this.view = viewName;
   }

   public @Nullable String getViewName() {
      return this.view instanceof String viewName ? viewName : null;
   }

   public void setView(@Nullable Object view) {
      this.view = view;
   }

   public @Nullable Object getView() {
      return this.view;
   }

   public boolean isViewReference() {
      return this.view instanceof String;
   }

   public ModelMap getModel() {
      if (!this.redirectModelScenario) {
         return this.defaultModel;
      }

      if (this.redirectModel == null) {
         this.redirectModel = new ModelMap();
      }

      return this.redirectModel;
   }

   public ModelMap getDefaultModel() {
      return this.defaultModel;
   }

   public void setRedirectModel(ModelMap redirectModel) {
      this.redirectModel = redirectModel;
   }

   public void setRedirectModelScenario(boolean redirectModelScenario) {
      this.redirectModelScenario = redirectModelScenario;
   }

   public void setStatus(@Nullable HttpStatusCode status) {
      this.status = status;
   }

   public @Nullable HttpStatusCode getStatus() {
      return this.status;
   }

   public void setBindingDisabled(String attributeName) {
      this.bindingDisabled.add(attributeName);
   }

   public boolean isBindingDisabled(String name) {
      return this.bindingDisabled.contains(name) || this.noBinding.contains(name);
   }

   public void setBinding(String attributeName, boolean enabled) {
      if (!enabled) {
         this.noBinding.add(attributeName);
      } else {
         this.noBinding.remove(attributeName);
      }
   }

   public SessionStatus getSessionStatus() {
      return this.sessionStatus;
   }

   public void setRequestHandled(boolean requestHandled) {
      this.requestHandled = requestHandled;
   }

   public boolean isRequestHandled() {
      return this.requestHandled;
   }

   public ModelAndViewContainer addAttribute(String name, @Nullable Object value) {
      this.getModel().addAttribute(name, value);
      return this;
   }

   public ModelAndViewContainer addAttribute(Object value) {
      this.getModel().addAttribute(value);
      return this;
   }

   public ModelAndViewContainer addAllAttributes(@Nullable Map<String, ?> attributes) {
      this.getModel().addAllAttributes(attributes);
      return this;
   }

   public ModelAndViewContainer mergeAttributes(@Nullable Map<String, ?> attributes) {
      this.getModel().mergeAttributes(attributes);
      return this;
   }

   public ModelAndViewContainer removeAttributes(@Nullable Map<String, ?> attributes) {
      if (attributes != null) {
         for (String key : attributes.keySet()) {
            this.getModel().remove(key);
         }
      }

      return this;
   }

   public boolean containsAttribute(String name) {
      return this.getModel().containsAttribute(name);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("ModelAndViewContainer: ");
      if (!this.isRequestHandled()) {
         if (this.isViewReference()) {
            sb.append("reference to view with name '").append(this.view).append('\'');
         } else {
            sb.append("View is [").append(this.view).append(']');
         }

         if (!this.redirectModelScenario) {
            sb.append("; default model ");
         } else {
            sb.append("; redirect model ");
         }

         sb.append(this.getModel());
      } else {
         sb.append("Request handled directly");
      }

      return sb.toString();
   }
}
