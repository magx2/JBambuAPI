package pl.grzeslowski.jbambuapi;

public interface ChannelMessageConsumer {
    public void consume(String topic, byte[] data);
}
