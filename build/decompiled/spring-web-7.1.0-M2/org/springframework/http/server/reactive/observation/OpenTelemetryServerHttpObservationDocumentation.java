package org.springframework.http.server.reactive.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.docs.ObservationDocumentation;

public enum OpenTelemetryServerHttpObservationDocumentation implements ObservationDocumentation {
   HTTP_REACTIVE_SERVER_REQUESTS {
      public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
         return OpenTelemetryServerRequestObservationConvention.class;
      }

      public KeyName[] getLowCardinalityKeyNames() {
         return OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.values();
      }

      public KeyName[] getHighCardinalityKeyNames() {
         return OpenTelemetryServerHttpObservationDocumentation.HighCardinalityKeyNames.values();
      }
   };

   public enum HighCardinalityKeyNames implements KeyName {
      URL_PATH {
         public String asString() {
            return "url.path";
         }
      },
      METHOD_ORIGINAL {
         public String asString() {
            return "http.request.method_original";
         }
      };
   }

   public enum LowCardinalityKeyNames implements KeyName {
      METHOD {
         public String asString() {
            return "http.request.method";
         }
      },
      STATUS {
         public String asString() {
            return "http.response.status_code";
         }
      },
      ROUTE {
         public String asString() {
            return "http.route";
         }
      },
      EXCEPTION {
         public String asString() {
            return "error.type";
         }
      },
      SCHEME {
         public String asString() {
            return "url.scheme";
         }
      },
      OUTCOME {
         public String asString() {
            return "outcome";
         }
      };
   }
}
