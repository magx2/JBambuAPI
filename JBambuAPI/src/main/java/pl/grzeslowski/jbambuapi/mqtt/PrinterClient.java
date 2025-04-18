package pl.grzeslowski.jbambuapi.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static pl.grzeslowski.jbambuapi.mqtt.CommunicationException.fromJsonException;
import static pl.grzeslowski.jbambuapi.mqtt.CommunicationException.fromMqttException;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.LedControlCommand.LedMode.*;

public final class PrinterClient implements AutoCloseable {
    private final AtomicInteger messageId = new AtomicInteger(1);
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Logger log;
    private final PrinterClientConfig config;
    private final MqttClient mqtt;
    private final List<ChannelMessageConsumer> subscribers = synchronizedList(new ArrayList<>());
    @Getter
    private final Channel channel = new Channel();

    PrinterClient(PrinterClientConfig config, MqttClient mqtt) {
        log = LoggerFactory.getLogger(getClass() + "." + config.serial());
        log.debug("Connecting to MQTT broker");
        this.config = config;
        this.mqtt = mqtt;
    }

    public PrinterClient(PrinterClientConfig config) throws CommunicationException {
        this(config, buildMqtt(config.uri().toString(), config.clientId()));
    }

    private static MqttClient buildMqtt(String uri, String clientId) {
        try {
            return new MqttClient(uri, clientId);
        } catch (MqttException e) {
            throw fromMqttException("Cannot create MQTT at %s! ".formatted(uri) + e.getLocalizedMessage(), e);
        }
    }

    public void connect() throws CommunicationException, NoSuchAlgorithmException, KeyManagementException {
        connect(null);
    }

