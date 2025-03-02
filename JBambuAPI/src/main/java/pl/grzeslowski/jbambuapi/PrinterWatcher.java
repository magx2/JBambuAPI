package pl.grzeslowski.jbambuapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.synchronizedList;

@Slf4j
public class PrinterWatcher implements ChannelMessageConsumer, AutoCloseable {
    private final ReadWriteLock fullStateLock = new ReentrantReadWriteLock();
    private Report fullState;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final List<PrinterStateSubscriber> subscribers = synchronizedList(new LinkedList<>());

    public PrinterWatcher() {
        jsonMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void consume(String topic, byte[] data) {
        if (!topic.endsWith("/report")) {
            return;
        }

        try {
            var delta = jsonMapper.readValue(data, Report.class);
            fullStateLock.writeLock().lock();
            try {
                fullState = fullState != null ? fullState.merge(delta) : delta;
            } finally {
                fullStateLock.writeLock().unlock();
            }
            subscribers.forEach(subscriber -> {
                try {
                    subscriber.newPrinterState(delta, fullState);
                } catch (Exception e) {
                    log.warn("Consumer {} could not accept message: {}", subscriber, delta, e);
                }
            });
        } catch (IOException e) {
            if (log.isWarnEnabled()) {
                log.warn("Cannot parse JSON: {}", new String(data, UTF_8), e);
            }
        }
    }

    public ReadWriteLock getFullStateLock() {
        fullStateLock.readLock().lock();
        try {
            return fullStateLock;
        } finally {
            fullStateLock.readLock().unlock();
        }
    }

    public void subscribe(PrinterStateSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    public boolean unsubscribe(PrinterStateSubscriber subscriber) {
        var remove = subscribers.remove(subscriber);
        if (!remove) {
            log.warn("Subscriber {} was not removed! " +
                    "It either was not in the list or equals is not implemented correctly.", subscriber);
        }
        return remove;
    }

    @Override
    public void close() {

    }

    public static interface PrinterStateSubscriber {
        void newPrinterState(Report delta, Report fullState);
    }
}
