package io.sailrocket.api.config;

import java.io.Serializable;
import java.util.Collection;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class Phase implements Serializable {
   protected static final Logger log = LoggerFactory.getLogger(Phase.class);
   protected static final boolean trace = log.isTraceEnabled();

   public final String name;
   public final Scenario scenario;
   public final long startTime;
   public final Collection<String> startAfter;
   public final Collection<String> startAfterStrict;
   public final Collection<String> terminateAfterStrict;
   public final long duration;
   public final long maxDuration;
   // identifier for sharing resources across iterations
   public final String sharedResources;

   public Phase(String name, Scenario scenario, long startTime,
                Collection<String> startAfter, Collection<String> startAfterStrict,
                Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources) {
      this.name = name;
      this.terminateAfterStrict = terminateAfterStrict;
      this.maxDuration = maxDuration;
      this.startAfter = startAfter;
      this.startAfterStrict = startAfterStrict;
      this.scenario = scenario;
      this.startTime = startTime;
      this.duration = duration;
      this.sharedResources = sharedResources;
      if (duration < 0) {
         throw new BenchmarkDefinitionException("Duration was not set for phase '" + name + "'");
      }
      if (scenario == null) {
         throw new BenchmarkDefinitionException("Scenario was not set for phase '" + name + "'");
      }

   }

   public String name() {
      return name;
   }

   public Scenario scenario() {
      return scenario;
   }

   /**
    * @return Start time in milliseconds after benchmark start, or negative value if the phase should start immediately
    * after its dependencies ({@link #startAfter()} and {@link #startAfterStrict()} are satisfied.
    */
   public long startTime() {
      return startTime;
   }

   /**
    * Phases that must be finished (not starting any further user sessions) in order to start.
    */
   public Collection<String> startAfter() {
      return startAfter;
   }

   /**
    * Phases that must be terminated (not running any user sessions) in order to start.
    */
   public Collection<String> startAfterStrict() {
      return startAfterStrict;
   }

   /**
    * Phases that must be terminated in order to terminate this phase.
    */
   public Collection<String> terminateAfterStrict() {
      return terminateAfterStrict;
   }

   /**
    * @return Duration in milliseconds over which new user sessions should be started.
    */
   public long duration() {
      return duration;
   }

   /**
    * @return Duration in milliseconds over which user sessions can run. After this time no more requests are allowed
    * and the phase should terminate.
    */
   public long maxDuration() {
      return maxDuration;
   }

   public static class AtOnce extends Phase {
      public final int users;

      public AtOnce(String name, Scenario scenario, long startTime,
                    Collection<String> startAfter, Collection<String> startAfterStrict,
                    Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, int users) {
         super(name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.users = users;
      }
   }

   public static class Always extends Phase {
      public final int users;

      public Always(String name, Scenario scenario, long startTime,
                    Collection<String> startAfter, Collection<String> startAfterStrict,
                    Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, int users) {
         super(name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.users = users;
      }
   }

   public static class RampPerSec extends Phase {
      public final double initialUsersPerSec;
      public final double targetUsersPerSec;
      public final int maxSessionsEstimate;


      public RampPerSec(String name, Scenario scenario, long startTime,
                        Collection<String> startAfter, Collection<String> startAfterStrict,
                        Collection<String> terminateAfterStrict,
                        long duration, long maxDuration,
                        double initialUsersPerSec, double targetUsersPerSec,
                        String sharedResources, int maxSessionsEstimate) {
         super(name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
         this.maxSessionsEstimate = maxSessionsEstimate;
      }
   }

   public static class ConstantPerSec extends Phase {
      public final double usersPerSec;
      public final int maxSessionsEstimate;

      public ConstantPerSec(String name, Scenario scenario, long startTime,
                            Collection<String> startAfter, Collection<String> startAfterStrict,
                            Collection<String> terminateAfterStrict,
                            long duration, long maxDuration, String sharedResources, double usersPerSec, int maxSessionsEstimate) {
         super(name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.usersPerSec = usersPerSec;
         this.maxSessionsEstimate = maxSessionsEstimate;
      }
   }

   public static class Sequentially extends Phase {
      public final int repeats;

      public Sequentially(String name, Scenario scenario, long startTime,
                          Collection<String> startAfter, Collection<String> startAfterStrict,
                          Collection<String> terminateAfterStrict,
                          long duration, long maxDuration, String sharedResources, int repeats) {
         super(name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.repeats = repeats;
      }
   }

   public static class Noop extends Phase {
      public Noop(String name, Collection<String> startAfter, Collection<String> startAfterStrict, Collection<String> terminateAfterStrict) {
         super(name, new Scenario(new Sequence[0], new Sequence[0], new String[0], new String[0]),
               -1, startAfter, startAfterStrict, terminateAfterStrict, 0, -1, null);
      }
   }
}
