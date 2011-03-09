package com.trifork.vmap;

public interface MergeableValue {
	/** To be called on an instance of T. */
 	public <T extends MergeableValue> T mergeWith (T other);
}
