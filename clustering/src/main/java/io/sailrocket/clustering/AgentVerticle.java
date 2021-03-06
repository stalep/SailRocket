package io.sailrocket.clustering;

import io.sailrocket.api.config.Simulation;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.impl.SimulationRunnerImpl;
import io.sailrocket.clustering.util.PhaseChangeMessage;
import io.sailrocket.clustering.util.PhaseControlMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class AgentVerticle extends AbstractVerticle {
    private static Logger log = LoggerFactory.getLogger(AgentVerticle.class);

    private String address;
    private EventBus eb;
    private SimulationRunnerImpl runner;
    private long statsTimerId = -1;

    @Override
    public void start() {
        address = deploymentID();
        eb = vertx.eventBus();

        eb.consumer(address, message -> {
            Simulation simulation = (Simulation) message.body();
            if (!initSimulation(simulation)) {
                message.fail(1, "Agent already initialized");
            } else {
                message.reply("OK");
            }
        });

        eb.consumer(Feeds.CONTROL, message -> {
            PhaseControlMessage controlMessage = (PhaseControlMessage) message.body();
            switch (controlMessage.command()) {
                case RUN:
                    runner.startPhase(controlMessage.phase());
                    break;
                case FINISH:
                    runner.finishPhase(controlMessage.phase());
                    break;
                case TRY_TERMINATE:
                    runner.tryTerminatePhase(controlMessage.phase());
                    break;
                case TERMINATE:
                    runner.terminatePhase(controlMessage.phase());
                    break;
            }
        });

        vertx.setPeriodic(1000, timerId -> {
            eb.send(Feeds.DISCOVERY, address, reply -> {
                log.trace("{} Pinging controller", address);
                if (reply.succeeded()) {
                    log.info("{} Got reply from controller.", address);
                    vertx.cancelTimer(timerId);
                } else {
                    if (reply.cause() instanceof ReplyException) {
                        ReplyFailure replyFailure = ((ReplyException) reply.cause()).failureType();
                        if (replyFailure == ReplyFailure.RECIPIENT_FAILURE) {
                            log.error("{} Failed to register, already registered!", address);
                        } else {
                            log.info("{} Failed to register: {}", address, replyFailure);
                        }
                    }
                }
            });
        });
    }

    private boolean initSimulation(Simulation simulation) {
        if (runner != null) {
            return false;
        }
        runner = new SimulationRunnerImpl(simulation);
        ReportSender reportSender = new ReportSender(simulation, eb, address);

        runner.init((phase, status) -> {
            if (status == PhaseInstance.Status.TERMINATED) {
                // collect stats one last time before acknowledging termination
                if (statsTimerId >= 0) {
                    vertx.cancelTimer(statsTimerId);
                }
                runner.visitSessions(reportSender);
                reportSender.send();
            }
            eb.send(Feeds.RESPONSE, new PhaseChangeMessage(address, phase, status));
        });
        statsTimerId = vertx.setPeriodic(simulation.statisticsCollectionPeriod(), timerId -> {
            runner.visitSessions(reportSender);
            reportSender.send();
        });
        return true;
    }
}
