package org.springframework.web.util.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

class InternalPathPatternParser {
   private final PathPatternParser parser;
   private char[] pathPatternData = new char[0];
   private int pathPatternLength;
   int pos;
   private int singleCharWildcardCount;
   private boolean wildcard = false;
   private boolean isCaptureSegmentsVariable = false;
   private boolean insideVariableCapture = false;
   private int variableCaptureCount = 0;
   private int pathElementStart;
   private int variableCaptureStart;
   private boolean hasMultipleSegmentsElement = false;
   private @Nullable List<String> capturedVariableNames;
   private @Nullable PathElement headPE;
   private @Nullable PathElement currentPE;

   InternalPathPatternParser(PathPatternParser parentParser) {
      this.parser = parentParser;
   }

   public PathPattern parse(String pathPattern) throws PatternParseException {
      Assert.notNull(pathPattern, "Path pattern must not be null");
      this.pathPatternData = pathPattern.toCharArray();
      this.pathPatternLength = this.pathPatternData.length;
      this.headPE = null;
      this.currentPE = null;
      this.capturedVariableNames = null;
      this.pathElementStart = -1;
      this.pos = 0;
      this.resetPathElementState();

      for (; this.pos < this.pathPatternLength; this.pos++) {
         char ch = this.pathPatternData[this.pos];
         char separator = this.parser.getPathOptions().separator();
         if (ch == separator) {
            if (this.pathElementStart != -1) {
               this.pushPathElement(this.createPathElement());
            }

            this.pushPathElement(new SeparatorPathElement(this.pos, separator));
         } else {
            if (this.pathElementStart == -1) {
               this.pathElementStart = this.pos;
            }

            if (ch == '?') {
               this.singleCharWildcardCount++;
            } else if (ch == '{') {
               if (this.insideVariableCapture) {
                  throw new PatternParseException(this.pos, this.pathPatternData, PatternParseException.PatternMessage.ILLEGAL_NESTED_CAPTURE);
               }

               this.insideVariableCapture = true;
               this.variableCaptureStart = this.pos;
            } else if (ch == '}') {
               if (!this.insideVariableCapture) {
                  throw new PatternParseException(this.pos, this.pathPatternData, PatternParseException.PatternMessage.MISSING_OPEN_CAPTURE);
               }

               this.insideVariableCapture = false;
               this.variableCaptureCount++;
            } else if (ch == ':') {
               if (this.insideVariableCapture && !this.isCaptureSegmentsVariable) {
                  this.skipCaptureRegex();
                  this.insideVariableCapture = false;
                  this.variableCaptureCount++;
               }
            } else if (this.isDoubleWildcard(separator)) {
               this.checkValidMultipleSegmentsElements(this.pos, this.pos + 1);
               this.pushPathElement(new WildcardSegmentsPathElement(this.pos, separator));
               this.hasMultipleSegmentsElement = true;
               this.pos++;
            } else if (ch == '*') {
               if (this.insideVariableCapture && this.variableCaptureStart == this.pos - 1) {
                  this.isCaptureSegmentsVariable = true;
               }

               this.wildcard = true;
            }

            if (this.insideVariableCapture) {
               if (this.variableCaptureStart + 1 + (this.isCaptureSegmentsVariable ? 1 : 0) == this.pos && !Character.isJavaIdentifierStart(ch)) {
                  throw new PatternParseException(
                     this.pos,
                     this.pathPatternData,
                     PatternParseException.PatternMessage.ILLEGAL_CHARACTER_AT_START_OF_CAPTURE_DESCRIPTOR,
                     Character.toString(ch)
                  );
               }

               if (this.pos > this.variableCaptureStart + 1 + (this.isCaptureSegmentsVariable ? 1 : 0) && !Character.isJavaIdentifierPart(ch) && ch != '-') {
                  throw new PatternParseException(
                     this.pos, this.pathPatternData, PatternParseException.PatternMessage.ILLEGAL_CHARACTER_IN_CAPTURE_DESCRIPTOR, Character.toString(ch)
                  );
               }
            }
         }
      }

      if (this.pathElementStart != -1) {
         this.pushPathElement(this.createPathElement());
      }

      return new PathPattern(pathPattern, this.parser, this.headPE);
   }

