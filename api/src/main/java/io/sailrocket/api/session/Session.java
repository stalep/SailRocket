package io.sailrocket.api.session;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.api.http.ValidatorResults;
import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.config.Phase;

public interface Session {
   /**
    * @return Integer >= 0 that's unique across whole simulation
    */
   int uniqueId();

   /// Common utility objects
   HttpClientPool httpClientPool();

   EventExecutor executor();

   Phase phase();

   ValidatorResults validatorResults();

   Statistics statistics(int sequenceId);

   Statistics[] statistics();

   RequestQueue requestQueue();

   /// Variable-related methods
   Session declare(Object key);

   Object getObject(Object key);

   Session setObject(Object key, Object value);

   Session declareInt(Object key);

   int getInt(Object key);

   Session setInt(Object key, int value);

   default Session addToInt(Object key, int delta) {
      setInt(key, getInt(key) + delta);
      return this;
   }

   boolean isSet(Object key);

   /**
    * Make variable set without changing it's (pre-allocated) value.
    */
   Object activate(Object key);

   Session unset(Object key);

   // Resources
   <R extends Session.Resource> void declareResource(ResourceKey<R> key, R resource);

   <R extends Session.Resource> R getResource(ResourceKey<R> key);

   // Sequence related methods
   void currentSequence(SequenceInstance current);

   SequenceInstance currentSequence();

   /**
    * Run anything that can be executed.
    * @param executor
    */
   void proceed(EventExecutor executor);

   void reset();

   void nextSequence(String name);

   void stop();

   void fail(Throwable t);

   interface Processor {
      /**
       * Invoked before we record first value from given response.
       * @param session
       */
      default void before(Session session) {
      }

      void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart);

      /**
       * Invoked after we record the last value from given response.
       * @param session
       */
      default void after(Session session) {
      }
   }

   interface Var {
      boolean isSet();
      void unset();
   }

   /**
    * Just a marker interface.
    */
   interface Resource {
   }

   interface ResourceKey<R extends Resource> {}
}
