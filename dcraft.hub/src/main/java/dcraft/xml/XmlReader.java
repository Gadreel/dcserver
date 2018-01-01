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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import dcraft.log.Logger;
import dcraft.util.StringUtil;
import dcraft.util.io.CharSequenceReader;

public class XmlReader implements IParseHandler {
	/*
	 * Read and parse xml that is held a string
	 * 
	 * @param xml the string that holds the Xml
	 * @return the root xml element
	 */
	public static XElement parse(CharSequence xml, boolean keepwhitespace, boolean skipemptynodes) {
		return new XmlReader(new CharSequenceReader(xml), keepwhitespace, skipemptynodes).parse();
	}

	// with custom tag mapper
	public static XElement parse(CharSequence xml, boolean keepwhitespace, boolean skipemptynodes,
			Map<String, Class<? extends XElement>> tagmap, Class<? extends XElement> defaulttag) 
	{
		XmlReader xr = new XmlReader(new CharSequenceReader(xml), keepwhitespace, skipemptynodes);
		
		xr.setTagMap(tagmap);
		xr.setDefaultTag(defaulttag);
		
		return xr.parse();
	}

	/*
	 * Read and parse xml that is pointed to by a reader (utf-8)
	 * 
	 * @param in the reader that holds the Xml
	 * @return the root xml element
	 */
	public static XElement parse(Reader in, boolean keepwhitespace, boolean skipemptynodes) {
		return new XmlReader(in, keepwhitespace, skipemptynodes).parse();
	}

	/*
	 * Read and parse xml that is pointed to by a stream (utf-8)
	 * 
	 * @param in the stream that holds the Xml
	 * @return the root xml element
	 */
	public static XElement parse(InputStream in, boolean keepwhitespace, boolean skipemptynodes) {
		return new XmlReader(new InputStreamReader(in), keepwhitespace, skipemptynodes).parse();
	}

	/*
	 * Read and parse xml that is held in memory (utf-8)
	 * 
	 * @param mem the memory that holds the Xml
	 * @return the root xml element
	 */
	//public static FuncResult<XElement> parse(Memory mem, boolean keepwhitespace) {
	//	return new XmlReader(mem, keepwhitespace).parse();
	//}

	/*
	 * Read and parse an xml file
	 * 
	 * @param fullname the file name to read and parse
	 * @return the root xml element
	 */
	public static XElement loadFile(String fullname, boolean keepwhitespace, boolean skipemptynodes) {
		try {
			return new XmlReader(new FileReader(fullname), keepwhitespace, skipemptynodes).parse();
		} 
		catch (FileNotFoundException x) {
			Logger.errorTr(233, fullname);
		}
		
		return null;
	}

	/*
	 * Read and parse an xml file
	 * 
	 * @param fl the file to read and parse
	 * @return the root xml element
	 */
	public static XElement loadFile(File fl, boolean keepwhitespace, boolean skipemptynodes) {
		try {
			return new XmlReader(new FileReader(fl), keepwhitespace, skipemptynodes).parse();
		} 
		catch (FileNotFoundException x) {
			Logger.errorTr(233, fl.getPath());
		}
		
		return null;
	}

	/*
	 * Read and parse an xml file
	 * 
	 * @param fl the file to read and parse
	 * @return the root xml element
	 */
	public static XElement loadFile(Path fl, boolean keepwhitespace, boolean skipemptynodes) {
		try {
			return new XmlReader(Files.newBufferedReader(fl), keepwhitespace, skipemptynodes).parse();
		} 
		catch (FileNotFoundException x) {
			Logger.errorTr(233, fl);
		}
		catch (IOException x) {
			Logger.errorTr(1, "Error loading file: " + fl);
		}
		
		return null;
	}
	
	// instance
	
	/**
	 * The source of the XML
	 */
	protected Reader input = null;
	/**
	 * The root of the class structure
	 */
	protected XElement top = null;
	/**
	 * The current element being worked on
	 */
	protected XElement element = null;
	/**
	 * Holds all the parent elements of the current element
	 */
	protected Stack<XElement> stack = new Stack<>();
	
	protected boolean keepwhitespace = false;
	protected  boolean skipemptynodes = false;
	