    public void connect(ConnectionCallback connectionCallback) throws CommunicationException, NoSuchAlgorithmException, KeyManagementException {
        if (connectionCallback != null) {
            mqtt.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    connectionCallback.connectComplete(reconnect);
                }

                @Override
                public void connectionLost(Throwable cause) {
                    connectionCallback.connectionLost(cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
        }
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
            mqtt.subscribe(topic, (finalTopic, msg) -> {
                var payload = msg.getPayload();
                if (log.isDebugEnabled()) {
                    log.debug("Message received: {}", new String(payload, UTF_8));
                }
                subscribers.forEach(subscriber -> {
                    try {
                        subscriber.consume(finalTopic, payload);
                    } catch (Exception e) {
                        if (log.isWarnEnabled()) {
                            log.warn("Consumer {} could not accept message: {}",
                                    subscriber, new String(payload, UTF_8), e);
                        }
                    }
                });
            });
        } catch (MqttException e) {
            throw fromMqttException("Cannot subscribe to MQTT at %s!".formatted(config.uri()), e);
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
        }}, new SecureRandom());

        options.setSocketFactory(sslContext.getSocketFactory());
        return options;
    }

    public void subscribe(ChannelMessageConsumer subscriber) {
        subscribers.add(subscriber);
    }

    public boolean unsubscribe(ChannelMessageConsumer subscriber) {
        var remove = subscribers.remove(subscriber);
        if (!remove) {
            log.warn("Subscriber {} was not removed! " +
                    "It either was not in the list or equals is not implemented correctly.", subscriber);
        }
        return remove;
    }

    public boolean isConnected() {
        return mqtt.isConnected();
    }

    @Override
    public void close() {
        subscribers.clear();
        try {
            mqtt.setCallback(null);
            log.debug("Closing MQTT {}", config.uri());
            if (mqtt.isConnected()) {
                mqtt.disconnect();
            }
        } catch (MqttException e) {
            throw fromMqttException("Cannot disconnect from MQTT at %s! ".formatted(config.uri()) + e.getLocalizedMessage(), e);
        }
    }

    public class Channel {
        public void sendCommand(Command command) {
            var id = messageId.getAndIncrement();
            String topicName;
            byte[] json;
            if (command instanceof RawCommand rawCommand) {
                topicName = requireNonNull(rawCommand.topic(), "RawCommand %s did returned null topic!".formatted(rawCommand));
                json = requireNonNull(rawCommand.buildRawCommand(id), "RawCommand %s did returned null payload!".formatted(rawCommand));
            } else {
                var message = switch (command) {
                    case InfoCommand info -> buildMessage(info);
                    case AmsControlCommand amsControlCommand -> buildMessage(amsControlCommand);
                    case AmsFilamentSettingCommand amsFilamentSettingCommand -> buildMessage(amsFilamentSettingCommand);
                    case AmsUserSettingCommand amsUserSettingCommand -> buildMessage(amsUserSettingCommand);
                    case ChangeFilamentCommand changeFilamentCommand -> buildMessage(changeFilamentCommand);
                    case GCodeFileCommand gCodeFile -> buildMessage(gCodeFile);
                    case GCodeLineCommand gCodeLine -> buildMessage(gCodeLine);
                    case IpCamRecordCommand ipCamRecord -> buildMessage(ipCamRecord);
                    case IpCamTimelapsCommand ipCamTimelaps -> buildMessage(ipCamTimelaps);
                    case LedControlCommand ledControl -> buildMessage(ledControl);
                    case PrintCommand printCommand -> buildMessage(printCommand);
                    case PrintSpeedCommand printSpeedCommand -> buildMessage(printSpeedCommand);
                    case PushingCommand pushingCommand -> buildMessage(pushingCommand);
                    case SystemCommand systemCommand -> buildMessage(systemCommand);
                    case XCamControlCommand xCamControl -> buildMessage(xCamControl);
                    case RawCommand rawCommand -> throw new IllegalStateException("This branch is not possible!");
                };
                message = addSequenceId(message, id);
                topicName = message.topic;
                try {
                    json = jsonMapper.writeValueAsBytes(message.payload);
                } catch (JsonProcessingException e) {
                    log.debug("Cannot convert to JSON: {}", message.payload, e);
                    throw fromJsonException("Cannot write JSON payload as bytes! " + e.getLocalizedMessage(), e);
                }
            }
            var topic = "device/%s/%s".formatted(config.serial(), topicName);

            if (log.isDebugEnabled()) {
                log.debug("Sending command {} to topic {} with json {}", command, topic, new String(json, UTF_8));
            }
            var mqttMessage = new MqttMessage(json);
            mqttMessage.setId(id);
            // set QoS to 0 because in LAN mode the publish method was hanging for eternity
            mqttMessage.setQos(0);
            try {
                mqtt.publish(topic, mqttMessage);
            } catch (MqttException e) {
                throw fromMqttException("Cannot publish command MQTT at %s! %s".formatted(config.uri(), e.getLocalizedMessage()), e);
            }
        }

        private Message addSequenceId(Message message, int id) {
            var payload = message.payload;
            return new Message(
                    message.topic,
                    new Message.Payload(
                            addSequenceId(payload.info, id),
                            addSequenceId(payload.pushing, id),
                            addSequenceId(payload.print, id),
                            addSequenceId(payload.camera, id),
                            addSequenceId(payload.xcam, id),
                            addSequenceId(payload.system, id)));
        }

        private Map<String, Object> addSequenceId(Map<String, Object> map, int id) {
            if (map == null) {
                return null;
            }
            var copy = new HashMap<>(map);
            copy.put("sequence_id", id + "");
            return unmodifiableMap(copy);
        }

        private Message buildMessage(InfoCommand info) {
            var command = switch (info) {
                case GET_VERSION -> "get_version";
            };
            return Message.request(Message.Payload.info(Map.of("command", command)));
        }

        private Message buildMessage(PushingCommand pushingCommand) {
            return Message.request(Message.Payload.pushing(Map.of(
                    "command", "pushall",
                    "version", pushingCommand.version,
                    "push_target", pushingCommand.pushTarget)));
        }

        private Message buildMessage(PrintCommand printCommand) {
            var command = switch (printCommand) {
                case STOP -> "stop";
                case PAUSE -> "pause";
                case RESUME -> "resume";
                case CALIBRATION -> "calibration";
                case UNLOAD_FILAMENT -> "unload_filament";
            };

            return Message.request(Message.Payload.print(Map.of(
                    "command", command,
                    "param", "")));
        }

        private Message buildMessage(ChangeFilamentCommand changeFilamentCommand) {
            return Message.request(Message.Payload.print(Map.of(
                    "command", "ams_change_filament",
                    "target", changeFilamentCommand.target,
                    "curr_temp", changeFilamentCommand.currentTemperature,
                    "tar_temp", changeFilamentCommand.targetTemperature
            )));
        }

        private Message buildMessage(AmsUserSettingCommand amsUserSettingCommand) {
            return Message.request(Message.Payload.print(Map.of(
                    "command", "ams_user_setting",
                    "ams_id", amsUserSettingCommand.amsId,
                    "startup_read_option", amsUserSettingCommand.startupReadOption,
                    "tray_read_option", amsUserSettingCommand.trayReadOption
            )));
        }

        private Message buildMessage(AmsFilamentSettingCommand ams) {
            return Message.request(Message.Payload.print(Map.of(
                    "command", "ams_filament_setting",
                    "ams_id", ams.amsId,
                    "tray_id", ams.trayId,
                    "tray_info_idx", ams.trayInfoIdx,
                    "tray_color", ams.trayColor,
                    "nozzle_temp_min", ams.nozzleTempMin,
                    "nozzle_temp_max", ams.nozzleTempMax,
                    "tray_type", ams.trayType
            )));
        }

        private Message buildMessage(AmsControlCommand amsControlCommand) {
            var param = switch (amsControlCommand) {
                case RESUME -> "resume";
                case RESET -> "reset";
                case PAUSE -> "pause";
            };

            return Message.request(Message.Payload.print(Map.of(
                    "command", "ams_control",
                    "param", param)));
        }

        private Message buildMessage(PrintSpeedCommand printSpeedCommand) {
            if (!printSpeedCommand.canSend()) {
                throw new IllegalArgumentException("Cannot send %s command!".formatted(printSpeedCommand));
            }
            return Message.request(Message.Payload.print(Map.of(
                    "command", "print_speed",
                    "param", printSpeedCommand.level + "")));
        }

        private Message buildMessage(GCodeFileCommand gCodeFileCommand) {
            return Message.request(Message.Payload.print(Map.of(
                    "command", "gcode_file",
                    "param", gCodeFileCommand.filename)));
        }

        private Message buildMessage(GCodeLineCommand gCodeLine) {
            var gCode = join("\\n", gCodeLine.lines);
            return Message.request(Message.Payload.print(Map.of(
                    "command", "gcode_line",
                    "param", gCode,
                    "user_id", gCodeLine.userId)));
        }

        private Message buildMessage(LedControlCommand ledControlCommand) {
            var ledNode = switch (ledControlCommand.ledNode) {
                case CHAMBER_LIGHT -> "chamber_light";
                case WORK_LIGHT -> "work_light";
            };
            var ledMode = switch (ledControlCommand.ledMode) {
                case ON -> "on";
                case OFF -> "off";
                case FLASHING -> "flashing";
            };
            return Message.request(Message.Payload.system(Map.of(
                    "command", "ledctrl",
                    "led_node", ledNode,
                    "led_mode", ledMode,

                    // only for flashing
                    "led_on_time", ofNullable(ledControlCommand.ledOnTime).orElse(0),
                    "led_off_time", ofNullable(ledControlCommand.ledOffTime).orElse(0),
                    "loop_times", ofNullable(ledControlCommand.loopTimes).orElse(0),
                    "interval_time", ofNullable(ledControlCommand.intervalTime).orElse(0)
            )));
        }

        private Message buildMessage(SystemCommand systemCommand) {
            var command = switch (systemCommand) {
                case GET_ACCESS_CODE -> "get_access_code";
            };
            return Message.request(Message.Payload.system(Map.of(
                    "command", command
            )));
        }

        private Message buildMessage(IpCamRecordCommand ipCamRecordCommand) {
            return Message.request(Message.Payload.camera(Map.of(
                    "command", "ipcam_record_set",
                    "control", ipCamRecordCommand.enable ? "enable" : "disable"
            )));
        }

        private Message buildMessage(IpCamTimelapsCommand ipCamTimelapsCommand) {
            return Message.request(Message.Payload.camera(Map.of(
                    "command", "ipcam_timelapse",
                    "control", ipCamTimelapsCommand.enable ? "enable" : "disable"
            )));
        }

        private Message buildMessage(XCamControlCommand xCamControlCommand) {
            var module = switch (xCamControlCommand.module) {
                case FIRST_LAYER_INSPECTOR -> "first_layer_inspector";
                case SPAGHETTI_DETECTOR -> "spaghetti_detector";
            };
            return Message.request(Message.Payload.xcam(Map.of(
                    "command", "xcam_control_set",
                    "module_name", module,
                    "control", xCamControlCommand.control,
                    "print_halt", xCamControlCommand.printHalt
            )));
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private static record Message(String topic, Payload payload) {
            private static record Payload(
                    Map<String, Object> info,
                    Map<String, Object> pushing,
                    Map<String, Object> print,
                    Map<String, Object> camera,
                    Map<String, Object> xcam,
                    Map<String, Object> system) {
                public static Payload info(Map<String, Object> info) {
                    return new Payload(info, null, null, null, null, null);
                }

                public static Payload pushing(Map<String, Object> pushing) {
                    return new Payload(null, pushing, null, null, null, null);
                }

                public static Payload print(Map<String, Object> print) {
                    return new Payload(null, null, print, null, null, null);
                }

                public static Payload camera(Map<String, Object> camera) {
                    return new Payload(null, null, null, camera, null, null);
                }

                public static Payload xcam(Map<String, Object> xcam) {
                    return new Payload(null, null, null, null, xcam, null);
                }

                public static Payload system(Map<String, Object> system) {
                    return new Payload(null, null, null, null, null, system);
                }

                private String toString(Object o) {
                    return switch (o) {
                        case null -> "<null>";
                        case Map<?, ?> map -> map.entrySet()
                                .stream()
                                .map(entry -> "%s=%s".formatted(toString(entry.getKey()), toString(entry.getValue())))
                                .collect(joining(", "));
                        case Collection<?> collection -> collection.stream()
                                .map(this::toString)
                                .collect(joining(", "));
                        default -> o.toString();
                    };
                }

                @Override
                public String toString() {
                    var info = toString(this.info);
                    var pushing = toString(this.pushing);
                    var print = toString(this.print);
                    var camera = toString(this.camera);
                    var xcam = toString(this.xcam);
                    var system = toString(this.system);
                    return """
                            Payload {
                                info=%s,
                                pushing=%s,
                                print=%s,
                                camera=%s,
                                xcam=%s,
                                system=%s
                            }""".formatted(info, pushing, print, camera, xcam, system);
                }
            }

            public static Message request(Payload payload) {
                return new Message("request", payload);
            }
        }

        public static sealed interface Command {

        }

        public static enum InfoCommand implements Command {
            /**
             * Get current version of printer
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#pushingpushall">"pushingpushall" API Doc</a>
             */
            GET_VERSION
        }

        /**
         * Reports the complete status of the printer.
         * <p>
         * This is unnecessary for the X1 series since it already transmits the full object each time. However, the P1 series only sends the values that have been updated compared to the previous report.
         * <p>
         * <b>As a rule of thumb, refrain from executing this command at intervals less than 5 minutes on the P1P, as it may cause lag due to its hardware limitations.</b>
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#pushingpushall">"pushingpushall" API Doc</a>
         */
        public static record PushingCommand(int version, int pushTarget) implements Command {
            public static PushingCommand defaultPushingCommand() {
                return new PushingCommand(1, 1);
            }
        }

        public static enum PrintCommand implements Command {
            /**
             * Stops a print
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printstop">"printstop" API Doc</a>
             */
            STOP,
            /**
             * Pauses a print
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printpause">"printpause" API Doc</a>
             */
            PAUSE,
            /**
             * Resumes a print
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printresume">"printresume" API Doc</a>
             */
            RESUME,
            /**
             * Starts calibration process.
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printcalibration">"printcalibration" API Doc</a>
             */
            CALIBRATION,
            /**
             * Unloads the filament.
             * <p>
             * <b>Note:</b> Some printers might need gcode_file with /usr/etc/print/filament_unload.gcode instead!
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printunload_filament">"printunload_filament" API Doc</a>
             */
            UNLOAD_FILAMENT
        }

        /**
         * Tells printer to perform a filament change using AMS.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printams_change_filament">"printams_change_filament" API Doc</a>
         *
         * @param target             The ID of the filament tray.
         * @param currentTemperature The old print temperature.
         * @param targetTemperature  The new print temperature.
         */
        public static record ChangeFilamentCommand(int target, int currentTemperature,
                                                   int targetTemperature) implements Command {
        }

        /**
         * Changes the AMS settings of the given unit.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printams_user_setting">"printams_user_setting" API Doc</a>
         *
         * @param amsId             ID of filament tray
         * @param startupReadOption Old print temperature
         * @param trayReadOption    New print temperature
         */
        public static record AmsUserSettingCommand(int amsId, boolean startupReadOption,
                                                   boolean trayReadOption) implements Command {
        }

        /**
         * Changes the setting of the given filament in the given AMS.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printams_filament_setting">"printams_filament_setting" API Doc</a>
         *
         * @param amsId         Index of the AMS
         * @param trayId        Index of the tray
         * @param trayInfoIdx   Probably the setting ID of the filament profile
         * @param trayColor     Formatted as hex RRGGBBAA (alpha is always FF)
         * @param nozzleTempMin Minimum nozzle temp for filament (in C)
         * @param nozzleTempMax Maximum nozzle temp for filament (in C)
         * @param trayType      Type of filament, such as "PLA" or "ABS"
         */
        public static record AmsFilamentSettingCommand(int amsId, int trayId, String trayInfoIdx, String trayColor,
                                                       int nozzleTempMin,
                                                       int nozzleTempMax, String trayType) implements Command {
        }

        /**
         * Gives basic control commands for the AMS.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printams_control">"printams_control" API Doc</a>
         */
        public static enum AmsControlCommand implements Command {
            RESUME, RESET, PAUSE
        }

        /**
         * Set print speed to one of the 4 presets.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printprint_speed">"printprint_speed" API Doc</a>
         */
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        @EqualsAndHashCode(onlyExplicitlyIncluded = true)
        @ToString
        public static final class PrintSpeedCommand implements Command {
            public static final PrintSpeedCommand SILENT = new PrintSpeedCommand(1, "Silent");
            public static final PrintSpeedCommand STANDARD = new PrintSpeedCommand(2, "Standard");
            public static final PrintSpeedCommand SPORT = new PrintSpeedCommand(3, "Sport");
            public static final PrintSpeedCommand LUDICROUS = new PrintSpeedCommand(4, "Ludicrous");
            private static final Set<PrintSpeedCommand> VALUES = Set.of(SILENT, STANDARD, SPORT, LUDICROUS);

            @Getter
            @EqualsAndHashCode.Include
            private final int level;
            @Getter
            private final String name;

            public boolean canSend() {
                return VALUES.contains(this);
            }

            public static PrintSpeedCommand findByLevel(int level) {
                return VALUES.stream()
                        .filter(cmd -> cmd.level == level)
                        .findFirst()
                        .orElseGet(() -> new PrintSpeedCommand(level, "Unknown(%d)".formatted(level)));
            }

            public static PrintSpeedCommand findByName(String name) {
                return VALUES.stream()
                        .filter(cmd -> cmd.name.equalsIgnoreCase(name))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown name: " + name));
            }
        }

        /**
         * Print a gcode file. This takes absolute paths.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printgcode_file">"printgcode_file" API Doc</a>
         *
         * @param filename Filename (on the printer's filesystem) to print
         */
        public static record GCodeFileCommand(String filename) implements Command {
        }

        /**
         * Send raw GCode to the printer.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#printgcode_line">"printgcode_line" API Doc</a>
         *
         * @param lines  Gcode to execute
         * @param userId userId (optional)
         */
        public static record GCodeLineCommand(List<String> lines, String userId) implements Command {
        }

        /**
         * Controls the LEDs of the printer.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#systemledctrl">"systemledctrl" API Doc</a>
         *
         * @param ledNode
         * @param ledMode
         * @param ledOnTime    The below effect is only used for "flashing" mode - LED on time in ms
         * @param ledOffTime   The below effect is only used for "flashing" mode - LED off time in ms
         * @param loopTimes    The below effect is only used for "flashing" mode - How many times to loop
         * @param intervalTime The below effect is only used for "flashing" mode - Looping interval
         */
        public static record LedControlCommand(LedNode ledNode, LedMode ledMode, Integer ledOnTime, Integer ledOffTime,
                                               Integer loopTimes,
                                               Integer intervalTime) implements Command {
            public static LedControlCommand on(LedNode ledNode) {
                return new LedControlCommand(ledNode, ON, null, null, null, null);
            }

            public static LedControlCommand off(LedNode ledNode) {
                return new LedControlCommand(ledNode, OFF, null, null, null, null);
            }

            public static LedControlCommand flashing(LedNode ledNode, int ledOnTime, int ledOffTime, int loopTimes, int intervalTime) {
                return new LedControlCommand(ledNode, FLASHING, ledOnTime, ledOffTime, loopTimes, intervalTime);
            }

            public static enum LedNode {
                CHAMBER_LIGHT, WORK_LIGHT
            }

            public static enum LedMode {
                ON, OFF, FLASHING
            }
        }

        public static enum SystemCommand implements Command {
            /**
             * Gets the LAN access code of the printer
             * <p>
             * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#systemget_access_code">"systemget_access_code" API Doc</a>
             */
            GET_ACCESS_CODE
        }

        /**
         * Turns on or off creating a recording of prints.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#cameraipcam_record_set">"cameraipcam_record_set" API Doc</a>
         *
         * @param enable enable or disable camera record set
         */
        public static record IpCamRecordCommand(boolean enable) implements Command {
        }

        /**
         * Turns on or off creating a timelapse of prints.
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#cameraipcam_timelapse">"cameraipcam_timelapse" API Doc</a>
         *
         * @param enable enable or disable camera timelaps
         */
        public static record IpCamTimelapsCommand(boolean enable) implements Command {
        }

        /**
         * Configures the XCam (camera AI features, including Micro LIDAR features).
         * <p>
         * <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md#xcamxcam_control_set">"xcamxcam_control_set" API Doc</a>
         *
         * @param module
         * @param control   Enable the module
         * @param printHalt Cause the module to halt the print on error
         */
        public static record XCamControlCommand(Module module, boolean control, boolean printHalt) implements Command {
            public static enum Module {
                FIRST_LAYER_INSPECTOR, SPAGHETTI_DETECTOR
            }
        }

        /**
         * Represents a raw command that can be sent directly to the printer via MQTT.
         * This command provides a byte array as its payload.
         */
        public static non-sealed interface RawCommand extends Command {

            /**
             * Returns the MQTT topic to which the command will be published.
             * The printer's serial number will be appended to this topic automatically.
             * <p>
             * Note: The topic must not start with a forward slash (/).
             *
             * @return the base topic for the command (without leading slash)
             */
            String topic();

            /**
             * Builds the raw byte payload to be sent with this command.
             *
             * @param sequenceId the unique identifier for this message
             * @return a byte array representing the payload to send to the printer
             */
            byte[] buildRawCommand(long sequenceId);
        }

        /**
         * Represents a raw string-based command to be sent to the printer via MQTT.
         * This command generates a string payload, which will be automatically converted to bytes using UTF-8 encoding.
         */
        public static interface RawStringCommand extends RawCommand {

            /**
             * Builds the raw string payload to be sent with this command.
             *
             * @param sequenceId the unique identifier for this message (typically used for ordering or tracking)
             * @return a string representing the payload to send to the printer
             */
            String buildRawStringCommand(long sequenceId);

            /**
             * Converts the string payload returned by {@link #buildRawStringCommand(long)} to a UTF-8 encoded byte array.
             *
             * @param sequenceId the unique identifier for this message
             * @return a UTF-8 encoded byte array representing the string payload
             */
            @Override
            default byte[] buildRawCommand(long sequenceId) {
                return buildRawStringCommand(sequenceId).getBytes(UTF_8);
            }
        }
    }
}
