package pl.grzeslowski.jbambuapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import static java.util.Objects.requireNonNull;

class LocalTest {
    @Test
    @DisplayName("should ")
    void x() throws NoSuchAlgorithmException, KeyManagementException, InterruptedException {
        // given
        var ip = requireNonNull(System.getenv("IP"), "Please pass IP");
        var serial = requireNonNull(System.getenv("SERIAL"), "Please pass SERIAL");
        var accessCode = requireNonNull(System.getenv("ACCESS_CODE"), "Please pass ACCESS_CODE");

        var config = PrinterClientConfig.buildDefault(ip, serial, accessCode.toCharArray());
        var printerClient = new PrinterClient(config);

        // when
        printerClient.connect();
        printerClient.update();
        Thread.sleep(5_000);
        var idx = 0;
        var lastState =printerClient.getFullState();
        while(idx++ < 10_000 && lastState.isEmpty()) {
            System.out.println("lastState: " + lastState);
            Thread.sleep(1_000);
            lastState  = printerClient.getFullState();
        }
        System.out.println(lastState.get());

        // then
        printerClient.close();
    }
}
