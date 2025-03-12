# JBambuAPI Documentation

## Installation

To use JBambuAPI in your project, add the following dependency to your build tool. Make sure to check for the latest
version at [Maven Repository](https://mvnrepository.com/artifact/pl.grzeslowski/JBambuAPI).

### Maven

```xml

<dependency>
	<groupId>pl.grzeslowski</groupId>
	<artifactId>JBambuAPI</artifactId>
	<version>LATEST_VERSION</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'pl.grzeslowski:JBambuAPI:LATEST_VERSION'
}
```

Replace `LATEST_VERSION` with the latest available version
from [Maven Repository](https://mvnrepository.com/artifact/pl.grzeslowski/JBambuAPI).

## PrinterClient

### Overview

The `PrinterClient` class is responsible for managing communication with a Bambu Labs 3D printer over MQTT. It
establishes a connection with the printer, sends commands, and subscribes to reports from the device.

### Usage Example

```java
import pl.grzeslowski.jbambuapi.*;
import org.eclipse.paho.client.mqttv3.MqttException;

public class PrinterClientExample {
    public static void main(String[] args) {
        PrinterClientConfig config = PrinterClientConfig.buildDefault("printer-host", "printer-serial", "access-code".toCharArray());
        PrinterClient printerClient = new PrinterClient(config);

        // Connect to the printer
        printerClient.connect();
        System.out.println("Connected to printer: " + printerClient.isConnected());

        // Subscribe to messages
        printerClient.subscribe((topic, data) -> {
            System.out.println("Received message on topic: " + topic);
            System.out.println("Message data: " + new String(data));
        });

        // Send a command (e.g., Start print job)
        printerClient.getChannel().sendCommand(new PrinterClient.Channel.PrintCommand(PrinterClient.Channel.PrintCommand.RESUME));

        // Disconnect after some time (for demonstration)
        Thread.sleep(5000);
        printerClient.close(); // you can also use try-catch with resources
    }
}
```

### Key Methods

- `connect()`: Establishes an MQTT connection with the printer.
- `subscribe(ChannelMessageConsumer subscriber)`: Adds a subscriber to listen for messages from the printer.
- `sendCommand(Command command)`: Sends a command to the printer.
- `close()`: Disconnects from the printer.

## PrinterWatcher

### Overview

`PrinterWatcher` listens for updates from the printer and maintains the latest `Report` state. It merges incremental
reports to keep track of the full printer status.

### Usage Example

```java
import pl.grzeslowski.jbambuapi.*;

public class PrinterWatcherExample {
    public static void main(String[] args) {
        PrinterClientConfig config = PrinterClientConfig.buildDefault("printer-host", "printer-serial", "access-code".toCharArray());
        PrinterClient printerClient = new PrinterClient(config);

        // Connect to the printer
        printerClient.connect();
        System.out.println("Connected to printer: " + printerClient.isConnected());

        // Printer Watcher
        PrinterWatcher watcher = new PrinterWatcher();
        printerClient.subscribe(watcher);

        watcher.subscribe((delta, fullState) -> {
            System.out.println("New state update received.");
            System.out.println("Delta: " + delta);
            System.out.println("Full State: " + fullState);
        });
    }
}
```

### Key Methods

- `consume(String topic, byte[] data)`: Parses incoming reports and updates the printer state.
- `subscribe(StateSubscriber subscriber)`: Registers a listener for state updates.
- `getFullStateLock()`: Returns the lock used to manage full-state access.

This documentation provides a basic understanding of JBambuAPI components and their usage. Let us know if you need
further details or refinements!

