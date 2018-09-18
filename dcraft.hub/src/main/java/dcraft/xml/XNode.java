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
package dcraft.xml;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.CompositeStruct;
import dcraft.struct.IPartSelector;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.Memory;

/**
 * Super class to support all xml classes (XElement and XTex)
 * 
 * @author Andy
 *
 */
public abstract class XNode extends CompositeStruct {
	protected int line = 0;
	protected int col = 0;
	
  /**
   * Returns formatted or unformatted XML source.
   * 
   * @param formatted
   *            whether to return formatted XML source. If true, the source is
   *            pretty-printed with new lines and indentation. If false, the XML
   *            string is returned as one lone, unformatted line.
   * @return a String containing the XML source
   */
  public String toString(boolean formatted) {
	  StringBuilder sb = new StringBuilder();
	  return toString(sb, formatted, 0).toString();
  }

  /**
   * Internal method used recursively to format XML with appropriate
   * indentation.
   * 
   * @param sb
   *            destination for xml (character) output
   * @param formatted
   *            whether to return formatted XML source. If true, the source is
   *            pretty-printed with new lines and indentation. If false, the XML
   *            string is returned as one long, unformatted line.
   * @param level
   *            the indentation level used to write leading spaces
   * 
   * @return a String containing the XML source
   */
  protected abstract StringBuilder toString(StringBuilder sb, boolean formatted, int level);
	
	/**
	 * sets the XML source code location information for this element
	 *
	 * @param line
	 *            the line number of the start tag
	 * @param col
	 *            the column number of the start tag
	 */
	public XNode withLocation(int line, int col) {
		this.line = line;
		this.col = col;
		
		return this;
	}
	
	/**
	 * gets the XML source code line number where this element was declared.
	 * This number will be 0 if it was never set.
	 *
	 * @return the XML source code line number of this element's declaration
	 */
	public int getLine() {
		return this.line;
	}
	
	/**
	 * gets the XML source code cloumn number where this element was declared.
	 * This number will be 0 if it was never set.
	 *
	 * @return the XML source code column number of this element's declaration
	 */
	public int getCol() {
		return this.col;
	}

	  /**
	   * Returns formatted or unformatted XML source.
	   * 
	   * @param formatted
	   *            whether to return formatted XML source. If true, the source is
	   *            pretty-printed with new lines and indentation. If false, the XML
	   *            string is returned as one lone, unformatted line.
	   * @return Memory containing the XML source (utf-8)
	   */
	public Memory toMemory(boolean formatted) {
		Memory m = new Memory();
		this.toMemory(m, formatted, 0);
		return m;
	}
	
	abstract protected void toMemory(Memory sb, boolean formatted, int level);
	
	@Override
	protected void doCopy(Struct n) {
		super.doCopy(n);
		
		XNode copy = (XNode) n;
		
		copy.line = this.line;
		copy.col = this.col;
	}
	
	@Override
	public boolean checkLogic(IParentAwareWork stack, XElement source) {
		return false;
	}
	
	@Override
	abstract public XNode deepCopy();
	
	/**
	 * quotes a string according to XML rules. When attributes and text elements
	 * are written out special characters have to be quoted.
	 *
	 * @param string
	 *            the string to process
	 * @return the string with quoted special characters
	 */
	public static String quote(CharSequence string) {
		if (string == null)
			return null;
		
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < string.length(); i++) {
			char ch = string.charAt(i);
			switch (ch) {
				case '&':
					sb.append("&amp;");
					break;
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				case '\'':
					sb.append("&#39;");
					break;
				default:
					sb.append(ch);
			}
		}
		
		return sb.toString();
	}
	
	public static String quote(char ch) {
		switch (ch) {
			case '&':
				return "&amp;";
			case '<':
				return "&lt;";
			case '>':
				return "&gt;";
			case '"':
				return "&quot;";
			case '\'':
				return "&#39;";
			default:
				return ch + "";
		}
	}
	
	/**
	* quotes a string according to XML rules. When attributes and text elements
	* are written out special characters have to be quoted.
	*
	* @param string
	*            the string to process
	* @return the string with quoted special characters
	*/
	static public String unquote(CharSequence string) {
		if (string == null)
			return null;
		 
		StringBuffer sb = new StringBuffer();
		boolean inQuote = false;
		StringBuffer quoteBuf = new StringBuffer();
		
		for (int i = 0; i < string.length(); i++) {
		  char ch = string.charAt(i);
		  
		  if (inQuote) {
			if (ch == ';') {
			  String quote = quoteBuf.toString();
			  
			  if (quote.equals("lt"))
				sb.append('<');
			  else if (quote.equals("gt"))
				sb.append('>');
			  else if (quote.equals("amp"))
				sb.append('&');
			  else if (quote.equals("quot"))
				sb.append('"');
			  else if (quote.equals("apos"))
				sb.append('\'');
			  else if (quote.startsWith("#x"))
				sb.append((char)Integer.parseInt(quote.substring(2), 16));
			  else if (quote.startsWith("#"))
				sb.append((char)Integer.parseInt(quote.substring(1)));
			  else
				sb.append(quoteBuf);
			  
			  inQuote = false;
			  quoteBuf.setLength(0);
			}
			else if (ch == ' ') {
				sb.append('&');
				sb.append(quoteBuf);
				sb.append(' ');
				
				inQuote = false;
				quoteBuf.setLength(0);
			  }
			else {
			  quoteBuf.append(ch);
			}
		  }
		  else {
			if (ch == '&')
			  inQuote = true;
			else
			  sb.append(ch);
		  }
		}
		
		if (inQuote)
		  sb.append(quoteBuf);
		
		return sb.toString();
	}
}