   private void skipCaptureRegex() {
      this.pos++;
      int regexStart = this.pos;
      int curlyBracketDepth = 0;
      boolean previousBackslash = false;

      while (this.pos < this.pathPatternLength) {
         char ch = this.pathPatternData[this.pos];
         if (ch == '\\' && !previousBackslash) {
            this.pos++;
            previousBackslash = true;
         } else {
            if (ch == '{' && !previousBackslash) {
               curlyBracketDepth++;
            } else if (ch == '}' && !previousBackslash) {
               if (curlyBracketDepth == 0) {
                  if (regexStart == this.pos) {
                     throw new PatternParseException(regexStart, this.pathPatternData, PatternParseException.PatternMessage.MISSING_REGEX_CONSTRAINT);
                  }

                  return;
               }

               curlyBracketDepth--;
            }

            if (ch == this.parser.getPathOptions().separator() && !previousBackslash) {
               throw new PatternParseException(this.pos, this.pathPatternData, PatternParseException.PatternMessage.MISSING_CLOSE_CAPTURE);
            }

            this.pos++;
            previousBackslash = false;
         }
      }

      throw new PatternParseException(this.pos - 1, this.pathPatternData, PatternParseException.PatternMessage.MISSING_CLOSE_CAPTURE);
   }

   private boolean isDoubleWildcard(char separator) {
      if (this.pos + 1 >= this.pathPatternLength) {
         return false;
      } else if (this.pathPatternData[this.pos] != '*' || this.pathPatternData[this.pos + 1] != '*') {
         return false;
      } else {
         return this.pos - 1 >= 0 && this.pathPatternData[this.pos - 1] != separator
            ? false
            : this.pos + 2 >= this.pathPatternLength || this.pathPatternData[this.pos + 2] == separator;
      }
   }

   private void checkValidMultipleSegmentsElements(int startPosition, int endPosition) {
      if (this.hasMultipleSegmentsElement) {
         throw new PatternParseException(this.pos, this.pathPatternData, PatternParseException.PatternMessage.CANNOT_HAVE_MANY_MULTISEGMENT_PATHELEMENTS);
      }

      if (startPosition > 1 && endPosition != this.pathPatternLength - 1) {
         throw new PatternParseException(this.pos, this.pathPatternData, PatternParseException.PatternMessage.INVALID_LOCATION_FOR_MULTISEGMENT_PATHELEMENT);
      }
   }

   private void pushPathElement(PathElement newPathElement) {
      if (!(newPathElement instanceof CaptureSegmentsPathElement) && !(newPathElement instanceof WildcardSegmentsPathElement)) {
         if (this.headPE == null) {
            this.headPE = newPathElement;
            this.currentPE = newPathElement;
         } else if (this.currentPE != null) {
            this.currentPE.next = newPathElement;
            newPathElement.prev = this.currentPE;
            this.currentPE = newPathElement;
         }
      } else if (this.currentPE == null) {
         this.headPE = newPathElement;
         this.currentPE = newPathElement;
      } else {
         if (!(this.currentPE instanceof SeparatorPathElement)) {
            throw new IllegalStateException("Expected SeparatorPathElement before " + newPathElement.getClass().getName() + " but was " + this.currentPE);
         }

         PathElement peBeforeSeparator = this.currentPE.prev;
         if (peBeforeSeparator == null) {
            this.headPE = newPathElement;
            newPathElement.prev = null;
         } else {
            peBeforeSeparator.next = newPathElement;
            newPathElement.prev = peBeforeSeparator;
         }

         this.currentPE = newPathElement;
      }

      this.resetPathElementState();
   }

