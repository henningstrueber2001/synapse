package de.otto.edison.eventsourcing.aws.consumer;

import de.otto.edison.eventsourcing.annotation.EnableEventSource;
import de.otto.edison.eventsourcing.annotation.EventSourceConsumer;
import de.otto.edison.eventsourcing.aws.configuration.AwsEventSourcingAutoConfiguration;
import de.otto.edison.eventsourcing.consumer.EventSourceConsumerProcess;
import de.otto.edison.eventsourcing.consumer.EventSourceNotification;
import de.otto.edison.eventsourcing.message.Message;
import de.otto.edison.eventsourcing.aws.s3.SnapshotReadService;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@EnableAutoConfiguration
@ComponentScan(basePackages = {
        "de.otto.edison.eventsourcing",
})
@SpringBootTest(classes = {
        EventConsumerIntegrationTest.class,
        EventConsumerIntegrationTest.TestConfiguration.class,
        AwsEventSourcingAutoConfiguration.class,
        EventSourceConsumerProcess.class
})
public class EventConsumerIntegrationTest {

    private static List<String> allReceivedEventKeys = new ArrayList<>();
    private static List<Apple> receivedAppleEventPayloads = new ArrayList<>();
    private static List<Banana> receivedBananaEventPayloads = new ArrayList<>();
    private static List<EventSourceNotification> events = new ArrayList<>();


    @Test
    public void shouldCallCorrectConsumerDependingOnEventKey() {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> events.size(), is(4));

        assertThat(receivedBananaEventPayloads.size(), is(2));
        assertThat(receivedBananaEventPayloads.get(0).bananaId, is("1"));
        assertThat(receivedBananaEventPayloads.get(1).bananaId, is("2"));
        assertThat(receivedAppleEventPayloads.size(), is(2));
        assertThat(receivedAppleEventPayloads.get(0).appleId, is("1"));
        assertThat(receivedAppleEventPayloads.get(1).appleId, is("2"));
        assertThat(events, hasSize(4));
        assertThat(events.get(0).getStatus(), is(EventSourceNotification.Status.STARTED));
        assertThat(events.get(0).getStreamName(), is("test-stream"));
        assertThat(events.get(1).getStatus(), is(EventSourceNotification.Status.FINISHED));
        assertThat(events.get(1).getStreamName(), is("test-stream"));
        assertThat(events.get(2).getStatus(), is(EventSourceNotification.Status.STARTED));
        assertThat(events.get(2).getStreamName(), is("test-stream"));
        assertThat(events.get(3).getStatus(), is(EventSourceNotification.Status.FAILED));
        assertThat(events.get(3).getStreamName(), is("test-stream"));

    }

    private static class Apple {
        public String appleId;
        public String name;
    }

    private static class Banana {
        public String bananaId;
        public String name;
    }

    public static class TestConsumer {
        @EventListener
        public void listenForFinishedEvent(EventSourceNotification eventSourceNotification) {
            events.add(eventSourceNotification);
        }

        @EventSourceConsumer(
                eventSource = "test",
                keyPattern = "^banana.*",
                payloadType = Banana.class)
        public void consumeBananaEvents(Message<Banana> message) {
            receivedBananaEventPayloads.add(message.getPayload());
            allReceivedEventKeys.add(message.getKey());
        }

        @EventSourceConsumer(
                eventSource = "test",
                keyPattern = "^apple.*",
                payloadType = Apple.class)
        public void consumeAppleEvents(Message<Apple> message) {
            receivedAppleEventPayloads.add(message.getPayload());
            allReceivedEventKeys.add(message.getKey());
        }
    }

    @EnableEventSource(name = "test", streamName = "test-stream")
    public static class TestConfiguration {

        @Bean
        public TestConsumer testConsumer() {
            return new TestConsumer();
        }

        @Bean
        @Primary
        public SnapshotReadService snapshotReadService() {
            File file = new File(getClass().getClassLoader().getResource("apple-banana-snapshot-2017-11-27T09-02Z-3053797267191232636.json.zip").getFile());

            SnapshotReadService snapshotReadServiceMock = Mockito.mock(SnapshotReadService.class);
            when(snapshotReadServiceMock.retrieveLatestSnapshot(any())).thenReturn(Optional.of(file));
            return snapshotReadServiceMock;
        }

        @Bean
        @Primary
        public KinesisClient kinesisClient() {
            return new KinesisClient() {
                @Override
                public String serviceName() {
                    return SERVICE_NAME;
                }

                @Override
                public void close() {
                    // do nothing
                }

                @Override
                public DescribeStreamResponse describeStream(DescribeStreamRequest describeStreamRequest) {
                    throw new UnsupportedOperationException("test kinesis client that throws exception on purpose");
                }
            };
        }

    }

}

