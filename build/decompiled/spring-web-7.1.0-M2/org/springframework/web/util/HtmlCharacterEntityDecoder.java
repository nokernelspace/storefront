package org.springframework.web.util;

class HtmlCharacterEntityDecoder {
   private static final int MAX_REFERENCE_SIZE = 10;
   private final HtmlCharacterEntityReferences characterEntityReferences;
   private final String originalMessage;
   private final StringBuilder decodedMessage;
   private int currentPosition = 0;
   private int nextPotentialReferencePosition = -1;
   private int nextSemicolonPosition = -2;

   public HtmlCharacterEntityDecoder(HtmlCharacterEntityReferences characterEntityReferences, String original) {
      this.characterEntityReferences = characterEntityReferences;
      this.originalMessage = original;
      this.decodedMessage = new StringBuilder(original.length());
   }

   public String decode() {
      while (this.currentPosition < this.originalMessage.length()) {
         this.findNextPotentialReference(this.currentPosition);
         this.copyCharactersTillPotentialReference();
         this.processPossibleReference();
      }

      return this.decodedMessage.toString();
   }

   private void findNextPotentialReference(int startPosition) {
      this.nextPotentialReferencePosition = Math.max(startPosition, this.nextSemicolonPosition - 10);

      do {
         this.nextPotentialReferencePosition = this.originalMessage.indexOf(38, this.nextPotentialReferencePosition);
         if (this.nextSemicolonPosition != -1 && this.nextSemicolonPosition < this.nextPotentialReferencePosition) {
            this.nextSemicolonPosition = this.originalMessage.indexOf(59, this.nextPotentialReferencePosition + 1);
         }

         if (this.nextPotentialReferencePosition == -1) {
            break;
         }

         if (this.nextSemicolonPosition == -1) {
            this.nextPotentialReferencePosition = -1;
            break;
         }

         if (this.nextSemicolonPosition - this.nextPotentialReferencePosition < 10) {
            break;
         }

         this.nextPotentialReferencePosition++;
      } while (this.nextPotentialReferencePosition != -1);
   }

   private void copyCharactersTillPotentialReference() {
      if (this.nextPotentialReferencePosition != this.currentPosition) {
         int skipUntilIndex = this.nextPotentialReferencePosition != -1 ? this.nextPotentialReferencePosition : this.originalMessage.length();
         if (skipUntilIndex - this.currentPosition > 3) {
            this.decodedMessage.append(this.originalMessage, this.currentPosition, skipUntilIndex);
            this.currentPosition = skipUntilIndex;
         } else {
            while (this.currentPosition < skipUntilIndex) {
               this.decodedMessage.append(this.originalMessage.charAt(this.currentPosition++));
            }
         }
      }
   }

   private void processPossibleReference() {
      if (this.nextPotentialReferencePosition != -1) {
         boolean isNumberedReference = this.originalMessage.charAt(this.currentPosition + 1) == '#';
         boolean wasProcessable = isNumberedReference ? this.processNumberedReference() : this.processNamedReference();
         if (wasProcessable) {
            this.currentPosition = this.nextSemicolonPosition + 1;
         } else {
            char currentChar = this.originalMessage.charAt(this.currentPosition);
            this.decodedMessage.append(currentChar);
            this.currentPosition++;
         }
      }
   }

   private boolean processNumberedReference() {
      char referenceChar = this.originalMessage.charAt(this.nextPotentialReferencePosition + 2);
      boolean isHexNumberedReference = referenceChar == 'x' || referenceChar == 'X';

      try {
         int value = !isHexNumberedReference ? Integer.parseInt(this.getReferenceSubstring(2)) : Integer.parseInt(this.getReferenceSubstring(3), 16);
         if (value >= 0 && value <= 1114111) {
            this.decodedMessage.appendCodePoint(value);
            return true;
         } else {
            return false;
         }
      } catch (NumberFormatException ex) {
         return false;
      }
   }

   private boolean processNamedReference() {
      String referenceName = this.getReferenceSubstring(1);
      char mappedCharacter = this.characterEntityReferences.convertToCharacter(referenceName);
      if (mappedCharacter != '\uffff') {
         this.decodedMessage.append(mappedCharacter);
         return true;
      } else {
         return false;
      }
   }

   private String getReferenceSubstring(int referenceOffset) {
      return this.originalMessage.substring(this.nextPotentialReferencePosition + referenceOffset, this.nextSemicolonPosition);
   }
}
