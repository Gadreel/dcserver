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
package dcraft.util;

import java.net.IDN;
import java.util.Iterator;
import java.util.regex.Pattern;

import dcraft.struct.Struct;
import dcraft.struct.builder.JsonMemoryBuilder;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

public class StringUtil {
	private static final int PAD_LIMIT = 8192;
	private static final Pattern ASCII_PATTERN = Pattern.compile("[^\\p{ASCII}]+");
	
	/**
	 * check that characters are compatible with IntegerStruct value range
	 * 
	 * @param value characters to scan
	 * @return true if is an Integer
	 */
	  // as in - integer (long) that is database (mumps) compatible
	  public static boolean isDataInteger(CharSequence value) {
		  if ((value == null) || (value.length() == 0) || (value.length() > 18))
			  return false;
		  
		  for (int i = 0; i < value.length(); i++) { 
			  char ch = value.charAt(i);
			  
			  if ((i==0) && ((ch != '-') && ! Character.isDigit(ch)))
				  return false;
			  
			  if (!Character.isDigit(ch))
				  return false;
		  }
		  
		  return true;
	  }
	  
	  /**
	   * Attempts to parse a string into a long, using a default value if parse fails.
	   * (note that API wise, DivConq considers Integer to mean 64 bit)
	   * 
	   * @param value string to parse
	   * @param defvalue value to use if parse fails
	   * @return parsed number or default
	   */
	  public static long parseInt(String value, long defvalue) {
		  try {
		  	if (value != null)
				  return Long.parseLong(value.trim());
		  }
		  catch (Exception x) {			  
		  }
		  
		  return defvalue;
	  }	  

	  /**
	   * Attempts to parse a string into a Long
	   * 
	   * @param value string to parse
	   * @return parsed number or null
	   */
		public static Long parseInt(String value) {
	        if (StringUtil.isEmpty(value)) 	        	
	        	return null;

			try {
				return Long.parseLong(value);
			}
			catch (Exception x) {			  
			}
	        
			return null;
		}
		
		public static Long parseLeadingInt(String value) {
	        if (StringUtil.isEmpty(value)) 	        	
	        	return null;
	        
			int finalpos = 0;
			
			for (int i = 0; i < value.length(); i++) { 
				char ch = value.charAt(i);
				
				if ((i==0) && ((ch != '-') && !Character.isDigit(ch)))
					return null;
				
				if (!Character.isDigit(ch))
					break;
				
				finalpos = i;
			}

			try {
				return Long.parseLong(value.substring(0, finalpos + 1));
			}
			catch (Exception x) {			  
			}
	        
			return null;
		}

		/**
		 * check to see if characters contains anything but whitespace
		 *  
		 * @param str string to check
		 * @return true if contains non-whitespace
		 */
	  public static boolean isNotEmpty(CharSequence str) {
		  return !StringUtil.isEmpty(str);		  
	  }
	  
		/**
		 * check to see if characters contains only whitespace
		 *  
		 * @param str string to check
		 * @return true if contains only whitespace
		 */
	  public static boolean isEmpty(CharSequence str) {
		  if (str == null)
			  return true;
		  
	      int strLen = str.length();
	      
	      for (int i = 0; i < strLen; i++) 
	          if (! Character.isWhitespace(str.charAt(i))) 
	              return false;

	      return true;
	  }	  

		public static String toEmpty(String css) {
			if (css == null)
				return "";
			
			return css;
		}
	  
	  /*
	   * scan through characters and remove excess whitespace - per XML
	   * whitespace rules.
	   */
	    public static String stripWhitespacePerXml(CharSequence str) {
	        if (StringUtil.isEmpty(str)) 
	            return null;
	        
	        int sz = str.length();
	        char[] chs = new char[sz];
	        int count = 0;
	        boolean needSpace = false;
	        boolean fndAnyNonSpace = false;
	        
	        for (int i = 0; i < sz; i++) {
	            if (!Character.isWhitespace(str.charAt(i))) {
	            	if (needSpace)
	            		chs[count++] = ' ';
	            		
	                chs[count++] = str.charAt(i);
	                fndAnyNonSpace = true;
	                needSpace = false;
	            }
	            else 
	            	needSpace = fndAnyNonSpace;
	        }
	        
	        return new String(chs, 0, count);
	    }
		  
