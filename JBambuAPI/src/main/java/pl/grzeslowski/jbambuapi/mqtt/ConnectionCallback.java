package pl.grzeslowski.jbambuapi.mqtt;

public interface ConnectionCallback {
    void connectComplete(boolean reconnect);

    void connectionLost(Throwable cause);
}
