-module(swimrabbit_driver_js).

%% A driver that runs messages through a JavaScript engine.

-export([init/1, shutdown/1, handle/3]).

%% for trying out ..
%-export([translate_result/1]).

-include_lib("amqp_client/include/amqp_client.hrl").

init([Script]) when is_binary(Script) ->
    true = js_driver:load_driver(),
    {ok, Js} = js_driver:new(),
    js:define(Js, Script),
    {ok, Js}.

shutdown(Js) ->
    js_driver:destroy(Js),
    ok.

handle(Js, {RK, Content}, ScriptState) ->
    JsMsg = translate_delivery(RK, Content),
    error_logger:info_msg("JsMsg: ~p", [JsMsg]),
    {ok, JsResult} = js:call(Js, <<"handle">>, [ScriptState, JsMsg]),
    Result = translate_result(JsResult),
    error_logger:info_msg("Result: ~p", [Result]),
    {Result, Js}.

%% --

%% Taken almost verbatim from erlang-rfc4627:get_field
struct_value(Struct, Key) when is_atom(Key) ->
    struct_value(Struct, list_to_binary(atom_to_list(Key)));
struct_value({struct, Props}, Key) when is_binary(Key) ->
    case lists:keysearch(Key, 1, Props) of
        {value, {_K, Val}} ->
            {ok, Val};
        false ->
            not_found
    end.

struct_value(Struct, Key, Default) ->
    case struct_value(Struct, Key) of
        {ok, Val} -> Val; 
        not_found    -> Default
    end.

translate_result(JsResult) ->
    Action = case struct_value(JsResult, action, <<"continue">>) of
                 <<"continue">> -> continue;
                 <<"commit">>   -> commit
             end,
    Messages = [translate_message(M) ||
                   M <- struct_value(JsResult, messages, [])],
    ScriptState = struct_value(JsResult, state, {struct, []}),
    {Action, Messages, ScriptState}.

%% JS messages look like
%% {"routing_key": key, "properties": {...}, "body": "..."}
translate_message(JsMessage) ->
    RK = struct_value(JsMessage, routing_key, <<"">>),
    JsProperties = struct_value(JsMessage, properties, {struct, []}),
    Body = struct_value(JsMessage, body, <<"">>),
    Properties = translate_properties(JsProperties),
    {RK, #amqp_msg{ props = Properties,
                    payload = Body }}.

translate_delivery(RoutingKey,
                   #amqp_msg{ props = Properties,
                              payload = Payload }) ->
    {struct, [{<<"body">>, Payload},
              {<<"routing_key">>, RoutingKey},
              {<<"properties">>,
               from_record(Properties, record_info(fields, 'P_basic'))}]}.

%% These copied from erlang-rfc4627; it is a pain that mochijson2
%% doesn't have them, or alternatively, that erlang_js doesn't use
%% erlang-rfc4627.

to_record({struct, Values}, Fallback, Fields) ->
    list_to_tuple([element(1, Fallback) | decode_record_fields(Values, Fallback, 2, Fields)]).

decode_record_fields(_Values, _Fallback, _Index, []) ->
    [];
decode_record_fields(Values, Fallback, Index, [Field | Rest]) ->
    [case lists:keysearch(list_to_binary(atom_to_list(Field)), 1, Values) of
	 {value, {_, Value}} ->
	     Value;
	 false ->
	     element(Index, Fallback)
     end | decode_record_fields(Values, Fallback, Index + 1, Rest)].

from_record(R, Fields) ->
    {struct, encode_record_fields(R, 2, Fields)}.

encode_record_fields(_R, _Index, []) ->
    [];
encode_record_fields(R, Index, [Field | Rest]) ->
    case element(Index, R) of
	undefined ->
	    encode_record_fields(R, Index + 1, Rest);
	Value ->
	    [{list_to_binary(atom_to_list(Field)), Value} | encode_record_fields(R, Index + 1, Rest)]
    end.

translate_properties(Props) ->
    to_record(Props, #'P_basic'{}, record_info(fields, 'P_basic')).
