package com.trifork.multiversion_common;

import java.security.MessageDigest;

public interface Digestable {
	void updateDigest(MessageDigest md);
}
