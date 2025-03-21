package pl.grzeslowski.jbambuapi;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClient.Channel.PushingCommand.defaultPushingCommand;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClientConfig.DEFAULT_PORT;
import static pl.grzeslowski.jbambuapi.mqtt.PrinterClientConfig.SCHEME;

@Slf4j
class LocalTest {

    @Test
    @DisplayName("should ")
    @Disabled
    void x() throws NoSuchAlgorithmException, KeyManagementException, InterruptedException, URISyntaxException {
        // given
        var config = buildConfig();
        var printerClient = new PrinterClient(config);

        // when
        printerClient.connect();
        printerClient.subscribe((topic, data) ->
                log.info(topic + ": " + new String(data, UTF_8)));

        var printerWatcher = new PrinterWatcher();
        printerClient.subscribe(printerWatcher);
        printerWatcher.subscribe((__, fullState) -> log.info("Full State: " + fullState));

        var channel = printerClient.getChannel();
        channel.sendCommand(defaultPushingCommand());

        Thread.sleep(5000);

        // then
        printerClient.close();
    }

    @Test
    @DisplayName("should ")
    @Disabled
    void y() throws NoSuchAlgorithmException, KeyManagementException, InterruptedException, URISyntaxException {
        // given
        var config = buildConfig();
        var printerClient = new PrinterClient(config);

        // when
        printerClient.connect();
        printerClient.subscribe(this::saveMessage);

        var channel = printerClient.getChannel();
        channel.sendCommand(defaultPushingCommand());

        TimeUnit.MINUTES.sleep(60);

        // then
        printerClient.close();
    }

    private void saveMessage(String topic, byte[] bytes) {
        log.info("Saving message from topic {}", topic);

        // Ensure the directory exists
        Path directory = Paths.get("src/test/resources/example/A1/");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Replace disallowed characters in the topic
        String safeTopic = topic.replaceAll("[^a-zA-Z0-9_-]", "_");

        // Generate the filename
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        var filename = String.format("%s---%s.json", timestamp, safeTopic);
        var filePath = directory.resolve(filename);

        // Save the file
        try {
            Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save message: {}", e.getMessage(), e);
        }
    }

    private static PrinterClientConfig buildConfig() throws URISyntaxException {
        var host = requireNonNull(System.getenv("HOST"), "Please pass HOST");
        var serial = requireNonNull(System.getenv("SERIAL"), "Please pass SERIAL");
        var accessCode = requireNonNull(System.getenv("ACCESS_CODE"), "Please pass ACCESS_CODE");
        var username = requireNonNull(System.getenv("USER"), "Please pass USER");

        return PrinterClientConfig.requiredFields(
                new URI(SCHEME + host + ":" + DEFAULT_PORT),
                username,
                serial,
                accessCode.toCharArray());
    }
}
