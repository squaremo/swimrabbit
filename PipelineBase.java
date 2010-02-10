
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/* TODO:

   - recover from serialised state
   - look again at recover, does it need zero (init arg)
   - proper implementation of outside
   - stateless pipeline?
   - consider using other thean map for config
   - outside.publish should be byte x string x headers
 */

class Publication {
  byte[] _body;
  String _key;
  Map<String, Object> _headers;
  public Publication(byte[] body, String key, Map<String, Object> headers) {
    _body = body;
    _key = key;
    _headers = headers;
  }
}

public abstract class PipelineBase implements Pipeline {

  public interface Outside {
    void publish(byte[] body, String key, /* option */ Map<String, Object> headers);
    void saveState(byte[] state);
    /* option */ byte[] retrieveState();
    void commit();
    void rollback();
  }

  // There's a mutex on operations involving publishes and acks.
  // That's because we may, for example, commit from a different
  // thread from that on which we are responding to incoming data and
  // publishing.  It's no good using synchronizedLists, either,
  // because both acks and publishes race with commits.  (I think
  // nested synchronized blocks is asking for deadlocks.  I may yet
  // eat my words.)
  private final Object mutex = new Object();
  
  List<Publication> _publishes = new ArrayList<Publication>();
  List<Datum> _acks = new ArrayList<Datum>();
  /* final */
  Outside _outside;

  // Force subclasses to either accept an outside, or supply one.
  public PipelineBase(Outside outside) {
    _outside = outside;
  }
  
  // These should be considered protected final They're not serialised
  // on the understanding that that done in the public/subclass interface.
  void doAllPublishes() {
    for (Publication p: _publishes) {
      _outside.publish(p._body, p._key, p._headers);
    }
  }

  void doAllAcks() {
    for (Datum d: _acks) {
      d.ack();
    }
  }

  void reset() {
    _publishes.clear();
    _acks.clear();
  }

  // These will probably be overridden -- protected
  
  void doCommit() {
    _outside.commit();
  }

  void doRollback() {
    _outside.rollback();
  }
  
  public void handleInput(Datum in) {
    input(in);
  }

  /* API mainly for subclasses to expose to other things (e.g.,
   * scripts) */
  
  public void commit() {
    synchronized(mutex) {
      doAllAcks();
      doAllPublishes();
      doCommit();
      reset();
    }
  }

  public void rollback() {
    synchronized(mutex) {
      doRollback();
      reset();
    }
  }

  // We don't technically need to serialize acks /with/ publishes,
  // but we do need to have one mutex for operations above, so it's
  // simpler.
  public void ack(Datum d) {
    synchronized(mutex) {
      _acks.add(d);
    }
  }
  
  public void publish(byte[] body, String key, Map<String, Object> headers) {
    synchronized(mutex) {
      _publishes.add(new Publication(body, key, headers));
    }
  }
  public final void publish(Datum in, String key) {
    publish(in.body(), key, in.headers());
  }
  public final void publish(byte[] body, String key) {
    publish(body, key, null);
  }
  public final void publish(byte[] body) {
    publish(body, "", null);
  }
  
  /** Override to initialise the pipeline based on configuration parameters */
  abstract public void init(Map<String, Object> zero);
  public void recover(Map<String, Object> zero) {
    init(zero);
  }

  /** Override to provide the pipeline program */
  abstract public void input(Datum in);
  
}
