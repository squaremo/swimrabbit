import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;

import java.util.Map;
import java.io.IOException;

// MAJOR FIXME
// Do something with these exceptions (but maybe not all of them)

public class Amqp {

  class AmqpOutside implements PipelineBase.Outside {
    public void publish(byte[] body, String key, Map<String, Object> headers){
      try {
        _channel.basicPublish(_name, key, propertiesWithHeaders(headers), body);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    public void saveState(byte[] state) {
      // TODO
    }

    public byte[] retrieveState() {
      // TODO
      return new byte[0];
    }

    public void commit() {
      try {
        _channel.txCommit();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public void rollback() {
      try{
        _channel.txRollback();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    AMQP.BasicProperties propertiesWithHeaders(Map<String, Object> headers) {
      if (headers==null || headers.isEmpty()) {
        return MessageProperties.PERSISTENT_BASIC;
      }
      else {
        try {
          AMQP.BasicProperties props = (AMQP.BasicProperties)
            MessageProperties.PERSISTENT_BASIC.clone();
          props.setHeaders(headers);
          return props;
        }
        catch (CloneNotSupportedException c) {
          throw new RuntimeException(c);
        }
      }
    }
  }

  class AmqpDatum implements Datum {
    Channel _channel;
    QueueingConsumer.Delivery _delivery;

    AmqpDatum(Channel ch, QueueingConsumer.Delivery deliv) {
      _channel = ch; _delivery = deliv;
    }

    public byte[] body() {
      return _delivery.getBody();
    }

    public String key() {
      return _delivery.getEnvelope().getRoutingKey();
    }

    public void ack() {
      try{
        _channel.basicAck(_delivery.getEnvelope().getDeliveryTag(), false);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public Map<String, Object> headers() {
      return _delivery.getProperties().getHeaders();
    }
  }

  String _name;
  Connection _connection;
  Channel _channel;

  volatile boolean keepRunning = false;
  
  public Amqp(String nodeName) {
    _name = nodeName;
  }
  
  public void connect() {
    try {
      _connection = new ConnectionFactory().newConnection("localhost");
      _channel = _connection.createChannel();
      _channel.txSelect();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void disconnect() {
    stopListening();
    try {
      _channel.close();
      _connection.close();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public PipelineBase.Outside outside() {
    return new AmqpOutside();
  }

  public void stopListening() {
    keepRunning = false;
  }
  
  public void startListening(final Pipeline pipe) {
    keepRunning = true;
    final QueueingConsumer consumer = new QueueingConsumer(_channel);
    Thread listener = new Thread(new Runnable() {
        public void run() {
          while (keepRunning) {
            // FIXME any good timing out here?  Otherwise we block
            // before we can stop
            try {
              QueueingConsumer.Delivery delivery = consumer.nextDelivery(20);
              if (delivery != null) {
                Datum d = new AmqpDatum(_channel, delivery);
                pipe.handleInput(d);
              }
            }
            catch (InterruptedException ie) {
              // go 'round again
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          // FIXME race here with channel closing
          //_channel.basicCancel(consumer.getConsumerTag());
        }
      });
    listener.setDaemon(true);
    listener.setName("AMQP node listener thread " + _name);
    // TODO unhandled exception handler
    listener.start();
    try {
      _channel.basicConsume(_name, consumer);
    }
    catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
  
}
