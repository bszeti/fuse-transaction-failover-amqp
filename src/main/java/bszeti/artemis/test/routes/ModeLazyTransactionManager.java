package bszeti.artemis.test.routes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    value="transaction.mode",
    havingValue = "LAZY_TRANSACTION_MANAGER")
public class ModeLazyTransactionManager extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(Routes.class);


    @Override
    public void configure() throws Exception {

        onException(Exception.class)
            .maximumRedeliveries("{{exception.maximumredeliveries}}")
            .log(LoggingLevel.ERROR,"Camel onException ModeLazyTransactionManager: ${exception}")
        ;

        from("{{receive.endpoint}}")
            .routeId("receive").autoStartup(false)
            .to("direct:doReceive")
        ;

    }
}
