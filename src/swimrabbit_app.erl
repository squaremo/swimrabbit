-module(swimrabbit_app).

-export([start/0]).

-rabbit_boot_step({?MODULE,
                   [{description, "SwimRabbit"},
                    {mfa, {swimrabbit_app, start, []}},
                    {requires, rabbithub},
                    {enables, networking_listening}]}).

start() ->
    application:start(rabbit_mochiweb),
    io:format("Swim Rabbit swim", []),
    {ok, Dispatch} = file:consult(filename:join(
                                    [filename:dirname(code:which(?MODULE)),
                                     "..", "priv", "dispatch.conf"])),
    application:set_env(webmachine, dispatch_list, Dispatch),
    rabbit_mochiweb:register_context_handler(
      "swim",
      fun (Req) -> webmachine_mochiweb:loop(Req) end).
