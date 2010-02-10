importPackage(Packages);
importPackage(Packages.com.rabbitmq.client);

var conn = ConnectionFactory().newConnection("localhost");
var ch = conn.createChannel();

// For our pipeline
ch.queueDeclare("js");
ch.exchangeDeclare("js", "fanout");

var amqp = new Amqp("js");

var script =
  "function handle(msg, ops) {" +
  "  ops.publish('foo', 'bar');" +
  "  ops.ack(msg);" +
  "}";

var js = JsPipeline(amqp.outside());
var args = new java.util.HashMap();
args.put("script", script);
js.init(args);
amqp.connect();
amqp.startListening(js);

// Now, to insert and retrieve messages
ch.queueDeclare("out");
ch.exchangeDeclare("in", "fanout");
ch.queueBind("out", "js", "");
ch.queueBind("js", "in", "");

// (X)in -> [|||]foobar -> JS -> (X)foobar -> [|||]out

var body = new Packages.java.lang.String("ooop").getBytes();
ch.basicPublish("in", "", MessageProperties.BASIC, body);
js.commit();

var res = ch.basicGet("out", true);
print(new Packages.java.lang.String(res.getBody()));