		  /*
		   * remove leading whitespace 
		   * whitespace rules.
		   */
		    public static String stripLeadingWhitespace(String str) {
		        if (StringUtil.isEmpty(str)) 
		            return str;
		        
		        int strLen = str.length();
		        int start = 0;
		        
	            while (start != strLen && Character.isWhitespace(str.charAt(start))) 
	                start++;

		        return str.substring(start);
		    }

			public static String trimLeadingChar(String source, char character) {
				for (int i = 0; i < source.length(); i++) {
					if (source.charAt(i) != character)
						return source.substring(i);
				}

				return "";
			}

		    public static String stripTrailingWhitespace(String str) {
		        if (StringUtil.isEmpty(str)) 
		            return "";

		        int end = str.length();
		        
	            while (end != 0 && Character.isWhitespace(str.charAt(end - 1))) 
	                end--;

		        return str.substring(0, end);
		    }
		    
		    public static String stripWhitespace(String str) {
		        if (StringUtil.isEmpty(str)) 
		            return str;
		        
		        int strLen = str.length();
		        int start = 0;
		        
	            while (start != strLen && Character.isWhitespace(str.charAt(start))) 
	                start++;

		        int end = strLen;
		        
	            while (end != 0 && Character.isWhitespace(str.charAt(end - 1))) 
	                end--;

		        return str.substring(start, end);
		    }
		    // Conversion
		    //-----------------------------------------------------------------------

		    // Padding
		    //-----------------------------------------------------------------------
		    /**
		     * <p>Repeat a String {@code repeat} times to form a
		     * new String.</p>
		     *
		     * <pre>
		     * StringUtils.repeat(null, 2) = null
		     * StringUtils.repeat("", 0)   = ""
		     * StringUtils.repeat("", 2)   = ""
		     * StringUtils.repeat("a", 3)  = "aaa"
		     * StringUtils.repeat("ab", 2) = "abab"
		     * StringUtils.repeat("a", -2) = ""
		     * </pre>
		     *
		     * @param str  the String to repeat, may be null
		     * @param repeat  number of times to repeat str, negative treated as zero
		     * @return a new String consisting of the original String repeated,
		     *  {@code null} if null String input
		     */
		    public static String repeat(final String str, final int repeat) {
		        // Performance tuned for 2.0 (JDK1.4)

		        if (str == null) {
		            return null;
		        }
		        if (repeat <= 0) {
		            return " ";
		        }
		        final int inputLength = str.length();
		        if (repeat == 1 || inputLength == 0) {
		            return str;
		        }
		        if (inputLength == 1 && repeat <= PAD_LIMIT) {
		            return repeat(str.charAt(0), repeat);
		        }

		        final int outputLength = inputLength * repeat;
		        switch (inputLength) {
		            case 1 :
		                return repeat(str.charAt(0), repeat);
		            case 2 :
		                final char ch0 = str.charAt(0);
		                final char ch1 = str.charAt(1);
		                final char[] output2 = new char[outputLength];
		                for (int i = repeat * 2 - 2; i >= 0; i--, i--) {
		                    output2[i] = ch0;
		                    output2[i + 1] = ch1;
		                }
		                return new String(output2);
		            default :
		                final StringBuilder buf = new StringBuilder(outputLength);
		                for (int i = 0; i < repeat; i++) {
		                    buf.append(str);
		                }
		                return buf.toString();
		        }
		    }

