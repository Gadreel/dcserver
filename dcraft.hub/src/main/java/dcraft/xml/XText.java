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

import dcraft.struct.BaseStruct;
import dcraft.struct.PathPart;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.Memory;
import dcraft.util.StringUtil;

/**
 * An XML element that simply contains text. This is normally a child element of
 * {@link XElement}.
 */
public class XText extends XNode {
	static public XText of(CharSequence value) {
		XText text = new XText();
		text.setValue(value);
		return  text;
	}
	
	static public XText ofRaw(CharSequence value) {
		XText text = new XText();
		text.setRawValue(value);
		return  text;
	}
	
	static public XText text() {
		return new XText();
	}
	
	/**
	 * The value of the TextElement.
	 */
	protected String content = null;
	protected StringBuilder buffered = null;
	protected boolean cdata = false;
	
	public boolean isCData() {
		return this.cdata;
	}
	
	public XText() {
	}
	
	/**
	 * @param string text content to associate with this node
	 */
	public XText(CharSequence string) {
		this.setValue(string);
	}

	public void append(char c) {
		if (this.content == null)
			this.content = XNode.quote(c);
		else
			this.content += XNode.quote(c);
	}

	public void append(String s) {
		if (this.content == null)
			this.content = XNode.quote(s);
		else
			this.content += XNode.quote(s);
	}

	public void appendEntity(String s) {
		if (this.content == null)
			this.content = s;
		else
			this.content += s;
	}

	public void appendBuffer(CharSequence s) {
		if (this.buffered == null)
			this.buffered = new StringBuilder(s);
		else
			this.buffered.append(s);
	}
	
	public void closeBuffer() {
		if (this.buffered != null)
			this.content = this.buffered.toString();
	}
	
	/**
	 * @param cdata flag to indicate that this text was stored within a CDATA section
	 * @param string text content to associate with this node
	 */
	public XText(boolean cdata, CharSequence string) {
		this.setValue(string, cdata);
	}
	
	@Override
	protected void doCopy(BaseStruct n) {
		super.doCopy(n);
		
		XText copy = (XText) n;
		
		copy.cdata = this.cdata;
		copy.content = this.content;
	}
	
	@Override
	public XText deepCopy() {
		XText copy = new XText();
		
		this.doCopy(copy);
		
		return copy;
	}
	
	/**
	 * Sets the value of this node
	 * 
	 * @param value the value to store 
	 */
	public void setValue(CharSequence value) {
		this.content = XNode.quote(value);
		this.cdata = false;
	}
	
	/*
	 * Sets the value of this node
	 * 
	 * @param value the value to store 
	 */
	public void setValue(CharSequence value, boolean cdata) {
		if (cdata)
			this.content = (value != null) ? value.toString() : null;
		else
			this.content = XNode.quote(value);
		
		this.cdata = cdata;
	}

	public void setRawValue(CharSequence str) {
		this.content = (str != null) ? str.toString() : null;
		this.cdata = false;
	}
	
	public void setRawValue(CharSequence str, boolean cdata) {
		this.content = (str != null) ? str.toString() : null;
		this.cdata = cdata;
	}
	
	public XText withValue(CharSequence value) {
		this.content = XNode.quote(value);
		this.cdata = false;

		return this;
	}
	
	public XText withRawValue(CharSequence str) {
		this.content = (str != null) ? str.toString() : null;
		this.cdata = false;
		
		return this;
	}

	/**
	 * 
	 * @return the value of this node
	 */
	public String getValue() {
		if (cdata)
			return this.content;
		
		return XNode.unquote(this.content);
	}
	
	public String getRawValue() {
		return this.content;
	}
	
	/**
	 * Sets this element to print as a CDATA section
	 * 
	 * @param cdata true if it should be printed as a CDATA section
	 */
	public void setCData(boolean cdata) {
		this.cdata = cdata;
	}

	/**
	 * @return true if this text is part of a CDATA section
	 */
	public boolean getCData() {
		return this.cdata;
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
		if (this.cdata) {
			return "<![CDATA[" + this.content + "]]>";

			/*  TODO fix to support ]]> in content
			 * 
			 * You do not escape the ]]> but you escape the > after ]] by inserting ]]><![CDATA[ before the >, think 
			 * of this just like a \ in C/Java/PHP/Perl string but only needed before a > and after a ]].
			 */
			
			
			//return this.cdataToString(this.content).toString();
		}
		
		return this.content;
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
		
		if (this.cdata) {
			sb.append("<![CDATA[");
			sb.append(this.content);
			sb.append("]]>");

			/*  TODO fix to support ]]> in content
			 * 
			 * You do not escape the ]]> but you escape the > after ]] by inserting ]]><![CDATA[ before the >, think 
			 * of this just like a \ in C/Java/PHP/Perl string but only needed before a > and after a ]].
			 */
		}
		else
			sb.append(this.content);
		
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
		
		if (this.cdata) {
			sb.write("<![CDATA[");
			sb.write(this.content);

			/*  TODO fix to support ]]> in content
			 * 
			 * You do not escape the ]]> but you escape the > after ]] by inserting ]]><![CDATA[ before the >, think 
			 * of this just like a \ in C/Java/PHP/Perl string but only needed before a > and after a ]].
			 * 
			 * 
			int index = 0;		
			
			while ((index >= 0) && (index <= sb.length())) {
				index = sb.indexOf("]]>", index);
				
				if (index < 0)
					break;
				
				index += 3;
				sb.insert(index, "]]&gt;<![CDATA[");
			}
			*/
			
			sb.write("]]>");
		}
		else
			sb.write(XNode.quote(this.content));
	}

	public static XText raw(CharSequence v) {
		XText t = new XText();
		t.setRawValue(v);
		return t;
	}

	public static XText cdata(CharSequence v) {
		XText t = new XText(true, v);
		return t;
	}

	public static XText escape(CharSequence v) {
		XText t = new XText(false, v);
		return t;
	}
	
	@Override
	public boolean isEmpty() {
		if (StringUtil.isNotEmpty(this.content)) {
			return ((this.buffered == null) || (this.buffered.length() == 0));
		}
		
		return false;
	}
	
	@Override
	public void clear() {
		this.buffered = null;
		this.content = null;
	}
	
	@Override
	public void toBuilder(ICompositeBuilder builder) {
		// TODO
	}
	
	@Override
	public BaseStruct select(PathPart... path) {
		// TODO
		
		return null;
	}
}
