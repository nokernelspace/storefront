package org.springframework.http.client.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.docs.ObservationDocumentation;

public enum ClientHttpObservationDocumentation implements ObservationDocumentation {
   HTTP_CLIENT_EXCHANGES {
      public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
         return DefaultClientRequestObservationConvention.class;
      }

      public KeyName[] getLowCardinalityKeyNames() {
         return ClientHttpObservationDocumentation.LowCardinalityKeyNames.values();
      }

      public KeyName[] getHighCardinalityKeyNames() {
         return new KeyName[]{ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL};
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
      URI {
         public String asString() {
            return "uri";
         }
      },
      STATUS {
         public String asString() {
            return "status";
         }
      },
      CLIENT_NAME {
         public String asString() {
            return "client.name";
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
