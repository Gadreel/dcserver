package dcraft.util;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 */
public class RndUtil {
	static public SecureRandom random = new SecureRandom();
	
	// only use this in tests or completely safe functions, this is not meant to provide secure random services to entire framework
	// SecureRandom is way too slow for what we need here
	static public Random testrnd = new Random();
	
	static public String nextUUId() {
		return UUID.randomUUID().toString().replace("-", "");
	}
}
