import java.io.IOException;
import java.util.Map;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.serialize.ScriptableOutputStream;
import org.mozilla.javascript.serialize.ScriptableInputStream;

public class JsPipeline extends StatefulPipeline<Scriptable> {

  public JsPipeline(Outside out) {
    super(out);
  }

  public class Ops {
    public void commit() { JsPipeline.this.commit(); }
    public void publish(String msg, String key) {
      JsPipeline.this.publish(msg.getBytes(), key);
    }
    public void ack(Datum d) {
      JsPipeline.this.ack(d);
    }
  }
  
  Scriptable _topLevel;
  Scriptable _global;
  Ops _ops = new Ops();

  Scriptable getCopyOfState() {
    // there's no clone anywhere in Rhino, so
    // we serialise/deserialise as a correct but not great
    // substitute
    try {
      return deserialiseState(serialiseState(_topLevel));
    }
    catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  void setState(Scriptable newState) { _topLevel = newState; }

  byte[] serialiseState(Scriptable s) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ScriptableOutputStream sout = new ScriptableOutputStream(out, _global);
    sout.writeObject(_topLevel);
    sout.close();
    return out.toByteArray();
  }
  
  Scriptable deserialiseState(byte[] ser) throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(ser);
    ScriptableInputStream sin = new ScriptableInputStream(in, _global);
    try {
      Scriptable s = (Scriptable)sin.readObject();
      return s;
    }
    catch (ClassNotFoundException cnfe) {
      throw new RuntimeException(cnfe);
    }
    finally {
      sin.close();
    }
  }
  
  public Scriptable initialState(Map<String, Object> zero) {
    Context context = ContextFactory.getGlobal().enterContext();
    // Always interpret, since otherwise we have difficulty serialising.
    context.setOptimizationLevel(-1);
    // this arrangement means _global isn't mutable, and
    // variables go in _topLevel.
    ScriptableObject global = context.initStandardObjects(null, true);
    global.sealObject();
    _global = global;
    _topLevel = context.newObject(_global);
    _topLevel.setPrototype(_global);
    _topLevel.setParentScope(null);
    // TODO complain if it's not there
    String script = zero.get("script").toString();
    context.evaluateString(_topLevel, script, "<script>", 1, null);
    context.exit();
    return _topLevel;
  }

  public void recover(Map<String, Object> zero) {
    // as an approximation; better factored out
    initialState(zero);
    super.recover(zero);
  }
  
  public void input(Datum in) {
    Context c = ContextFactory.getGlobal().enterContext();
    Object maybeFunc = _topLevel.get("handle", _topLevel);
    if (Scriptable.NOT_FOUND == maybeFunc ||
        ! (maybeFunc instanceof Function)) {
      // TODO better exception
      throw new RuntimeException("No handle function");
    }
    Function func = (Function)maybeFunc;
    Object[] args = new Object[2];
    args[0] = in; args[1] = _ops;
    Object result = func.call(c, _topLevel, _topLevel, args);
    // There's a couple of ways this interface could work: commit as a
    // side-effect, and commit as a return value.  We use side-effect
    // for now, so the return value is ignored.
  }
  
}
