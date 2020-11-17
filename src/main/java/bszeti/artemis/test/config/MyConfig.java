package bszeti.artemis.test.config;

import java.util.concurrent.CountDownLatch;

import javax.jms.ConnectionFactory;

import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.JmsTransactionManager;

@Configuration
public class MyConfig {
	private static final Logger log = LoggerFactory.getLogger(MyConfig.class);

    @Bean
    public CountDownLatch sendThreadsCountDown(@Value("${send.threads}") int sendThreads) {
        return new CountDownLatch(sendThreads);
    }

    @Bean
    @ConditionalOnProperty(
        value="transaction.mode",
        havingValue = "TRANSACTION_MANAGER_WITH_PROPAGATION")
	public JmsTransactionManager myJmsTransactionManager(@Autowired ConnectionFactory pooledConnectionFactory){
		log.debug("Creating myJmsTransactionManager.");
		return new JmsTransactionManager(pooledConnectionFactory);
	}

	@Bean
    @ConditionalOnProperty(
        value="transaction.mode",
        havingValue = "TRANSACTION_MANAGER_WITH_PROPAGATION")
	public SpringTransactionPolicy jmsSendTransaction(@Autowired JmsTransactionManager myJmsTransactionManager, @Value("${transaction.propagation}") String transactionPropagation){
		SpringTransactionPolicy transactionPolicy = new SpringTransactionPolicy(myJmsTransactionManager);
		transactionPolicy.setPropagationBehaviorName(transactionPropagation);
		return transactionPolicy;
	}

	@Bean
	@ConditionalOnProperty(
		value="transaction.mode",
		havingValue = "SJMS")
	public SjmsComponent sjms(@Autowired ConnectionFactoryConfig connectionFactoryConfig){
		SjmsComponent sjmsComponent = new SjmsComponent();
		sjmsComponent.setConnectionFactory(connectionFactoryConfig.singleConnectionFactory());
		sjmsComponent.setConnectionCount(connectionFactoryConfig.getMaxConnections());
		return sjmsComponent;

	}
}