		    /**
		     * <p>Repeat a String {@code repeat} times to form a
		     * new String, with a String separator injected each time. </p>
		     *
		     * <pre>
		     * StringUtils.repeat(null, null, 2) = null
		     * StringUtils.repeat(null, "x", 2)  = null
		     * StringUtils.repeat("", null, 0)   = ""
		     * StringUtils.repeat("", "", 2)     = ""
		     * StringUtils.repeat("", "x", 3)    = "xxx"
		     * StringUtils.repeat("?", ", ", 3)  = "?, ?, ?"
		     * </pre>
		     *
		     * @param str        the String to repeat, may be null
		     * @param separator  the String to inject, may be null
		     * @param repeat     number of times to repeat str, negative treated as zero
		     * @return a new String consisting of the original String repeated,
		     *  {@code null} if null String input
		     * @since 2.5
		     */
		    public static String repeat(final String str, final String separator, final int repeat) {
		        if(str == null || separator == null) {
		            return repeat(str, repeat);
		        }
		        // given that repeat(String, int) is quite optimized, better to rely on it than try and splice this into it
		        final String result = repeat(str + separator, repeat);
		        return removeEnd(result, separator);
		    }

		    /**
		     * <p>Removes a substring only if it is at the end of a source string,
		     * otherwise returns the source string.</p>
		     *
		     * <p>A {@code null} source string will return {@code null}.
		     * An empty ("") source string will return the empty string.
		     * A {@code null} search string will return the source string.</p>
		     *
		     * <pre>
		     * StringUtils.removeEnd(null, *)      = null
		     * StringUtils.removeEnd("", *)        = ""
		     * StringUtils.removeEnd(*, null)      = *
		     * StringUtils.removeEnd("www.domain.com", ".com.")  = "www.domain.com"
		     * StringUtils.removeEnd("www.domain.com", ".com")   = "www.domain"
		     * StringUtils.removeEnd("www.domain.com", "domain") = "www.domain.com"
		     * StringUtils.removeEnd("abc", "")    = "abc"
		     * </pre>
		     *
		     * @param str  the source String to search, may be null
		     * @param remove  the String to search for and remove, may be null
		     * @return the substring with the string removed if found,
		     *  {@code null} if null String input
		     * @since 2.1
		     */
		    public static String removeEnd(final String str, final String remove) {
		        if (isEmpty(str) || isEmpty(remove)) {
		            return str;
		        }
		        if (str.endsWith(remove)) {
		            return str.substring(0, str.length() - remove.length());
		        }
		        return str;
		    }
		    /**
		     * <p>Returns padding using the specified delimiter repeated
		     * to a given length.</p>
		     *
		     * <pre>
		     * StringUtils.repeat('e', 0)  = ""
		     * StringUtils.repeat('e', 3)  = "eee"
		     * StringUtils.repeat('e', -2) = ""
		     * </pre>
		     *
		     * <p>Note: this method doesn't not support padding with
		     * <a href="http://www.unicode.org/glossary/#supplementary_character">Unicode Supplementary Characters</a>
		     * as they require a pair of {@code char}s to be represented.
		     * If you are needing to support full I18N of your applications
		     * consider using {@link #repeat(String, int)} instead.
		     * </p>
		     *
		     * @param ch  character to repeat
		     * @param repeat  number of times to repeat char, negative treated as zero
		     * @return String with repeated character
		     * @see #repeat(String, int)
		     */
		    public static String repeat(final char ch, final int repeat) {
		        final char[] buf = new char[repeat];
		        for (int i = repeat - 1; i >= 0; i--) {
		            buf[i] = ch;
		        }
		        return new String(buf);
		    }

		    /**
		     * <p>Right pad a String with spaces (' ').</p>
		     *
		     * <p>The String is padded to the size of {@code size}.</p>
		     *
		     * <pre>
		     * StringUtils.rightPad(null, *)   = null
		     * StringUtils.rightPad("", 3)     = "   "
		     * StringUtils.rightPad("bat", 3)  = "bat"
		     * StringUtils.rightPad("bat", 5)  = "bat  "
		     * StringUtils.rightPad("bat", 1)  = "bat"
		     * StringUtils.rightPad("bat", -1) = "bat"
		     * </pre>
		     *
		     * @param str  the String to pad out, may be null
		     * @param size  the size to pad to
		     * @return right padded String or original String if no padding is necessary,
		     *  {@code null} if null String input
		     */
		    public static String rightPad(final String str, final int size) {
		        return rightPad(str, size, ' ');
		    }

