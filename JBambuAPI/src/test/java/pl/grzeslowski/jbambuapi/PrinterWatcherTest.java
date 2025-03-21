package pl.grzeslowski.jbambuapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.grzeslowski.jbambuapi.mqtt.PrinterWatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrinterWatcherTest {
    @Test
    @DisplayName("should parse PrinterState from all JSON files")
    void parse() throws IOException {
        // given
        var watcher = new PrinterWatcher();
        var files = readExampleJsonFiles();
        var subscriber = mock(PrinterWatcher.StateSubscriber.class);

        // when
        watcher.subscribe(subscriber);
        files.stream()
                .map(file -> file.getBytes(UTF_8))
                .forEach(bytes -> watcher.consume("device/123/report", bytes));

        // then
        verify(subscriber, times(files.size())).newState(any(), any());
    }

    public static ArrayList<String> readExampleJsonFiles() throws IOException {
        var jsonFiles = Files.walk(Paths.get("src/test/resources/example/A1"))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .collect(toList());
        assertThat(jsonFiles).isNotEmpty();

        var files = new ArrayList<String>(jsonFiles.size());
        for (Path path : jsonFiles) {
            files.add(Files.readString(path));
        }
        return files;
    }
}