   private char[] getPathElementText() {
      char[] pathElementText = new char[this.pos - this.pathElementStart];
      System.arraycopy(this.pathPatternData, this.pathElementStart, pathElementText, 0, this.pos - this.pathElementStart);
      return pathElementText;
   }

   private PathElement createPathElement() {
      if (this.insideVariableCapture) {
         throw new PatternParseException(this.pos, this.pathPatternData, PatternParseException.PatternMessage.MISSING_CLOSE_CAPTURE);
      }

      PathElement newPE = null;
      char separator = this.parser.getPathOptions().separator();
      if (this.variableCaptureCount > 0) {
         if (this.variableCaptureCount == 1 && this.pathElementStart == this.variableCaptureStart && this.pathPatternData[this.pos - 1] == '}') {
            if (this.isCaptureSegmentsVariable) {
               this.checkValidMultipleSegmentsElements(this.pathElementStart, this.pos - 1);
               this.hasMultipleSegmentsElement = true;
               newPE = new CaptureSegmentsPathElement(this.pathElementStart, this.getPathElementText(), separator);
            } else {
               try {
                  newPE = new CaptureVariablePathElement(this.pathElementStart, this.getPathElementText(), this.parser.isCaseSensitive(), separator);
               } catch (PatternSyntaxException pse) {
                  throw new PatternParseException(
                     pse,
                     this.findRegexStart(this.pathPatternData, this.pathElementStart) + pse.getIndex(),
                     this.pathPatternData,
                     PatternParseException.PatternMessage.REGEX_PATTERN_SYNTAX_EXCEPTION
                  );
               }

               this.recordCapturedVariable(this.pathElementStart, ((CaptureVariablePathElement)newPE).getVariableName());
            }
         } else {
            if (this.isCaptureSegmentsVariable) {
               throw new PatternParseException(
                  this.pathElementStart, this.pathPatternData, PatternParseException.PatternMessage.CAPTURE_ALL_IS_STANDALONE_CONSTRUCT
               );
            }

            RegexPathElement newRegexSection = new RegexPathElement(
               this.pathElementStart, this.getPathElementText(), this.parser.isCaseSensitive(), this.pathPatternData, separator
            );

            for (String variableName : newRegexSection.getVariableNames()) {
               this.recordCapturedVariable(this.pathElementStart, variableName);
            }

            newPE = newRegexSection;
         }
      } else if (this.wildcard) {
         if (this.pos - 1 == this.pathElementStart) {
            newPE = new WildcardPathElement(this.pathElementStart, separator);
         } else {
            newPE = new RegexPathElement(this.pathElementStart, this.getPathElementText(), this.parser.isCaseSensitive(), this.pathPatternData, separator);
         }
      } else if (this.singleCharWildcardCount != 0) {
         newPE = new SingleCharWildcardedPathElement(
            this.pathElementStart, this.getPathElementText(), this.singleCharWildcardCount, this.parser.isCaseSensitive(), separator
         );
      } else {
         newPE = new LiteralPathElement(this.pathElementStart, this.getPathElementText(), this.parser.isCaseSensitive(), separator);
      }

      return newPE;
   }

   private int findRegexStart(char[] data, int offset) {
      for (int pos = offset; pos < data.length; pos++) {
         if (data[pos] == ':') {
            return pos + 1;
         }
      }

      return -1;
   }

   private void resetPathElementState() {
      this.pathElementStart = -1;
      this.singleCharWildcardCount = 0;
      this.insideVariableCapture = false;
      this.variableCaptureCount = 0;
      this.wildcard = false;
      this.isCaptureSegmentsVariable = false;
      this.variableCaptureStart = -1;
   }

   private void recordCapturedVariable(int pos, String variableName) {
      if (this.capturedVariableNames == null) {
         this.capturedVariableNames = new ArrayList<>();
      }

      if (this.capturedVariableNames.contains(variableName)) {
         throw new PatternParseException(pos, this.pathPatternData, PatternParseException.PatternMessage.ILLEGAL_DOUBLE_CAPTURE, variableName);
      }

      this.capturedVariableNames.add(variableName);
   }
}
