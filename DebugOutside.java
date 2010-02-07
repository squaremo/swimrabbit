import java.util.Map;

public class DebugOutside implements PipelineBase.Outside {

  static int _datum = 0;
  public static class DebugDatum implements Datum {
    byte[] _body; String _key; int _ser;
    public DebugDatum(String body, String key) {
      _body = body.getBytes();
      _key = key;
      _ser = _datum++;
    }

    public String key() { return _key; }
    public byte[] body() { return _body; }
    public Map<String, Object> headers() { return null; }
    public void ack() {
      System.out.println("ack: " + String.valueOf(_ser));
    }
  }

  public void publish(byte[] body, String key, Map<String, Object> headers) {
    System.out.println("publish to key \"" + key + "\" (" +
                       ((headers==null) ? "null" : headers.toString()) + "):");
    System.out.println(new String(body));
  }

  byte[] _state = null;
  public void saveState(byte[] state) {
    _state = state;
  }

  public byte[] retrieveState() {
    return _state;
  }
  
  public void commit() {
    System.out.println("commit.");
  }

  public void rollback() {
    System.out.println("rollback.");
  }
  
}
