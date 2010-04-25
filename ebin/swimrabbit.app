{application, swimrabbit,
 [{description, ""},
  {vsn, "0.01"},
  {modules, [
    swimrabbit_driver_inline,
    swimrabbit_engine,
    swimrabbit_driver_js
  ]},
  {registered, []},
  {env, []},
  {applications, [kernel, stdlib, rabbit, amqp_client, erlang_js]}]}.
