package bszeti.artemis.test.config;

import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfig {

    @Bean
    public CountDownLatch sendThreadsCountDown(@Value("${send.threads}") int sendThreads) {
        return new CountDownLatch(sendThreads);
    }

}
