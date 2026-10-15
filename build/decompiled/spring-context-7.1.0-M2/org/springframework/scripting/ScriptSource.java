package org.springframework.scripting;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

public interface ScriptSource {
   String getScriptAsString() throws IOException;

   boolean isModified();

   @Nullable String suggestedClassName();
}
