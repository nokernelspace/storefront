package org.springframework.scripting;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

public interface ScriptFactory {
   String getScriptSourceLocator();

   Class<?> @Nullable [] getScriptInterfaces();

   boolean requiresConfigInterface();

   @Nullable Object getScriptedObject(ScriptSource scriptSource, Class<?> @Nullable ... actualInterfaces) throws IOException, ScriptCompilationException;

   @Nullable Class<?> getScriptedObjectType(ScriptSource scriptSource) throws IOException, ScriptCompilationException;

   boolean requiresScriptedObjectRefresh(ScriptSource scriptSource);
}
