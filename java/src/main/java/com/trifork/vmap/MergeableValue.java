package com.trifork.vmap;

public interface MergeableValue<T extends MergeableValue> {
 	public T mergeWith (T other);
}
