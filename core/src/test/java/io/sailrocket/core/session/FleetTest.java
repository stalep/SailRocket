package io.sailrocket.core.session;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.core.builders.ScenarioBuilder;
import io.sailrocket.core.extractors.ArrayRecorder;
import io.sailrocket.core.extractors.SequenceScopedCountRecorder;
import io.sailrocket.core.extractors.DefragProcessor;
import io.sailrocket.core.extractors.JsonExtractor;
import io.sailrocket.core.steps.AwaitConditionStep;
import io.sailrocket.core.test.CrewMember;
import io.sailrocket.core.test.Fleet;
import io.sailrocket.core.test.Ship;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class FleetTest extends BaseScenarioTest {

   private static final Fleet FLEET = new Fleet("Československá námořní flotila")
         .addBase("Hamburg")
         .addShip(new Ship("Republika")
               .dwt(10500)
               .addCrew(new CrewMember("Captain", "Adam", "Korkorán"))
               .addCrew(new CrewMember("Cabin boy", "Pepek", "Novák"))
         )
         .addShip(new Ship("Julius Fučík").dwt(7100))
         .addShip(new Ship("Lidice").dwt(8500));
   public static final int MAX_SHIPS = 16;

   @Override
   protected void initRouter() {
      router.route("/fleet").handler(routingContext -> {
         HttpServerResponse response = routingContext.response();
         response.end(Json.encodePrettily(FLEET));
      });
      router.route("/ship").handler(routingContext -> {
         String shipName = routingContext.queryParam("name").stream().findFirst().orElse("");
         Optional<Ship> ship = FLEET.getShips().stream().filter(s -> s.getName().equals(shipName)).findFirst();
         if (!ship.isPresent()) {
            routingContext.response().setStatusCode(404).end();
            return;
         }
         switch (routingContext.request().method()) {
            case GET:
               routingContext.response().end(Json.encodePrettily(ship.get()));
               break;
            case DELETE:
               routingContext.response().setStatusCode(204).setStatusMessage("Ship sunk.").end();
               break;
         }
      });
   }

   /**
    * Fetch a fleet (list of ships), then fetch each ship separately and if it has no crew, sink the ship.
    */
   @Test
   public void testSinkEmptyShips(TestContext ctx) throws InterruptedException {
      // We need to call async() to prevent termination when the test method completes
      Async async = ctx.async(2);

      ScenarioBuilder scenario = scenarioBuilder()
            .intVar("numberOfSunkShips")
            .initialSequence("fleet")
               .step(s -> s.setInt("numberOfSunkShips", 0))
               .step().httpRequest(HttpMethod.GET).path("/fleet")
                  .handler()
                     .bodyExtractor(new JsonExtractor(".ships[].name", new DefragProcessor(new ArrayRecorder("shipNames", MAX_SHIPS))))
                  .endHandler()
               .endStep()
               .step().foreach("shipNames", "numberOfShips").sequence("ship").endStep()
            .endSequence()
            .sequence("ship")
               .step().httpRequest(HttpMethod.GET).pathGenerator(FleetTest::currentShipQuery)
                  .handler()
                     .bodyExtractor(new JsonExtractor(".crew[]", new SequenceScopedCountRecorder("crewCount", MAX_SHIPS)))
                  .endHandler()
               .endStep()
               .step().breakSequence(s -> currentCrewCount(s) > 0)
                  .onBreak(s -> s.addToInt("numberOfShips", -1))
                  .dependency(new SequenceScopedVarReference("crewCount"))
               .endStep()
               .step().httpRequest(HttpMethod.DELETE).pathGenerator(FleetTest::currentShipQuery)
                  .handler()
                     .statusExtractor(((status, session) -> {
                        if (status == 204) {
                           session.addToInt("numberOfSunkShips", -1);
                        } else {
                           ctx.fail("Unexpected status " + status);
                        }
                     }))
                  .endHandler()
               .endStep()
               .step(s -> s.addToInt("numberOfSunkShips", 1).addToInt("numberOfShips", -1))
            .endSequence()
            .initialSequence("final")
               .step(new AwaitConditionStep(s -> s.isSet("numberOfShips") && s.getInt("numberOfShips") <= 0))
               .step(new AwaitConditionStep(s -> s.getInt("numberOfSunkShips") <= 0))
               .step(s -> {
                  log.info("Test completed");
                  for (Statistics stats : s.statistics()) {
                     log.trace(stats);
                  }
                  async.countDown();
               })
            .endSequence();

      runScenario(scenario.build(), 2);
   }

   private int currentCrewCount(Session session) {
      return ((IntVar[]) session.getObject("crewCount"))[session.currentSequence().index()].get();
   }

   private static String currentShipQuery(Session s) {
      String shipName = (String) (((ObjectVar[]) s.getObject("shipNames"))[s.currentSequence().index()]).get();
      // TODO: avoid allocations when constructing URL
      return "/ship?name=" + encode(shipName);
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }
}