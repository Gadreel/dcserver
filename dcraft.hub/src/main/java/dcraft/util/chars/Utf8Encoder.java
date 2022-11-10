/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.util.chars;

import io.netty.buffer.ByteBuf;

import java.io.UnsupportedEncodingException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

public class Utf8Encoder {
	static public byte[] encode(CharSequence chars) {
		if (chars == null)
			return null;

		return chars.toString().getBytes(StandardCharsets.UTF_8);
	}

	static public byte[] encode(int ch) {
		if (ch == -1)
			return null;

		return Character.toString(ch).getBytes(StandardCharsets.UTF_8);
	}
}