		    /**
		     * <p>Right pad a String with a specified character.</p>
		     *
		     * <p>The String is padded to the size of {@code size}.</p>
		     *
		     * <pre>
		     * StringUtils.rightPad(null, *, *)     = null
		     * StringUtils.rightPad("", 3, 'z')     = "zzz"
		     * StringUtils.rightPad("bat", 3, 'z')  = "bat"
		     * StringUtils.rightPad("bat", 5, 'z')  = "batzz"
		     * StringUtils.rightPad("bat", 1, 'z')  = "bat"
		     * StringUtils.rightPad("bat", -1, 'z') = "bat"
		     * </pre>
		     *
		     * @param str  the String to pad out, may be null
		     * @param size  the size to pad to
		     * @param padChar  the character to pad with
		     * @return right padded String or original String if no padding is necessary,
		     *  {@code null} if null String input
		     * @since 2.0
		     */
		    public static String rightPad(final String str, final int size, final char padChar) {
		        if (str == null) {
		            return null;
		        }
		        final int pads = size - str.length();
		        if (pads <= 0) {
		            return str; // returns original String when possible
		        }
		        if (pads > PAD_LIMIT) {
		            return rightPad(str, size, String.valueOf(padChar));
		        }
		        return str.concat(repeat(padChar, pads));
		    }

		    /**
		     * <p>Right pad a String with a specified String.</p>
		     *
		     * <p>The String is padded to the size of {@code size}.</p>
		     *
		     * <pre>
		     * StringUtils.rightPad(null, *, *)      = null
		     * StringUtils.rightPad("", 3, "z")      = "zzz"
		     * StringUtils.rightPad("bat", 3, "yz")  = "bat"
		     * StringUtils.rightPad("bat", 5, "yz")  = "batyz"
		     * StringUtils.rightPad("bat", 8, "yz")  = "batyzyzy"
		     * StringUtils.rightPad("bat", 1, "yz")  = "bat"
		     * StringUtils.rightPad("bat", -1, "yz") = "bat"
		     * StringUtils.rightPad("bat", 5, null)  = "bat  "
		     * StringUtils.rightPad("bat", 5, "")    = "bat  "
		     * </pre>
		     *
		     * @param str  the String to pad out, may be null
		     * @param size  the size to pad to
		     * @param padStr  the String to pad with, null or empty treated as single space
		     * @return right padded String or original String if no padding is necessary,
		     *  {@code null} if null String input
		     */
		    public static String rightPad(final String str, final int size, String padStr) {
		        if (str == null) {
		            return null;
		        }
		        if (isEmpty(padStr)) {
		            padStr = " ";
		        }
		        final int padLen = padStr.length();
		        final int strLen = str.length();
		        final int pads = size - strLen;
		        if (pads <= 0) {
		            return str; // returns original String when possible
		        }
		        if (padLen == 1 && pads <= PAD_LIMIT) {
		            return rightPad(str, size, padStr.charAt(0));
		        }

		        if (pads == padLen) {
		            return str.concat(padStr);
		        } else if (pads < padLen) {
		            return str.concat(padStr.substring(0, pads));
		        } else {
		            final char[] padding = new char[pads];
		            final char[] padChars = padStr.toCharArray();
		            for (int i = 0; i < pads; i++) {
		                padding[i] = padChars[i % padLen];
		            }
		            return str.concat(new String(padding));
		        }
		    }

		    /**
		     * <p>Left pad a String with spaces (' ').</p>
		     *
		     * <p>The String is padded to the size of {@code size}.</p>
		     *
		     * <pre>
		     * StringUtils.leftPad(null, *)   = null
		     * StringUtils.leftPad("", 3)     = "   "
		     * StringUtils.leftPad("bat", 3)  = "bat"
		     * StringUtils.leftPad("bat", 5)  = "  bat"
		     * StringUtils.leftPad("bat", 1)  = "bat"
		     * StringUtils.leftPad("bat", -1) = "bat"
		     * </pre>
		     *
		     * @param str  the String to pad out, may be null
		     * @param size  the size to pad to
		     * @return left padded String or original String if no padding is necessary,
		     *  {@code null} if null String input
		     */
		    public static String leftPad(final String str, final int size) {
		        return leftPad(str, size, ' ');
		    }

