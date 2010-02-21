-module(swimrabbit_driver_inline).

-behavior(gen_server).

-include_lib("amqp_client/include/amqp_client.hrl").

%% Implements a wrapper that takes an Erlang procedure for handling
%% messages.  This is mostly an effort to try to get the (stream)
%% callback protocol right.

-export([init/1, terminate/2]).
-export([handle_call/3, handle_cast/2, handle_info/2]).
-export([code_change/3]).

-record(stream, {node, function, state = undefined, connection, channel}).

%% Presume any queues, bindings, etc. are already present.

init([Node, Function]) ->
    Connection = amqp_connection:start_direct_link(),
    Channel = amqp_connection:open_channel(Connection),
    #'tx.select_ok'{} = amqp_channel:call(Channel, #'tx.select'{}),
    %% Make sure we consume from the queue once we're set up
    gen_server:cast(self(), start_consuming),
    {ok, #stream{node = Node, function = Function,
                 connection = Connection, channel = Channel}}.

terminate(shutdown, #stream{connection = Connection,
                            channel = Channel}) ->
    ok = amqp_channel:close(Channel),
    ok = amqp_connection:close(Connection),
    ok.

handle_cast(start_consuming,
            State =  #stream{channel = Channel, node = Node}) ->
    amqp_channel:subscribe(Channel,
                           #'basic.consume'{queue = Node},
                           self()),
    {noreply, State}.

handle_call(_Msg, _From, State) ->
    {stop, unhandled_call, State}.

handle_info(#'basic.consume_ok'{}, State) ->
    {noreply, State};
handle_info({#'basic.deliver'{ 'delivery_tag' = DeliveryTag }, Content},
           State = #stream{function = Function,
                           channel = Channel,
                           node = Node,
                           state = S}) ->
    {Action, Messages, NewS} = Function(Content, State),
    ok = publish(Channel, Node, Messages),
    ok = ackUpTo(Channel, DeliveryTag),
    case Action of
        commit   -> ok = commit(Channel);
        continue -> ok
    end,
    {noreply, State#stream{state = NewS}}.

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

commit(Channel) ->
    #'tx.commit_ok'{} = amqp_channel:call(Channel, #'tx.commit'{}),
    ok.
