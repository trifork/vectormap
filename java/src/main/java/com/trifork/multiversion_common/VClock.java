/**
 * 
 */
package com.trifork.multiversion_common;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.Iterator;

import java.security.MessageDigest;

import com.google.protobuf.ByteString;

public class VClock implements Digestable, Iterable<VClock.VClockEntry> {

	final ByteString[] peers;
	final int[] counters;
	final int[] utc_secs;
	final int max_secs;

	/** OBS: Parameter is mutated (sorted). */
	public VClock(Entry<ByteString, Time>[] ents) {
		Arrays.sort(ents, BY_PEER);
		this.peers = new ByteString[ents.length];
		this.counters = new int[ents.length];
		this.utc_secs = new int[ents.length];
		
		for (int i = 0; i < ents.length; i++) {
			peers[i] = ents[i].getKey();
			counters[i] = ents[i].getValue().count;
			utc_secs[i] = ents[i].getValue().time;
		}
		this.max_secs = max(utc_secs);
	}

	public VClock(Map<ByteString, Time> map) {
		this(entriesByTime(map.entrySet()));
	}

	public VClock(ByteString[] peers, int[] counters, int[] times) {
		if (peers.length != counters.length ||
			peers.length != times.length)
			throw new IllegalArgumentException("VClock: array lengths do not match: "+peers.length+"/"+counters.length+"/"+times.length);

		this.peers = peers;
		this.counters = counters;
		this.utc_secs = times;
		this.max_secs = max(utc_secs);

		for (int i=1; i<peers.length; i++) {
			if (CompareUtil.compareByteStrings(peers[i], peers[i-1]) <= 0)
				throw new IllegalArgumentException("VClock: entries are not sorted by peer name: "+this);
			// TODO: Handle more gracefully?
		}
	}

	/*------------- Accessors --------------------------*/
	public int size() {
		return peers.length;
	}
	
	public ByteString getPeer(int nr) {
		return peers[nr];
	}

	public int getCounter(int nr) {
		return counters[nr];
	}

	public int getUtcSecs(int nr) {
		return utc_secs[nr];
	}

	public int getMaxSecs() {
		return max_secs;
	}
	
	public Iterator<VClockEntry> iterator() {
		return new Iterator<VClockEntry>() {
			int pos = 0;
			public boolean hasNext() {return pos < peers.length;}
			public VClockEntry next() {
				int nr = pos++;
				return new VClockEntry(peers[nr], counters[nr], utc_secs[nr]);
			}
			public void remove() {throw new UnsupportedOperationException();}
		};
	}
	
	public static class VClockEntry /* implements Map.Entry<ByteString, Time> */ {
		public final ByteString peer;
		public final int counter;
		public final int utc_secs;
		
		public VClockEntry(ByteString peer, int counter, int utcSecs) {
			super();
			this.peer = peer;
			this.counter = counter;
			utc_secs = utcSecs;
		}
		
//		public String getKey() {return peer;}
//		public Time getValue() {return new Time(counter, utc_secs);}
	}

	/*--------------------------------------------------*/

	protected static int max(int[] xs) {
		int max = Integer.MIN_VALUE;
 		for (int x : xs) max = Math.max(max, x);
		return max;
	}

	public static class Time {
		final int count;
		final int time;

		public Time(int count, int time) {
			this.count = count;
			this.time = time;
		}

		public Time increment() {
			return new Time(count + 1, currentTimeSeconds());
		}

		public boolean equals(Object o) {
			if (! (o instanceof Time)) return false;
			Time other = (Time) o;
			return (count==other.count &&
					time ==other.time);
		}

		public String toString() {
			return "Time("+count+","+time+")";
		}

		/** Derive an incremented time - or, given null, create a new
		 *  Time with a count of one.
		 *  In both cases, the timestamp of the returned Time is the
		 *  present time. */
		public static Time increment(Time org) {
			return (org!=null)
				? org.increment()
				: new Time(1, currentTimeSeconds());
		}

		public static int currentTimeSeconds() {
			return (int) (System.currentTimeMillis()/1000);
		}
	}

