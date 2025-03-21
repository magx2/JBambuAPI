package pl.grzeslowski.jbambuapi.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.Serial;

@SuppressWarnings("SerializableHasSerializationMethods")
public class CommunicationException extends RuntimeException{
    @Serial
    private static final long serialVersionUID = 1L;

    private CommunicationException(String message) {
        super(message);
    }

    private CommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    private CommunicationException(Throwable cause) {
        super(cause);
    }

    private CommunicationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static CommunicationException fromMqttException(String cause, MqttException mqttException) {
        return new CommunicationException(cause, mqttException);
    }

    public static CommunicationException fromJsonException(String cause, JsonProcessingException exception) {
        return new CommunicationException(cause, exception);
    }
}
