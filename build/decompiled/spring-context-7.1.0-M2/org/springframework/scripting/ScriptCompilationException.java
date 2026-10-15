package org.springframework.scripting;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;

public class ScriptCompilationException extends NestedRuntimeException {
   private final @Nullable ScriptSource scriptSource;

   public ScriptCompilationException(String msg) {
      super(msg);
      this.scriptSource = null;
   }

   public ScriptCompilationException(String msg, Throwable cause) {
      super(msg, cause);
      this.scriptSource = null;
   }

   public ScriptCompilationException(ScriptSource scriptSource, String msg) {
      super("Could not compile " + scriptSource + ": " + msg);
      this.scriptSource = scriptSource;
   }

   public ScriptCompilationException(ScriptSource scriptSource, Throwable cause) {
      super("Could not compile " + scriptSource, cause);
      this.scriptSource = scriptSource;
   }

   public ScriptCompilationException(ScriptSource scriptSource, String msg, Throwable cause) {
      super("Could not compile " + scriptSource + ": " + msg, cause);
      this.scriptSource = scriptSource;
   }

   public @Nullable ScriptSource getScriptSource() {
      return this.scriptSource;
   }
}
