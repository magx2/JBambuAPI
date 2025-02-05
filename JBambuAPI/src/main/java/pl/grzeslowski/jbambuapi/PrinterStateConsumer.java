package pl.grzeslowski.jbambuapi;

public interface PrinterStateConsumer {
    public void consumer(PrinterState delta, PrinterState full);
}
