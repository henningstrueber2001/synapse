package de.otto.edison.eventsourcing.aws.kinesis;


import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.edison.eventsourcing.aws.testsupport.TestStreamSource;
import de.otto.edison.eventsourcing.consumer.MessageConsumer;
import de.otto.edison.eventsourcing.consumer.EventSource;
import de.otto.edison.eventsourcing.consumer.StreamPosition;
import de.otto.edison.eventsourcing.message.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.synchronizedList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@EnableAutoConfiguration
@ComponentScan(basePackages = {"de.otto.edison.eventsourcing"})
@SpringBootTest(classes = KinesisEventSourceIntegrationTest.class)
public class KinesisEventSourceIntegrationTest {

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(new byte[]{});
    private static final int EXPECTED_NUMBER_OF_ENTRIES_IN_FIRST_SET = 10;
    private static final int EXPECTED_NUMBER_OF_ENTRIES_IN_SECOND_SET = 10;
    private static final int EXPECTED_NUMBER_OF_SHARDS = 2;
    private static final String STREAM_NAME = "promo-compaction-test";

    @Autowired
    private KinesisClient kinesisClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private EventSource eventSource;
    private List<Message<String>> messages = synchronizedList(new ArrayList<Message<String>>());

    @Before
    public void before() {
        messages.clear();
    }

    @PostConstruct
    public void setup() {
        KinesisStreamSetupUtils.createStreamIfNotExists(kinesisClient, STREAM_NAME, EXPECTED_NUMBER_OF_SHARDS);
        KinesisStream kinesisStream = new KinesisStream(kinesisClient, STREAM_NAME);
        this.eventSource = new KinesisEventSource("kinesisEventSource", kinesisStream, eventPublisher, objectMapper);
        this.eventSource.register(MessageConsumer.of(".*", String.class, messages::add));
    }

    @Test
    public void consumeDataFromKinesisStream() {
        // when
        StreamPosition startFrom = writeToStream("users_small1.txt").getFirstReadPosition();

        // then
        eventSource.consumeAll(
                startFrom,
                stopCondition()
        );

        assertThat(messages, not(empty()));
        assertThat(messages, hasSize(EXPECTED_NUMBER_OF_ENTRIES_IN_FIRST_SET));
    }

    @Test
    public void consumeDeleteMessagesFromKinesisStream() {
        // given
        final StreamPosition streamPosition = eventSource.consumeAll(
                StreamPosition.of(),
                stopCondition()
        );
        messages.clear();
        kinesisClient.putRecord(PutRecordRequest.builder().streamName(STREAM_NAME).partitionKey("deleteEvent").data(EMPTY_BYTE_BUFFER).build());
        // when
        eventSource.consumeAll(
                streamPosition,
                stopCondition()
        );
        // then
        assertThat(messages, hasSize(1));
        assertThat(messages.get(0).getKey(), is("deleteEvent"));
        assertThat(messages.get(0).getPayload(), is(nullValue()));
    }

    @Test
    public void consumerShouldReadNoMoreAfterStartingPoint() {
        // when
        writeToStream("users_small1.txt");
        StreamPosition startFrom = writeToStream("users_small2.txt").getFirstReadPosition();

        // then
        StreamPosition nextStreamPosition = eventSource.consumeAll(
                startFrom,
                stopCondition());

        assertThat(nextStreamPosition.shards(), hasSize(EXPECTED_NUMBER_OF_SHARDS));
        assertThat(messages, hasSize(EXPECTED_NUMBER_OF_ENTRIES_IN_SECOND_SET));
        assertThat(messages.stream().map(Message::getKey).sorted().collect(Collectors.toList()), is(expectedListOfKeys()));
    }

    @Test
    public void consumerShouldResumeAtStartingPoint() {
        // when
        writeToStream("users_small1.txt");
        StreamPosition startFrom = writeToStream("users_small2.txt").getLastStreamPosition();

        // then
        StreamPosition next = eventSource.consumeAll(
                startFrom,
                stopCondition());

        assertThat(messages, empty());
        assertThat(next.shards(), hasSize(EXPECTED_NUMBER_OF_SHARDS));
    }

    private TestStreamSource writeToStream(String filename) {
        TestStreamSource streamSource = new TestStreamSource(kinesisClient, STREAM_NAME, filename);
        streamSource.writeToStream();
        return streamSource;
    }

    private List<String> expectedListOfKeys() {
        return IntStream.range(EXPECTED_NUMBER_OF_ENTRIES_IN_FIRST_SET + 1, EXPECTED_NUMBER_OF_ENTRIES_IN_FIRST_SET + EXPECTED_NUMBER_OF_ENTRIES_IN_SECOND_SET + 1).mapToObj(String::valueOf).collect(Collectors.toList());
    }

    private Predicate<Message<?>> stopCondition() {
        return e -> e.getPayload() == null;
    }
}