		    /**
		     * <p>Left pad a String with a specified character.</p>
		     *
		     * <p>Pad to a size of {@code size}.</p>
		     *
		     * <pre>
		     * StringUtils.leftPad(null, *, *)     = null
		     * StringUtils.leftPad("", 3, 'z')     = "zzz"
		     * StringUtils.leftPad("bat", 3, 'z')  = "bat"
		     * StringUtils.leftPad("bat", 5, 'z')  = "zzbat"
		     * StringUtils.leftPad("bat", 1, 'z')  = "bat"
		     * StringUtils.leftPad("bat", -1, 'z') = "bat"
		     * </pre>
		     *
		     * @param str  the String to pad out, may be null
		     * @param size  the size to pad to
		     * @param padChar  the character to pad with
		     * @return left padded String or original String if no padding is necessary,
		     *  {@code null} if null String input
		     * @since 2.0
		     */
		    public static String leftPad(final String str, final int size, final char padChar) {
		        if (str == null) {
		            return null;
		        }
		        final int pads = size - str.length();
		        if (pads <= 0) {
		            return str; // returns original String when possible
		        }
		        if (pads > PAD_LIMIT) {
		            return leftPad(str, size, String.valueOf(padChar));
		        }
		        return repeat(padChar, pads).concat(str);
		    }

		    /**
		     * <p>Left pad a String with a specified String.</p>
		     *
		     * <p>Pad to a size of {@code size}.</p>
		     *
		     * <pre>
		     * StringUtils.leftPad(null, *, *)      = null
		     * StringUtils.leftPad("", 3, "z")      = "zzz"
		     * StringUtils.leftPad("bat", 3, "yz")  = "bat"
		     * StringUtils.leftPad("bat", 5, "yz")  = "yzbat"
		     * StringUtils.leftPad("bat", 8, "yz")  = "yzyzybat"
		     * StringUtils.leftPad("bat", 1, "yz")  = "bat"
		     * StringUtils.leftPad("bat", -1, "yz") = "bat"
		     * StringUtils.leftPad("bat", 5, null)  = "  bat"
		     * StringUtils.leftPad("bat", 5, "")    = "  bat"
		     * </pre>
		     *
		     * @param str  the String to pad out, may be null
		     * @param size  the size to pad to
		     * @param padStr  the String to pad with, null or empty treated as single space
		     * @return left padded String or original String if no padding is necessary,
		     *  {@code null} if null String input
		     */
		    public static String leftPad(final String str, final int size, String padStr) {
		        if (str == null) {
		            return null;
		        }
		        if (isEmpty(padStr)) {
		            padStr = " ";
		        }
		        final int padLen = padStr.length();
		        final int strLen = str.length();
		        final int pads = size - strLen;
		        if (pads <= 0) {
		            return str; // returns original String when possible
		        }
		        if (padLen == 1 && pads <= PAD_LIMIT) {
		            return leftPad(str, size, padStr.charAt(0));
		        }

		        if (pads == padLen) {
		            return padStr.concat(str);
		        } else if (pads < padLen) {
		            return padStr.substring(0, pads).concat(str);
		        } else {
		            final char[] padding = new char[pads];
		            final char[] padChars = padStr.toCharArray();
		            for (int i = 0; i < pads; i++) {
		                padding[i] = padChars[i % padLen];
		            }
		            return new String(padding).concat(str);
		        }
		    }

