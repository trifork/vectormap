package com.trifork.multiversion_common;

import java.util.Comparator;

import com.google.protobuf.ByteString;

/** Handy comparators. */
public class CompareUtil {
	public static final Comparator<byte[]> COMPARE_BYTE_ARRAY =
		new Comparator<byte[]>() {
		public int compare(byte[] o1, byte[] o2) {
			return compareByteArrays(o1, o2);
		}
	};

	public static int compareByteArrays(byte[] a, byte[] b) {
		int la = a.length, lb = b.length;
		for (int i=0; i<la && i<lb; i++) {
			int diff = a[i] - b[i];
			if (diff != 0) return diff;
		}
		return la - lb;
	}


	public static final Comparator<ByteString> COMPARE_BYTE_STRING =
		new Comparator<ByteString>() {
		public int compare(ByteString a, ByteString b) {
			return compareByteStrings(a, b);
		}
	};

	public static int compareByteStrings(ByteString a, ByteString b) {
		return a.asReadOnlyByteBuffer().compareTo(b.asReadOnlyByteBuffer());
	}
}