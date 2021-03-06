package io.sailrocket.core.extractors;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DefragProcessor implements Session.Processor, ResourceUtilizer, Session.ResourceKey<DefragProcessor.Context> {
   private static final Logger log = LoggerFactory.getLogger(DefragProcessor.class);

   private final Session.Processor delegate;

   public DefragProcessor(Session.Processor delegate) {
      this.delegate = delegate;
   }

   @Override
   public void before(Session session) {
      delegate.before(session);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      Context ctx = session.getResource(this);
      if (isLastPart && !ctx.isBuffering()) {
         delegate.process(session, data, offset, length, true);
         return;
      }
      ctx.buffer(data, offset, length);
      if (isLastPart) {
         ctx.flush(session, delegate);
      }
   }

   @Override
   public void after(Session session) {
      delegate.after(session);
   }

   @Override
   public void reserve(Session session) {
      session.declare(this);
      // Note: contrary to the recommended pattern the Context won't reserve all objects ahead, the CompositeByteBuf
      // will be allocated only if needed (and only once). This is necessary since we don't know the type of allocator
      // that is used for the received buffers ahead.
      session.declareResource(this, new Context());
      if (delegate instanceof ResourceUtilizer) {
         ((ResourceUtilizer) delegate).reserve(session);
      }
   }

   static class Context implements Session.Resource {
      CompositeByteBuf composite = null;

      public boolean isBuffering() {
         return composite != null && composite.isReadable();
      }

      public void buffer(ByteBuf data, int offset, int length) {
         log.debug("Buffering {} bytes", length);
         if (composite == null) {
            composite = new CompositeByteBuf(data.alloc(), data.isDirect(), 16);
         }
         composite.addComponent(true, data.retainedSlice(offset, length));
      }

      public void flush(Session session, Session.Processor processor) {
         log.debug("Flushing {} bytes", composite.writerIndex());
         processor.process(session, composite, 0, composite.writerIndex(), true);
         // Not sure if this is the right call to forget/reuse the components (if needed)
         composite.clear();
      }
   }
}
