package org.springframework.http.server.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.docs.ObservationDocumentation;

public enum ServerHttpObservationDocumentation implements ObservationDocumentation {
   HTTP_SERVLET_SERVER_REQUESTS {
      public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
         return DefaultServerRequestObservationConvention.class;
      }

      public KeyName[] getLowCardinalityKeyNames() {
         return ServerHttpObservationDocumentation.LowCardinalityKeyNames.values();
      }

      public KeyName[] getHighCardinalityKeyNames() {
         return ServerHttpObservationDocumentation.HighCardinalityKeyNames.values();
      }
   };

   public enum HighCardinalityKeyNames implements KeyName {
      HTTP_URL {
         public String asString() {
            return "http.url";
         }
      };
   }

   public enum LowCardinalityKeyNames implements KeyName {
      METHOD {
         public String asString() {
            return "method";
         }
      },
      STATUS {
         public String asString() {
            return "status";
         }
      },
      URI {
         public String asString() {
            return "uri";
         }
      },
      EXCEPTION {
         public String asString() {
            return "exception";
         }
      },
      OUTCOME {
         public String asString() {
            return "outcome";
         }
      };
   }
}
