package pl.grzeslowski.jbambuapi;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.grzeslowski.jbambuapi.PrinterClient.Channel.LedControl;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static pl.grzeslowski.jbambuapi.PrinterClient.Channel.LedControl.LedNode.WORK_LIGHT;
import static pl.grzeslowski.jbambuapi.PrinterClient.Channel.PushingCommand.defaultPushingCommand;
import static pl.grzeslowski.jbambuapi.PrinterClientConfig.DEFAULT_PORT;
import static pl.grzeslowski.jbambuapi.PrinterClientConfig.SCHEME;

@Slf4j
class LocalTest {
    @Test
    @DisplayName("should ")
    void x() throws NoSuchAlgorithmException, KeyManagementException, InterruptedException, URISyntaxException {
        // given
        var host = requireNonNull(System.getenv("HOST"), "Please pass HOST");
        var serial = requireNonNull(System.getenv("SERIAL"), "Please pass SERIAL");
        var accessCode = requireNonNull(System.getenv("ACCESS_CODE"), "Please pass ACCESS_CODE");
        var username = requireNonNull(System.getenv("USER"), "Please pass USER");

        var config = PrinterClientConfig.requiredFields(
                new URI(SCHEME + host + ":" + DEFAULT_PORT),
                username,
                serial,
                accessCode.toCharArray());
        var printerClient = new PrinterClient(config);

        // when
        printerClient.connect();
        printerClient.subscribe((topic, data) ->
                log.info(topic + ": " + new String(data, UTF_8)));
        var channel = printerClient.getChannel();
        channel.sendCommand(defaultPushingCommand());
        channel.sendCommand(LedControl.off(WORK_LIGHT));

        Thread.sleep(5000);

        // then
        printerClient.close();
    }
}
