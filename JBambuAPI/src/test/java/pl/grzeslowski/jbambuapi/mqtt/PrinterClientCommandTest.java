package pl.grzeslowski.jbambuapi.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.PrintSpeedCommand.LUDICROUS;

@ExtendWith(MockitoExtension.class)
class PrinterClientCommandTest {
    @InjectMocks
    PrinterClient printerClient;
    @Spy
    PrinterClientConfig config = PrinterClientConfig.requiredFields(
            URI.create("https://127.0.0.1"),
            "utest",
            "s-e-r-i-a-l",
            "p4$$vv0rD".toCharArray());
    @Mock
    MqttClient mqttClient;

    @Test
    @DisplayName("should send proper PrintSpeedCommand")
    void printSpeedCommand() throws MqttException {
        // given
        var command = LUDICROUS;

        // when
        printerClient.getChannel().sendCommand(command);

        // then
        verify(mqttClient).publish(
                eq("device/%s/request".formatted(config.serial())),
                assertArg(message -> {
                    assertThat(new String(message.getPayload())).isEqualTo("{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"4\",\"command\":\"print_speed\"},\"camera\":null,\"xcam\":null,\"system\":null}");
                })
        );
    }
}
