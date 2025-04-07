package pl.grzeslowski.jbambuapi.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.LedControlCommand.LedNode.WORK_LIGHT;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.XCamControlCommand.Module.FIRST_LAYER_INSPECTOR;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.XCamControlCommand.Module.SPAGHETTI_DETECTOR;

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
    final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{index}: should send proper {0} command")
    @MethodSource
    void sendCommand(PrinterClient.Channel.Command command, String json) throws Exception {
        // given
        var expected = mapper.readValue(json, Map.class);

        // when
        printerClient.getChannel().sendCommand(command);

        // then
        verify(mqttClient).publish(
                eq("device/%s/request".formatted(config.serial())),
                assertArg(message -> {
                    var map = mapper.readValue(message.getPayload(), Map.class);
                    assertThat(map)
                            .as(new String(message.getPayload()))
                            .isEqualTo(expected);
                })
        );
    }

    static Stream<Arguments> sendCommand() {
        return Stream.of(
                Arguments.of(InfoCommand.GET_VERSION, "{\"info\":{\"sequence_id\":\"1\",\"command\":\"get_version\"},\"pushing\":null,\"print\":null,\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(AmsControlCommand.RESUME, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"resume\",\"command\":\"ams_control\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(AmsControlCommand.RESET, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"reset\",\"command\":\"ams_control\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(AmsControlCommand.PAUSE, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"pause\",\"command\":\"ams_control\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(new AmsFilamentSettingCommand(1, 2, "3", "4", 5, 6, "7"), "{\"info\":null,\"pushing\":null,\"print\":{\"tray_color\":\"4\",\"tray_type\":\"7\",\"nozzle_temp_max\":6,\"nozzle_temp_min\":5,\"ams_id\":1,\"sequence_id\":\"1\",\"tray_info_idx\":\"3\",\"tray_id\":2,\"command\":\"ams_filament_setting\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(new AmsUserSettingCommand(1, true, false), "{\"info\":null,\"pushing\":null,\"print\":{\"ams_id\":1,\"sequence_id\":\"1\",\"startup_read_option\":true,\"tray_read_option\":false,\"command\":\"ams_user_setting\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(new ChangeFilamentCommand(1, 2, 3), "{\"info\":null,\"pushing\":null,\"print\":{\"tar_temp\":3,\"sequence_id\":\"1\",\"curr_temp\":2,\"command\":\"ams_change_filament\",\"target\":1},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(new GCodeFileCommand("filename.gcode"), "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"filename.gcode\",\"command\":\"gcode_file\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(new GCodeLineCommand(List.of("1", "2", "3"), "123"), "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"1\\\\n2\\\\n3\",\"user_id\":\"123\",\"command\":\"gcode_line\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(new IpCamRecordCommand(false), "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":{\"sequence_id\":\"1\",\"command\":\"ipcam_record_set\",\"control\":\"disable\"},\"xcam\":null,\"system\":null}"),
                Arguments.of(new IpCamTimelapsCommand(true), "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":{\"sequence_id\":\"1\",\"command\":\"ipcam_timelapse\",\"control\":\"enable\"},\"xcam\":null,\"system\":null}"),
                Arguments.of(LedControlCommand.on(WORK_LIGHT), "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":null,\"xcam\":null,\"system\":{\"led_on_time\":0,\"led_node\":\"work_light\",\"led_mode\":\"on\",\"sequence_id\":\"1\",\"interval_time\":0,\"led_off_time\":0,\"command\":\"ledctrl\",\"loop_times\":0}}"),
                Arguments.of(LedControlCommand.flashing(WORK_LIGHT, 1, 2, 3, 4), "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":null,\"xcam\":null,\"system\":{\"led_on_time\":1,\"led_mode\":\"flashing\",\"led_node\":\"work_light\",\"sequence_id\":\"1\",\"interval_time\":4,\"led_off_time\":2,\"command\":\"ledctrl\",\"loop_times\":3}}"),
                Arguments.of(PrintCommand.STOP, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"\",\"command\":\"stop\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintCommand.PAUSE, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"\",\"command\":\"pause\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintCommand.RESUME, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"\",\"command\":\"resume\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintCommand.CALIBRATION, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"\",\"command\":\"calibration\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintCommand.UNLOAD_FILAMENT, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"\",\"command\":\"unload_filament\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintSpeedCommand.SILENT, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"1\",\"command\":\"print_speed\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintSpeedCommand.STANDARD, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"2\",\"command\":\"print_speed\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintSpeedCommand.SPORT, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"3\",\"command\":\"print_speed\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PrintSpeedCommand.LUDICROUS, "{\"info\":null,\"pushing\":null,\"print\":{\"sequence_id\":\"1\",\"param\":\"4\",\"command\":\"print_speed\"},\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(PushingCommand.defaultPushingCommand(), "{\"info\":null,\"pushing\":{\"sequence_id\":\"1\",\"push_target\":1,\"version\":1,\"command\":\"pushall\"},\"print\":null,\"camera\":null,\"xcam\":null,\"system\":null}"),
                Arguments.of(SystemCommand.GET_ACCESS_CODE, "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":null,\"xcam\":null,\"system\":{\"sequence_id\":\"1\",\"command\":\"get_access_code\"}}"),
                Arguments.of(new XCamControlCommand(FIRST_LAYER_INSPECTOR, true, false), "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":null,\"xcam\":{\"sequence_id\":\"1\",\"print_halt\":false,\"control\":true,\"module_name\":\"first_layer_inspector\",\"command\":\"xcam_control_set\"},\"system\":null}"),
                Arguments.of(new XCamControlCommand(SPAGHETTI_DETECTOR, false, true), "{\"info\":null,\"pushing\":null,\"print\":null,\"camera\":null,\"xcam\":{\"sequence_id\":\"1\",\"print_halt\":true,\"control\":false,\"module_name\":\"spaghetti_detector\",\"command\":\"xcam_control_set\"},\"system\":null}"));
    }

    @Test
    @DisplayName("should increment `sequence_id` when sending multiple commands")
    @SuppressWarnings("unchecked")
    void updateSequenceId() throws Exception {
        // given
        var json = """
                {
                  "info": {
                    "sequence_id": "3",
                    "command": "get_version"
                  },
                  "pushing": null,
                  "print": null,
                  "camera": null,
                  "xcam": null,
                  "system": null
                }""";
        var expected = mapper.readValue(json, Map.class);

        // when
        printerClient.getChannel().sendCommand(InfoCommand.GET_VERSION);
        printerClient.getChannel().sendCommand(InfoCommand.GET_VERSION);
        printerClient.getChannel().sendCommand(InfoCommand.GET_VERSION);

        // then
        var messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(3)).publish(
                eq("device/%s/request".formatted(config.serial())),
                messageCaptor.capture());
        var lastMessage = messageCaptor.getAllValues().get(2);
        var map = mapper.readValue(lastMessage.getPayload(), Map.class);

        assertThat(map)
                .as(new String(lastMessage.getPayload()))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should send RawCommand")
    void sendRawCommand() throws Exception {
        // given
        var command = new RawCommand() {
            @Override
            public String topic() {
                return "m-y-t-o-p-i-c";
            }

            @Override
            public byte[] buildRawCommand(long sequenceId) {
                return "{\"foo\": \"boo\", \"sequenceId\": %d}".formatted(sequenceId).getBytes(UTF_8);
            }
        };

        // when
        printerClient.getChannel().sendCommand(command);

        // then
        verify(mqttClient).publish(
                eq("device/%s/m-y-t-o-p-i-c".formatted(config.serial())),
                assertArg(message ->
                        assertThat(new String(message.getPayload()))
                                .isEqualTo("{\"foo\": \"boo\", \"sequenceId\": 1}")));
    }

    @Test
    @DisplayName("should send RawStringCommand")
    void sendRawStringCommand() throws Exception {
        // given
        var command = new RawStringCommand() {
            @Override
            public String topic() {
                return "m-y-t-o-p-i-c";
            }

            @Override
            public String buildRawStringCommand(long sequenceId) {
                return "{\"foo\": \"boo\", \"sequenceId\": %d}".formatted(sequenceId);
            }
        };

        // when
        printerClient.getChannel().sendCommand(command);

        // then
        verify(mqttClient).publish(
                eq("device/%s/m-y-t-o-p-i-c".formatted(config.serial())),
                assertArg(message ->
                        assertThat(new String(message.getPayload()))
                                .isEqualTo("{\"foo\": \"boo\", \"sequenceId\": 1}")));
    }
}
