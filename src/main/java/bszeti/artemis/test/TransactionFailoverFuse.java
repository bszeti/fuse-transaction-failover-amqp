/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bszeti.artemis.test;

import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jms.Message;
import javax.jms.QueueBrowser;
import javax.jms.Session;

import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
public class TransactionFailoverFuse implements CommandLineRunner {

   private static final Logger log = LoggerFactory.getLogger(TransactionFailoverFuse.class);

   private static Process server0;
   private static Process server1;
   private static Boolean noServer;
   public static void main(String[] args) throws Exception{

      try {
         log.debug("Args: {} {} {}",args[0], args[1], args[2]);
         noServer = Boolean.valueOf(args[2]);
         if (!noServer) {
            //Start servers - this is needed before the main run as DefaultMessageListenerContainer tries to get a connection during start
            server0 = ServerUtil.startServer(args[0], TransactionFailoverFuse.class.getSimpleName() + "0", 0, 5000);
            server1 = ServerUtil.startServer(args[1], TransactionFailoverFuse.class.getSimpleName() + "1", 1, 5000);
         }

         //Main run
         SpringApplication.run(TransactionFailoverFuse.class, args);

      } finally {
         if (!noServer) {
            //Stop server
            log.info("Shut down servers");
            ServerUtil.killServer(server0);
            ServerUtil.killServer(server1);
         }
         log.info("Done");
      }
   }

   @Autowired
   private JmsTemplate jmsTemplate;

   @Autowired
   private ConfigurableApplicationContext applicationContext;

   @Value("${source.queue}")
   String sourceQueue;

   @Value("${target.queue}")
   String targetQueue;

   @Value("${send.enabled}")
   Boolean sendEnabled;

   @Value("${receive.enabled}")
   Boolean receiveEnabled;

   @Value("${receive.brokerFailover}")
   Boolean brokerFailover;

   @Value("${shutDownDelay}")
   Integer shutDownDelay;

   @Autowired
   CamelContext camelContext;

   @Autowired
   Counter counter;

   @Autowired
   CountDownLatch sendThreadsCountDown;


   @Override
   public void run(String... args) throws Exception {
      try {

         if (sendEnabled) {
            log.info("Wait for send to complete.");
            sendThreadsCountDown.await();
         }


         //Start receiving messages
         if (receiveEnabled) {
            Thread.sleep(1000);
            log.info("Start receiving");

            log.info("Message count before listener start - sent: {}, received: {}, forwarded: {}", counter.getSendCounter().get(), counter.getReceiveCounter().get(), counter.getReceiveForwardedCounter().get());
            camelContext.startRoute("jms.receive");

            //Wait until we received 10% of messages before trigger broker failover
            while (counter.getReceiveCounter().get() < counter.getSendCounter().get()/10) {
               Thread.sleep(100);
            }

            //Broker failover
            if (!noServer && brokerFailover) {
               ServerUtil.killServer(server0);
               log.info("Message count after failover - sent: {}, received: {}, forwarded: {}", counter.getSendCounter().get(), counter.getReceiveCounter().get(), counter.getReceiveForwardedCounter().get());
            }


            //Wait until we received all of messages, and no more was incoming in the last second
            while (counter.getReceiveCounter().get() < counter.getSendCounter().get() || counter.getReceiveCounter().get() > counter.getReceiveCounterLast()) {
               Thread.sleep(1000);
            }

            //Wait some before shutdown
            Thread.sleep(shutDownDelay);
         }

         log.info("Counting queue sizes...");
         jmsTemplate.setReceiveTimeout(1000);
         Message msg;

         //Source queue is expected not to have any messages
         int sourceCount = 0;
         while((msg = jmsTemplate.receive(sourceQueue)) != null) {
            sourceCount++;
         }

         //Browse may not give back the exact number with CORE - but leaving code here for reference
//         int targetBrowserCount = jmsTemplate.browse(targetQueue, (Session session, QueueBrowser browser) ->{
//            Enumeration enumeration = browser.getEnumeration();
//            int counter = 0;
//            while (enumeration.hasMoreElements()) {
//               Message message = (Message) enumeration.nextElement();
//               counter += 1;
//               String uuid = message.getStringProperty("UUID");
//               this.counter.sentUUIDs.remove(uuid);
//            }
//            return counter;
//         });

         //Check successfully arrived messages on target
         int targetCount = 0;
         while((msg = jmsTemplate.receive(targetQueue)) != null) {
            targetCount++;
            String uuid = msg.getStringProperty("UUID");
            this.counter.sentUUIDs.remove(uuid);
         }


         this.counter.sentUUIDs.entrySet().stream()
             .forEach(e->log.info("Message missing: {} - {}",e.getKey(),e.getValue()));


         //Check messages on DLQ
//         int DLQCount = jmsTemplate.browse("DLQ", (Session session, QueueBrowser browser) ->{
//            Enumeration enumeration = browser.getEnumeration();
//            int counter = 0;
//            while (enumeration.hasMoreElements()) {
//               Message dlqMsg = (Message) enumeration.nextElement();
//               log.info("Message in DLQ: {} - {}",dlqMsg.getStringProperty("UUID"), dlqMsg.getStringProperty("SEND_COUNTER"));
//               counter += 1;
//            }
//            return counter;
//         });

         int DLQCount = 0;
         while((msg = jmsTemplate.receive("DLQ")) != null) {
            DLQCount++;
            String uuid = msg.getStringProperty("UUID");
            this.counter.sentUUIDs.remove(uuid);
            log.info("Message in DLQ: {} - {}",msg.getStringProperty("UUID"), msg.getStringProperty("SEND_COUNTER"));
         }

         log.info("Message count at the end - sent: {}, received: {}, forwarded: {}", counter.getSendCounter().get(), counter.getReceiveCounter().get(), counter.getReceiveForwardedCounter().get());
         log.info("Message count on source queue: {}", sourceCount);
         log.info("Message count on target queue: {}",targetCount);
         log.warn("Message count on DLQ queue: {}", DLQCount);
         //Number of messages on DLQ should be 0 for a seamless failover

      } finally {
         //Shut down listeners and scheduled tasks
         log.info("Stop applicationContext");
         applicationContext.close();
      }
   }


}
