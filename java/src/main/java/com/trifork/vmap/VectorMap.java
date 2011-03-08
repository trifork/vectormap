package com.trifork.vmap;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

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
		Map<String, VClock.Time> max = new HashMap<String, VClock.Time>();
		for (VEntry ent : content.values()) {
			ent.vClock.updateMax(max);
		}

		Time thisTime = max.get(thisPeer);
		
		max.put(thisPeer, thisTime = Time.increment(thisTime));
		return new VClock(max);
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
		try {
			put(key, (DataSource)null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void put(String key, String string) throws IOException {
		put(key, "text/plain;charset=utf-8", string);
	}

	public void put(String key, byte[] data) throws IOException {
		put(key, "application/binary", data);
	}

	public void put(String key, String mimeType, Object value)
			throws IOException {
		put(key, new DataHandler(value, mimeType).getDataSource());
	}

	public void put(String key, DataSource ds) throws IOException {
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

}
