import java.util.Map;

interface Datum {
  void ack();
  byte[] body();
  String key();
  Map<String, Object> headers();
}
