package pl.grzeslowski.jbambuapi.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;

import java.net.URI;
import java.util.Arrays;

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
    public static final String LOCAL_USERNAME = "bblp";
    public static final String SCHEME = "ssl://";
    public static final int DEFAULT_CONNECTION_TIMEOUT = org.eclipse.paho.client.mqttv3.MqttConnectOptions.CONNECTION_TIMEOUT_DEFAULT;
    public static final int DEFAULT_KEEP_ALIVE_INTERVAL = org.eclipse.paho.client.mqttv3.MqttConnectOptions.KEEP_ALIVE_INTERVAL_DEFAULT;
    public static final boolean DEFAULT_AUTOMATIC_RECONNECT = false;

    public static PrinterClientConfig requiredFields(URI host, String username, String serial, char[] accessCode) {
        return new PrinterClientConfig(
                host,
                MqttClient.generateClientId(),
                username,
                serial,
                accessCode,
                DEFAULT_CONNECTION_TIMEOUT,
                DEFAULT_KEEP_ALIVE_INTERVAL,
                DEFAULT_AUTOMATIC_RECONNECT);
    }


    public static PrinterClientConfig buildDefault(String host, String serial, char[] accessCode) {
        return new PrinterClientConfig(
                URI.create(SCHEME + host + ":" + DEFAULT_PORT),
                MqttClient.generateClientId(),
                LOCAL_USERNAME,
                serial,
                accessCode,
                DEFAULT_CONNECTION_TIMEOUT,
                DEFAULT_KEEP_ALIVE_INTERVAL,
                DEFAULT_AUTOMATIC_RECONNECT);
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
