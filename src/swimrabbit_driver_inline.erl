-module(swimrabbit_driver_inline).

%% Implements a driver that takes an Erlang procedure for handling
%% messages.

-export([init/1, shutdown/1, handle/3]).

init([Function]) ->
    {ok, Function}.

shutdown(_Function) ->
    ok.

handle(Function, Msg, ScriptState) ->
    {Function(ScriptState, Msg), Function}.
