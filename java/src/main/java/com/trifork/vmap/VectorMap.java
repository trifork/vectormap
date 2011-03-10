package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import javax.mail.util.ByteArrayDataSource;

import com.trifork.activation.ActivationUtil;
import com.trifork.activation.ActivationUtil.Decoder;

public class VectorMap implements MergeableValue<VectorMap> {

	public static class VEntry {

		public final VClock vClock;
		public final DataSource[] values;

		public VEntry(VClock vClock, DataSource[] ds) {
			this.vClock = vClock;
			this.values = ds;
		}

	}

	public static final String MIME_TYPE_PROTOBUF_STRING;
	public static final MimeType MIME_TYPE_PROTOBUF;

	static {
		try {
			MIME_TYPE_PROTOBUF = new MimeType("application", "x-protobuf");
			MIME_TYPE_PROTOBUF.setParameter("proto", "vectormap");
			MIME_TYPE_PROTOBUF.setParameter("message", "PBVectorMap");
			MIME_TYPE_PROTOBUF_STRING = MIME_TYPE_PROTOBUF.toString();
		} catch (MimeTypeParseException e) {
			throw new RuntimeException(e);
		}
	}

	public DataSource toDatasource() {
		try {
			return new ByteArrayDataSource(new PBEncoder().encode(this),
										   MIME_TYPE_PROTOBUF_STRING);
		} catch (IOException ioe) {
			return null; // How to handle this?...
		}
	}

	private String thisPeer;
	Map<String, VEntry> content;
	private VClock update_vclock;

	protected VectorMap(Map<String, VEntry> content) {
		this.content = content;
	}

	protected VectorMap(String thisPeer, Map<String, VEntry> content) {
		this.content = content;
		setThisPeer(thisPeer);
	}

	public void setThisPeer(String thisPeer2) {
		this.thisPeer = thisPeer2;
		this.update_vclock = computeUpdateVClock();
	}

	public VectorMap(String thisPeer) throws IOException {
		this(thisPeer, new HashMap<String, VEntry>());
	}

	public VectorMap() throws IOException {
		this(new HashMap<String, VEntry>());
	}

	private VClock computeUpdateVClock() {
		Map<String, VClock.Time> lub = new HashMap<String, VClock.Time>();
		for (VEntry ent : content.values()) {
			ent.vClock.updateLUB(lub);
		}

		VClock.incrementForPeer(lub, thisPeer);
		return new VClock(lub);
	}

	//==================== Basic map methods ==============================

	public int size() {
		return content.size();
	}

	public <T> T get(String key, Class<T> representationClass)
			throws UnsupportedFlavorException, IOException {

		VEntry enc = content.get(key);
		if (enc == null)
			return null;
		DataSource encodedValue = enc.values[0];
		if (encodedValue == null)
			return null;

		return ActivationUtil.decode(encodedValue, representationClass);
	}

	public void remove(String key) {
		put(key, (DataSource)null);
	}

	public void put(String key, String string) throws IOException {
		put(key, "text/plain;charset=utf-8", string);
	}

	public void put(String key, byte[] data) throws IOException {
		put(key, "application/octet-stream", data);
	}

	public void put(String key, String mimeType, Object value)
			throws IOException {
		put(key, new DataHandler(value, mimeType).getDataSource());
	}

	public void put(String key, DataSource ds) {
		update_vclock.timeStamp(thisPeer);
		if (ds == null) { // TODO: Why this test?
			content.put(key, new VEntry(update_vclock, new DataSource[] { null }));
		} else {
			content.put(key, new VEntry(update_vclock, new DataSource[] { ds }));
		}
	}

	public boolean containsKey(String key) {
		VEntry entry = content.get(key);
		if (entry == null) return false; // Not present at all.
		for (DataSource ds : entry.values)
			if (ds != null)
				return true; // Value present.
		return false; // Only tombstone present, if any.
	}

	//==================== Extra accessor methods ====================

	public List<String> conflicts() {
		List<String> conflict_keys = new ArrayList<String>();
		for (Map.Entry<String,VEntry> e : content.entrySet()) {
			if (e.getValue().values.length > 1)
				conflict_keys.add(e.getKey());
		}
		return conflict_keys;
	}
	

	//==================== Merging ========================================

	public VectorMap mergeWith(VectorMap other) {
		return VectorMap.merge(this,other);
	}


	public static VectorMap merge(VectorMap v1, VectorMap v2) {
		final HashMap<String, VEntry> union = new HashMap<String, VEntry>();

		for (Map.Entry<String,VEntry> e1 : v1.content.entrySet()) {
			String key = e1.getKey();
			VEntry value = e1.getValue();

			VEntry value2 = v2.content.get(key);
			if (value2 != null) {
				value = merge_entry(value, value2);
			}
			union.put(key, value);
		}

		for (Map.Entry<String,VEntry> e2 : v2.content.entrySet()) {
			String key = e2.getKey();
			if (! union.containsKey(key)) {
				union.put(key, e2.getValue());
			}
		}
	
		return new VectorMap(union);
	}

	public static VEntry merge_entry(VEntry e1, VEntry e2) {
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
		if (e1.vClock.max_secs < e2.vClock.max_secs) {
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

		HashSet<DataSource> unmergeable_values = new HashSet<DataSource>();
		IdentityHashMap<Class<? extends MergeableValue>, MergeableValue> mergeable_values =
			new IdentityHashMap<Class<? extends MergeableValue>, MergeableValue>();

		for (DataSource ds : e1.values) {
			insert_value(ds, unmergeable_values, mergeable_values);
		}
		for (DataSource ds : e2.values) {
			insert_value(ds, unmergeable_values, mergeable_values);
		}

		DataSource[] result_values = new DataSource[unmergeable_values.size() +
													mergeable_values.size()];

		int i = 0;
		for (MergeableValue mv : mergeable_values.values()) {
			result_values[i++] = mv.toDatasource();
			
		}
		for (DataSource ds : unmergeable_values) {
			result_values[i++] = ds;
		}

		return new VEntry(merge_vclock, result_values);
	}

	public static void insert_value(DataSource ds,
									HashSet<DataSource> unmergeable_values,
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
				decoded = decoder.decode(ds);
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
		final DataSource ds;
		public LazyDecodingFailedException(DataSource ds, Throwable t) {
			super(t);
			this.ds = ds;
		}
	}

	/** Placeholder for an undecoded value. */
	static class LazilyDecodedMergeableValue<T extends MergeableValue> implements MergeableValue<T> {
		final DataSource ds;
		final Decoder<T> decoder;

		public LazilyDecodedMergeableValue(DataSource ds, Decoder<T> decoder) {
			this.ds = ds;
			this.decoder = decoder;
		}

		public T mergeWith(T other) {
			final MergeableValue<T> decoded;
			try {
				decoded = decoder.decode(ds);
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
