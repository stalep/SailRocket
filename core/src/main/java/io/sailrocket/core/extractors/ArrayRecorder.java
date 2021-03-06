package io.sailrocket.core.extractors;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.session.ObjectVar;
import io.sailrocket.core.api.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ArrayRecorder implements Session.Processor, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ArrayRecorder.class);
   private final String var;
   private final int maxSize;

   public ArrayRecorder(String var, int maxSize) {
      this.var = var;
      this.maxSize = maxSize;
   }

   public void before(Session session) {
      ObjectVar[] array = (ObjectVar[]) session.activate(var);
      for (int i = 0; i < array.length; ++i) {
         array[i].unset();
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      ObjectVar[] array = (ObjectVar[]) session.activate(var);
      String value = data.toString(offset, length, StandardCharsets.UTF_8);
      for (int i = 0; i < array.length; ++i) {
         if (array[i].isSet()) continue;
         array[i].set(value);
         return;
      }
      log.warn("Exceed maximum size of the array {} ({}), dropping value {}", var, maxSize, value);
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
      session.setObject(var, ObjectVar.newArray(session, maxSize));
      session.unset(var);
   }
}
