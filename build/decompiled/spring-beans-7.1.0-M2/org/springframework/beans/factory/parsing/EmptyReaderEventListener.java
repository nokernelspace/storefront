package org.springframework.beans.factory.parsing;

public class EmptyReaderEventListener implements ReaderEventListener {
   @Override
   public void defaultsRegistered(DefaultsDefinition defaultsDefinition) {
   }

   @Override
   public void componentRegistered(ComponentDefinition componentDefinition) {
   }

   @Override
   public void aliasRegistered(AliasDefinition aliasDefinition) {
   }

   @Override
   public void importProcessed(ImportDefinition importDefinition) {
   }
}
