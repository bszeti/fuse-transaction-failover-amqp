package bszeti.artemis.test.config;

import javax.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "connection")
public class ConnectionFactoryConfig {

    private String type;
    private String remoteUrl;
    private String username;
    private String password;
    private Integer maxConnections;


    //AMQP ConnectionFactory
    public ConnectionFactory amqpConnectionFactory(){
        JmsConnectionFactory factory = new JmsConnectionFactory(remoteUrl);

        if (StringUtils.hasLength(this.getUsername())) {
            factory.setUsername(this.getUsername());
        }
        if (StringUtils.hasLength(this.getPassword())) {
            factory.setPassword(this.getPassword());
        }

        return factory;
    }

    //CORE ConnectionFactory
    public ConnectionFactory coreConnectionFactory(){
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(remoteUrl);

        if (StringUtils.hasLength(this.getUsername())) {
            factory.setUser(this.getUsername());
        }
        if (StringUtils.hasLength(this.getPassword())) {
            factory.setPassword(this.getPassword());
        }

        return factory;
    }

    //Non-pooled ConnectionFactory - used by SJMS
    public ConnectionFactory singleConnectionFactory() {
        ConnectionFactory cf = null;
        switch (this.getType()) {
            case "AMQP":
                cf = amqpConnectionFactory();
                break;
            case "CORE":
                cf = coreConnectionFactory();
                break;
        }
        return cf;
    }

    //Pooled ConnectionFactory - autowired by AMQP
    @Bean
    public ConnectionFactory pooledConnectionFactory() {
        JmsPoolConnectionFactory jmsPoolConnectionFactory = new org.messaginghub.pooled.jms.JmsPoolConnectionFactory();
        jmsPoolConnectionFactory.setConnectionFactory(singleConnectionFactory());
        jmsPoolConnectionFactory.setMaxConnections(this.getMaxConnections());
        return jmsPoolConnectionFactory;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
    }
}
