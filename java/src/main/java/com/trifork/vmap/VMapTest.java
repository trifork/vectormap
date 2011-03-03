package com.trifork.vmap;

import java.io.IOException;

public class VMapTest {

	public static void main(String[] args) throws Throwable {
		
		
		VectorMap vm = new VectorMap();
		
		vm.setThisPeer("kresten");
		
		String fooism = vm.get("foo", String.class);
		
		vm.put("bar",  "Hello!");
		vm.put("baz",  new byte[0]);
		
		
	}
	
}
