package org.springframework.web.util.pattern;

import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class CaptureSegmentsPathElement extends PathElement {
   private final String variableName;

   CaptureSegmentsPathElement(int pos, char[] captureDescriptor, char separator) {
      super(pos, separator);
      this.variableName = new String(captureDescriptor, 2, captureDescriptor.length - 3);
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      if (pathIndex == 0 && this.next != null) {
         for (int endPathIndex = pathIndex; endPathIndex < matchingContext.pathLength; endPathIndex++) {
            if (this.next.matches(endPathIndex, matchingContext)) {
               this.collectParameters(matchingContext, pathIndex, endPathIndex);
               return true;
            }
         }

         return false;
      } else {
         if (pathIndex < matchingContext.pathLength && !matchingContext.isSeparator(pathIndex)) {
            return false;
         }

         if (matchingContext.determineRemainingPath) {
            matchingContext.remainingPathIndex = matchingContext.pathLength;
         }

         this.collectParameters(matchingContext, pathIndex, matchingContext.pathLength);
         return true;
      }
   }

   private void collectParameters(PathPattern.MatchingContext matchingContext, int pathIndex, int endPathIndex) {
      if (matchingContext.extractingVariables) {
         MultiValueMap<String, String> parametersCollector = NO_PARAMETERS;

         for (int i = pathIndex; i < endPathIndex; i++) {
            PathContainer.Element element = matchingContext.pathElements.get(i);
            if (element instanceof PathContainer.PathSegment pathSegment) {
               MultiValueMap<String, String> parameters = pathSegment.parameters();
               if (!parameters.isEmpty()) {
                  if (parametersCollector == NO_PARAMETERS) {
                     parametersCollector = new LinkedMultiValueMap();
                  }

                  parametersCollector.addAll(parameters);
               }
            }
         }

         matchingContext.set(this.variableName, this.pathToString(pathIndex, endPathIndex, matchingContext.pathElements), parametersCollector);
      }
   }

   private String pathToString(int fromSegment, int toSegment, List<PathContainer.Element> pathElements) {
      StringBuilder sb = new StringBuilder();
      int i = fromSegment;

      for (int max = toSegment; i < max; i++) {
         PathContainer.Element element = pathElements.get(i);
         if (element instanceof PathContainer.PathSegment pathSegment) {
            sb.append(pathSegment.valueToMatch());
         } else {
            sb.append(element.value());
         }
      }

      return sb.toString();
   }

   @Override
   public int getNormalizedLength() {
      return 1;
   }

   @Override
   public char[] getChars() {
      return ("/{*" + this.variableName + "}").toCharArray();
   }

   @Override
   public int getWildcardCount() {
      return 0;
   }

   @Override
   public int getCaptureCount() {
      return 1;
   }

   @Override
   public String toString() {
      return "CaptureSegments(/{*" + this.variableName + "})";
   }
}