	protected Map<String, Class<? extends XElement>> tagmap = new HashMap<String, Class<? extends XElement>>();
	protected Class<? extends XElement> defaulttag = XElement.class;
	
	public XElement getTop() {
		return this.top;
	}
	
	public void setTagMap(Map<String, Class<? extends XElement>> v) {
		this.tagmap = v;
	}
	
	public void setDefaultTag(Class<? extends XElement> v) {
		this.defaulttag = v;
	}
	
	/*
	 * Set XML source to be a Reader 
	 * 
	 * @param input the XML source to be parsed
	 */
	public XmlReader(Reader input, boolean keepwhitespace, boolean skipemptynodes) {
		this.input = input;
		this.keepwhitespace = keepwhitespace;
		this.skipemptynodes = skipemptynodes;
	}

	/**
	 * Parses the XML and returns the root element.  Comments and PI
	 * will be missing, this is a really basic and lightweight XML utility.
	 * 
	 * @return the root XML element 
	 */
	public XElement parse() {
		this.top = null;
		this.element = null;
		
		if (! XmlParser.parse(this, this.input))
			return null;
		
		if (this.top == null) 
			Logger.errorTr(247);
			
		return this.top;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see IParseHandler#startDocument()
	 */
	@Override
	public boolean startDocument() {
		this.top = null;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IParseHandler#endDocument()
	 */
	@Override
	public boolean endDocument() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IParseHandler#startElement(java.lang.String, java.lang.String, java.util.Map, int, int)
	 */
	@Override
	public boolean startElement(String tag, Map<String, String> attributes, int line, int col) {
		XElement newElement = this.createElement(tag);

		newElement.withLocation(line, col);

		for (Map.Entry<String,String> entry : attributes.entrySet()) 
			newElement.setRawAttribute((String) entry.getKey(),
					(String) entry.getValue());

		if (this.top == null)
			this.top = newElement;
		else
			this.element.add(newElement);
		
		if (this.element != null)
			this.stack.push(this.element);
		
		this.element = newElement;
		
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IParseHandler#element(java.lang.String, java.lang.String, java.util.Map, int, int)
	 */
	@Override
	public boolean element(String tag, Map<String, String> attributes, int line, int col) {
		XElement newElement = this.createElement(tag);

		newElement.withLocation(line, col);

		for (Map.Entry<String,String> entry : attributes.entrySet()) 
			newElement.setRawAttribute((String) entry.getKey(),
					(String) entry.getValue());

		if (this.top == null)
			this.top = newElement;
		else
			this.element.add(newElement);
		
		return true;
	}
	
	public XElement createElement(String tag) {
		Class<? extends XElement> el = this.tagmap.get(tag);
		
		if (el == null)
			el = this.defaulttag;
		
		try {
			XElement x = el.newInstance();
			x.setName(tag);
			return x;
		} 
		catch (Exception x) {
			Logger.error("Unable to create XML element " + tag + " because: " + x);
		}
		
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IParseHandler#endElement(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean endElement(String tag) {
		if (! this.stack.isEmpty())
			this.element = this.stack.pop();
			
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see IParseHandler#text(java.lang.String, boolean, int, int)
	 */
	@Override
	public boolean text(String str, boolean cdata, int line, int col) {
		// comments outside of the document are lost, but do not break us
		if ((this.element == null) || (str == null))
			return true;
		
		if (this.skipemptynodes && StringUtil.isEmpty(str))
			return true;
		
		if (! this.keepwhitespace) {
			// non-cdata text should not keep its extra whitespace
			if (! cdata) 
				str = StringUtil.stripWhitespacePerXml(str.trim());
			
			if (StringUtil.isEmpty(str))
				return true;	// nothing added, but this is still correct
		}
		
		XText text = new XText();
		
		text.withLocation(line, col);
		
		if (cdata)
			text.setValue(str, true);
		else
			text.setRawValue(str);
		
		this.element.add(text);
		
		return true;
	}
	
	@Override
	public boolean comment(String str, int line, int col) {
		// comments outside of the document are lost, but do not break us
		if ((this.element == null) || (str == null))
			return true;
		
		this.element.add(XComment.of(str)
				.withLocation(line, col));
		
		return true;
	}
}
