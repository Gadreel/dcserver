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

import java.util.Map;

/**
 * Use to create a custom Xml parser for use with XmlParser.  SAX like
 * processing which handles start and end of elements as well as text
 * nodes.
 * 
 * @author Andy
 *
 */
public interface IParseHandler {
	/*
	 * Called to indicate the start of a tagged element
	 * 
	 * @param tag
	 *            the name part of the tag
	 * @param attributes
	 *            the table of attributes for this element
	 * @param line
	 *            the line number where this element was started
	 * @param col
	 *            the column number where this element was started
	 * @throws XMLParseException
	 *             if a semantic error is observed
	 */
	boolean startElement(String tag, Map<String, String> attributes, int line, int col);

	/*
	 * Called to indicate the end of a tagged element
	 * 
	 * @param tag
	 *            the name part of the tag
	 * @throws XMLParseException
	 *             if a semantic error is observed
	 */
	boolean endElement(String tag);
	
	/*
	 * Called to indicate a complete tagged element
	 * 
	 * @param tag
	 *            the name part of the tag
	 * @param attributes
	 *            the table of attributes for this element
	 * @param line
	 *            the line number where this element was started
	 * @param col
	 *            the column number where this element was started
	 * @throws XMLParseException
	 *             if a semantic error is observed
	 */
	boolean element(String tag, Map<String, String> attributes, int line, int col);

	/*
	 * Called to indicate the start of the XML document being read
	 * 
	 * @throws XMLParseException
	 *             if a semantic error is observed
	 */
	boolean startDocument();

	/*
	 * Called to indicate the end of the XML document being read
	 * 
	 * @throws XMLParseException
	 *             if a semantic error is observed
	 */
	boolean endDocument();

	/*
	 * Called to indicate that an untagged element has been read
	 * 
	 * @param str
	 *            the value of the untagged element
	 * @param cdata
	 *            true if the element was a CDATA element
	 * @param line
	 *            the line number where this element was started
	 * @param col
	 *            the column number where this element was started
	 * @throws XMLParseException
	 *             if a semantic error is observed
	 */
	boolean text(String str, boolean cdata, int line, int col);
	
	boolean comment(String str, int line, int col);
}
