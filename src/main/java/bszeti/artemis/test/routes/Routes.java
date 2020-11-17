package bszeti.artemis.test.routes;

import java.beans.ExceptionListener;
import java.util.concurrent.CountDownLatch;

import bszeti.artemis.test.Counter;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Routes extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(Routes.class);

    @Autowired
    Counter counter;

    @Autowired
    CountDownLatch sendThreadsCountDown;

    @Value("${send.threads}")
    int sendThreads;

    @Value("${send.message}")
    String sendMessage;

    @Value("${receive.addAmqDuplId}")
    Boolean addAmqDuplId;

    @Value("${transaction.mode}")
    String transactionMode;


    @Override
    public void configure() throws Exception {

        onException(Exception.class)
            .maximumRedeliveries("{{exception.maximumredeliveries}}")
            .log(LoggingLevel.ERROR,"Camel onException: ${exception} ")
        ;

        // Receive messages and optionally forward them to another queue
        // If message body contains "error" an exception is thrown (before forwarding)
        // The consumer can have transacted=true, then the rest of the route uses transaction policy receive.forward.propagation. This is to test different scenarios for the forwarding. Default is PROPAGATION_REQUIRED
        from("direct:doReceive")
            .routeId("doReceive")

            .log(LoggingLevel.DEBUG, log, "Received: ${header.UUID} - ${header.SEND_COUNTER}")
            .setHeader("receiveCounter").exchange(e->counter.getReceiveCounter().incrementAndGet())
            .script().message(m->{
                if (counter.getReceivedUUIDs().put(m.getHeader("UUID",String.class),m.getHeader("receiveCounter",String.class)) != null) {
                    log.warn("Received again: {} - {}", m.getHeader("UUID"), m.getHeader("receiveCounter"));
                }
                return null;
            })


            .choice()
                .when(constant("{{receive.forwardEnabled}}"))
                .delay(constant("{{receive.delayBeforeForward}}"))
                .setHeader("_AMQ_DUPL_ID").message(m->{
                    if (addAmqDuplId) return m.getHeader("UUID");
                    return null;
                })
                .to("direct:forward")
                .log(LoggingLevel.DEBUG, log, "Forwarded: ${header.UUID} - ${header.SEND_COUNTER}")
                .process(e-> counter.getReceiveForwardedCounter().incrementAndGet())
                .end()
            .end()

            //Throw exception after forward so we can test transacted send
            .choice()
                .when(simple("${body} contains 'error' "))
                .throwException(Exception.class, "error - ${header.UUID}")
            .end()

            .delay(constant("{{receive.delayBeforeDone}}"))
            .log(LoggingLevel.DEBUG, log, "Done: ${header.UUID} - ${header.SEND_COUNTER}")
        ;


        // Send messages -  send.threads * send.count
        // Message body is from property send.message. For example a simple expression: #{'$'}{exchangeId}/#{'$'}{header.CamelLoopIndex}
        // Add a UUID header. Use send.headeruuid=_AMQ_DUPL_ID for Artemis duplicate detection.
        from("timer:sender?period=1&repeatCount={{send.threads}}")
            .routeId("send").autoStartup("{{send.enabled}}")
            .onCompletion()
                .process(e->sendThreadsCountDown.countDown())
            .end()

                .threads().poolSize(sendThreads).maxPoolSize(sendThreads).maxQueueSize(sendThreads).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)

                    .log(LoggingLevel.INFO, log, "Sending {{send.count}}")
                    .loop(constant("{{send.count}}"))
                        .setHeader("UUID").exchange(e->java.util.UUID.randomUUID().toString())
                        .setHeader("SEND_COUNTER").exchange(e->counter.getSendCounter().incrementAndGet())
                        .setBody(simple(sendMessage))
                        .log(LoggingLevel.DEBUG, log, "Sending: ${header.UUID} - ${header.SEND_COUNTER}")
                        .to("direct:send")
                        .script().message(m->counter.getSentUUIDs().put(m.getHeader("UUID").toString(),m.getHeader("SEND_COUNTER").toString()))
                        .delay(constant("{{send.delay}}"))
                    .end()
                .end()
            .setProperty("sentUUIDsSize").exchange(e->counter.getSentUUIDs().size())
            .log(LoggingLevel.INFO, log, "Total sent: {{send.count}} - ${header.sentUUIDsSize}")

        ;

        //Send and forward
        if (transactionMode.startsWith("SJMS")) {
            from("direct:send").routeId("jms.send")
                .to("sjms:queue:{{source.queue}}?transacted=false");

            //With SJMS Forward is non-transacted by default
            from("direct:forward").routeId("jms.forward")
                .to("sjms:queue:{{target.queue}}?transacted=true");

        } else {

            from("direct:send").routeId("jms.send")
                .to("amqp:queue:{{source.queue}}?transacted=false");

            //Forward is transacted if receive was transacted unless PROPAGATION_NOT_SUPPORTED
            from("direct:forward").routeId("jms.forward")
                .to("amqp:queue:{{target.queue}}");
        }

        //Receive
        if (transactionMode.equals("NO_TRANSACTION_MANAGER")) {
            from("amqp:queue:{{source.queue}}?concurrentConsumers={{receive.concurrentConsumers}}&transacted={{receive.transacted}}&cacheLevelName={{receive.cacheLevel}}&lazyCreateTransactionManager=false")
                .routeId("jms.receive").autoStartup(false)
                .to("direct:doReceive")
            ;
        }

        if (transactionMode.equals("LAZY_TRANSACTION_MANAGER")) {
            from("amqp:queue:{{source.queue}}?concurrentConsumers={{receive.concurrentConsumers}}&transacted={{receive.transacted}}&cacheLevelName={{receive.cacheLevel}}")
                .routeId("jms.receive").autoStartup(false)
                .to("direct:doReceive")
            ;
        }

        if (transactionMode.equals("TRANSACTION_MANAGER_WITH_PROPAGATION")) {
            from("amqp:queue:{{source.queue}}?concurrentConsumers={{receive.concurrentConsumers}}&transacted={{receive.transacted}}&cacheLevelName={{receive.cacheLevel}}&transactionManager=#myJmsTransactionManager")
                .routeId("jms.receive").autoStartup(false)
                .transacted("jmsSendTransaction")
                .to("direct:doReceive")
            ;
        }

        if (transactionMode.equals("SJMS")) {
            from("sjms:queue:{{source.queue}}?transacted={{receive.transacted}}&consumerCount={{receive.concurrentConsumers}}")
                .routeId("jms.receive").autoStartup(false)
                .to("direct:doReceive")
            ;
        }

        /**
         * Log message counters. It also updates receiveCounterLast, which is needed to shut down after all messages are received.
         */
        from("timer:printCounter?period=1000")
            .setBody(b->{
                int receiveCurrent = counter.getReceiveCounter().get();
                int receiveDiff = receiveCurrent - counter.getReceiveCounterLast();
                counter.setReceiveCounterLast(receiveCurrent);
                int sendCurrent = counter.getSendCounter().get();
                int sendDiff = sendCurrent - counter.getSendCounterLast();
                counter.setSendCounterLast(sendCurrent);
                return "Messages - sent: "+sendCurrent+" ("+sendDiff+"/s), received: "+receiveCurrent+" ("+receiveDiff+"/s), forwarded: " + counter.getReceiveForwardedCounter().get();
            })
            .log(LoggingLevel.INFO, log, "${body}")
        ;

    }

}