	public void timeStamp(ByteString peer) {
		for (int i = 0; i < peers.length; i++) {
			if (peers[i].equals(peer)) {
				utc_secs[i] = (int) (System.currentTimeMillis()/1000);
				break;
			}
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		boolean first = true;
		for (int i = 0; i < peers.length; i++) {
			if (!first) sb.append(","); else first=false;
			sb.append("{").
				append(peers[i]).
				append(",").
				append(counters[i]).
				append(",").
				append(utc_secs[i]). // TODO: format as datetime?
				append("}");
		}
		return sb.append("]").toString();
	}

	public boolean equals(Object other) {
		return (other instanceof VClock) && equals((VClock) other);
	}

	public boolean equals(VClock other) {
		return Arrays.equals(peers,    other.peers)
			&& Arrays.equals(counters, other.counters)
			&& Arrays.equals(utc_secs, other.utc_secs);
	}

	//==================== LUB computation ====================

	/** Update lowest upper bound. */
	public Map<ByteString, Time> updateLUB(Map<ByteString, Time> lub) {
		for (int i = 0; i < peers.length; i++) {
			Time t = lub.get(peers[i]);

			int new_count = t==null ? 0 : t.count;
			int new_time  = t==null ? 0 : t.time;
			boolean update = false;
			if (new_count < counters[i]) {
				new_count = counters[i]; update = true;
			}
			if (new_time < utc_secs[i]) {
				new_time = utc_secs[i]; update = true;
			}
			if (update) lub.put(peers[i], new Time(new_count, new_time));
		}
		return lub;
	}

	public static Map<ByteString,Time> incrementForPeer(Map<ByteString,Time> map, ByteString peer) {
		map.put(peer, Time.increment(map.get(peer)));
		return map;
	}

	/** Binary LUB operator. */
	public static VClock lub(VClock a, VClock b) {
		return new VClock(b.updateLUB(a.updateLUB(new HashMap<ByteString,Time>())));
	}

	/** Unary increment operator */
	public VClock incrementForPeer(ByteString peer) {
		return new VClock(incrementForPeer(updateLUB(new HashMap<ByteString,Time>()),
										   peer));
	}



	//==================== Comparison ====================
	public static final int SAME       = 0;
	public static final int BEFORE     = (1 << 0);
	public static final int AFTER      = (1 << 1);
	public static final int CONCURRENT = BEFORE | AFTER;

	public static int compare(VClock vc1, VClock vc2) {
		int result = SAME;
		int len1 = vc1.peers.length;
		int len2 = vc2.peers.length;

		int i1 = 0, i2 = 0;
		while (i1 < len1 && i2 < len2) {
			ByteString peer1 = vc1.peers[i1];
			ByteString peer2 = vc2.peers[i2];
			int peercmp = CompareUtil.compareByteStrings(peer1, peer2);
			if (peercmp == 0) {
				int cnt1 = vc1.counters[i1];
				int cnt2 = vc2.counters[i2];
				if (cnt1 < cnt2) { // vc2 is newer
					result |= BEFORE;
				} else if (cnt1 > cnt2) { // vc1 is newer
					result |= AFTER;
				} else if (/* cnt1==cnt2 && */ 
						vc1.utc_secs[i1] != vc2.utc_secs[i2]) {
					result |= CONCURRENT;
				}
				i1++; i2++;
			} else if (peercmp < 0) { // peer 1 not present in vc2
				result |= AFTER;
				i1++;
			} else { // peer 2 not present in vc1
				result |= BEFORE;
				i2++;
			}
			if (result==CONCURRENT) break; // Nothing more to do.
		}

		if (i1 < len1) { // peer 1 not present in vc2
			result |= AFTER;
		}
		if (i2 < len2) { // peer 2 not present in vc1
			result |= BEFORE;
		}

		return result;
	}

	//==================== Hashing ==============================

	public void updateDigest(MessageDigest md) {
		// TODO: Add separators?
		try {
			for (int i=0; i<peers.length; i++) {
				// TODO: Perhaps use a CharsetEncoder instead.
				md.update(peers[i].asReadOnlyByteBuffer());
				md.update(String.valueOf(counters[i]).getBytes("US-ASCII"));
				md.update(String.valueOf(utc_secs[i]).getBytes("US-ASCII"));
			}
		} catch (java.io.UnsupportedEncodingException uee) {
			throw new RuntimeException(uee); // Should never happen.
		}
	}

	//==================== Construction helpers ====================

	/** Convert an Entry set to a array, sorted by time. */
	protected static Entry<ByteString, Time>[] entriesByTime(Collection<Entry<ByteString, Time>> org) {
		Entry<ByteString, Time>[] entries = org.toArray((Entry<ByteString, Time>[]) new Entry[org.size()]);
		Arrays.sort(entries, VClock.BY_PEER);
		return entries;
	}

	final static Comparator<Entry<ByteString, Time>> BY_PEER =
		new Comparator<Entry<ByteString, Time>>() {
			@Override
			public int compare(Entry<ByteString, Time> o1, Entry<ByteString, Time> o2) {
				ByteString peer1 = o1.getKey();
				ByteString peer2 = o2.getKey();
				return CompareUtil.compareByteStrings(peer1, peer2);
			}
	};

	final static Comparator<Entry<ByteString, Time>> BY_TIME =
		new Comparator<Entry<ByteString, Time>>() {
			@Override
			public int compare(Entry<ByteString, Time> o1, Entry<ByteString, Time> o2) {
				int time1 = o1.getValue().time;
				int time2 = o2.getValue().time;
				if (time1 > time2) {
					return -1;
				} else if (time1 < time2) {
					return 1;
				} else {
					return 0;
				}
			}
	};
}