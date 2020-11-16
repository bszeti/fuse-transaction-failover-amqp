package bszeti.artemis.test.config;

import javax.jms.ConnectionFactory;
import javax.jms.Session;

import bszeti.artemis.test.TransactionFailoverFuse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.PlatformTransactionManager;

/****
 * In this mode we use a TransactionManager and a custom DefaultJmsListenerContainerFactory
 */
@Configuration
@ConditionalOnProperty(
    value = "transaction.mode",
    havingValue = "JMS_TRANSACTION_MANAGER")
@EnableJms
public class ModeTransactionManager {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    TransactionFailoverFuse app;

    @Value("${receive.cacheLevel}")
    String cacheLevel;

    @Bean
    public PlatformTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new JmsTransactionManager(connectionFactory);

    }

    @Bean
    public JmsListenerContainerFactory<?> msgFactory(ConnectionFactory connectionFactory, PlatformTransactionManager transactionManager) {
        log.info("Custom JmsListenerContainerFactory is created.");
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setTransactionManager(transactionManager);
        factory.setSessionTransacted(true); //setSessionTransacted=true is required otherwise receive may not be transacted
        factory.setConnectionFactory(connectionFactory);
        factory.setCacheLevelName(cacheLevel); //Default is CACHE_AUTO which means CACHE_NONE if TransactionManager is set, CACHE_CONSUMER otherwise
        factory.setAutoStartup(false); //We need to disable auto-startup because we want to start listeners manually after putting messages on the source queue
        factory.setReceiveTimeout(5000L);
        return factory;
    }

    @JmsListener(destination = "${source.queue}", concurrency = "${receive.concurrentConsumers}", containerFactory = "msgFactory")
    public void receiveMessageWithTransactionManager(String text, Session session, @Header("SEND_COUNTER") String counter, @Header("UUID") String uuid) throws InterruptedException {
        app.doReceiveMessage(text, session, counter, uuid);
    }
}
