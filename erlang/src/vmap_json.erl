-module(vmap_json).

-include("vmap_internal.hrl").

-export([encode/1]).


encode(#vmap{dict=Dict,hash=H}) ->
    {Data,Meta} = encode_dict(Dict,[],[]),    
    {struct, [{'_meta', 
	       {struct, [{hash,hexstring(H)}] ++ Meta}}
             | Data]};

%encode(#vobj{vclock=VC,values=Vals}) ->
%    {struct, [{'_vclock',encode_vclock(VC,[])},
%	      {'values', lists:map(fun encode/1,Vals)}]}; 
%
encode(#vmime{mime_type=T,hash=_H,body=B}) ->
    {struct, 
     [{mime_type,T},
      {body,base64:encode(B)}]};

encode(O) -> O.


encode_dict([], D,M) ->
    {D,M};
encode_dict([{Key,Obj}|T], D,M) ->
    Data = encode_data(Key,Obj),
    Meta = encode_meta(Key,Obj),
    encode_dict(T, Data ++ D, [Meta|M]).

encode_data(_,#vobj{values=[tombstone|_]}) ->
    [];
encode_data(Key,#vobj{values=[V|_]}) ->
    [{Key,encode(V)}].

encode_meta(Key,#vobj{values=[V|Vals],vclock=C}) ->
    {Key,{struct,[{hash, hexstring( vmap:datum_hash(V) )},
		  {vclock, encode_vclock(C,[])},
		  {deleted, V == tombstone}
		  | encode_alts(Vals,[])] }}.

encode_alts([],[]) ->
     [];
encode_alts([],R) ->
     [{alts,R}];
encode_alts([O|T],R) ->
    encode_alts(T, [encode(O)|R]). 



encode_vclock([],R) ->
    {struct, lists:reverse(R)};

encode_vclock([{P,C,T}|Tail],R) ->
    encode_vclock(Tail, [{P, [C,T]}|R]).

hexstring(Bin) ->
  list_to_binary([io_lib:format("~2.16.0B", [X]) ||
    X <- binary_to_list(Bin)]).

-ifdef(TEST).

encode_test() ->
    crypto:start(),

    MA = vmap:store(<<"X">>, <<"test">>,  vmap:new(jens)),
    MB = vmap:store(<<"Y">>, <<"test-ÆØÅ">>, vmap:set_update_peer(peter, MA)),
    MC = vmap:store(<<"Y">>, %<<"123">>, 
		    {mime, <<"application/binary">>, <<"binary encoded string">>}, 
		    vmap:set_update_peer(anders, MA)),
    MD = vmap:merge(MB, MC),

    Enc = 
	encode(vmap:vmap_ensure_hash(MD)),

%    io:format("ENC=~p~n", [Enc]),
    JSON = (mochijson2:encoder([]))(Enc),
    
    io:format("OUT=~s", [JSON]),

    throw(x).

-endif.
