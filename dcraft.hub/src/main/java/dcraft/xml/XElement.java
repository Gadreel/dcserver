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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import dcraft.hub.resource.ResourceBase;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.IPartSelector;
import dcraft.struct.ListStruct;
import dcraft.struct.PathPart;
import dcraft.struct.Struct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.Memory;
import dcraft.util.StringUtil;

/**
 * Represents a XML element and contains all the information that the
 * original had. Attributes may be set, accessed and deleted. Child elements may
 * be set accessed or deleted. This can be converted back to XML, along with its
 * children, in a formatted or unformatted string.
 */
public class XElement extends XNode {
	static public XElement tag(String name) {
		XElement el = new XElement();
		el.tagName = name;
		return el;
	}
	
	protected String tagName = null;

	protected Map<String, String> attributes = null;
	protected List<XNode> children = null;

	public XElement() {
	}
	
	/**
	 * constructor specifying the name with an optional array
	 * of objects to be added as child elements
	 * 
	 * @param tag the name of the tag
	 * @param children
	 *            an array of objects to be added as children
	 */
	public XElement(String tag, Object... children) {
		this.tagName = tag;

		for (int i = 0; i < children.length; i++) {
			Object obj = children[i];

			if (obj instanceof XNode)
				this.add((XNode) obj);
			else if (obj instanceof XAttribute)
				this.setAttribute(((XAttribute) obj).getName(), ((XAttribute) obj).getRawValue());
			else
				this.add(obj.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	/*
	 * TODO public Object clone() throws CloneNotSupportedException {
	 * TaggedElement newElement = (TaggedElement)super.clone(); if (attributes
	 * != null) { newElement.attributes = new
	 * HashMap<String,String>(attributes); } if (elements != null) {
	 * newElement.elements = new ArrayList<Element>(); Iterator<Element> it =
	 * elements.iterator(); while (it.hasNext()) {
	 * newElement.elements.add((Element)it.next().clone()); } } return
	 * newElement; }
	 */

	public XElement(XMLStreamReader xmlStreamReader, boolean keepwhitespace) {
		this.tagName = xmlStreamReader.getLocalName();
		
		 for (int a = 0; a < xmlStreamReader.getAttributeCount(); a++) 
			 this.setAttribute(xmlStreamReader.getAttributeLocalName(a), xmlStreamReader.getAttributeValue(a));
		
		 try {
			while (xmlStreamReader.hasNext()) {
				 int n = xmlStreamReader.next();
			        
				 switch (n) {
				 case XMLStreamConstants.START_ELEMENT:
					 this.add(new XElement(xmlStreamReader, keepwhitespace));					 
					 break;
				 case XMLStreamConstants.END_ELEMENT:
					 return;
				 case XMLStreamConstants.SPACE:
				 case XMLStreamConstants.CHARACTERS:
					 String str = xmlStreamReader.getText();
					 
					if (!keepwhitespace) {
						str = StringUtil.stripWhitespacePerXml(str);
					}

					// this is not always good - see if we can do it anyway
					if (StringUtil.isEmpty(str)) 
						break;
					
					XText text = new XText();
					
					text.setRawValue(str);
					
					this.add(text);
						
					 break;
					 /*
				 case XMLStreamConstants.ATTRIBUTE:
					 for (int a = 0; a < xmlStreamReader.getAttributeCount(); a++) {
						 this.setAttribute(xmlStreamReader.getAttributeLocalName(a), xmlStreamReader.getAttributeValue(a));
					 }
					 break;
					 */
				 case XMLStreamConstants.CDATA:
					 String str2 = xmlStreamReader.getText();
					 
						if (!keepwhitespace) {
							str2 = str2.trim();
							
							if (StringUtil.isEmpty(str2)) 
								break;
						}
						
						XText text2 = new XText();
						
						text2.setValue(str2, true);
						
						this.add(text2);
					 break;
				 }
			 }
		} 
		 catch (XMLStreamException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}

	/**
	 * gets the element name 
	 * 
	 * @return the element name 
	 */
	public String getName() {
		return this.tagName;
	}
	
	public void setName(String v) {
		this.tagName = v;
	}

	/**
	 * sets an attribute of this element
	 * 
	 * @param name
	 *            the name of the attribute to be set
	 * @param value
	 *            the value of the attribute to be set
	 */
	public void setAttribute(String name, String value) {
		if (StringUtil.isEmpty(name))  // || StringUtil.isEmpty(value))
			return;
		
		if (this.attributes == null)
			this.createAttrHash();

		this.attributes.put(name, XNode.quote(value));
	}
	
	public void setRawAttribute(String name, String value) {
		if (StringUtil.isEmpty(name))  // || StringUtil.isEmpty(value))
			return;
		
		if (this.attributes == null)
			this.createAttrHash();

		this.attributes.put(name, value);
	}
	
	public XElement withAttribute(String name, String value) {
		if (StringUtil.isEmpty(name))  // || StringUtil.isEmpty(value))
			return this;
		
		if (this.attributes == null)
			this.createAttrHash();

		this.attributes.put(name, XNode.quote(value));
		
		return this;
	}

	protected void createAttrHash() {
		synchronized (this) {
			if (this.attributes == null)
				this.attributes = new HashMap<>();
		}
	}

	public String attr(String name) {
		return this.getAttribute(name);
	}

	public XElement attr(String name, String value) {
		return this.withAttribute(name, value);
	}

	/**
	 * gets the specified attribute of this element
	 * 
	 * @param name
	 *            the name of the attribute to get
	 * @return the value of the attribute
	 */
	public String getAttribute(String name) {
		return (this.attributes == null ? null : XNode.unquote(this.attributes.get(name)));
	}

	public String getRawAttribute(String name) {
		//return (this.attributes == null ? null : XNode.unquote(this.attributes.get(name)));
		return (this.attributes == null ? null : this.attributes.get(name));
	}
	
	/**
	 * gets the specified attribute of this element but returns given default
	 * value if the attribute does not exist
	 * 
	 * @param name
	 *            the name of the attribute to get
	 * @param defaultValue
	 *            the value to be returned if the attribute doesn't exist
	 * @return the value of the attribute
	 */
	public String getAttribute(String name, String defaultValue) {
		String result = this.getAttribute(name);
		return result == null ? defaultValue : result;
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as Integer (dc thinks of integers as 64bit)
	 */
	public Long getAttributeAsInteger(String name) {
		return Struct.objectToInteger(this.getAttribute(name));
	}
	
	public long getAttributeAsInteger(String name, long defaultval) {
		Long x = Struct.objectToInteger(this.getAttribute(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as BigInteger 
	 */
	public BigInteger getAttributeAsBigInteger(String name) {
		return Struct.objectToBigInteger(this.getAttribute(name));
	}
	
	public BigInteger getAttributeAsBigInteger(String name, BigInteger defaultval) {
		BigInteger x = Struct.objectToBigInteger(this.getAttribute(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as BigDecimal
	 */
	public BigDecimal getAttributeAsDecimal(String name) {
		return Struct.objectToDecimal(this.getAttribute(name));
	}
	
	public BigDecimal getAttributeAsDecimal(String name, BigDecimal defaultval) {
		BigDecimal x = Struct.objectToDecimal(this.getAttribute(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as Boolean
	 */
	public Boolean getAttributeAsBoolean(String name) {
		return Struct.objectToBoolean(this.getAttribute(name));
	}

	public boolean getAttributeAsBooleanOrFalse(String name) {
		Boolean b = Struct.objectToBoolean(this.getAttribute(name));
		
		return (b == null) ? false : b.booleanValue();
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as DateTime
	 */
	public ZonedDateTime getAttributeAsDateTime(String name) {
		return Struct.objectToDateTime(this.getAttribute(name));
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as BigDateTime
	 */
	public BigDateTime getAttributeAsBigDateTime(String name) {
		return Struct.objectToBigDateTime(this.getAttribute(name));
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as Date
	 */
	public LocalDate getAttributeAsDate(String name) {
		return Struct.objectToDate(this.getAttribute(name));
	}
	
	/**
	 * @param name of the Attribute desired
	 * @return Attribute's value as Time
	 */
	public LocalTime getAttributeAsTime(String name) {
		return Struct.objectToTime(this.getAttribute(name));
	}

	/**
	 * finds out whether an attribute exists
	 * 
	 * @param name
	 *            the name of the attribute to look for
	 * @return whether the attribute exists in this element
	 */
	public boolean hasAttribute(String name) {
		return this.attributes == null ? false : this.attributes.containsKey(name);
	}

	public boolean hasEmptyAttribute(String name) {
		return this.attributes == null ? true : StringUtil.isEmpty(this.attributes.get(name));
	}

	public boolean hasNotEmptyAttribute(String name) {
		return this.attributes == null ? false : StringUtil.isNotEmpty(this.attributes.get(name));
	}

	/**
	 * finds out whether this element has any attributes
	 * 
	 * @return whether this element has any attributes
	 */
	public boolean hasAttributes() {
		return this.attributes != null && ! this.attributes.isEmpty();
	}

	/**
	 * gets the attributes in the form of an indexed table
	 * 
	 * @return the attribute table for this element
	 */
	public Map<String, String> getAttributes() {
		if (this.attributes == null)
			this.createAttrHash();

		return this.attributes;
	}

	/**
	 * removes the named attribute
	 *  
	 * @param name Name of attribute to remove
	 */
	public void removeAttribute(String name) {
		if (this.attributes != null)
			this.attributes.remove(name);
	}
	
	public void clearAttributes() {
		// once attributes field is set they are always expected to be present, even after clear
		if (this.attributes != null)
			this.attributes = new HashMap<>();
	}

	/**
	 * gets the number of child elements this element has
	 * 
	 * @return the number of child elements this element has
	 */
	public int children() {
		return this.children == null ? 0 : this.children.size();
	}

	/**
	 * finds out whether this element has any child elements
	 * 
	 * @return whether this element has any child elements
	 */
	public boolean hasChildren() {
		return this.children() != 0;
	}

	/**
	 * removes the children from this element
	 *
	 * use new so any one with a ref to children will still have the old
	 */
	public void clearChildren() {
		if (this.children != null)
			this.children = new ArrayList<>();
	}
	
	public void clear() {
		if (this.children != null)
			this.children = new ArrayList<>();
		
		if (this.attributes != null)
			this.createAttrHash();
	}

	/**
	 * adds a child to the end of this element
	 * 
	 * @param element
	 *            the child element to be added
	 */
	public void add(XNode element) {
		this.add(-1, element);
	}
	
	public XElement with(XNode... nodes) {
		for (XNode v : nodes)
			this.add(-1, v);
		
		return this;
	}
	
	public XElement withAll(Collection<? extends XNode> vlist) {
		if (vlist != null)
			for (XNode v : vlist)
				this.add(-1, v);
		
		return this;
	}
	
	public XElement append(Object... items) {
		for (Object v : items) {
			if (v instanceof XNode) {
				this.add((XNode) v);
			}
			else {
				this.add(v.toString());
			}
		}
		
		return this;
	}

	/**
	 * inserts a child into this element. If the index is out of range, the
	 * child is added at the end
	 * 
	 * @param index
	 *            the location to insert the child
	 * @param element
	 *            the child element to be added
	 */
	public void add(int index, XNode element) {
		if (this.children == null)
			this.children = new CopyOnWriteArrayList<XNode>();

		if ((index < 0) || (index >= this.children.size()))
			this.children.add(element);
		else
			this.children.add(index, element);
	}

	/**
	 * adds a child to the end of this element
	 * 
	 * @param string
	 *            the child element to be added
	 */
	public void add(String string) {
		this.add(new XText(string));
	}
	
	public XElement withText(String v) {
		if (v != null)
			this.add(new XText(v));
		
		return this;
	}
	
	public XElement withCData(String v) {
		this.add(new XText(true, v));
		
		return this;
	}

	/**
	 * inserts a child into this element If the index is out of range, the child
	 * is added at the end
	 * 
	 * @param index
	 *            the location to insert the child
	 * @param string
	 *            the child element to be added
	 */
	public void add(int index, String string) {
		this.add(index, new XText(string));
	}
	
	public void append(char c) {
		if ((this.children != null) && (this.children.size() > 0)) {
			XNode node = this.children.get(this.children.size() - 1);
			
			if (node instanceof XText) {
				XText t = (XText) node;
				
				if (!t.getCData()) {
					t.append(c);
					return;
				}
			}
		}
		
		this.add(c + "");
	}
	
	public void append(String s) {
		if ((this.children != null) && (this.children.size() > 0)) {
			XNode node = this.children.get(this.children.size() - 1);
			
			if (node instanceof XText) {
				XText t = (XText) node;
				
				if (!t.getCData()) {
					t.append(s);
					return;
				}
			}
		}
		
		this.add(s);
	}
	
	public void appendRaw(String s) {
		if ((this.children != null) && (this.children.size() > 0)) {
			XNode node = this.children.get(this.children.size() - 1);
			
			if (node instanceof XText) {
				XText t = (XText) node;
				
				if (t.getCData()) {
					t.appendEntity(s);
					return;
				}
			}
		}
		
		this.withCData(s);
	}

	/**
	 * gets a child from the specified place in this element
	 * 
	 * @param i
	 *            the index where the child is to be added
	 * @return the specified child
	 */
	public XNode getChild(int i) {
		if ((i < 0) || (i >= this.children()))
			return null;

		return this.children.get(i);
	}

	public XElement getChildAsElement(int i) {
		if ((i < 0) || (i >= this.children()))
			return null;

		return (XElement) this.children.get(i);
	}

	/**
	 * replaces a child element with the one given
	 * 
	 * @param index
	 *            the child element number to replace
	 * @param newElement
	 *            the new element to replace the old one
	 */
	public void replace(int index, XNode newElement) {
		if (this.children == null)
			this.children = new CopyOnWriteArrayList<XNode>();
		
		if (index >= this.children.size())
			this.children.add(newElement);
		else
			this.children.set(index, newElement);
	}

	/**
	 * replaces all children with the children of the provided element
	 * 
	 * @param source
	 *       		the new element providing the source of the children
	 */
	public void replaceChildren(XElement source) {
		this.children = new CopyOnWriteArrayList<>();

		if (source.hasChildren())
			this.children.addAll(source.children);
	}
	
	public void replaceAttributes(XElement source) {
		synchronized (this) {
			// always replace
			this.attributes = new HashMap<>();
		}

		if (source.hasAttributes()) 
			this.attributes.putAll(source.attributes);
	}
	
	public void replace(XElement source) {
		this.tagName = source.tagName;
		this.replaceChildren(source);
		this.replaceAttributes(source);
	}

	/**
	 * Removes a child from this element
	 * 
	 * @param index
	 *            the index of the child to be removed
	 * @return whether the child was found and removed or not
	 */
	public XNode remove(int index) {
		if (this.children == null)
			return null;
		
		if (index >= 0 && index < this.children.size())
			return this.children.remove(index);
		
		return null;
	}

	/**
	 * Removes a child from this element
	 * 
	 * @param element
	 *            the child to be removed
	 * @return whether the child was found and removed or not
	 */
	public boolean remove(XNode element) {
		if (element == null)
			return true;
		
		if (this.children != null)
			return this.children.remove(element);
		
		return false;
	}
	
	public XElement newNode() {
		return new XElement(this.tagName);
	}

	@Override
	public XElement deepCopy() {
		XElement copy = this.newNode();
		
		this.doCopy(copy);
		
		return copy;
	}
	
	public void mergeDeep(XElement el, boolean childrenTop) {
		if (el.attributes != null) {
			for (Entry<String, String> entry : el.attributes.entrySet())
				this.withAttribute(entry.getKey(), entry.getValue());
		}

		if (el.children != null) {
			for (XNode entry : el.children) {
				if (childrenTop)
					this.add(0, entry.deepCopy());
				else
					this.add(entry.deepCopy());
			}
		}
	}

	/**
	 * Finds a named child tagged element. If there is no such child, null is
	 * returned.
	 * 
	 * @param name
	 *            the name of the child of this TaggedElement to find
	 * @return the name of the found element or null if not found
	 */
	public XElement find(String... name) {
		if (this.children != null)
			for (int i = 0; i < this.children.size(); i++) {
				XNode element = this.children.get(i);
				
				if (element instanceof XElement) {
					for (int n = 0; n < name.length; n++)
						if (((XElement) element).getName().equals(name[n]))
							return (XElement) element;
				}
			}
		
		return null;
	}

	public XElement findId(String id) {
		if (id == null)
			return null;
		
		if (this.attributes != null) 
			if (id.equals(this.getAttribute("id")) || id.equals(this.getAttribute("Id")) || id.equals(this.getAttribute("ID")))
				return this;
		
		if (this.children != null) {
			for (XNode n : this.children) {
				if (n instanceof XElement) {
					XElement match = ((XElement)n).findId(id);
					
					if (match != null)
						return match;
				}
			}
		}
		
		return null;
	}

	public XElement findParentOfId(String id) {
		return findParentOfId(id, null);
	}

	public XElement findParentOfId(String id, XElement parent) {
		if (id == null)
			return null;
		
		if (this.attributes != null) 
			if (id.equals(this.getAttribute("id")) || id.equals(this.getAttribute("Id")) || id.equals(this.getAttribute("ID")))
				return parent;
		
		if (this.children != null) {
			for (XNode n : this.children) {
				if (n instanceof XElement) {
					XElement match = ((XElement)n).findParentOfId(id, this);
					
					if (match != null)
						return match;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * A way to select child or sub child elements similar to XPath but lightweight.
	 * Cannot select values or attributes, just elements.  * is supported to match
	 * all elements at a given level.
	 * 
	 * For example: "Toys/Toy" called on "&lt;Person&gt;" means select all Toy elements
	 * inside of the Toys child element (child of Person).
	 * 
	 * Cannot go up levels, or back to root.  Do not start with a slash as in "/People".
	 * 
	 * @param path a backslash delimited string
	 * @return list of all matching elements, or empty list if no match
	 */
	public List<XElement> selectAll(String path) {
		List<XElement> matches = new ArrayList<>();
		this.selectAll(path, matches);
		return matches;
	}
	
	/**
	 * Internal, recursive search used by selectAll
	 * 
	 * @param path a backslash delimited string
	 * @param matches list of all matching elements, or empty list if no match
	 */
	public void selectAll(String path, List<XElement> matches) {
		if (!this.hasChildren())
			return;
		
		int pos = path.indexOf('/');

		// go back to root not supported
		if (pos == 0)
			return;
		
		String name = null;
		
		if (pos == -1) {
			name = path;
			path = null;
		}
		else { 
			name = path.substring(0, pos);
			path = path.substring(pos + 1);
		}
		
		// TODO add filter per XPath - [@n = f]
		
		for (XNode n : this.children) {
			if (n instanceof XElement) {
				if ("*".equals(name) || ((XElement)n).getName().equals(name)) {
					if (pos == -1) 
						matches.add((XElement)n);
					else  
						((XElement)n).selectAll(path, matches);
				}
			}
		}
	}
	
	/**
	 * A way to select text of a  child or sub child elements similar to XPath but lightweight.
	 * '*' is supported to match
	 * all elements at a given level.  Returns only the first match.
	 * 
	 * For example: "Toys/Toy" called on "&lt;Person&gt;" means return the text of first Toy element
	 * inside of the Toys child element (child of Person).
	 * 
	 * Cannot go up levels, or back to root.  Do not start with a slash as in "/People".
	 * 
	 * @param path a backslash delimited string
	 * @return text of first matching element, or null if no match
	 */
	public String selectFirstText(String path) {
		XElement first = this.selectFirst(path);
		
		if (first != null)
			return first.getText();
		
		return null;
	}
	
	/**
	 * A way to select text of a  child or sub child elements similar to XPath but lightweight.
	 * '*' is supported to match
	 * all elements at a given level.  Returns only the first match.
	 * 
	 * For example: "Toys/Toy" called on "&lt;Person&gt;" means return the text of first Toy element
	 * inside of the Toys child element (child of Person).
	 * 
	 * Cannot go up levels, or back to root.  Do not start with a slash as in "/People".
	 * 
	 * @param path a backslash delimited string
	 * @param def default text if none found
	 * @return text of first matching element, or null if no match
	 */
	public String selectFirstText(String path, String def) {
		XElement first = this.selectFirst(path);
		
		if (first != null) {
			String t = first.getText();
			
			if (StringUtil.isNotEmpty(t))
				return t;
		}
		
		return def;
	}
	
	/**
	 * A way to select text of a  child or sub child elements similar to XPath but lightweight.
	 * '*' is supported to match
	 * all elements at a given level.  Returns only the first match.
	 * 
	 * For example: "Toys/Toy" called on "&lt;Person&gt;" means return the text of first Toy element
	 * inside of the Toys child element (child of Person).
	 * 
	 * Cannot go up levels, or back to root.  Do not start with a slash as in "/People".
	 * 
	 * @param path a backslash delimited string
	 * @param def default object to return if path not found
	 * @return text of first matching element, or null if no match
	 */
	public Object selectFirstValue(String path, Object def) {
		XElement first = this.selectFirst(path);
		
		if (first != null) {
			String t = first.getText();
			
			if (StringUtil.isNotEmpty(t))
				return t;
		}
		
		return def;
	}
	
	/**
	 * A way to select child or sub child elements similar to XPath but lightweight.
	 * Cannot select values or attributes, just elements.  * is supported to match
	 * all elements at a given level.  Returns only the first match.
	 * 
	 * For example: "Toys/Toy" called on "&lt;Person&gt;" means return first Toy element
	 * inside of the Toys child element (child of Person).
	 * 
	 * Cannot go up levels, or back to root.  Do not start with a slash as in "/People".
	 * 
	 * @param path a backslash delimited string
	 * @return first matching element, or null if no match
	 */
	public XElement selectFirst(String path) {
		if (!this.hasChildren())
			return null;
		
		int pos = path.indexOf('/');

		// go back to root not supported
		if (pos == 0)
			return null;
		
		String name = null;
		
		if (pos == -1) {
			name = path;
			path = null;
		}
		else { 
			name = path.substring(0, pos);
			path = path.substring(pos + 1);
		}
		
		// TODO add filter per XPath - [@n = f]
		
		for (XNode n : this.children) {
			if (n instanceof XElement) {
				if ("*".equals(name) || ((XElement)n).getName().equals(name)) {
					if (pos == -1) 
						return (XElement)n;
					else  {
						XElement r = ((XElement)n).selectFirst(path);
						
						if (r != null)
							return r;
					}
				}
			}
		}
		
		return null;
	}

	/**
	 * Finds the index of a named child tagged element. If there is no such
	 * child, -1 is returned.
	 * 
	 * @param name
	 *            the name of the child of this TaggedElement to find
	 * @return the index of the found element or -1 if not found
	 */
	public int findIndex(String name) {
		if (this.children != null)
			for (int i = 0; i < this.children.size(); i++) {
				XNode node = this.children.get(i);
				
				if (node instanceof XElement) 
					if (((XElement) node).getName().equals(name))
						return i;
			}
		
		return -1;
	}
	
	public int findIndex(XElement el) {
		if (this.children != null)
			for (int i = 0; i < this.children.size(); i++) {
				XNode node = this.children.get(i);
				
				if (node == el)
					return i;
			}
		
		return -1;
	}
	
	/** _Tr
	 * A way to select a child or sub child structure similar to XPath but lightweight.
	 * Can select composites and scalars.  Use a . or / delimiter.
	 *
	 * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
	 * 4th toy in this person's Toys list.
	 *
	 * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
	 *
	 * @param path parts of the path holding a list index or a field name
	 * @return selected structure if any, otherwise null
	 */
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 0)
			return this;
		
		PathPart part = path[0];
		
		if (part.isField()) {
			String partField = part.getField();
			
			if (partField.startsWith("@")) {
				return StringStruct.of(this.getAttribute(partField.substring(1)));
			}
			
			if (partField.equals("#"))
				return StringStruct.of(this.getText());
			
			ListStruct nodes = ListStruct.list();
			
			nodes.withCollection(this.selectAll(partField));
			
			if (path.length == 1)
				return nodes;
			
			return nodes.select(Arrays.copyOfRange(path, 1, path.length));
		}
		else if (this.children != null) {
			int partIndex = part.getIndex();
			
			XNode node = this.children.get(partIndex);
			
			if (path.length == 1)
				return node;
			
			return node.select(Arrays.copyOfRange(path, 1, path.length));
		}
		
		return null;
	}
	
	/**
	 * sets the list of children of this element. This method replaces the
	 * current children.
	 * 
	 * @param elements
	 *            the new list of children
	 */
	public void setElements(List<XNode> elements) {
		this.children = elements;
	}

	/**
	 * gets a list of the children of this element. This method always returns a
	 * List even if it is empty.
	 * 
	 * @return the List containing the children of this element
	 */
	public List<XNode> getChildren() {
		if (this.children == null)
			this.children = new CopyOnWriteArrayList<XNode>();
		
		return this.children;
	}

	public int getChildCount() {
		if (this.children == null)
			return 0;
		
		return this.children.size();
	}
	
	/**
	 * 
	 * @return the text contained in this element if any, else null
	 */
	public String getText() {
		if (!this.hasChildren())
			return null;

		int ccount = this.children.size();
		
		if (ccount == 1) {
			// TODO improve to support multiple CDATA sections in one element
			XNode f = this.children.get(0);
			
			if (f instanceof XText)
				return ((XText)f).getValue();
		}
		
		StringBuilder sb = new StringBuilder();
		
		this.gatherText(sb);
		
		return sb.toString();
	}
	
	public void gatherText(StringBuilder sb) {
		if (!this.hasChildren())
			return;

		for (XNode n : this.children) {
			if (n instanceof XText) 
				sb.append(((XText)n).getValue());
			else if (n instanceof XElement)
				((XElement)n).gatherText(sb);
		}
	}

	public boolean hasText() {
		if (!this.hasChildren())
			return false;
		
		for (int i = 0; i < this.children.size(); i++) {
			XNode f = this.children.get(i);

			if ((f instanceof XText) && ((XText)f).isNotEmpty())
				return true;
		}

		return false;
	}
	
	// returns Value attribute if present, else returns text
	public String getValue() {
		if (this.hasAttribute("Value"))
			return this.getAttribute("Value");
		
		return this.getText();
	}
	
	public boolean hasValue() {
		if (this.hasAttribute("Value"))
			return StringUtil.isNotEmpty(this.getAttribute("Value"));
		
		return this.hasText();
	}
	
	public void clearValue() {
		this.clearChildren();
		this.removeAttribute("Value");
		
	}

	public XElement value(String v) {
		this.setValue(v);
		return this;
	}
	
	public void setValue(String v) {
		this.clearChildren();
		this.removeAttribute("Value");
		
		if (v == null)
			return;
		
		if ((v.length() > 250) || v.contains("\n") || v.contains("\t") || v.contains("<") || v.contains(">") || v.contains("&")) {
			this.withCData(v);
		}
		else {
			this.setAttribute("Value", v);
		}
	}
	
	/**
	 * assumes that the text content of this element is escaped xml,
	 * reads and parses the text content and returns the root element
	 * from that content
	 * 
	 * @param keepwhitespace don't strip white space when parsing the content of element
	 * @return root xml element for parsed content or null
	 */
	public XElement toXml(boolean keepwhitespace, boolean skipemptynodes) {
		if (! this.hasChildren()) {
			Logger.errorTr(244);
			return null;
		}
		
		XNode f = this.children.get(0);
		
		if (f instanceof XText)
			return XmlReader.parse(((XText)f).getValue(), keepwhitespace, skipemptynodes);
		
		Logger.errorTr(245);
		return null;
	}

	/**
	 * assumes the text content of this element is Json, reads and
	 * parses the text content
	 * 
	 * @return struct or null
	 */
	public CompositeStruct toStruct() {
		if (!this.hasChildren())
			return null;
		
		XNode f = this.children.get(0);
		
		if (f instanceof XText)
			return CompositeParser.parseJson(((XText)f).getValue());
		
		return null;
	}

	/**
	 * 
	 * @return the first child node or null
	 */
	public XNode getFirstChild() {
		if (this.hasChildren())
			return this.children.get(0);
		
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.toString(true);
	}

	public String toPrettyString() {
		return this.toString(true);
	}

	/**
	 * returns just the tag start, not the content or children or the tag end.  
	 * useful for debugging
	 * 
	 * @return tag start in xml syntax
	 */
	public String toLocalString() {
		StringBuilder sb = new StringBuilder();

		// Put the opening tag out
		sb.append("<" + this.tagName);

		// Write the attributes out
		if (this.attributes != null) 
			for (Map.Entry<String, String> entry : this.attributes.entrySet()) {
				sb.append(" " + entry.getKey() + "=");
				sb.append("\"" + entry.getValue() + "\"");
			}

		sb.append(">");
		
		return sb.toString();
	}
	
	public String toInnerString() {
		return this.toInnerString(true);
	}
	
	public String toInnerString(boolean formatted) {
		StringBuilder sb = new StringBuilder();

		// write out the closing tag or other elements
		boolean formatThis = formatted;
		
		if (this.hasChildren()) {
			for (XNode element : this.children) {
				formatThis = (element instanceof XText) ? false : formatted;
				element.toString(sb, formatThis, 0);
			}
		}
		
	    return sb.toString();
	}

	/* (non-Javadoc)
	 * @see dcraft.xml.XNode#toString(java.lang.StringBuffer, boolean, int)
	 */
	@Override
	protected StringBuilder toString(StringBuilder sb, boolean formatted, int level) {
		// Add leading newline and spaces, if necessary
		if (formatted && level > 0) {
			sb.append("\n");
			for (int i = level; i > 0; i--)
				sb.append("\t");
		}

		// Put the opening tag out
		sb.append("<" + this.tagName);

		// Write the attributes out
		if (this.attributes != null) 
			for (Map.Entry<String, String> entry : this.attributes.entrySet()) {
				sb.append(" " + entry.getKey() + "=");
				sb.append("\"" + entry.getValue() + "\"");
			}

		// write out the closing tag or other elements
		boolean formatThis = formatted;
		boolean nontext = false;
		
		if (!this.hasChildren()) {
			sb.append(" /> ");
		} 
		else {
			sb.append(">");
			
			for (XNode node : this.children) {
				formatThis = (node instanceof XText) ? false : formatted;
				
				if (!(node instanceof XText))
					nontext = true;

				node.toString(sb, formatThis, level + 1);
			}
			
			// Add leading newline and spaces, if necessary
			if (formatThis || nontext) {
				sb.append("\n");
				
				for (int i = level; i > 0; i--)
					sb.append("\t");
			}
			
			// Now put the closing tag out
			sb.append("</" + this.tagName + "> ");
		}
		
		return sb;
	}
	
	/* (non-Javadoc)
	 * @see dcraft.xml.XNode#toMemory(dcraft.lang.Memory, boolean, int)
	 */
	@Override
	protected void toMemory(Memory sb, boolean formatted, int level) {
		// Add leading newline and spaces, if necessary
		if (formatted && level > 0) {
			sb.write("\n");
			for (int i = level; i > 0; i--)
				sb.write("\t");
		}

		// Put the opening tag out
		sb.write("<" + this.tagName);

		// Write the attributes out
		if (this.attributes != null) 
			for (Map.Entry<String, String> entry : this.attributes.entrySet()) {
				sb.write(" " + entry.getKey() + "=");
				sb.write("\"" + entry.getValue() + "\"");
			}

		// write out the closing tag or other elements
		boolean formatThis = formatted;
		boolean nontext = false;
		
		if (!this.hasChildren()) {
			sb.write(" /> ");
		} 
		else {
			sb.write(">");
			
			for (XNode node : this.children) {
				formatThis = (node instanceof XText) ? false : formatted;
				
				if (!(node instanceof XText))
					nontext = true;
				
				node.toMemory(sb, formatThis, level + 1);
			}
			
			// Add leading newline and spaces, if necessary
			if (formatThis || nontext) {
				sb.write("\n");
				
				for (int i = level; i > 0; i--)
					sb.write("\t");
			}
			
			// Now put the closing tag out
			sb.write("</" + this.tagName + "> ");
		}
	}
	
	@Override
	public boolean isEmpty() {
		if ((this.children == null) || (this.children.size() == 0)) {
			return ((this.attributes == null) || (this.children.size() == 0));
		}
		
		return false;
	}
	
	public boolean isChildEmpty() {
		return ((this.children == null) || (this.children.size() == 0));
	}

	@Override
	protected void doCopy(Struct n) {
		super.doCopy(n);
		
		XElement copy = (XElement) n;
		
		if (this.attributes != null) {
			copy.attributes = new HashMap<>();
			
			for (Entry<String, String> entry : this.attributes.entrySet())
				copy.attributes.put(entry.getKey(), entry.getValue());
		}
		
		if (this.children != null) {
			copy.children = new CopyOnWriteArrayList<>();
			
			for (XNode entry : this.children)
				copy.children.add(entry.deepCopy());
		}
	}
	
	@Override
	public void toBuilder(ICompositeBuilder builder) {
		// TODO ?
	}
}
