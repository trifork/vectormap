package com.trifork.vmap;

import javax.activation.DataSource;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Arrays;

import java.security.MessageDigest;

import com.trifork.activation.RichDataSource;
import com.trifork.activation.ActivationUtil;
import com.trifork.activation.ActivationUtil.Decoder;
import com.trifork.multiversion_common.Digestable;
import com.trifork.multiversion_common.VClock;

/** Multi-version value entry.
 */
public class VEntry implements Digestable {

	public final VClock vClock;
	public final RichDataSource[] values;

	private final byte[] TOMBSTONE_HASH = {0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0};

	public VEntry(VClock vClock, RichDataSource[] ds) {
		this.vClock = vClock;
		this.values = ds;
	}

	//==================== Hashing ==============================

	public void updateDigest(MessageDigest md) {
		Arrays.sort(values, RichDataSource.BY_HASH);

		// TODO: Add separators?
		for (RichDataSource v : values) {
			if (v != null)
				v.updateDigest(md);
			else
				md.update(TOMBSTONE_HASH);
		}
		vClock.updateDigest(md);
	}

	//==================== Merging ========================================

	public static VEntry merge(VEntry e1, VEntry e2) {
		switch (VClock.compare(e1.vClock, e2.vClock)) {
		case VClock.SAME:   return e1; // e1 and e2 should be identical... but pruning effects might cause them not to be.
		case VClock.BEFORE: return e2;
		case VClock.AFTER:  return e1;
		case VClock.CONCURRENT: return merge_concurrent(e1, e2);
		}//switch
		throw new RuntimeException();
	}

	public static VEntry merge_concurrent(VEntry e1, VEntry e2) {
		// Make e1 be the newest-by-max_secs entry:
		if (e1.vClock.getMaxSecs() < e2.vClock.getMaxSecs()) {
			VEntry tmp=e1; e1=e2; e2=tmp;
		}

		VClock merge_vclock = VClock.lub(e1.vClock, e2.vClock);

		/* We work with three collections:
		 * (1) The unmergeable values.
		 * (2) The mergeable values - not merged (and not decoded).
		 * (3) The mergeable values which have taken part in a merge.
		 *  The last two are both contained in 'mergeable_values'; the
		 *  undecoded ones as LazilyDecodedMergeableValue objects.
		 */

		HashSet<RichDataSource> unmergeable_values = new HashSet<RichDataSource>();
		IdentityHashMap<Class<? extends MergeableValue>, MergeableValue> mergeable_values =
			new IdentityHashMap<Class<? extends MergeableValue>, MergeableValue>();

		for (RichDataSource ds : e1.values) {
			insert_value(ds, unmergeable_values, mergeable_values);
		}
		for (RichDataSource ds : e2.values) {
			insert_value(ds, unmergeable_values, mergeable_values);
		}

		RichDataSource[] result_values =
			new RichDataSource[unmergeable_values.size() +
							   mergeable_values.size()];

		int i = 0;
		for (MergeableValue mv : mergeable_values.values()) {
			result_values[i++] = RichDataSource.make(mv.toDatasource());
			
		}
		for (RichDataSource ds : unmergeable_values) {
			result_values[i++] = ds;
		}

		return new VEntry(merge_vclock, result_values);
	}

	public static void insert_value(RichDataSource ds,
									HashSet<RichDataSource> unmergeable_values,
									IdentityHashMap<Class<? extends MergeableValue>, MergeableValue> mergeable_values)
	{
		Decoder<MergeableValue> decoder;
		try {
			decoder = ActivationUtil.getDecoder(ds.getContentType(),
												MergeableValue.class);
		} catch (Exception e) {
			// Value is undecodeable. Regard as unmergeable.
			unmergeable_values.add(ds);
			return;
		}

		
		final Class<? extends MergeableValue> repr_class = decoder.getRepresentationClass();
		final MergeableValue existing = mergeable_values.get(repr_class);
		MergeableValue<?> new_value;
		if (existing != null) {
			final MergeableValue decoded;
			try {
				decoded = decoder.decode(ds.getDataSource());
			} catch (IOException ioe) {
				// Decoding of new value failed. Put in 'unmergeable_values'.
				unmergeable_values.add(ds);
				return;
			}

			try {
				new_value = existing.mergeWith(decoded);
			} catch (LazyDecodingFailedException ldfe) {
				// Decoding of existing failed. Moved it to 'unmergeable_values':
				unmergeable_values.add(ldfe.ds);
				new_value = decoded;
			}
		} else {
			// Delay decoding until needed:
			new_value = new LazilyDecodedMergeableValue(ds, decoder);
		}
		mergeable_values.put(repr_class, new_value);
	}


	static class LazyDecodingFailedException extends RuntimeException {
		final RichDataSource ds;
		public LazyDecodingFailedException(RichDataSource ds, Throwable t) {
			super(t);
			this.ds = ds;
		}
	}

	/** Placeholder for an undecoded value. */
	static class LazilyDecodedMergeableValue<T extends MergeableValue> implements MergeableValue<T> {
		final RichDataSource ds;
		final Decoder<T> decoder;

		public LazilyDecodedMergeableValue(RichDataSource ds, Decoder<T> decoder) {
			this.ds = ds;
			this.decoder = decoder;
		}

		public T mergeWith(T other) {
			final MergeableValue<T> decoded;
			try {
				decoded = decoder.decode(ds.getDataSource());
			} catch (IOException ioe) {
				throw new LazyDecodingFailedException(ds, ioe);
			}
			return decoded.mergeWith(other);
		}

		public DataSource toDatasource() {
			return ds;
		}
	}

}