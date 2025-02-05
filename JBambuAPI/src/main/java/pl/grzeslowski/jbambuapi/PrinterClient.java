package pl.grzeslowski.jbambuapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.synchronizedList;
import static pl.grzeslowski.jbambuapi.CommunicationException.fromMqttException;

public final class PrinterClient implements AutoCloseable {
    private final AtomicInteger messageId = new AtomicInteger(1);
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Logger log;
    private final PrinterClientConfig config;
    private final MqttClient mqtt;
    private final List<PrinterStateConsumer> subscribers = synchronizedList(new ArrayList<>());

    private final ReadWriteLock fullStateLock = new ReentrantReadWriteLock();
    private PrinterState fullState = null;

    public PrinterClient(PrinterClientConfig config) throws CommunicationException {
        log = LoggerFactory.getLogger(getClass() + "." + config.serial());
        log.debug("Connecting to MQTT broker");
        this.config = config;
        var uri = config.uri().toString();
        try {
            log.debug("Creating MQTT {}", uri);
            this.mqtt = new MqttClient(uri, config.clientId());
        } catch (MqttException e) {
            throw fromMqttException("Cannot create MQTT at %s! ".formatted(uri) + e.getLocalizedMessage(), e);
        }
        jsonMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void connect() throws CommunicationException, NoSuchAlgorithmException, KeyManagementException {
        var options = buildMqttOptions();
        try {
            log.debug("Connecting to MQTT {}", config.uri());
            mqtt.connect(options);
        } catch (MqttException e) {
            throw fromMqttException("Cannot connect to MQTT at %s! ".formatted(config.uri()) + e.getLocalizedMessage(), e);
        }

        try {
            var topic = "device/%s/report".formatted(config.serial());
            log.debug("Subscribing to {}", topic);
            mqtt.subscribe(topic, (__, msg) -> {
                var payload = msg.getPayload();
                if (log.isDebugEnabled()) {
                    log.debug("Message received: {}", new String(payload, UTF_8));
                }
                var delta = jsonMapper.readValue(payload, PrinterState.class);
                fullStateLock.writeLock().lock();
                try {
                    fullState = PrinterState.merge(fullState, delta);
                } finally {
                    fullStateLock.writeLock().unlock();
                }
                subscribers.forEach(subscriber -> {
                    try {
                        subscriber.consumer(delta, fullState);
                    } catch (Exception e) {
                        log.warn("Consumer [{}] could not accept message: {}", subscriber, delta, e);
                    }
                });
            });
        } catch (MqttException e) {
            throw fromMqttException("Cannot subscribe to MQTT at %s! ".formatted(config.uri()), e);
        }
    }

    private MqttConnectOptions buildMqttOptions() throws NoSuchAlgorithmException, KeyManagementException {
        var options = new MqttConnectOptions();
        options.setUserName(config.username());
        options.setPassword(config.accessCode());
        options.setConnectionTimeout(config.connectionTimeout());
        options.setKeepAliveInterval(config.keepAliveInterval());
        options.setAutomaticReconnect(config.automaticReconnect());
        options.setSSLHostnameVerifier((hostname, session) -> true);
        options.setHttpsHostnameVerificationEnabled(false);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }}, new java.security.SecureRandom());

        options.setSocketFactory(sslContext.getSocketFactory());
        return options;
    }

    /**
     * Forces printer to send (full) current state. Use with caution because it can lag the printer.
     *
     * @throws CommunicationException error during publishing to MQTT topic
     */
    public void update() throws CommunicationException {
        var topic = "device/%s/report".formatted(config.serial());
        try {
            var message = new MqttMessage("{\"pushing\": {\"command\": \"pushall\"}}".getBytes(UTF_8));
            message.setId(messageId.getAndIncrement());
            message.setQos(0);
            mqtt.publish(topic, message);
        } catch (MqttException e) {
            throw fromMqttException("Cannot request for reports! " + e.getLocalizedMessage(), e);
        }
    }

    public void subscribe(PrinterStateConsumer subscriber) {
        subscribers.add(subscriber);
    }

    public boolean unsubscribe(PrinterStateConsumer subscriber) {
        var remove = subscribers.remove(subscriber);
        if (!remove) {
            log.warn("Subscriber [{}] was not removed! " +
                    "It either was not in the list or equals is not implemented correctly.", subscriber);
        }
        return remove;
    }

    public boolean isConnected() {
        return mqtt.isConnected();
    }

    public Optional<PrinterState> getFullState() {
        fullStateLock.readLock().lock();
        try {
            return Optional.ofNullable(fullState);
        } finally {
            fullStateLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        subscribers.clear();
        fullState = null;
        try {
            log.debug("Closing MQTT {}", config.uri());
            mqtt.disconnect();
        } catch (MqttException e) {
            throw fromMqttException("Cannot disconnect from MQTT at %s! ".formatted(config.uri()) + e.getLocalizedMessage(), e);
        }
    }
}
