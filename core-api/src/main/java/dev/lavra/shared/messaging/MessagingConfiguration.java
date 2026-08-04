package dev.lavra.shared.messaging;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Service Bus port. As with storage, nothing here opens a connection:
 * the first send does, so a context that publishes nothing starts without a
 * broker.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
class MessagingConfiguration {

    @Bean(destroyMethod = "close")
    EventPublisher eventPublisher(MessagingProperties properties, Clock clock) {
        ServiceBusClientBuilder clientBuilder = new ServiceBusClientBuilder()
                .connectionString(properties.connectionString());
        return new ServiceBusEventPublisher(clientBuilder, EventMapper.create(), clock);
    }
}
