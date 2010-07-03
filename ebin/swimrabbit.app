{application, swimrabbit,
 [{description, "swim rabbit swim"},
  {vsn, "0.01"},
  {modules, [
    swimrabbit_driver_inline,
    swimrabbit_engine,
    swimrabbit_driver_js,
    swimrabbit_app,
    swimrabbit_sup,
    swimrabbit_rsrc_root
  ]},
  {registered, []},
  {env, []},
  {applications, [kernel, stdlib, rabbit, amqp_client,
                  erlang_js, rabbit_mochiweb]}]}.
