package com.trifork.vmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataSource;

import com.google.protobuf.ByteString;
import com.trifork.activation.BSDataSource;
import com.trifork.activation.RichDataSource;
import com.trifork.multiversion_common.VClock;
import com.trifork.vmap.protobuf.PB.PBClock;
import com.trifork.vmap.protobuf.PB.PBEntry;
import com.trifork.vmap.protobuf.PB.PBValue;
import com.trifork.vmap.protobuf.PB.PBVectorMap;

/** Utility to read from application/x-protobuf;proto=vectormap byte stream */
public class PBDecoder {

	public VectorMap decode(InputStream in) throws IOException {

		PBVectorMap vm = PBVectorMap.parseFrom(in);

		VClock[] clocks = new VClock[vm.getClockPoolCount()];
		for (int i = 0; i < vm.getClockPoolCount(); i++) {
			clocks[i] = decodeVClock(vm.getClockPool(i), vm);
		}

		Map<String, VEntry> map = new HashMap<String, VEntry>();
		for (int i = 0; i < vm.getEntriesCount(); i++) {
			PBEntry ent = vm.getEntries(i);
			VEntry e = decode(ent, clocks, vm);
			map.put(ent.getKey(), e);
		}

		return new VectorMap(map);
	}

	private static VClock decodeVClock(PBClock pbc, PBVectorMap vm)
			throws IOException {

		if (pbc.getCounterCount() != pbc.getNodeCount()
				|| pbc.getCounterCount() != pbc.getUtcSecsCount()) {
			throw new IOException("bad vclock encoding");
		}

		int length = pbc.getCounterCount();

		ByteString[] peers = new ByteString[length];
		int[] counters = new int[length];
		int[] times = new int[length];

		for (int i = 0; i < length; i++) {
			peers[i] = ByteString.copyFromUtf8((vm.getStringPool(pbc.getNode(i)))); //TODO: store as 'bytes' ?
			counters[i] = pbc.getCounter(i);
			times[i] = pbc.getUtcSecs(i);
		}

		return new VClock(peers, counters, times);
	}

	private static VEntry decode(PBEntry ent, VClock[] clocks, PBVectorMap vm) {
		RichDataSource[] values = decodeValues(ent, vm);
		return new VEntry(clocks[ent.getClock()], values);
	}

	private static RichDataSource[] decodeValues(PBEntry ent, PBVectorMap vm) {
		RichDataSource[] results = new RichDataSource[ent.getValuesCount()];
		for (int i = 0; i < results.length; i++) {
			results[i] = RichDataSource.make(decodeValue(ent.getValues(i), vm));
		}
		return results;
	}

	private static DataSource decodeValue(PBValue value, PBVectorMap vm) {
		if (!value.hasContent())
			return null;
		return new BSDataSource(vm.getStringPool(value.getMimeType()), value
				.getContent());
	}

}
