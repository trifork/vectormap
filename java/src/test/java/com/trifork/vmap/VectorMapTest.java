package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import javax.activation.DataSource;
import javax.activation.DataHandler;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.*;

import com.trifork.activation.Digestable;
import com.trifork.activation.Digest;
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


	private static void checkHash(Digestable data, byte[] expected_hash) {
		assertArrayEquals(expected_hash, Digest.digestOf(data));
	}

	private static RichDataSource makeDataSource(Object object, String mimetype) {
		return RichDataSource.make(new DataHandler(object, mimetype).getDataSource());
	}
}
