package pl.grzeslowski.jbambuapi.mqtt;

public interface ChannelMessageConsumer {
    public void consume(String topic, byte[] data);
}
