package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import javax.activation.DataSource;
import javax.activation.DataHandler;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.*;

import com.trifork.multiversion_common.Digestable;
import com.trifork.multiversion_common.Digest;
import com.trifork.activation.RichDataSource;

public class VectorMapTest {

	@Test
	public void testSimple() throws IOException, UnsupportedFlavorException
	{
		VectorMap vm = new VectorMap();
		
		vm.setThisPeer("kresten");
		
		vm.put("foo",  "Hello!");
		String fooism = vm.get("foo", String.class);
		
		vm.put("bar",  "Hello!");
		vm.put("baz",  new byte[0]);

	}

	private static VClock createVClock1() {
		HashMap<String,VClock.Time> vc1_entries = new HashMap();
		vc1_entries.put("jens", new VClock.Time(1,1000));
		vc1_entries.put("hans", new VClock.Time(2, 800));
		return new VClock(vc1_entries);
	}

	@Test
	public void testVClockHashing() throws IOException, UnsupportedFlavorException {
		// Erlang says: [{<<"hans">>, 2, 800}, {<<"jens">>, 1, 1000}] ->
		// <<242,245,148,131,135,229,250,224,188,173,106,25,34,156,1,196,236,224,159,71>>
		// Conversion to byte range:
		// perl -ne 'while (/[\d]+/g) {$x=($& +128)%256-128; print "$x,"} print "\n"'
		// says:
		byte[] vc1_expected_hash = {-14,-11,-108,-125,-121,-27,-6,-32,-68,-83,
									106,25,34,-100,1,-60,-20,-32,-97,71};
		VClock vc1 = createVClock1();
		checkHash(vc1, vc1_expected_hash);
	}


	final static String TEXT_UTF8 = "text/plain;charset=utf-8";


	@Test
	public void testDataSourceHashing() throws IOException, UnsupportedFlavorException {
		RichDataSource ds1 = makeDataSource("Hello World", TEXT_UTF8);
		RichDataSource ds2 = makeDataSource("Testing ÆØÅ", TEXT_UTF8);
		RichDataSource ds3 = makeDataSource("Music: ♩♪♬", TEXT_UTF8);

 		checkHash(ds1, Digest.digestOf(Digest.digestOf((TEXT_UTF8+"\nHello World").getBytes("UTF-8"))));
 		checkHash(ds2, Digest.digestOf(Digest.digestOf((TEXT_UTF8+"\nTesting ÆØÅ").getBytes("UTF-8"))));
 		checkHash(ds3, Digest.digestOf(Digest.digestOf((TEXT_UTF8+"\nMusic: ♩♪♬").getBytes("UTF-8"))));
	}

	@Test
	public void testVEntryHashing() throws IOException, UnsupportedFlavorException {
		VClock vc1 = createVClock1();

		RichDataSource ds1 = makeDataSource("Hello World", TEXT_UTF8);
		RichDataSource ds2 = makeDataSource("Testing ÆØÅ", TEXT_UTF8);
		RichDataSource ds3 = makeDataSource("Music: ♩♪♬", TEXT_UTF8);

		VEntry ve1 = new VEntry(vc1, new RichDataSource[] {ds1});
		VEntry ve2 = new VEntry(vc1, new RichDataSource[] {ds1, ds2});
		VEntry ve3 = new VEntry(vc1, new RichDataSource[] {ds1, ds2, ds3});

 		checkHash(ve1, new byte[] {96,101,-3,43,-94,-97,-15,90,98,117,65,-108,48,79,34,-28,-14,-128,-26,-4});

 		checkHash(ve2, new byte[] {0,-62,97,55,-75,98,81,-102,32,110,-94,69,20,49,-4,-29,44,102,-45,122});

 		checkHash(ve3, new byte[] {-38,-111,-94,79,-127,-85,83,62,68,-80,75,39,78,-4,103,-75,1,48,104,23});
	}

