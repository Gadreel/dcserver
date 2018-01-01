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

import dcraft.util.Memory;
import dcraft.util.StringUtil;

/**
 * An XML element that simply contains text. This is normally a child element of
 * {@link XElement}.
 */
public class XComment extends XNode {
	static public XComment of(String value) {
		XComment text = new XComment();
		text.setValue(value);
		return  text;
	}
	
	static public XComment comment() {
		return new XComment();
	}
	
	/**
	 * The value of the TextElement.
	 */
	protected String content = null;
	
	public XComment() {
	}
	
	@Override
	public XNode deepCopy() {
		XComment copy = new XComment();
		copy.content = this.content;
		return copy;
	}
	
	/**
	 * Sets the value of this node
	 * 
	 * @param value the value to store 
	 */
	public void setValue(String value) {
		this.content = value;
	}
	
	public XComment withValue(String value) {
		this.content = value;
		return this;
	}

	/**
	 * 
	 * @return the value of this node
	 */
	public String getValue() {
		return this.content;
	}
	
	public boolean isNotEmpty() {
		return StringUtil.isNotEmpty(this.content);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "<!--" +  this.content + "-->";
	}

	/* (non-Javadoc)
	 * @see dcraft.xml.XNode#toString(java.lang.StringBuffer, boolean, int)
	 */
	@Override
	protected StringBuilder toString(StringBuilder sb, boolean formatted, int level) {
		if (formatted) {
			sb.append("\n");
			for (int i = level; i > 0; i--)
				sb.append("\t");
		}
		
		sb.append("<!--");
		sb.append(this.content);
		sb.append("-->");
		
		return sb;
	}

	/* (non-Javadoc)
	 * @see dcraft.xml.XNode#toMemory(dcraft.lang.Memory, boolean, int)
	 */
	@Override
	protected void toMemory(Memory sb, boolean formatted, int level) {
		if (formatted) {
			sb.write("\n");
			for (int i = level; i > 0; i--)
				sb.write("\t");
		}
		
		sb.write("<!--");
		sb.write(this.content);
		sb.write("-->");
	}
}
