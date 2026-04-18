public class TestProducerConfigTCCL {
  public static void main(String[] args) throws Exception {
    var isolated = new ClassLoader(null) {};
    var t = new Thread(() -> {
      try {
        Thread.currentThread().setContextClassLoader(isolated);
        var props = new java.util.Properties();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        new org.apache.kafka.clients.producer.ProducerConfig(props);
        System.out.println("OK");
      } catch (Throwable t1) {
        t1.printStackTrace();
      }
    });
    t.start();
    t.join();
  }
}
