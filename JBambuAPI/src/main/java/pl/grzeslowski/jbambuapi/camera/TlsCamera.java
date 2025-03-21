package pl.grzeslowski.jbambuapi.camera;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * This object supports camera for A-series and P-series (without X-series)
 * <p>
 * Documentation: <a href="https://github.com/Doridian/OpenBambuAPI/blob/main/video.md#a1-and-p1">video.md</a>
 */
public abstract class TlsCamera implements AutoCloseable, Iterable<byte[]> {
    public static final byte START_MAGIC_NUMBER = (byte) 0xFF;
    public static final byte END_MAGIC_NUMBER = (byte) 0xD8;
    private final Logger log;
    private final CameraConfig cameraConfig;
    private Socket socket;
    private final ReadWriteLock socketLock = new ReentrantReadWriteLock();

    public TlsCamera(CameraConfig cameraConfig) {
        log = LoggerFactory.getLogger(getClass() + "." + cameraConfig.host().replaceAll("\\.", "_"));
        this.cameraConfig = cameraConfig;
    }

    public void connect() throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        socketLock.writeLock().lock();
        try {
            if (socket != null) {
                throw new IllegalStateException("Socket already created");
            }
            log.debug("Connecting to {}:{}", cameraConfig.host(), cameraConfig.port());
            if (cameraConfig.certificate() != null) {
                log.debug("Creating socket with given TLS certificate");
                socket = createSSLContext()
                        .getSocketFactory()
                        .createSocket(cameraConfig.host(), cameraConfig.port());
            } else {
                log.debug("Creating socket with TLS certificate");
                socket = new Socket(cameraConfig.host(), cameraConfig.port());
            }
            authenticate(socket.getOutputStream());
        } finally {
            socketLock.writeLock().unlock();
        }
    }

    public boolean isConnected() {
        socketLock.readLock().lock();
        try {
            return socket != null;
        } finally {
            socketLock.readLock().unlock();
        }
    }

    public SSLContext createSSLContext() throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, KeyManagementException {
        var certFactory = CertificateFactory.getInstance("X.509");
        var rawCert = cameraConfig.certificate().replaceAll("-----BEGIN CERTIFICATE-----|-----END CERTIFICATE-----|\\s", "");
        var certInput = new ByteArrayInputStream(
                Base64.getMimeDecoder().decode(rawCert)
        );
        var certificate = certFactory.generateCertificate(certInput);

        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("bambu_cert", certificate);

        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    private void authenticate(OutputStream out) throws IOException {
        log.debug("Authenticating");
        var buffer = ByteBuffer.allocate(80).order(LITTLE_ENDIAN);
        buffer.putInt(0x40); // Payload size
        buffer.putInt(0x3000); // Type
        buffer.putInt(0); // Flags
        buffer.putInt(0); // Reserved

        var usernameBytes = Arrays.copyOf(cameraConfig.username().getBytes(US_ASCII), 32);
        var passwordBytes = Arrays.copyOf(cameraConfig.accessCode(), 32);

        buffer.put(usernameBytes);
        buffer.put(passwordBytes);

        out.write(buffer.array());
        out.flush();
        log.debug("Authentication packet sent");
    }

    @Override
    public Iterator<byte[]> iterator() {
        socketLock.readLock().lock();
        try {
            if (socket == null) {
                throw new IllegalStateException("Camera is not connected! Please use `connect` method before!");
            }
            return new ImageIterator(new DataInputStream(socket.getInputStream()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            socketLock.readLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        socketLock.writeLock().lock();
        try {
            var localSocket = socket;
            socket = null;
            if (localSocket != null) {
                localSocket.close();
            }
        } finally {
            socketLock.writeLock().unlock();
        }
    }

    @RequiredArgsConstructor
    private class ImageIterator implements Iterator<byte[]> {
        private final DataInputStream in;

        @Override
        public boolean hasNext() {
            return TlsCamera.this.isConnected();
        }

        @Override
        public byte[] next() {
            socketLock.readLock().lock();
            try {
                return internalNext();
            } catch (Exception e) {
                try {
                    TlsCamera.this.close();
                } catch (Exception ex) {
                    log.error("Error while closing {}", getClass().getSimpleName(), ex);
                }
                if (e instanceof NoSuchElementException nse) {
                    throw nse;
                }
                throw new NoSuchElementException(e);
            } finally {
                socketLock.readLock().unlock();
            }
        }

        private byte[] internalNext() throws IOException {
            log.debug("Reading next image...");
            byte[] header = new byte[16];
            in.readFully(header);

            var buffer = ByteBuffer.wrap(header).order(LITTLE_ENDIAN);
            int payloadSize = buffer.getInt();
            int itrack = buffer.getInt();
            int flags = buffer.getInt();
            int reserved = buffer.getInt();

            if (payloadSize <= 0) {
                throw new NoSuchElementException("There is no payload! Breaking connection!");
            }

            var imageData = new byte[payloadSize];
            var bytesRead = 0;
            while (bytesRead < payloadSize) {
                int result = in.read(imageData, bytesRead, payloadSize - bytesRead);
                if (result == -1) {
                    log.error("End of stream reached. Breaking connection!");
                    break;
                }
                bytesRead += result;
            }

            if (imageData.length <= 2
                    || imageData[0] != START_MAGIC_NUMBER
                    || imageData[1] != END_MAGIC_NUMBER) {
                throw new NoSuchElementException("Invalid image data.");
            }

            log.debug("JPEG image received, size: " + imageData.length);
            return imageData;
        }
    }
}
