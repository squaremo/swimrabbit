importPackage(Packages);
var out = new DebugOutside();
var js = new JsPipeline(out);

var script = "var i = 0; function handle(msg, ops) {" +
  "ops.publish(i++, 'score');" +
  "ops.ack(msg);" + 
  "}";
var args = new java.util.HashMap();
args.put("script", script);
js.init(args);

var d = DebugOutside.DebugDatum("foo", "bar");

js.input(d);
js.input(d);
js.commit();

var js2 = new JsPipeline(out);
js2.recover(args);

js2.input(d);
js2.input(d);
js2.commit();
