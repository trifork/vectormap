/**
 * 
 */
package com.trifork.vmap;

import java.util.Map;
import java.util.Map.Entry;

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

	public VClock(Entry<String, Time>[] ents) {
		this.peers = new String[ents.length];
		this.counters = new int[ents.length];
		this.utc_secs = new int[ents.length];

		for (int i = 0; i < ents.length; i++) {
			peers[i] = ents[i].getKey();
			counters[i] = ents[i].getValue().count;
			utc_secs[i] = ents[i].getValue().time;
		}
	}

	public static class Time {
		final int count;
		final int time;

		public Time(int count, int time) {
			this.count = count;
			this.time = time;
		}

		public Time increment() {
			return new Time(count + 1, (int) (System.currentTimeMillis()/1000));
		}

		public boolean equals(Object o) {
			if (! (o instanceof Time)) return false;
			Time other = (Time) o;
			return (count==other.count &&
					time ==other.time);
		}
	}

	public Map<String, Time> updateMax(Map<String, Time> max) {
		for (int i = 0; i < peers.length; i++) {
			Time t = max.get(peers[i]);
			if (t == null || counters[i] > t.count) {
				max.put(peers[i], new Time(counters[i], utc_secs[i]));
			}
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

}