		    /**
		     * Gets a CharSequence length or {@code 0} if the CharSequence is
		     * {@code null}.
		     *
		     * @param cs
		     *            a CharSequence or {@code null}
		     * @return CharSequence length or {@code 0} if the CharSequence is
		     *         {@code null}.
		     * @since 2.4
		     * @since 3.0 Changed signature from length(String) to length(CharSequence)
		     */
		    public static int length(final CharSequence cs) {
		        return cs == null ? 0 : cs.length();
		    }

		    // Centering
		    //-----------------------------------------------------------------------
		    /**
		     * <p>Centers a String in a larger String of size {@code size}
		     * using the space character (' ').</p>
		     *
		     * <p>If the size is less than the String length, the String is returned.
		     * A {@code null} String returns {@code null}.
		     * A negative size is treated as zero.</p>
		     *
		     * <p>Equivalent to {@code center(str, size, " ")}.</p>
		     *
		     * <pre>
		     * StringUtils.center(null, *)   = null
		     * StringUtils.center("", 4)     = "    "
		     * StringUtils.center("ab", -1)  = "ab"
		     * StringUtils.center("ab", 4)   = " ab "
		     * StringUtils.center("abcd", 2) = "abcd"
		     * StringUtils.center("a", 4)    = " a  "
		     * </pre>
		     *
		     * @param str  the String to center, may be null
		     * @param size  the int size of new String, negative treated as zero
		     * @return centered String, {@code null} if null String input
		     */
		    public static String center(final String str, final int size) {
		        return center(str, size, ' ');
		    }

		    /**
		     * <p>Centers a String in a larger String of size {@code size}.
		     * Uses a supplied character as the value to pad the String with.</p>
		     *
		     * <p>If the size is less than the String length, the String is returned.
		     * A {@code null} String returns {@code null}.
		     * A negative size is treated as zero.</p>
		     *
		     * <pre>
		     * StringUtils.center(null, *, *)     = null
		     * StringUtils.center("", 4, ' ')     = "    "
		     * StringUtils.center("ab", -1, ' ')  = "ab"
		     * StringUtils.center("ab", 4, ' ')   = " ab "
		     * StringUtils.center("abcd", 2, ' ') = "abcd"
		     * StringUtils.center("a", 4, ' ')    = " a  "
		     * StringUtils.center("a", 4, 'y')    = "yayy"
		     * </pre>
		     *
		     * @param str  the String to center, may be null
		     * @param size  the int size of new String, negative treated as zero
		     * @param padChar  the character to pad the new String with
		     * @return centered String, {@code null} if null String input
		     * @since 2.0
		     */
		    public static String center(String str, final int size, final char padChar) {
		        if (str == null || size <= 0) {
		            return str;
		        }
		        final int strLen = str.length();
		        final int pads = size - strLen;
		        if (pads <= 0) {
		            return str;
		        }
		        str = leftPad(str, strLen + pads / 2, padChar);
		        str = rightPad(str, size, padChar);
		        return str;
		    }

		    /**
		     * <p>Centers a String in a larger String of size {@code size}.
		     * Uses a supplied String as the value to pad the String with.</p>
		     *
		     * <p>If the size is less than the String length, the String is returned.
		     * A {@code null} String returns {@code null}.
		     * A negative size is treated as zero.</p>
		     *
		     * <pre>
		     * StringUtils.center(null, *, *)     = null
		     * StringUtils.center("", 4, " ")     = "    "
		     * StringUtils.center("ab", -1, " ")  = "ab"
		     * StringUtils.center("ab", 4, " ")   = " ab "
		     * StringUtils.center("abcd", 2, " ") = "abcd"
		     * StringUtils.center("a", 4, " ")    = " a  "
		     * StringUtils.center("a", 4, "yz")   = "yayz"
		     * StringUtils.center("abc", 7, null) = "  abc  "
		     * StringUtils.center("abc", 7, "")   = "  abc  "
		     * </pre>
		     *
		     * @param str  the String to center, may be null
		     * @param size  the int size of new String, negative treated as zero
		     * @param padStr  the String to pad the new String with, must not be null or empty
		     * @return centered String, {@code null} if null String input
		     * @throws IllegalArgumentException if padStr is {@code null} or empty
		     */
		    public static String center(String str, final int size, String padStr) {
		        if (str == null || size <= 0) {
		            return str;
		        }
		        if (isEmpty(padStr)) {
		            padStr = " ";
		        }
		        final int strLen = str.length();
		        final int pads = size - strLen;
		        if (pads <= 0) {
		            return str;
		        }
		        str = leftPad(str, strLen + pads / 2, padStr);
		        str = rightPad(str, size, padStr);
		        return str;
		    }
		    
