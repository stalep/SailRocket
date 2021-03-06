package io.sailrocket.core.steps;

import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.VarReference;
import io.sailrocket.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class DependencyStep implements Step {
   private static final Logger log = LoggerFactory.getLogger(Step.class);
   private static final boolean trace = log.isTraceEnabled();

   private final VarReference[] dependencies;

   protected DependencyStep(VarReference[] dependencies) {
      this.dependencies = dependencies;
   }

   @Override
   public boolean prepare(Session session) {
      if (dependencies != null) {
         for (VarReference ref : dependencies) {
            if (!ref.isSet(session)) {
               log.trace("Sequence is blocked by missing var reference {}", ref);
               return false;
            }
         }
      }
      return true;
   }
}
