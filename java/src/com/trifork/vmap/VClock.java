/**
 * 
 */
package com.trifork.vmap;

import java.util.Map;
import java.util.Map.Entry;

public class VClock {

	final String[] peers;
	final int[] counters;
	final long[] utc_millis;

	public VClock(String[] peers, int[] counters, long[] times) {
		this.peers = peers;
		this.counters = counters;
		this.utc_millis = times;
	}

	public VClock(Entry<String, Time>[] ents) {
		this.peers = new String[ents.length];
		this.counters = new int[ents.length];
		this.utc_millis = new long[ents.length];

		for (int i = 0; i < ents.length; i++) {
			peers[i] = ents[i].getKey();
			counters[i] = ents[i].getValue().count;
			utc_millis[i] = ents[i].getValue().time;
		}
	}

	public static class Time {
		int count;
		long time;

		public Time(int count, long time) {
			this.count = count;
			this.time = time;
		}

		public Time increment() {
			return new Time(count + 1, System.currentTimeMillis());
		}
	}

	public void updateMax(Map<String, Time> max) {
		for (int i = 0; i < peers.length; i++) {
			Time t = max.get(peers[i]);
			if (t == null || counters[i] > t.count) {
				max.put(peers[i], new Time(counters[i], utc_millis[i]));
			}
		}
	}

	public void timeStamp(String peer) {
		for (int i = 0; i < peers.length; i++) {
			if (peers[i].equals(peer)) {
				utc_millis[i] = System.currentTimeMillis();
				break;
			}
		}
	}

}