	    /**
	     * replace single quotes with \'
	     * 
	     * @param str content to escape
	     * @return escaped content or null
	     */
	    public static String escapeSingleQuotes(String str) {
	    	if (str == null)
	    		return null;
	    	
	    	return str.replace("\'", "\\\'");
	    }
	    
	    // letters, digits, dash and underscore (doesn't bunch dashes)
	    public static String slug(String str) {
	    	if (StringUtil.isEmpty(str))
	    		return null;
	    	
	    	StringBuilder sb = new StringBuilder();

	    	str = str.trim();

			for (int i = 0; i < str.length(); i++) { 
				char ch = str.charAt(i);
				
				if ((ch == '_') || Character.isLetterOrDigit(ch))
					sb.append(Character.isUpperCase(ch) ? Character.toLowerCase(ch) : ch);
			  	else
				  	sb.append('-');
			}
			
			while (sb.charAt(0) == '-')
				sb.deleteCharAt(0);
			
			while (sb.charAt(sb.length() - 1) == '-')
				sb.deleteCharAt(sb.length() - 1);
	    	
			str = sb.toString();
			
			while (str.indexOf("--") != -1)
				str = str.replace("--", "-");

			str = ASCIIFoldingFilter.foldToASCII(str);

			return str;
	    }

	    /**
	     * return Memory (utf-8) containing given string
	     * 
	     * @param att string to copy into memory
	     * @return memory containing the string
	     */
		public static Memory toMemory(String att) {
			JsonMemoryBuilder rb = new JsonMemoryBuilder();
			rb.write(att);
			return rb.getMemory();
		}
		
		/**
		 * generate a user friendly security code - for user confirmation, password resets and the like
		 * 
		 * @return 12 character security code
		 */
		public static String buildSecurityCode() {
			return randomChars(12, "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789").toString();
		}
		
		/*
		 * generate a user friendly security code - for user confirmation, password resets and the like
		 * 
		 * @return security code
		 */
		public static String buildSecurityCode(int chars) {
			return randomChars(chars, "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789").toString();
		}
		
		public static String buildSimpleCode(int chars) {
			return randomChars(chars, "ABCDEFGHIJKLMNPQRSTUVWXYZ").toString();
		}
		
		public static CharSequence randomChars(int count, String charPool) {
			if (isEmpty(charPool) || (count < 1))
				return "";
			
	        StringBuilder buf = new StringBuilder(count);
			
	        for (int i = 0; i < count; i++)
	        	buf.append(charPool.charAt(RndUtil.random.nextInt(charPool.length())));
	        
	        return buf;
		}

		/**
		 * check characters to see if they contain restricted characters.  character x7F is not
		 * allowed.  characters x00 - x08 are not allowed.  characters x0E - x1F are not allowed.
		 * 
		 * @param str characters to check
		 * @return true if any restricted characters are present
		 */
		public static boolean containsRestrictedChars(CharSequence str) {
			if (str == null)
				return false;
			
			for (int i = 0; i < str.length(); i++) {
				int ch = str.charAt(i);
				
	            if ((ch < (int)0x9) || ((ch < (int)0x20) && (ch > (int)0xD)) || (ch == (int)0x7F)) 
	            	return true;
			}
			
			return false;
		}

		/**
		 * check characters to see if they contain restricted characters.  character x7F is not
		 * allowed.  characters x00 - x08 are not allowed.  characters x0E - x1F are not allowed.
		 * 
		 * @param ch character to check
		 * @return true if any restricted characters are present
		 */
		public static boolean isRestrictedChar(int ch) {
            if ((ch < (int)0x9) || ((ch < (int)0x20) && (ch > (int)0xD)) || (ch == (int)0x7F)) 
            	return true;
			
			return false;
		}
	
