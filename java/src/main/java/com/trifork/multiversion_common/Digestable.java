package com.trifork.activation;

import java.security.MessageDigest;

public interface Digestable {
	void updateDigest(MessageDigest md);
}
