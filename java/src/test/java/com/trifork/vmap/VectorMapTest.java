package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.*;

import com.trifork.activation.Digestable;
import com.trifork.activation.Digest;

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

	@Test
	public void testVClockHashing() throws IOException, UnsupportedFlavorException {
		// Erlang says: [{<<"hans">>, 2, 800}, {<<"jens">>, 1, 1000}] ->
		// <<242,245,148,131,135,229,250,224,188,173,106,25,34,156,1,196,236,224,159,71>>
		// Conversion to byte range:
		// perl -ne 'while (/[\d]+/g) {$x=($& +128)%256-128; print "$x,"} print "\n"'
		// says:
		byte[] vc1_expected = {-14,-11,-108,-125,-121,-27,-6,-32,-68,-83,106,25,34,-100,1,-60,-20,-32,-97,71};

		HashMap<String,VClock.Time> vc1_entries = new HashMap();
		vc1_entries.put("jens", new VClock.Time(1,1000));
		vc1_entries.put("hans", new VClock.Time(2, 800));
		VClock vc1 = new VClock(vc1_entries);
		assertArrayEquals(Digest.digestOf(vc1), vc1_expected);
	}
}