		static public String stripAllNonAscii(String input) {
			if (input == null)
				return null;

			return ASCII_PATTERN.matcher(input).replaceAll(" ");
		}

		public static String alignRight(String val, char pad, int width) {
			if (val.length() > width) {
				val = val.substring(0, width - 1) + "*";
				return val;
			}
			
			while (val.length() < width)
				val = pad + val;
			
			return val;
		}	

		public static String alignLeft(String val, char pad, int width) {
			if (val == null)
				val = "";
			
			if (val.length() > width) {
				val = "*" + val.substring(0, width - 1);
				return val;
			}
			
			while (val.length() < width)
				val = val + pad;
			
			return val;
		}	

	    /*
	     */
	    public static String join(String[] strings, String separator) {
	        if ((strings == null) || (strings.length == 0)) 
	            return "";
	        
	        if (strings.length == 1) 
	            return strings[0];

	        // two or more elements
	        StringBuilder buf = new StringBuilder(1024);

	        for (int i = 0; i < strings.length; i++) {
	            if ((i > 0) && isNotEmpty(separator)) 
	                buf.append(separator);
	            
	            String el = strings[i];
		        
		        if (isNotEmpty(el)) 
		            buf.append(el);
	        }
	        
	        return buf.toString();
	    }
	    
	    public static String join(String[] strings, String separator, int startIndex, int endIndex) {
	        if ((strings == null) || (strings.length == 0)) 
	            return "";

	        int noOfItems = endIndex - startIndex;
	        
	        if (noOfItems <= 0) 
	            return "";

	        StringBuilder buf = new StringBuilder(noOfItems * 24);

	        for (int i = startIndex; i < endIndex; i++) {
	            if ((i > startIndex) && isNotEmpty(separator))
	                buf.append(separator);
	            
	            String el = strings[i];
		        
		        if (isNotEmpty(el)) 
		            buf.append(el);
	        }
	        
	        return buf.toString();
	    }
	    
	    /*
	     */
	    public static String join(Iterator<String> strings, String separator) {
	        if (strings == null) 
	            return null;

	        if (!strings.hasNext()) 
	            return "";

	        String first = strings.next();
	        
	        if (!strings.hasNext()) 
	            return first;

	        StringBuilder buf = new StringBuilder(1024); 
	        
	        if (isNotEmpty(first)) 
	            buf.append(first);

	        while (strings.hasNext()) {
	            if (isNotEmpty(separator)) 
	                buf.append(separator);

	            String el = strings.next();
	            
	            if (isNotEmpty(el))
	                buf.append(el);
	        }
	        
	        return buf.toString();
	    }

	    /*
	     */
	    public static String join(Iterable<String> strings, String separator) {
	    	if (strings == null)
	    		return null;
	    	
	    	return join(strings.iterator(), separator);
	    }
	
	static public String cleanPhone(String phone) {
		if (phone == null)
			return null;
		
		StringBuilder psb = new StringBuilder();
		
		phone = StringUtil.stripWhitespace(phone);
		int strLen = phone.length();
		int start = 0;
		
		while (start != strLen) {
			if (Character.isDigit(phone.charAt(start)))
				psb.append(phone.charAt(start));
			
			start++;
		}
		
		return psb.toString();
	}

	public static int countOccurrences(String haystack, char needle) {
		int count = 0;

		for (int i=0; i < haystack.length(); i++) {
			if (haystack.charAt(i) == needle)
				count++;
		}

		return count;
	}

	public static String apiClean(Object source, int maxlength) {
	    String in = Struct.objectToString(source);

	    if (StringUtil.isEmpty(in))
	    	return "";

	    String out = ASCIIFoldingFilter.foldToASCII(in);

	    if (out.length() > maxlength)
	    	out = out.substring(0, maxlength);

	    return out;
	}
}
