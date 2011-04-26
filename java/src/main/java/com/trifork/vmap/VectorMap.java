package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.security.MessageDigest;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import javax.mail.util.ByteArrayDataSource;

import com.google.protobuf.ByteString;
import com.trifork.activation.RichDataSource;
import com.trifork.activation.ActivationUtil;
import com.trifork.multiversion_common.Digest;
import com.trifork.multiversion_common.Digestable;
import com.trifork.multiversion_common.VClock;

public class VectorMap implements MergeableValue<VectorMap>, Digestable {

	private ByteString thisPeer;
	Map<String, VEntry> content;
	private VClock update_vclock;
	private byte[] hash;

	protected VectorMap(Map<String, VEntry> content) {
		this.content = content;
	}

	protected VectorMap(ByteString thisPeer, Map<String, VEntry> content) {
		this.content = content;
		setThisPeer(thisPeer);
	}

	public void setThisPeer(ByteString thisPeer2) {
		this.thisPeer = thisPeer2;
		this.update_vclock = computeUpdateVClock();
	}

	public VectorMap(ByteString thisPeer) throws IOException {
		this(thisPeer, new HashMap<String, VEntry>());
	}

	public VectorMap() throws IOException {
		this(new HashMap<String, VEntry>());
	}

	public VectorMap(VectorMap org) throws IOException {
		this(org.thisPeer, new HashMap(org.content));
	}

	private VClock computeUpdateVClock() {
		Map<ByteString, VClock.Time> lub = new HashMap<ByteString, VClock.Time>();
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
		RichDataSource encodedValue = enc.values[0];
		if (encodedValue == null)
			return null;

		return ActivationUtil.decode(encodedValue.getDataSource(),
									 representationClass);
	}

	public void remove(String key) {
		put(key, (RichDataSource)null);
	}

	public void put(String key, String string) throws IOException {
		put(key, "text/plain;charset=utf-8", string);
	}

	public void put(String key, byte[] data) throws IOException {
		put(key, "application/octet-stream", data);
	}

	public <T extends MergeableValue<T>> void put(String key, T m) throws IOException {
		put(key, m.toDatasource());
	}
	public void put2(String key, Object m) throws IOException { // For Scala visibility... :-|
		put(key, (MergeableValue)m);
	}

	public void put(String key, String mimeType, Object value)
			throws IOException {
		put(key, new DataHandler(value, mimeType).getDataSource());
	}

	public void put(String key, DataSource ds) {
		put(key, RichDataSource.make(ds));
	}

	public void put(String key, RichDataSource ds) {
		update_vclock.timeStamp(thisPeer);
		if (ds == null) { // TODO: Why this test?
			content.put(key, new VEntry(update_vclock, new RichDataSource[] { null }));
		} else {
			content.put(key, new VEntry(update_vclock, new RichDataSource[] { ds }));
		}
		hash = null;
	}

	public boolean containsKey(String key) {
		VEntry entry = content.get(key);
		if (entry == null) return false; // Not present at all.
		for (RichDataSource ds : entry.values)
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

	
	//==================== Hashing ====================
	protected byte[] hash() {
		if (hash==null) hash = computeHash();
		return hash;
	}

	protected byte[] computeHash() {
		return Digest.digestOf(this);
	}

	public void updateDigest(MessageDigest md) {
		Map.Entry<String, VEntry>[] entries =
			content.entrySet().toArray(new Map.Entry[content.size()]);
		Arrays.sort(entries, COMPARE_MAP_ENTRY_BY_KEY);

		try {
			for (Map.Entry<String, VEntry> e : entries) {
				//TODO: Separators?
				md.update(e.getKey().getBytes("UTF-8"));
				e.getValue().updateDigest(md);
			}
		} catch (java.io.UnsupportedEncodingException uee) {
			throw new RuntimeException(uee); // Should never happen.
		}
	}

	public static final Comparator<Map.Entry<String, VEntry>> COMPARE_MAP_ENTRY_BY_KEY =
		new  Comparator<Map.Entry<String, VEntry>>() {
		public int compare(Map.Entry<String, VEntry> e1, Map.Entry<String, VEntry> e2) {
			return e1.getKey().compareTo(e2.getKey());
		}
	};


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
				value = VEntry.merge(value, value2);
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

	//==================== Conversion to DataSource ====================

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
}
