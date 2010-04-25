-module(swimrabbit_engine).

-behavior(gen_server).

-include_lib("amqp_client/include/amqp_client.hrl").

%% A gen_server that takes an Erlang module for handling
%% messages.  This is mostly an effort to try to get the (stream)
%% callback protocol right.

%% So far, the callback module is:
%% init :: args -> engine-state
%% shutdown :: engine-state -> 'ok'
%% handle :: engine-state, message, script-state ->
%%           {{'commit' | 'continue', [message], script-state}, engine-state}

-export([init/1, terminate/2]).
-export([handle_call/3, handle_cast/2, handle_info/2]).
-export([code_change/3]).

-record(engine, {node, module,
                 engine_state = undefined,
                 script_state = undefined,
                 connection, channel}).

%% Assume any queues, bindings, etc. are already present.

init([Node, Module, InitArgs]) ->
    Connection = amqp_connection:start_direct_link(),
    Channel = amqp_connection:open_channel(Connection),
    #'tx.select_ok'{} = amqp_channel:call(Channel, #'tx.select'{}),
    %% Make sure we consume from the queue once we're set up
    gen_server:cast(self(), start_consuming),
    InitState = Module:init(InitArgs),
    {ok, #engine{node = Node, module = Module,
                engine_state = InitState,
                connection = Connection, channel = Channel}}.

terminate(shutdown, #engine{module = Module,
                            engine_state = EngineState,
                            connection = Connection,
                            channel = Channel}) ->
    ok = amqp_channel:close(Channel),
    ok = amqp_connection:close(Connection),
    ok = Module:shutdown(EngineState),
    ok.

handle_cast(start_consuming,
            State =  #engine{channel = Channel, node = Node}) ->
    amqp_channel:subscribe(Channel,
                           #'basic.consume'{queue = Node},
                           self()),
    {noreply, State}.

handle_call(_Msg, _From, State) ->
    {stop, unhandled_call, State}.

handle_info(#'basic.consume_ok'{}, State) ->
    {noreply, State};
handle_info({#'basic.deliver'{ 'delivery_tag' = DeliveryTag,
                               'routing_key' = RoutingKey},
             Content},
            State = #engine{module = Module,
                            channel = Channel,
                            node = Node,
                            engine_state = EngineState,
                            script_state = ScriptState}) ->
    {{Action, Messages, NewScriptState}, NewEngineState} =
        Module:handle(EngineState, {RoutingKey, Content}, ScriptState),
    ok = publish(Channel, Node, Messages),
    ok = ackUpTo(Channel, DeliveryTag),
    case Action of
        commit   -> ok = commit(Channel, NewScriptState);
        continue -> ok
    end,
    {noreply, State#engine{script_state = NewScriptState,
                           engine_state = NewEngineState}}.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%% --- private

publish(_Channel, _Exchange, []) ->
    ok;
publish(Channel, Exchange, [{RoutingKey, Content} | Rest]) ->
    amqp_channel:cast(Channel, #'basic.publish'{
                        routing_key = RoutingKey,
                        exchange = Exchange},
                      Content),
    publish(Channel, Exchange, Rest).

ackUpTo(Channel, DeliveryTag) ->
    amqp_channel:cast(Channel, #'basic.ack'{ multiple = true,
                                             delivery_tag = DeliveryTag }),
    ok.

commit(Channel, _ScriptState) ->
    #'tx.commit_ok'{} = amqp_channel:call(Channel, #'tx.commit'{}),
    %% TODO send the script state to LVC
    ok.
