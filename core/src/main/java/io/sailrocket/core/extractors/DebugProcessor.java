package io.sailrocket.core.extractors;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DebugProcessor implements Session.Processor {
   private static final Logger log = LoggerFactory.getLogger(DebugProcessor.class);

   @Override
   public void before(Session session) {
      log.debug("Before");
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      log.debug("Process (last? {}): '{}'", isLastPart, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void after(Session session) {
      log.debug("After");
   }
}