	@Test
	public void testVectorMapHashing() throws IOException, UnsupportedFlavorException {
		VClock vc1 = createVClock1();

		RichDataSource ds1 = makeDataSource("Hello World", TEXT_UTF8);
		RichDataSource ds2 = makeDataSource("Testing ÆØÅ", TEXT_UTF8);
		RichDataSource ds3 = makeDataSource("Music: ♩♪♬", TEXT_UTF8);

		VEntry ve1 = new VEntry(vc1, new RichDataSource[] {ds1});
		VEntry ve2 = new VEntry(vc1, new RichDataSource[] {ds1, ds2});
		VEntry ve3 = new VEntry(vc1, new RichDataSource[] {ds1, ds2, ds3});

		HashMap<String,VEntry> raw_map = new HashMap<String,VEntry>();

		VectorMap vmap0 = new VectorMap(raw_map);
		// Compare with vmap:vmap_ensure_hash(vmap:new(<<"peer">>)).
 		checkHash(vmap0, new byte[] {-38,57,-93,-18,94,107,75,13,50,85,
									 -65,-17,-107,96,24,-112,-81,-40,7,9});

		raw_map.put("hello", ve1);
		VectorMap vmap1 = new VectorMap(raw_map);
 		checkHash(vmap1, new byte[] {-88,26,-9,-13,-26,50,103,-41,114,23,
									 61,118,-30,60,-105,19,124,-58,46,126});

		raw_map.put("test", ve2);
		VectorMap vmap2 = new VectorMap(raw_map);
 		checkHash(vmap2, new byte[] {29,-77,11,-112,-37,60,112,-22,94,47,
									 -44,-20,125,-88,-36,-100,37,74,-72,68});

		raw_map.put("music", ve3);
		VectorMap vmap3 = new VectorMap(raw_map);
 		checkHash(vmap3, new byte[] {-83,-120,-53,54,69,113,-43,-31,-20,44,
									 -7,0,-124,-29,79, -109,62,-65,-120,-51});
	}

	private static void checkHash(Digestable data, byte[] expected_hash) {
		assertArrayEquals(expected_hash, Digest.digestOf(data));
	}

	private static RichDataSource makeDataSource(Object object, String mimetype) {
		return RichDataSource.make(new DataHandler(object, mimetype).getDataSource());
	}
}


/* Generating hash codes in Erlang shell:

BinToJavaHash = fun(Bin) -> [(X+128) rem 256 - 128 || X <- binary_to_list(Bin)] end.

rr("../src/vmap_internal.hrl").

VC = [{<<"hans">>, 2, 800}, {<<"jens">>, 1, 1000}].
BinToJavaHash(crypto:sha_final(vmap:vmap_digest_vclock(crypto:sha_init(), VC))).

TEXT_UTF8 = <<"text/plain;charset=utf-8">>.
ToUtf8 = fun(S) -> unicode:characters_to_binary(S, utf8) end.
TextAsMime = fun(S) -> vmap:create_vmime(TEXT_UTF8, ToUtf8(S)) end.
Val1 = TextAsMime("Hello World").
Val2 = TextAsMime("Testing ÆØÅ").
Val3 = TextAsMime("Music: ♩♪♬").
VObjHash = fun(VObj)-> crypto:sha_final(vmap:vmap_digest_key_value({<<"">>, VObj}, crypto:sha_init())) end.
BinToJavaHash(VObjHash(#vobj{vclock=VC, values=[Val1]})).
BinToJavaHash(VObjHash(#vobj{vclock=VC, values=[Val1,Val2]})).
BinToJavaHash(VObjHash(#vobj{vclock=VC, values=[Val1,Val2,Val3]})).

TextAsMimeTuple = fun(S) -> {mime, TEXT_UTF8, ToUtf8(S)} end.
M1 = vmap:store(<<"hello">>, TextAsMimeTuple("Hello World"), vmap:new(<<"hans">>)).
M2a = vmap:store(<<"test">>, TextAsMimeTuple("Hello World"), vmap:new(<<"jens">>)).
M2b = vmap:store(<<"test">>, TextAsMimeTuple("Testing ÆØÅ"), M1).
M2 = vmap:merge(M2a, M2b).

M3a = vmap:store(<<"music">>, TextAsMimeTuple("Hello World"), vmap:new(<<"jens">>)).
M3b = vmap:store(<<"music">>, TextAsMimeTuple("Testing ÆØÅ"), vmap:new(<<"preben">>)).
M3c = vmap:store(<<"music">>, TextAsMimeTuple("Music: ♩♪♬"), vmap:set_update_peer(<<"olaf">>, M2)).
M3 = vmap:merge(M3a, vmap:merge(M3b, M3c)).

PatchDict = fun(Dict,VClock) -> lists:map(fun({Key, Obj=#vobj{}}) -> {Key, Obj#vobj{vclock=VClock}} end, Dict) end.
PatchMap = fun(VMap, VClock) -> (VMap#vmap{dict=PatchDict(VMap#vmap.dict, VClock)})#vmap{hash=undefined} end.

BinToJavaHash((vmap:vmap_ensure_hash(PatchMap(M1, VC)))#vmap.hash).
BinToJavaHash((vmap:vmap_ensure_hash(PatchMap(M2, VC)))#vmap.hash).
BinToJavaHash((vmap:vmap_ensure_hash(PatchMap(M3, VC)))#vmap.hash).
   
 */