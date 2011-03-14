%% ``The contents of this file are subject to the Erlang Public License,
%% Version 1.1, (the "License"); you may not use this file except in
%% compliance with the License. You should have received a copy of the
%% Erlang Public License along with this software. If not, it can be
%% retrieved via the world wide web at http://www.erlang.org/.
%% 
%% Software distributed under the License is distributed on an "AS IS"
%% basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
%% the License for the specific language governing rights and limitations
%% under the License.
%% 

-module(vmap).
-author("Kresten Krab Thorup <krab@trifork.com>").

-define(TOMBSTONE_SHA, 
	<<0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0>>).

-include("vmap_internal.hrl").

-export([new/1, set_update_peer/2, find/2, foldl/3, delete/2, add/2, store/3, conflicts/1, merge/2, keys/1, vmap_ensure_hash/1]).
-export([datum_hash/1]).

new(Peer) ->
    #vmap{ update_peer=to_peer(Peer) }.

set_update_peer(Peer,#vmap{}=VMap) ->
    VMap#vmap{ update_peer=Peer }.

keys(#vmap{}=VMap) ->
    foldl(fun({Key,_},Acc) ->
		  [Key|Acc]
	  end,
	  [],
	  VMap).

foldl(Fun,Acc0,#vmap{dict=Dict}) ->
    vdict_fold(Fun,Acc0,Dict).

-spec find(Key :: key(), VMap :: vmap()) -> notfound | [ datum() ].
find(Key,#vmap{dict=VDict}) ->
    case vdict_find(Key, VDict) of
	notfound ->
	    notfound;
	#vobj{ values=[tombstone] } -> 
	    notfound;
	#vobj{ values=Values } -> 
	    decode(Values, [])
    end.

decode([],Result) ->
     Result;
decode([H|T],Result ) ->
    decode(T, [decode(H)|Result]).

decode(#vmime{ mime_type= <<"application/binary">>, body=Body }) ->
    Body;
decode(#vmime{ mime_type= <<"text/plain;charset=utf-8">>, body=Body }) ->
    unicode:characters_to_list([Body], utf8);
decode(Object) -> Object.

-spec conflicts( VMap :: vmap() ) -> [ key() ].
conflicts(#vmap{dict=VDict}) ->
    lists:foldl(fun({_,#vobj{ values=[_] }},Others) -> Others;
		   ({Key,_},Others) -> [Key|Others]
		end,
		[],
		VDict).

-spec delete(Key::key(), VMap::vmap()) -> vmap().
delete(Key, VMap) ->
    do_store(Key, tombstone, VMap).

-spec add(Datum::datum() | {mime, binary(), binary()}, VMap::vmap()) -> vmap().
add(#vmap{}=Datum, VMap) ->
    VM = vmap_ensure_hash(Datum),
    store(VM#vmap.hash, VM, VMap);
add(Datum,VMap) ->
    store(datum_hash(Datum), Datum, VMap).

-spec store( key(),
	       integer() | float()
	     | string() | binary()
	     | vmap() 
	     | {mime, binary(), binary()}
	     | tombstone,
	     vmap()) -> vmap().

store(Key, {mime,ContentType,Body}, VMap) ->
    do_store(Key,create_vmime(ContentType,Body),VMap);
store(Key, #vmap{}=Value, VMap) ->
    do_store(Key,Value,VMap);
store(Key, AM, VMap) when is_atom(AM) ->
    do_store(Key, erlang:atom_to_binary(AM) ,VMap);
store(Key, B, VMap) when is_binary(B) ->
    do_store(Key, B ,VMap);
store(Key, List, VMap) when is_list(List) ->
    do_store(Key, list_to_binary(List) ,VMap).



-spec do_store(Key :: key(), Value::datum(), VMap::vmap()) -> vmap().
do_store(Key, Value, #vmap{ update_peer=Peer, dict=Dict }=VMap) when Peer =/= undefined ->

    case vdict_find(Key, Dict) of
        notfound ->
	    case Value of 
		tombstone -> 
		    VMap;
		_ ->
		    VMap#vmap {
		      dict=vdict_add(Key, 
				     #vobj{ vclock = vclock_new(Peer),
					    values=[Value] },
				     Dict),
		      hash=undefined
		     }
	    end;
	      
	#vobj{ vclock=VClock } -> 
	    VMap#vmap {
	      dict = vdict_replace(Key,
				   #vobj{
				     vclock = vclock_increment(Peer,VClock),
				     values=[Value] },
				   Dict),
	      hash=undefined
	     }
    end.

create_vmime(ContentType,Body) when is_binary(ContentType), is_binary(Body) ->
    VMime = #vmime{mime_type=ContentType,body=Body},
    VMime#vmime{hash = datum_hash(VMime)}.

-spec merge(Map1::vmap(), Map2::vmap()) -> vmap().
merge(#vmap{dict=D1}, #vmap{dict=D2}) ->
    #vmap{dict=vdict_merge(D1,D2)}.

-spec vdict_merge(vdict(),vdict()) -> vdict().
vdict_merge([], List) ->
    List;
vdict_merge(List, []) ->
    List;
vdict_merge([{Key,Obj}=Head|Tail], Other) ->
    case lists:keytake(Key, 1, Other) of
	false ->
	    [Head] ++ vdict_merge(Tail, Other);
	{value, {_,Obj2}, OtherTail} ->
	    [{Key, vobj_merge(Obj,Obj2)}] ++ vdict_merge(Tail,OtherTail)
    end.

-spec vobj_merge(V1::vobj(),V2::vobj()) -> vobj().
vobj_merge(#vobj{vclock=VC1}=V1, #vobj{vclock=VC2}=V2) ->
    case vclock_compare(VC1,VC2) of
	earlier    -> V2;
	later      -> V1;
	concurrent ->
	    case vclock_time(VC1) > vclock_time(VC2) of
		true ->
		    Values = vdata_merge(V1#vobj.values, V2#vobj.values, []);
		false ->
		    Values = vdata_merge(V2#vobj.values, V1#vobj.values, [])
	    end,
	    
	    #vobj{vclock=vclock_lub(VC1,VC2),
		  values=Values}
    end.

vdata_merge([],Other,Result) -> lists:reverse(Result, Other) ;
vdata_merge([H|T],Other,Result) ->
    {Merged,Other2} = vdata_merge_elm(H,Other,[]),
    vdata_merge(T,Other2,[Merged|Result]).

%% bottom case
vdata_merge_elm(Elm,[],Result) ->
    {Elm,Result};

%% remove duplicate vmime values (don't compate bodies)
vdata_merge_elm( #vmime{hash=H,mime_type=M}=Elm,
		[#vmime{hash=H,mime_type=M}|Rest],Result) when H =/= undefined ->
    vdata_merge_elm(Elm,Rest,Result);

%% remove duplicate maps
vdata_merge_elm(#vmap{hash=H}=Map1,[#vmap{hash=H}|Rest],Result)  when H =/= undefined ->
    vdata_merge_elm(Map1,Rest,Result);

%% merge maps
vdata_merge_elm(#vmap{}=Map1,[#vmap{}=Map2|Rest],Result) ->
    vdata_merge_elm(merge(Map1,Map2),Rest,Result);

%% remove duplicate tombstones
vdata_merge_elm(tombstone,[tombstone|Rest],Result) ->
    vdata_merge_elm(tombstone,Rest,Result);

vdata_merge_elm(Elm,[H|Rest],Result) ->
    vdata_merge_elm(Elm,Rest,[H|Result]).




-spec vmap_ensure_hash/1 :: (vmap()) -> vmap().
vmap_ensure_hash(#vmap{hash=undefined}=VMap) ->
    Context = vmap_digest(VMap, crypto:sha_init()),
    Hash=crypto:sha_final(Context),
    VMap#vmap{hash=Hash};
vmap_ensure_hash(#vmap{hash= <<_:160>>}=VMap) ->
    VMap.

vmap_digest(#vmap{dict=Dict}, Context) ->
    SDict = lists:sort(fun({Key1,_},{Key2,_}) -> Key1 =< Key2 end, Dict),
    lists:foldl(fun vmap_digest_key_value/2, Context, SDict).

sha_update(CTX,Val) ->
    crypto:sha_update(CTX,Val).

vmap_digest_key_value({Key,#vobj{ vclock=VClock, values=Values}}, Context) ->
    C1 = sha_update(Context, Key),
    C2 = vmap_digest_data(C1, Values),
    vmap_digest_vclock(C2, VClock).

vmap_digest_data(Context, Values) ->
    SValues = lists:sort(fun(Datum1, Datum2) -> 
				 datum_hash(Datum1) =< datum_hash(Datum2) 
			 end, 
			 Values),
    lists:foldl(fun vmap_digest_datum/2, Context, SValues).

vmap_digest_datum(Datum, Context) ->
    DH = datum_hash(Datum),
%    io:format("DH=~w D=~w~n", [DH,Datum]),
    sha_update(Context, DH ).

vmap_digest_vclock(Context, VClock) ->
    SVClock = lists:sort(fun({Peer1,_,_},{Peer2,_,_}) -> 
				 Peer1 =< Peer2 
			 end, 
			 VClock),
    lists:foldl(fun vmap_digest_clock/2, Context, SVClock).

vmap_digest_clock({Peer,Count,Time}, C0) ->
    C1 = sha_update(C0, Peer),
    C2 = sha_update(C1, list_to_binary(integer_to_list(Count))),
         sha_update(C2, list_to_binary(integer_to_list(Time))).


-spec datum_hash(Datum :: datum()) -> hash().
datum_hash(#vmap{hash=undefined}=VMap) ->
    VMap2 = vmap_ensure_hash(VMap),
    VMap2#vmap.hash;
datum_hash(#vmap{hash=Hash}) ->
    Hash;
datum_hash(#vmime{hash=Hash}) when Hash =/= undefined ->
    Hash;
datum_hash(#vmime{mime_type= <<_/binary>>=ContentType, body= <<_/binary>>=Body}) ->
    C0 = crypto:sha_init(),
    C1 = crypto:sha_update(C0, ContentType),
    C2 = crypto:sha_update(C1, <<"\n">>),
    C3 = crypto:sha_update(C2, Body),
    crypto:sha_final(C3);
datum_hash(Binary) when is_binary(Binary) ->
    crypto:sha(Binary);
datum_hash(tombstone) ->
    ?TOMBSTONE_SHA.



%% utility

utc_secs() ->
    {MegaSecs, Secs, _} = erlang:now(),
    MegaSecs*1000000 + Secs.

to_peer(Peer) when is_binary(Peer) ->
    Peer;
to_peer(Peer) when is_atom(Peer) ->
    erlang:atom_to_binary(Peer,utf8).


%%% vclock ADT

-spec vclock_new(Peer :: peer() | atom()) -> vclock().    
vclock_new(Peer) ->
    [ { to_peer(Peer), 1, utc_secs() } ].

vclock_time(VClock) ->
    vclock_time(VClock, 0).

vclock_time([{_,_,T}|Rest], N) ->
    vclock_time(Rest,erlang:max(T,N));
vclock_time([],N) ->
    N.

-spec vclock_increment(Peer :: peer(), VClock :: vclock() ) -> vclock().
vclock_increment(Peer, VClock) when is_binary(Peer) ->
    case lists:keyfind(Peer, 1, VClock) of
	false ->
	    lists:sort(fun({P1,_,_},{P2,_,_}) -> P1 =< P2 end,
		       vclock_new(Peer) ++ VClock) ;
	
	{_Peer,Count,_Time} ->
	    lists:keyreplace(Peer, 1, VClock, {Peer, Count+1, utc_secs() })
    end;
vclock_increment(Peer,VClock) when is_atom(Peer) ->
    vclock_increment(to_peer(Peer),VClock).


%[{<<"peter">>,1,1298626254}],
%[{<<"jens">>,1,1298626254}, {<<"peter">>,1,1298626254}]


-spec vclock_compare(VC1 :: vclock() ,VC2 :: vclock()) -> earlier | later | concurrent.
    
vclock_compare(VC1,VC2) ->
    case vclock_leq(VC1,VC2) of
	true -> earlier;
	false ->
	    case vclock_leq(VC2,VC1) of
		true ->
		    later;
		false ->
		    concurrent
	    end
    end.


vclock_leq([], _) ->
    true;
vclock_leq([{Peer,Count,_}|Rest], [{Peer,Count2,_}|Rest2]) when Count =< Count2->
    vclock_leq(Rest,Rest2);
vclock_leq([{Peer,_,_}|_]=Rest1, [{Peer2,_,_}|VClock2]) when Peer2 < Peer ->
    vclock_leq(Rest1,VClock2);
vclock_leq(_, _) ->
    false.

vclock_lub([], VClock) ->
    VClock;
vclock_lub(VClock, []) ->
    VClock;
vclock_lub([{Peer,Count,Time}|Rest], [{Peer,Count2,Time2}|Rest2]) ->
    [{Peer,erlang:max(Count,Count2),erlang:max(Time,Time2)} | vclock_lub(Rest,Rest2)];
vclock_lub([{Peer,_,_}=Clock|Rest], [{Peer2,_,_}|_]=VClock2) when Peer < Peer2 ->
    [Clock | vclock_lub(Rest,VClock2)];
vclock_lub([{Peer,_,_}|_]=VClock, [{Peer2,_,_}=Clock2|Rest2]) when Peer2 < Peer ->
    [Clock2 | vclock_lub(VClock,Rest2)].


%%% vdict ADT


-spec vdict_find(Key::key(), Dict::vdict()) -> vobj() | notfound.
vdict_find(Key,Dict) ->
    case lists:keyfind(Key, 1, Dict) of
	false    -> 
	    notfound;
	{_,VObj} -> VObj
    end.

vdict_add(Key,VObj,VDict) ->
    [{Key,VObj}|VDict].

vdict_replace(Key,VObj,VDict) ->
    lists:keyreplace(Key, 1, VDict, {Key,VObj}).

vdict_fold(_Fun,Acc0,[]) ->
    Acc0;
vdict_fold(Fun,Acc0,[{_,#vobj{values=[tombstone]}}|T]) ->
    vdict_fold(Fun, Acc0, T);
vdict_fold(Fun,Acc0,[{Key,#vobj{values=Values}}|T]) ->
    Acc1 = Fun({Key,Values},Acc0),
    vdict_fold(Fun, Acc1, T).



-ifdef(TEST).

vclock_eq(VC1,VC2) ->
    vclock_leq(VC1,VC2) andalso vclock_leq(VC2,VC1).

vclock_test() ->
    
    V0   = vclock_new(jens),
    V1   = vclock_new(peter),

    V12  = vclock_increment(peter,V1),
    V122 = vclock_increment(jens,V12),
    V13  = vclock_increment(jens,V1),
    
    true = vclock_leq(V1,V1),
    true = vclock_leq(V1,V12),
    true = vclock_leq(V1,V122),
    true = vclock_leq(V1,V13),
    
    V2a = vclock_lub(V122,V13),
    V2b = vclock_lub(V13,V122),
    true = vclock_eq(V2a,V2b),

    V3a = vclock_lub(V0,V12),
    V3b = vclock_lub(V12,V0),
    true = vclock_eq(V3a,V3b),

    false = vclock_leq(V12,V1),
    false = vclock_leq(V122,V1),
    false = vclock_leq(V13,V1).

concur_vclock_test() ->

    C1 = [{<<"jens">>,1,1298629834},{<<"peter">>,1,1298629834}],
    C2 = [{<<"anders">>,1,1298629834},{<<"jens">>,1,1298629834}],

    concurrent = vclock_compare(C1,C2),
    false = vclock_leq(C1,C2),
    false = vclock_leq(C2,C1)
    .   

vobj_test() ->

    C1 = [{<<"jens">>,1,1298629834},{<<"peter">>,1,1298629834}],
    X1 = #vobj{vclock=C1,
               values=[create_vmime(<<"text/plain;charset=utf-8">>,
				    <<"foobar">>)]},
    
    C2 = [{<<"anders">>,1,1298629834},{<<"jens">>,1,1298629834}],
    X2 = #vobj{vclock=C2, values=[tombstone]},
    
    #vobj{values=Vals} = vobj_merge(X1,X2),
    
    2 = length(Vals).
    

vmap_test() ->    
    MA = store(<<"X">>, "test",  new(jens)),
    MB = store(<<"Y">>, "test-ÆØÅ", set_update_peer(peter, MA)),
    MC = delete(<<"X">>, set_update_peer(anders, MA)),
    MD = merge(MB, MC),

    io:format( "~nMD=~P~n", [MD,40]),

    [<<"Y">>] = keys(MD),
    [] = conflicts(MD),

    notfound = find(<<"X">>,MD),
    notfound = find(<<"Z">>,MD),

    [<<"test-ÆØÅ">>] = find(<<"Y">>, MD),		

    ME = store(<<"X">>, "foobar", MB),
    MM = merge(MC, ME),

    io:format( "~nME=~P~nMC=~P~nMM=~P~n", [ME,20,MC,20,MM,40]),

    [<<"X">>] = conflicts(MM)
    .

    

-endif.
