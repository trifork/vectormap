
-ifdef(TEST).
-include_lib("eunit/include/eunit.hrl").
-endif.

-record(vmap, { 
	  dict = [] :: vdict(),
	  update_peer :: peer(),
	  hash :: hash() | undefined
	 }).

-record(vobj, {
	  vclock :: vclock(),
	  values :: [ datum() ]
	 }).

-record(vmime, { 
	  mime_type :: binary(),
	  hash      :: hash(), 
	  body      :: binary()
	 }).

-type key()    :: binary().
-type vdict()  :: [{ key(), vobj() }].
-type vmap()   :: #vmap{}.
-type vobj()   :: #vobj{}.
-type datum()  :: #vmap{} | #vmime{} | binary() | tombstone. 
-type hash()   :: <<_ : 160>>.
-type clock()  :: { peer(), integer(), integer() }.
-type vclock() :: [ clock() ].
-type peer()   :: binary().
