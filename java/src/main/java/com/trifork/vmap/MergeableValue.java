package com.trifork.vmap;

import javax.activation.DataSource;

public interface MergeableValue<T extends MergeableValue> {
 	public T mergeWith (T other);

	public DataSource toDatasource();
}
