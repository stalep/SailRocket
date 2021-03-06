package io.sailrocket.clustering;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.core.parser.BenchmarkParser;
import io.sailrocket.core.parser.ParserException;
import io.sailrocket.clustering.util.PersistenceUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class ControllerRestServer {
   private static final Logger log = LoggerFactory.getLogger(ControllerRestServer.class);
   private static final String MIME_TYPE_SERIALIZED = "application/java-serialized-object";
   private static final Set<String> MIME_TYPE_YAML = new HashSet<>(
         Arrays.asList("text/vnd.yaml", "text/yaml", "text/x-yaml", "application/x-yaml"));

   private static final String CONTROLLER_HOST = System.getProperty(Properties.CONTROLLER_HOST, "localhost");
   private static final int CONTROLLER_PORT = Integer.parseInt(System.getProperty(Properties.CONTROLLER_PORT, "8090"));
   private static final String BASE_URL = "http://" + CONTROLLER_HOST + ":" + CONTROLLER_PORT;

   private final AgentControllerVerticle controller;
   private final HttpServer httpServer;
   private final Router router;

   public ControllerRestServer(AgentControllerVerticle controller) {
      this.controller = controller;
      router = Router.router(controller.getVertx());

      router.route().handler(BodyHandler.create());
      router.get("/").handler(this::handleIndex);
      router.post("/benchmark").handler(this::handlePostBenchmark);
      router.get("/benchmark").handler(this::handleListBenchmarks);
      router.get("/benchmark/:benchmarkname").handler(this::handleGetBenchmark);
      router.get("/benchmark/:benchmarkname/start").handler(this::handleBenchmarkStart);
      router.get("/agents").handler(this::handleGetAgentCount);
      router.get("/run").handler(this::handleListRuns);
      router.get("/run/:runid").handler(this::handleGetRun);
      router.get("/run/:runid/kill").handler(this::handleRunKill);

      httpServer = controller.getVertx().createHttpServer().requestHandler(router::accept).listen(CONTROLLER_PORT);
   }

   public void stop(Future<Void> stopFuture) {
      httpServer.close(result -> stopFuture.complete());
   }

   private void handleIndex(RoutingContext ctx) {
      StringBuilder sb = new StringBuilder("Hello from SailRocket, these are available URLs:\n");
      for (Route route : router.getRoutes()) {
         if (route.getPath() != null) { // avoid the default route
            sb.append(route.getPath()).append('\n');
         }
      }
      ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(sb.toString());
   }

   private void handlePostBenchmark(RoutingContext ctx) {
      String contentType = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE).trim();
      Charset charset = StandardCharsets.UTF_8;
      int semicolonIndex = contentType.indexOf(';');
      if (semicolonIndex >= 0) {
         String tmp = contentType.substring(semicolonIndex + 1).trim();
         if (tmp.startsWith("charset=")) {
            charset = Charset.forName(tmp.substring(8));
         }
         contentType = contentType.substring(0, semicolonIndex).trim();
      }

      Benchmark benchmark;
      if (contentType.equals(MIME_TYPE_SERIALIZED)) {
         byte[] bytes = ctx.getBody().getBytes();
         benchmark = PersistenceUtil.deserialize(bytes);
      } else if (MIME_TYPE_YAML.contains(contentType)) {
         String source = ctx.getBodyAsString(charset.name());
         try {
            benchmark = BenchmarkParser.instance().buildBenchmark(source);
         } catch (ParserException e) {
            log.error("Failed to read benchmark", e);
            benchmark = null;
         }
      } else {
         ctx.response().setStatusCode(406).setStatusMessage("Unsupported Content-Type.");
         return;
      }

      if (benchmark != null) {
         String location = BASE_URL + "/benchmark/" + encode(benchmark.name());
         controller.addBenchmark(benchmark, event -> {
            if (event.succeeded()) {
               ctx.response().setStatusCode(204)
                     .putHeader(HttpHeaders.LOCATION, location).end();
            } else {
               ctx.response().setStatusCode(500).end();
            }
         });

      } else {
         ctx.response().setStatusCode(400).end("Cannot read benchmark.");
      }
   }

   private void handleListBenchmarks(RoutingContext routingContext) {
      JsonArray array = new JsonArray();
      controller.getBenchmarks().forEach(array::add);
      routingContext.response().setStatusCode(200).end(array.toBuffer());
   }

   private void handleGetBenchmark(RoutingContext ctx) {
      String name = ctx.pathParam("benchmarkname");
      Benchmark benchmark = controller.getBenchmark(name);
      if (benchmark == null) {
         ctx.response().setStatusCode(404).setStatusMessage("No benchmark '" + name + "'").end();
         return;
      }

      String acceptHeader = ctx.request().getHeader(HttpHeaders.ACCEPT);
      int semicolonIndex = acceptHeader.indexOf(';');
      if (semicolonIndex >= 0) {
         acceptHeader = acceptHeader.substring(0, semicolonIndex).trim();
      }
      if (acceptHeader.equals(MIME_TYPE_SERIALIZED)) {
         byte[] bytes = PersistenceUtil.serialize(benchmark);
         if (bytes == null) {
            ctx.response().setStatusCode(500).end("Error encoding benchmark.");
         } else {
            ctx.response().setStatusCode(200)
                  .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_SERIALIZED)
                  .end(Buffer.buffer(bytes));
         }
      } else if (MIME_TYPE_YAML.contains(acceptHeader) || "*/*".equals(acceptHeader)) {
         if (benchmark.source() == null) {
            ctx.response().setStatusCode(406).setStatusMessage("Benchmark does not preserve the original source.");
         } else {
            ctx.response().setStatusCode(200)
                  .putHeader(HttpHeaders.CONTENT_TYPE, "text/vnd.yaml; charset=UTF-8")
                  .end(benchmark.source());
         }
      } else {
         ctx.response().setStatusCode(406).setStatusMessage("Unsupported type in Accept.").end();
      }
   }

   private void handleBenchmarkStart(RoutingContext routingContext) {
      String benchmarkName = routingContext.pathParam("benchmarkname");
      Benchmark benchmark = controller.getBenchmark(benchmarkName);
      if (benchmark != null) {
         String runId = controller.startBenchmark(benchmark);
         routingContext.response().setStatusCode(202).
               putHeader(HttpHeaders.LOCATION, BASE_URL + "/run/" + runId)
               .end("Initializing agents...");
      } else {
         //benchmark has not been defined yet
         String msg = "Benchmark not found";
         routingContext.response().setStatusCode(500).end(msg);
      }
   }

   private void handleGetAgentCount(RoutingContext routingContext) {
      routingContext.response().end(Integer.toString(controller.agents.size()));
   }

   private void handleListRuns(RoutingContext routingContext) {
      JsonArray array = new JsonArray();
      controller.runs().stream().map(run -> run.id).forEach(array::add);
      routingContext.response().setStatusCode(200).end(array.toBuffer());
   }

   private void handleGetRun(RoutingContext routingContext) {
      JsonObject body = new JsonObject();
      Run run = controller.run(routingContext.pathParam("runid"));
      if (run == null) {
         routingContext.response().setStatusCode(404).end();
         return;
      }
      body.put("runId", run.id);
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
      if (run.benchmark != null) {
         body.put("benchmark", run.benchmark.name());
      }
      if (run.startTime > Long.MIN_VALUE) {
         body.put("started", simpleDateFormat.format(new Date(run.startTime)));
      }
      if (run.terminateTime > Long.MIN_VALUE) {
         body.put("terminated", simpleDateFormat.format(new Date(run.terminateTime)));
      }
      JsonArray jsonPhases = new JsonArray();
      body.put("phases", jsonPhases);
      for (ControllerPhase phase : run.phases.values()) {
         JsonObject jsonPhase = new JsonObject();
         jsonPhases.add(jsonPhase);
         jsonPhase.put("name", phase.definition().name);
         jsonPhase.put("status", phase.status());
         if (phase.absoluteStartTime() > Long.MIN_VALUE) {
            jsonPhase.put("started", simpleDateFormat.format(new Date(phase.absoluteStartTime())));
         }
      }
      JsonArray jsonAgents = new JsonArray();
      body.put("agents", jsonAgents);
      for (AgentInfo agent : controller.agents.values()) {
         JsonObject jsonAgent = new JsonObject();
         jsonAgents.add(jsonAgent);
         jsonAgent.put("address", agent.address);
         jsonAgent.put("status", agent.status);
      }
      String status = body.encodePrettily();
      routingContext.response().end(status);
   }

   private void handleRunKill(RoutingContext routingContext) {
      if (controller.kill(routingContext.pathParam("runid"))) {
         routingContext.response().setStatusCode(202).end();
      } else {
         routingContext.response().setStatusCode(404).end();
      }
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }
}
