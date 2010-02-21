import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public abstract class StatefulPipeline<State> extends PipelineBase {

  public StatefulPipeline(Outside out) {
    super(out);
  }
  
  /* private */
  State _rollbackState;

  // We just need extra stuff in PipelineBase methods
  
  protected void doCommit() {
    saveState();
    super.doCommit();
  }

  protected void doRollback() {
    super.doRollback();
    setState(_rollbackState);
  }

  /* final -- override initialState instead */
  public void init(Map<String, Object> zero) {
    State initial = initialState(zero);
    _rollbackState = initial;
  }

  // FIXME this should be some other kind of exception
  public void recover(Map<String, Object> args) {
    byte[] ser = _outside.retrieveState();
    if (ser != null) {
      try {
        State s = deserialiseState(ser);
        _rollbackState = s;
        setState(s);
      }
      catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
    else {
      init(args);
    }
  }
  
  // Helpers
  
  public void saveState() {
    State current = getCopyOfState();
    try {
      _outside.saveState(serialiseState(current));
    }
    catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    _rollbackState = current;
  }

  // getting very gen_server ..
  abstract State initialState(Map<String, Object> args);
  abstract State getCopyOfState();
  abstract void setState(State s);
  
  abstract byte[] serialiseState(State current) throws IOException;
  abstract State deserialiseState(byte[] ser) throws IOException;
  
}
