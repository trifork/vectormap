package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import org.junit.Test;


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
}
