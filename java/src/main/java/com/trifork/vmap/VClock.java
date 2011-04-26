/**
 * 
 */
package com.trifork.vmap;

import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;

import java.security.MessageDigest;

import com.trifork.multiversion_common.Digestable;

public class VClock implements Digestable {

	final String[] peers;
	final int[] counters;
	final int[] utc_secs;
	final int max_secs;

	/** OBS: Parameter is mutated (sorted). */
	public VClock(Entry<String, Time>[] ents) {
		Arrays.sort(ents, BY_PEER);
		this.peers = new String[ents.length];
		this.counters = new int[ents.length];
		this.utc_secs = new int[ents.length];
		
		for (int i = 0; i < ents.length; i++) {
			peers[i] = ents[i].getKey();
			counters[i] = ents[i].getValue().count;
			utc_secs[i] = ents[i].getValue().time;
		}
		this.max_secs = max(utc_secs);
	}

	public VClock(Map<String, Time> map) {
		this(entriesByTime(map.entrySet()));
	}

	public VClock(String[] peers, int[] counters, int[] times) {
		if (peers.length != counters.length ||
			peers.length != times.length)
			throw new IllegalArgumentException("VClock: array lengths do not match: "+peers.length+"/"+counters.length+"/"+times.length);

		this.peers = peers;
		this.counters = counters;
		this.utc_secs = times;
		this.max_secs = max(utc_secs);

		for (int i=1; i<peers.length; i++) {
			if (peers[i].compareTo(peers[i-1]) <= 0)
				throw new IllegalArgumentException("VClock: entries are not sorrted by peer name: "+this);
			// TODO: Handle more gracefully?
		}
	}

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

	public void timeStamp(String peer) {
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
	public Map<String, Time> updateLUB(Map<String, Time> lub) {
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

	public static Map<String,Time> incrementForPeer(Map<String,Time> map, String peer) {
		map.put(peer, Time.increment(map.get(peer)));
		return map;
	}

	/** Binary LUB operator. */
	public static VClock lub(VClock a, VClock b) {
		return new VClock(b.updateLUB(a.updateLUB(new HashMap<String,Time>())));
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
			String peer1 = vc1.peers[i1];
			String peer2 = vc2.peers[i2];
			int peercmp = peer1.compareTo(peer2);
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
				md.update(peers[i].getBytes("UTF-8"));
				md.update(String.valueOf(counters[i]).getBytes("US-ASCII"));
				md.update(String.valueOf(utc_secs[i]).getBytes("US-ASCII"));
			}
		} catch (java.io.UnsupportedEncodingException uee) {
			throw new RuntimeException(uee); // Should never happen.
		}
	}

	//==================== Construction helpers ====================

	/** Convert an Entry set to a array, sorted by time. */
	protected static Entry<String, Time>[] entriesByTime(Collection<Entry<String, Time>> org) {
		Entry<String, Time>[] entries = (org.toArray(new Entry[org.size()]));
		Arrays.sort(entries, VClock.BY_PEER);
		return entries;
	}

	final static Comparator<Entry<String, Time>> BY_PEER =
		new Comparator<Entry<String, Time>>() {
			@Override
			public int compare(Entry<String, Time> o1, Entry<String, Time> o2) {
				String peer1 = o1.getKey();
				String peer2 = o2.getKey();
				return peer1.compareTo(peer2);
			}
	};

	final static Comparator<Entry<String, Time>> BY_TIME =
		new Comparator<Entry<String, Time>>() {
			@Override
			public int compare(Entry<String, Time> o1, Entry<String, Time> o2) {
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