package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import com.trifork.activation.ActivationUtil;
import com.trifork.vmap.VClock.Time;

public class VectorMap {

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
			MIME_TYPE_PROTOBUF.setParameter("proto", "vectormap.proto");
			MIME_TYPE_PROTOBUF.setParameter("message", "PBVectorMap");
			MIME_TYPE_PROTOBUF_STRING = MIME_TYPE_PROTOBUF.toString();
		} catch (MimeTypeParseException e) {
			throw new RuntimeException(e);
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

	@SuppressWarnings("unchecked")
	private VClock computeUpdateVClock() {
		Map<String, VClock.Time> lub = new HashMap<String, VClock.Time>();
		for (VEntry ent : content.values()) {
			ent.vClock.updateLUB(lub);
		}

		VClock.incrementForPeer(lub, thisPeer);
		return new VClock(lub);
	}

	//==================== Basic map methods ==============================

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


	//==================== Merging ========================================

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
		ArrayList<DataSource> values =
			new ArrayList<DataSource>(e1.values.length + e2.values.length);
		
		return null; // TODO!
	}
}
