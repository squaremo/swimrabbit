{application, swimrabbit,
 [{description, ""},
  {vsn, "0.01"},
  {modules, [
    swimrabbit_driver_inline
  ]},
  {registered, []},
  {env, []},
  {applications, [kernel, stdlib, rabbit, amqp_client]}]}.
