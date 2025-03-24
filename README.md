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
import pl.grzeslowski.jbambuapi.mqtt.*;
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
import pl.grzeslowski.jbambuapi.camera.*;

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

## ConnectionCallback

To track MQTT connection events (such as connection loss and reconnection), implement the `ConnectionCallback` interface
and pass it to the `PrinterClient.connect()` method.

### Example

```java
PrinterClient client = new PrinterClient(config);
client.

connect(new ConnectionCallback() {
  @Override
  public void connectComplete ( boolean reconnect){
    if (reconnect) {
      System.out.println("Reconnected to the printer MQTT broker.");
    } else {
      System.out.println("Initial connection established.");
    }
  }

  @Override
  public void connectionLost (Throwable cause){
    System.err.println("Connection lost: " + cause.getMessage());
  }
});
```

This provides a convenient way to respond to connection changes in environments where printer availability might
fluctuate (e.g., Wi-Fi or power interruptions).

## ASeriesCamera & PSeriesCamera

### Overview

The `ASeriesCamera` and `PSeriesCamera` classes provide access to live JPEG frames from A-series and P-series Bambu Lab
printers using a TLS connection. They are built on top of `TlsCamera`, which handles secure socket communication,
authentication, and streaming.

Both classes require a `CameraConfig` object to establish the connection.

### Usage Example

```java
import pl.grzeslowski.jbambuapi.*;

import java.util.NoSuchElementException;

public class CameraExample {
    public static void main(String[] args) throws Exception {
        try (CameraConfig config = new CameraConfig(
                "192.168.1.100", // IP or hostname
                CameraConfig.DEFAULT_PORT,
                CameraConfig.LOCAL_USERNAME,
                "12345678".getBytes(), // use correct access code 
                CameraConfig.BAMBU_CERTIFICATE); // TLS certificate string 
             ASeriesCamera camera = new ASeriesCamera(config)) { // or new PSeriesCamera(config)
            assert !camera.isConnected();
            camera.connect();
            assert camera.isConnected();

            try {
                for (byte[] frame : camera) {
                    saveImage(frame);
                }
            } catch (NoSuchElementException ex) {
                // after receiving NoSuchElementException camera will not be connected anymore
                assert !camera.isConnected();
                // you can reuse camera with `connect` method
            }
        }
    }

    private static void saveImage(byte[] imageData) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String image = "frame_" + timestamp + ".jpg";
        try (FileOutputStream fos = new FileOutputStream(image)) {
            fos.write(imageData);
        }
    }
}
```

### Key Methods

- `connect()`: Connects to the camera using TLS and authenticates.
- `isConnected()`: Checks if the socket connection is active.
- `iterator()`: Returns an iterator over JPEG byte arrays (frames).
- `close()`: Closes the connection safely.

### Notes

- You must call `connect()` before iterating over frames.
- The camera connection is secured with TLS and requires proper authentication.
- If the socket breaks or the received data is corrupted, a `NoSuchElementException` will be thrown during iteration.
  Always wrap the frame reading logic in a `try-catch` block to handle this gracefully.
