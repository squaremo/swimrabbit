-module(swimrabbit_rsrc_root).

-export([init/1, content_types_provided/2]).

-export([to_html/2]).

-include_lib("webmachine/include/webmachine.hrl").

init(_) ->
    {ok, none}.

content_types_provided(Req, Ctx) ->
    {[{"text/html", to_html}], Req, Ctx}.

to_html(Req, Ctx) ->
    {"Swim Rabbit swim!", Req, Ctx}.
