package de.otto.synapse.example.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.synapse.channel.InMemoryChannel;
import de.otto.synapse.channel.InMemoryChannels;
import de.otto.synapse.example.producer.configuration.MyServiceProperties;
import de.otto.synapse.sender.InMemoryMessageSender;
import de.otto.synapse.sender.MessageSenderFactory;
import de.otto.synapse.translator.JsonStringMessageTranslator;
import de.otto.synapse.translator.MessageTranslator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MyServiceProperties.class)
public class TestMessageSenderConfiguration {

    @Bean
    public MessageSenderFactory messageSenderFactory(final ObjectMapper objectMapper,
                                                     final MyServiceProperties properties) {
        final InMemoryChannel productStream = InMemoryChannels.getChannel(properties.getProductStreamName());
        final MessageTranslator<String> messageTranslator = new JsonStringMessageTranslator(objectMapper);
        return streamName -> new InMemoryMessageSender(messageTranslator, productStream);
    }

}