package com.trifork.multiversion_common;

import java.util.Map;

import com.trifork.vmap.protobuf.PB.PBClock;
import com.trifork.vmap.protobuf.PB.PBClock.Builder;

public abstract class VClockPBUtil {

	public static PBClock encodeVClock(Map<String, Integer> string_pool, VClock clock) {
		PBClock.Builder cb = PBClock.newBuilder();
	
		for (int p = 0; p < clock.size(); p++) {
			cb.addNode(string_pool.get(clock.getPeer(p).toStringUtf8()));
			cb.addCounter(clock.getCounter(p));
			cb.addUtcSecs(clock.getUtcSecs(p));				
		}
		
		return cb.build();
	}

}
