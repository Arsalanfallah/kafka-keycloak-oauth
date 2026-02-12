package ir.isc.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy;
import org.apache.kafka.clients.consumer.internals.ShareAcknowledgementMode;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.requests.JoinGroupRequest;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.utils.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author arsaln-fallah
 * Company:ISC
 * @date 2/10/26 7:12 PM
 */
public class ConsumerConfig {
    public static final List<String> ASSIGN_FROM_SUBSCRIBED_ASSIGNORS = List.of("range", "roundrobin", "sticky", "cooperative-sticky");
    public static final String GROUP_ID_CONFIG = "group.id";
    private static final String GROUP_ID_DOC = "A unique string that identifies the consumer group this consumer belongs to. This property is required if the consumer uses either the group management functionality by using <code>subscribe(topic)</code> or the Kafka-based offset management strategy.";
    public static final String GROUP_INSTANCE_ID_CONFIG = "group.instance.id";
    private static final String GROUP_INSTANCE_ID_DOC = "A unique identifier of the consumer instance provided by the end user. Only non-empty strings are permitted. If set, the consumer is treated as a static member, which means that only one instance with this ID is allowed in the consumer group at any time. This can be used in combination with a larger session timeout to avoid group rebalances caused by transient unavailability (e.g. process restarts). If not set, the consumer will join the group as a dynamic member, which is the traditional behavior.";
    public static final String MAX_POLL_RECORDS_CONFIG = "max.poll.records";
    private static final String MAX_POLL_RECORDS_DOC = "The maximum number of records returned in a single call to poll(). Note, that <code>max.poll.records</code> does not impact the underlying fetching behavior. The consumer will cache the records from each fetch request and returns them incrementally from each poll.";
    public static final int DEFAULT_MAX_POLL_RECORDS = 500;
    public static final String MAX_POLL_INTERVAL_MS_CONFIG = "max.poll.interval.ms";
    private static final String MAX_POLL_INTERVAL_MS_DOC = "The maximum delay between invocations of poll() when using consumer group management. This places an upper bound on the amount of time that the consumer can be idle before fetching more records. If poll() is not called before expiration of this timeout, then the consumer is considered failed and the group will rebalance in order to reassign the partitions to another member. For consumers using a non-null <code>group.instance.id</code> which reach this timeout, partitions will not be immediately reassigned. Instead, the consumer will stop sending heartbeats and partitions will be reassigned after expiration of the session timeout (defined by the client config <code>session.timeout.ms</code> if using the Classic rebalance protocol, or by the broker config <code>group.consumer.session.timeout.ms</code> if using the Consumer protocol). This mirrors the behavior of a static consumer which has shutdown.";
    public static final String SESSION_TIMEOUT_MS_CONFIG = "session.timeout.ms";
    private static final String SESSION_TIMEOUT_MS_DOC = "The timeout used to detect client failures when using Kafka's group management facility. The client sends periodic heartbeats to indicate its liveness to the broker. If no heartbeats are received by the broker before the expiration of this session timeout, then the broker will remove this client from the group and initiate a rebalance. Note that the value must be in the allowable range as configured in the broker configuration by <code>group.min.session.timeout.ms</code> and <code>group.max.session.timeout.ms</code>. Note that this client configuration is not supported when <code>group.protocol</code> is set to \"consumer\". In that case, session timeout is controlled by the broker config <code>group.consumer.session.timeout.ms</code>.";
    public static final String HEARTBEAT_INTERVAL_MS_CONFIG = "heartbeat.interval.ms";
    private static final String HEARTBEAT_INTERVAL_MS_DOC = "The expected time between heartbeats to the consumer coordinator when using Kafka's group management facilities. Heartbeats are used to ensure that the consumer's session stays active and to facilitate rebalancing when new consumers join or leave the group. This config is only supported if <code>group.protocol</code> is set to \"classic\". In that case, the value must be set lower than <code>session.timeout.ms</code>, but typically should be set no higher than 1/3 of that value. It can be adjusted even lower to control the expected time for normal rebalances.If <code>group.protocol</code> is set to \"consumer\", this config is not supported, as the heartbeat interval is controlled by the broker with <code>group.consumer.heartbeat.interval.ms</code>.";
    public static final String GROUP_PROTOCOL_CONFIG = "group.protocol";
    public static final String GROUP_PROTOCOL_DOC = "The group protocol consumer should use. We currently support \"classic\" or \"consumer\". If \"consumer\" is specified, then the consumer group protocol will be used. Otherwise, the classic group protocol will be used.";
    public static final String GROUP_REMOTE_ASSIGNOR_CONFIG = "group.remote.assignor";
    public static final String GROUP_REMOTE_ASSIGNOR_DOC = "The name of the server-side assignor to use. If not specified, the group coordinator will pick the first assignor defined in the broker config group.consumer.assignors.This configuration is applied only if <code>group.protocol</code> is set to \"consumer\".";
    public static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";
    public static final String CLIENT_DNS_LOOKUP_CONFIG = "client.dns.lookup";
    public static final String ENABLE_AUTO_COMMIT_CONFIG = "enable.auto.commit";
    private static final String ENABLE_AUTO_COMMIT_DOC = "If true the consumer's offset will be periodically committed in the background.";
    public static final String AUTO_COMMIT_INTERVAL_MS_CONFIG = "auto.commit.interval.ms";
    private static final String AUTO_COMMIT_INTERVAL_MS_DOC = "The frequency in milliseconds that the consumer offsets are auto-committed to Kafka if <code>enable.auto.commit</code> is set to <code>true</code>.";
    public static final String PARTITION_ASSIGNMENT_STRATEGY_CONFIG = "partition.assignment.strategy";
    private static final String PARTITION_ASSIGNMENT_STRATEGY_DOC = "A list of class names or class types, ordered by preference, of supported partition assignment strategies that the client will use to distribute partition ownership amongst consumer instances when group management is used. Available options are:<ul><li><code>org.apache.kafka.clients.consumer.RangeAssignor</code>: Assigns partitions on a per-topic basis.</li><li><code>org.apache.kafka.clients.consumer.RoundRobinAssignor</code>: Assigns partitions to consumers in a round-robin fashion.</li><li><code>org.apache.kafka.clients.consumer.StickyAssignor</code>: Guarantees an assignment that is maximally balanced while preserving as many existing partition assignments as possible.</li><li><code>org.apache.kafka.clients.consumer.CooperativeStickyAssignor</code>: Follows the same StickyAssignor logic, but allows for cooperative rebalancing.</li></ul><p>The default assignor is [RangeAssignor, CooperativeStickyAssignor], which will use the RangeAssignor by default, but allows upgrading to the CooperativeStickyAssignor with just a single rolling bounce that removes the RangeAssignor from the list.</p><p>Implementing the <code>org.apache.kafka.clients.consumer.ConsumerPartitionAssignor</code> interface allows you to plug in a custom assignment strategy.</p>";
    public static final String AUTO_OFFSET_RESET_CONFIG = "auto.offset.reset";
    public static final String AUTO_OFFSET_RESET_DOC = "What to do when there is no initial offset in Kafka or if the current offset does not exist any more on the server (e.g. because that data has been deleted): <ul><li>earliest: automatically reset the offset to the earliest offset</li><li>latest: automatically reset the offset to the latest offset</li><li>by_duration:&lt;duration&gt;: automatically reset the offset to a configured &lt;duration&gt; from the current timestamp. &lt;duration&gt; must be specified in ISO8601 format (PnDTnHnMn.nS). Negative duration is not allowed.</li><li>none: throw exception to the consumer if no previous offset is found for the consumer's group</li><li>anything else: throw exception to the consumer.</li></ul><p>Note that altering partition numbers while setting this config to latest may cause message delivery loss since producers could start to send messages to newly added partitions (i.e. no initial offsets exist yet) before consumers reset their offsets.";
    public static final String FETCH_MIN_BYTES_CONFIG = "fetch.min.bytes";
    public static final int DEFAULT_FETCH_MIN_BYTES = 1;
    private static final String FETCH_MIN_BYTES_DOC = "The minimum amount of data the server should return for a fetch request. If insufficient data is available the request will wait for that much data to accumulate before answering the request. The default setting of 1 byte means that fetch requests are answered as soon as that many byte(s) of data is available or the fetch request times out waiting for data to arrive. Setting this to a larger value will cause the server to wait for larger amounts of data to accumulate which can improve server throughput a bit at the cost of some additional latency.";
    public static final String FETCH_MAX_BYTES_CONFIG = "fetch.max.bytes";
    private static final String FETCH_MAX_BYTES_DOC = "The maximum amount of data the server should return for a fetch request. Records are fetched in batches by the consumer, and if the first record batch in the first non-empty partition of the fetch is larger than this value, the record batch will still be returned to ensure that the consumer can make progress. As such, this is not a absolute maximum. The maximum record batch size accepted by the broker is defined via <code>message.max.bytes</code> (broker config) or <code>max.message.bytes</code> (topic config). A fetch request consists of many partitions, and there is another setting that controls how much data is returned for each partition in a fetch request - see <code>max.partition.fetch.bytes</code>. Note that there is a current limitation when performing remote reads from tiered storage (KIP-405) - only one partition out of the fetch request is fetched from the remote store (KAFKA-14915). Note also that the consumer performs multiple fetches in parallel.";
    public static final int DEFAULT_FETCH_MAX_BYTES = 52428800;
    public static final String FETCH_MAX_WAIT_MS_CONFIG = "fetch.max.wait.ms";
    private static final String FETCH_MAX_WAIT_MS_DOC = "The maximum amount of time the server will block before answering the fetch request there isn't sufficient data to immediately satisfy the requirement given by fetch.min.bytes. This config is used only for local log fetch. To tune the remote fetch maximum wait time, please refer to 'remote.fetch.max.wait.ms' broker config";
    public static final int DEFAULT_FETCH_MAX_WAIT_MS = 500;
    public static final String METADATA_MAX_AGE_CONFIG = "metadata.max.age.ms";
    public static final String MAX_PARTITION_FETCH_BYTES_CONFIG = "max.partition.fetch.bytes";
    private static final String MAX_PARTITION_FETCH_BYTES_DOC = "The maximum amount of data per-partition the server will return. Records are fetched in batches by the consumer. If the first record batch in the first non-empty partition of the fetch is larger than this limit, the batch will still be returned to ensure that the consumer can make progress. The maximum record batch size accepted by the broker is defined via <code>message.max.bytes</code> (broker config) or <code>max.message.bytes</code> (topic config). See fetch.max.bytes for limiting the consumer request size. Consider increasing <code>max.partition.fetch.bytes</code> especially in the cases of remote storage reads (KIP-405), because currently only one partition per fetch request is served from the remote store (KAFKA-14915).";
    public static final int DEFAULT_MAX_PARTITION_FETCH_BYTES = 1048576;
    public static final String SEND_BUFFER_CONFIG = "send.buffer.bytes";
    public static final String RECEIVE_BUFFER_CONFIG = "receive.buffer.bytes";
    public static final String CLIENT_ID_CONFIG = "client.id";
    public static final String CLIENT_RACK_CONFIG = "client.rack";
    public static final String DEFAULT_CLIENT_RACK = "";
    public static final String RECONNECT_BACKOFF_MS_CONFIG = "reconnect.backoff.ms";
    public static final String RECONNECT_BACKOFF_MAX_MS_CONFIG = "reconnect.backoff.max.ms";
    public static final String RETRY_BACKOFF_MS_CONFIG = "retry.backoff.ms";
    public static final String ENABLE_METRICS_PUSH_CONFIG = "enable.metrics.push";
    public static final String ENABLE_METRICS_PUSH_DOC = "Whether to enable pushing of client metrics to the cluster, if the cluster has a client metrics subscription which matches this client.";
    public static final String RETRY_BACKOFF_MAX_MS_CONFIG = "retry.backoff.max.ms";
    public static final String METRICS_SAMPLE_WINDOW_MS_CONFIG = "metrics.sample.window.ms";
    public static final String METRICS_NUM_SAMPLES_CONFIG = "metrics.num.samples";
    public static final String METRICS_RECORDING_LEVEL_CONFIG = "metrics.recording.level";
    public static final String METRIC_REPORTER_CLASSES_CONFIG = "metric.reporters";
    public static final String CHECK_CRCS_CONFIG = "check.crcs";
    private static final String CHECK_CRCS_DOC = "Automatically check the CRC32 of the records consumed. This ensures no on-the-wire or on-disk corruption to the messages occurred. This check adds some overhead, so it may be disabled in cases seeking extreme performance.";
    public static final String KEY_DESERIALIZER_CLASS_CONFIG = "key.deserializer";
    public static final String KEY_DESERIALIZER_CLASS_DOC = "Deserializer class for key that implements the <code>org.apache.kafka.common.serialization.Deserializer</code> interface.";
    public static final String VALUE_DESERIALIZER_CLASS_CONFIG = "value.deserializer";
    public static final String VALUE_DESERIALIZER_CLASS_DOC = "Deserializer class for value that implements the <code>org.apache.kafka.common.serialization.Deserializer</code> interface.";
    public static final String SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG = "socket.connection.setup.timeout.ms";
    public static final String SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG = "socket.connection.setup.timeout.max.ms";
    public static final String CONNECTIONS_MAX_IDLE_MS_CONFIG = "connections.max.idle.ms";
    public static final String REQUEST_TIMEOUT_MS_CONFIG = "request.timeout.ms";
    private static final String REQUEST_TIMEOUT_MS_DOC = "The configuration controls the maximum amount of time the client will wait for the response of a request. If the response is not received before the timeout elapses the client will resend the request if necessary or fail the request if retries are exhausted.";
    public static final String DEFAULT_API_TIMEOUT_MS_CONFIG = "default.api.timeout.ms";
    public static final String INTERCEPTOR_CLASSES_CONFIG = "interceptor.classes";
    public static final String INTERCEPTOR_CLASSES_DOC = "A list of classes to use as interceptors. Implementing the <code>org.apache.kafka.clients.consumer.ConsumerInterceptor</code> interface allows you to intercept (and possibly mutate) records received by the consumer. By default, there are no interceptors.";
    public static final String EXCLUDE_INTERNAL_TOPICS_CONFIG = "exclude.internal.topics";
    private static final String EXCLUDE_INTERNAL_TOPICS_DOC = "Whether internal topics matching a subscribed pattern should be excluded from the subscription. It is always possible to explicitly subscribe to an internal topic.";
    public static final boolean DEFAULT_EXCLUDE_INTERNAL_TOPICS = true;
    static final String LEAVE_GROUP_ON_CLOSE_CONFIG = "internal.leave.group.on.close";
    static final String THROW_ON_FETCH_STABLE_OFFSET_UNSUPPORTED = "internal.throw.on.fetch.stable.offset.unsupported";
    public static final String ISOLATION_LEVEL_CONFIG = "isolation.level";
    public static final String ISOLATION_LEVEL_DOC = "Controls how to read messages written transactionally. If set to <code>read_committed</code>, consumer.poll() will only return transactional messages which have been committed. If set to <code>read_uncommitted</code> (the default), consumer.poll() will return all messages, even transactional messages which have been aborted. Non-transactional messages will be returned unconditionally in either mode. <p>Messages will always be returned in offset order. Hence, in  <code>read_committed</code> mode, consumer.poll() will only return messages up to the last stable offset (LSO), which is the one less than the offset of the first open transaction. In particular any messages appearing after messages belonging to ongoing transactions will be withheld until the relevant transaction has been completed. As a result, <code>read_committed</code> consumers will not be able to read up to the high watermark when there are in flight transactions.</p><p> Further, when in <code>read_committed</code> the seekToEnd method will return the LSO</p>";
    public static final String ALLOW_AUTO_CREATE_TOPICS_CONFIG = "allow.auto.create.topics";
    private static final String ALLOW_AUTO_CREATE_TOPICS_DOC = "Allow automatic topic creation on the broker when subscribing to or assigning a topic. A topic being subscribed to will be automatically created only if the broker allows for it using `auto.create.topics.enable` broker configuration.";
    public static final boolean DEFAULT_ALLOW_AUTO_CREATE_TOPICS = true;
    public static final String SECURITY_PROVIDERS_CONFIG = "security.providers";
    private static final String SECURITY_PROVIDERS_DOC = "A list of configurable creator classes each returning a provider implementing security algorithms. These classes should implement the <code>org.apache.kafka.common.security.auth.SecurityProviderCreator</code> interface.";
    public static final String SHARE_ACKNOWLEDGEMENT_MODE_CONFIG = "share.acknowledgement.mode";
    private static final String SHARE_ACKNOWLEDGEMENT_MODE_DOC = "Controls the acknowledgement mode for a share consumer. If set to <code>implicit</code>, the acknowledgement mode of the consumer is implicit and it must not use <code>org.apache.kafka.clients.consumer.ShareConsumer.acknowledge()</code> to acknowledge delivery of records. Instead, delivery is acknowledged implicitly on the next call to poll or commit. If set to <code>explicit</code>, the acknowledgement mode of the consumer is explicit and it must use <code>org.apache.kafka.clients.consumer.ShareConsumer.acknowledge()</code> to acknowledge delivery of records.";

}
