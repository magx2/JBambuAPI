package pl.grzeslowski.jbambuapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.util.ReflectionUtils;
import pl.grzeslowski.jbambuapi.mqtt.PrinterWatcher;
import pl.grzeslowski.jbambuapi.mqtt.Report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
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

    // Consuming a valid report message updates the fullState with merged data
    @Test
    @DisplayName("Should update fullState with merged data when consuming a valid report message")
    public void test_consuming_valid_report_message_updates_full_state() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var report = new Report(new Report.Info("test", null, null, null, null), null);
        var reportJson = new ObjectMapper().writeValueAsBytes(report);

        // When
        printerWatcher.consume("device/topic/report", reportJson);

        // Then
        var fullStateLock = printerWatcher.getFullStateLock();
        fullStateLock.readLock().lock();
        try {
            var field = PrinterWatcher.class.getDeclaredField("fullState");
            field.setAccessible(true);
            var fullState = (Report) field.get(printerWatcher);
            assertThat(fullState).isNotNull();
            assertThat(fullState.info().command()).isEqualTo("test");
        } finally {
            fullStateLock.readLock().unlock();
        }
    }

    // Subscribing a new StateSubscriber adds it to the subscribers list
    @Test
    @DisplayName("Should add new StateSubscriber to the subscribers list")
    public void test_subscribing_new_state_subscriber_adds_to_list() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var subscriber = mock(PrinterWatcher.StateSubscriber.class);

        // When
        printerWatcher.subscribe(subscriber);

        // Then
        var field = PrinterWatcher.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        var subscribers = (List<PrinterWatcher.StateSubscriber>) field.get(printerWatcher);
        assertThat(subscribers).contains(subscriber);
    }

    // Unsubscribing an existing StateSubscriber removes it from the list and returns true
    @Test
    @DisplayName("Should remove existing StateSubscriber from list and return true")
    public void test_unsubscribing_existing_subscriber_removes_from_list() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var subscriber = mock(PrinterWatcher.StateSubscriber.class);
        printerWatcher.subscribe(subscriber);

        // When
        var result = printerWatcher.unsubscribe(subscriber);

        // Then
        assertThat(result).isTrue();
        var field = PrinterWatcher.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        var subscribers = (List<PrinterWatcher.StateSubscriber>) field.get(printerWatcher);
        assertThat(subscribers).doesNotContain(subscriber);
    }

    // All subscribers are notified when a new state is received
    @Test
    @DisplayName("Should notify all subscribers when a new state is received")
    public void test_all_subscribers_are_notified_when_new_state_received() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var subscriber1 = mock(PrinterWatcher.StateSubscriber.class);
        var subscriber2 = mock(PrinterWatcher.StateSubscriber.class);
        printerWatcher.subscribe(subscriber1);
        printerWatcher.subscribe(subscriber2);

        var report = new Report(new Report.Info("test", null, null, null, null), null);
        var reportJson = new ObjectMapper().writeValueAsBytes(report);

        // When
        printerWatcher.consume("device/topic/report", reportJson);

        // Then
        verify(subscriber1).newState(any(Report.class), any(Report.class));
        verify(subscriber2).newState(any(Report.class), any(Report.class));
    }

    // Closing the PrinterWatcher clears all subscribers and resets fullState to null
    @Test
    @DisplayName("Should clear all subscribers and reset fullState to null when closed")
    public void test_closing_printer_watcher_clears_subscribers_and_resets_state() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var subscriber = mock(PrinterWatcher.StateSubscriber.class);
        printerWatcher.subscribe(subscriber);

        var report = new Report(new Report.Info("test", null, null, null, null), null);
        var reportJson = new ObjectMapper().writeValueAsBytes(report);
        printerWatcher.consume("device/topic/report", reportJson);

        // When
        printerWatcher.close();

        // Then
        var subscribersField = PrinterWatcher.class.getDeclaredField("subscribers");
        subscribersField.setAccessible(true);
        var subscribers = (List<PrinterWatcher.StateSubscriber>) subscribersField.get(printerWatcher);
        assertThat(subscribers).isEmpty();

        var fullStateField = PrinterWatcher.class.getDeclaredField("fullState");
        fullStateField.setAccessible(true);
        var fullState = fullStateField.get(printerWatcher);
        assertThat(fullState).isNull();
    }

    // Getting the fullStateLock returns the lock while properly handling read lock acquisition and release
    @SuppressWarnings("DataFlowIssue")
    @Test
    @DisplayName("Should handle read lock properly when getting fullStateLock")
    public void test_getting_full_state_lock_handles_read_lock_properly() {
        // Given
        var printerWatcher = new PrinterWatcher();
        var lockField = ReflectionUtils.findField(PrinterWatcher.class, "fullStateLock");
        ReflectionUtils.makeAccessible(lockField);
        var lock = (ReadWriteLock) ReflectionUtils.getField(lockField, printerWatcher);
        var readLock = spy(lock.readLock());

        var mockLock = mock(ReadWriteLock.class);
        when(mockLock.readLock()).thenReturn(readLock);
        ReflectionUtils.setField(lockField, printerWatcher, mockLock);

        // When
        var returnedLock = printerWatcher.getFullStateLock();

        // Then
        assertThat(returnedLock).isSameAs(mockLock);
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    // Consuming a message with a topic not ending with "/report" is ignored
    @Test
    @DisplayName("Should ignore message when topic doesn't end with /report")
    public void test_consuming_message_with_wrong_topic_is_ignored() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var report = new Report(new Report.Info("test", null, null, null, null), null);
        var reportJson = new ObjectMapper().writeValueAsBytes(report);
        var subscriber = mock(PrinterWatcher.StateSubscriber.class);
        printerWatcher.subscribe(subscriber);

        // When
        printerWatcher.consume("device/topic/not-report", reportJson);

        // Then
        verify(subscriber, never()).newState(any(), any());

        var fullStateField = PrinterWatcher.class.getDeclaredField("fullState");
        fullStateField.setAccessible(true);
        var fullState = fullStateField.get(printerWatcher);
        assertThat(fullState).isNull();
    }

    // When a subscriber throws an exception during notification, other subscribers still receive updates
    @Test
    @DisplayName("Should continue notifying other subscribers when one throws exception")
    public void test_when_subscriber_throws_exception_others_still_receive_updates() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var badSubscriber = mock(PrinterWatcher.StateSubscriber.class);
        var goodSubscriber = mock(PrinterWatcher.StateSubscriber.class);

        doThrow(new RuntimeException("Test exception")).when(badSubscriber).newState(any(), any());

        printerWatcher.subscribe(badSubscriber);
        printerWatcher.subscribe(goodSubscriber);

        var report = new Report(new Report.Info("test", null, null, null, null), null);
        var reportJson = new ObjectMapper().writeValueAsBytes(report);

        // When
        printerWatcher.consume("device/topic/report", reportJson);

        // Then
        verify(badSubscriber).newState(any(), any());
        verify(goodSubscriber).newState(any(), any());
    }

    // When fullState is null and first report is received, delta becomes the fullState
    @Test
    @DisplayName("Should set delta as fullState when fullState is null and first report is received")
    public void test_when_full_state_is_null_delta_becomes_full_state() throws Exception {
        // Given
        var printerWatcher = new PrinterWatcher();
        var report = new Report(new Report.Info("test", null, null, null, null), null);
        var reportJson = new ObjectMapper().writeValueAsBytes(report);

        // When
        printerWatcher.consume("device/topic/report", reportJson);

        // Then
        var fullStateField = PrinterWatcher.class.getDeclaredField("fullState");
        fullStateField.setAccessible(true);
        var fullState = (Report) fullStateField.get(printerWatcher);

        assertThat(fullState).isNotNull();
        assertThat(fullState).isEqualTo(report);
    }
}
