package pl.grzeslowski.jbambuapi;

import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.CONNECTION_TIMEOUT_DEFAULT;
import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.KEEP_ALIVE_INTERVAL_DEFAULT;

public record PrinterClientConfig(
        URI uri,
        String clientId,
        String username,
        String serial,
        char[] accessCode,
        int connectionTimeout,
        int keepAliveInterval,
        boolean automaticReconnect) implements AutoCloseable {
    public static final int DEFAULT_PORT = 8883;
    public static final String DEFAULT_USERNAME = "bblp";

    public static PrinterClientConfig buildDefault(String ip, String serial, char[] accessCode) {
        return new PrinterClientConfig(
                URI.create("ssl://" + ip + ":" + DEFAULT_PORT),
                UUID.randomUUID().toString(),
                DEFAULT_USERNAME,
                serial,
                accessCode,
                CONNECTION_TIMEOUT_DEFAULT,
                KEEP_ALIVE_INTERVAL_DEFAULT,
                false);
    }

    @Override
    public String toString() {
        return "PrinterClientConfig{" +
                "uri=" + uri +
                ", clientId='" + clientId + '\'' +
                ", username='" + username + '\'' +
                ", serial='" + serial + '\'' +
                ", accessCode=<SECRET>" +
                ", connectionTimeout=" + connectionTimeout +
                ", keepAliveInterval=" + keepAliveInterval +
                ", automaticReconnect=" + automaticReconnect +
                '}';
    }

    @Override
    public void close() {
        Arrays.fill(accessCode, (char) 0);
    }
}
