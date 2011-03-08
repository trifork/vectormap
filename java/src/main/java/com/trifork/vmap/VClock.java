/**
 * 
 */
package com.trifork.vmap;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;

public class VClock {

	final String[] peers;
	final int[] counters;
	final int[] utc_secs;

	public VClock(String[] peers, int[] counters, int[] times) {
		if (peers.length != counters.length ||
			peers.length != times.length)
			throw new IllegalArgumentException("VClock: array lengths do not match: "+peers.length+"/"+counters.length+"/"+times.length);
		this.peers = peers;
		this.counters = counters;
		this.utc_secs = times;
	}

	protected VClock(Entry<String, Time>[] ents) {
		this.peers = new String[ents.length];
		this.counters = new int[ents.length];
		this.utc_secs = new int[ents.length];

		for (int i = 0; i < ents.length; i++) {
			peers[i] = ents[i].getKey();
			counters[i] = ents[i].getValue().count;
			utc_secs[i] = ents[i].getValue().time;
		}
	}

	public VClock(Map<String, Time> map) {
		this(entriesByTime(map.entrySet()));
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

	public Map<String, Time> updateMax(Map<String, Time> max) {
		for (int i = 0; i < peers.length; i++) {
			Time t = max.get(peers[i]);

			int new_count = t==null ? 0 : t.count;
			int new_time  = t==null ? 0 : t.time;
			boolean update = false;
			if (new_count < counters[i]) {
				new_count = counters[i]; update = true;
			}
			if (new_time < utc_secs[i]) {
				new_time = utc_secs[i]; update = true;
			}
			if (update) max.put(peers[i], new Time(new_count, new_time));
		}
		return max;
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

	//==================== Comparison ====================
	public static final int SAME       = 0;
	public static final int BEFORE     = (1 << 1);
	public static final int AFTER      = (1 << 2);
	public static final int CONCURRENT = BEFORE | AFTER;

	public static int compare(VClock vc1, VClock vc2) {
		int result = SAME;
		int len1 = vc1.peers.length;
		int len2 = vc2.peers.length;
		for (int i1 = 0, i2 = 0;
			 i1 < len1 && i2 < len2;
			 ) {
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
		return result;
	}


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