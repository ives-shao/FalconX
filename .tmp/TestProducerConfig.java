public class TestProducerConfig {
  public static void main(String[] args) {
    try {
      System.out.println(org.apache.kafka.clients.producer.ProducerConfig.class.getName());
      var props = new java.util.Properties();
      props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
      props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
      props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
      var cfg = new org.apache.kafka.clients.producer.ProducerConfig(props);
      System.out.println(cfg.originals());
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
