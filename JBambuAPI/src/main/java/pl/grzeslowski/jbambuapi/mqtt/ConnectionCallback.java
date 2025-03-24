package pl.grzeslowski.jbambuapi.mqtt;

/**
 * Callback interface for monitoring MQTT connection events.
 * <p>
 * Implement this interface if you want to receive notifications when the
 * connection to the MQTT broker is established or lost.
 * </p>
 *
 * <p>
 * Used in {@link PrinterClient#connect(ConnectionCallback)} to provide hooks
 * for managing connection lifecycle events.
 * </p>
 */
public interface ConnectionCallback {

    /**
     * Called when the MQTT connection has been successfully established.
     *
     * @param reconnect true if the connection was re-established after a disconnection,
     *                  false if this is the initial connection.
     */
    void connectComplete(boolean reconnect);

    /**
     * Called when the MQTT connection is lost.
     *
     * @param cause the exception or reason for the connection loss.
     */
    void connectionLost(Throwable cause);
}
