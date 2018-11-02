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
package dcraft.struct.scalar;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.util.HexUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class StringStruct extends ScalarStruct {
	static public StringStruct of(CharSequence v) {
		StringStruct struct = new StringStruct();
		struct.value = v;
		return struct;
	}

	static public StringStruct ofEmpty() {
		StringStruct struct = new StringStruct();
		return struct;
	}

	static public StringStruct ofAny(Object v) {
		StringStruct struct = new StringStruct();
		struct.adaptValue(v);
		return struct;
	}

	public CharSequence value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("String");
	}

	public StringStruct() {
	}

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToString(v);
	}

	public CharSequence getValue() {
		return this.value;
	}

	public String getValueAsString() {
		if (StringUtil.isNotEmpty(this.value))
			return this.value.toString();

		return null;
	}
	
	public void setValue(String v) { 
		this.value = v; 
	}
	
	public void append(String v) {
		if (this.value == null)
			this.value = new StringBuilder();
		
		if (this.value instanceof StringBuilder) {
			((StringBuilder)this.value).append(v);
		}
		else {
			this.value = this.value + v;
		}
	}
	
	@Override
	public boolean isEmpty() {
		return StringUtil.isEmpty(this.value);
	}
	
	@Override
	public boolean isNull() {
		return (this.value == null);
	}
	
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 1) {
			PathPart part = path[0];
			
			if (part.isField() && "@Length".equals(part.getField())) {
				return IntegerStruct.of((this.value != null) ? this.value.length() : 0);
			}
		}
		
		return super.select(path);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Lower".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value))
				this.value = this.value.toString().toLowerCase();
			
			return ReturnOption.CONTINUE;
		}
		else if ("Upper".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value))
				this.value = this.value.toString().toUpperCase();
			
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Format".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			String pat = StackUtil.stringFromElement(stack, code, "Pattern");
			
			this.value = Struct.objectToString(DataUtil.format(sref, pat));
			
			return ReturnOption.CONTINUE;
		}
		else if ("Append".equals(code.getName())) {
			if (this.value == null)
				this.value = "";
			
			String sref = code.hasAttribute("Value")
					? StackUtil.stringFromElement(stack, code, "Value")
					: StackUtil.resolveValueToString(stack, code.getText());

			String its = Struct.objectToString(sref);
			
			if (its != null)
				this.value = this.value + its;
			
			return ReturnOption.CONTINUE;
		}
		else if ("Prepend".equals(code.getName())) {
			if (this.value == null)
				this.value = "";
			
			String sref = code.hasAttribute("Value")
					? StackUtil.stringFromElement(stack, code, "Value")
					: StackUtil.resolveValueToString(stack, code.getText());
			
			String its = Struct.objectToString(sref);
			
			if (its != null)
				this.value = its + this.value;
			
			return ReturnOption.CONTINUE;
		}
		else if ("Replace".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value)) {
				String from = StackUtil.stringFromElement(stack, code, "Old");
				String to = StackUtil.stringFromElement(stack, code, "New");
				String pattern = StackUtil.stringFromElement(stack, code, "Pattern");
				
				if (StringUtil.isEmpty(pattern))
					this.value = this.value.toString().replace(from, to);
				else 
					this.value = this.value.toString().replaceAll(pattern, to);
			}
			
			
			return ReturnOption.CONTINUE;
		}
		else if ("Substring".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value)) {
				int from = (int) StackUtil.intFromElement(stack, code, "From", 0);
				int to = (int) StackUtil.intFromElement(stack, code, "To", 0);
				int length = (int) StackUtil.intFromElement(stack, code, "Length", 0);
						
				if (to > 0) 
					this.value = this.value.toString().substring(from, to);
				else if (length > 0) 
					this.value = this.value.toString().substring(from, from + length);
				else
					this.value = this.value.toString().substring(from);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("FillCode".equals(code.getName())) {
			int length = (int) StackUtil.intFromElement(stack, code, "Length", 12);
			this.value = StringUtil.buildSecurityCode(length);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Trim".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value)) 
				this.value = StringUtil.stripWhitespace(this.value.toString().trim());
			
			return ReturnOption.CONTINUE;
		}
		else if ("TrimStart".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value)) 
				this.value = StringUtil.stripLeadingWhitespace(this.value.toString());
			
			return ReturnOption.CONTINUE;
		}
		else if ("TrimEnd".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value)) 
				this.value = StringUtil.stripTrailingWhitespace(this.value.toString());
			
			return ReturnOption.CONTINUE;
		}
		else if ("LeftPad".equals(code.getName())) {
			if (StringUtil.isEmpty(this.value)) 
				this.value = "";
			
			int size = (int) StackUtil.intFromElement(stack, code, "Size", 1);
			String ch = code.hasAttribute("With") ? StackUtil.stringFromElement(stack, code, "With") : " ";
				
			this.value = StringUtil.leftPad(this.value.toString(), size, ch);
			
			return ReturnOption.CONTINUE;
		}
		else if ("RightPad".equals(code.getName())) {
			if (StringUtil.isEmpty(this.value)) 
				this.value = "";
			
			int size = (int) StackUtil.intFromElement(stack, code, "Size", 1);
			String ch = code.hasAttribute("With") ? StackUtil.stringFromElement(stack, code, "With") : " ";
				
			this.value = StringUtil.rightPad(this.value.toString(), size, ch);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Split".equals(code.getName())) {
			String delim = StackUtil.stringFromElement(stack, code, "Delim", ",");
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				ListStruct res = ListStruct.list();
				
				if ( StringUtil.isNotEmpty(this.value)) {
					res.with(this.value.toString().split(delim));
				}
				
				StackUtil.addVariable(stack, result, res);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("IndexOf".equals(code.getName())) {
			String find = StackUtil.stringFromElement(stack, code, "Find", ",");
			long from = StackUtil.intFromElement(stack, code, "From", 0);
			String result = StackUtil.stringFromElement(stack, code, "Result");

			if (StringUtil.isNotEmpty(result)) {
				IntegerStruct res = IntegerStruct.of(-1);

				if ( StringUtil.isNotEmpty(this.value)) {
					res.adaptValue(this.value.toString().indexOf(find, (int) from));
				}

				StackUtil.addVariable(stack, result, res);
			}

			return ReturnOption.CONTINUE;
		}
		else if ("HexEncode".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value))
				this.value = HexUtil.encodeHex(this.value.toString().trim());

			return ReturnOption.CONTINUE;
		}
		else if ("ParseJson".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");

			if (StringUtil.isNotEmpty(this.value)) {
				StackUtil.addVariable(stack, result, CompositeParser.parseJson(this.value));
			}

			return ReturnOption.CONTINUE;
		}

		/*
		// TODO also implement
		// <Piece Delim="str" Index="num" />
		// <Align Size="num" Pad="left|right" PadChar="c" />
		 */
		
		return super.operation(stack, code);
	}
	
    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	StringStruct nn = (StringStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public Struct deepCopy() {
		StringStruct cp = new StringStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (StringStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return StringStruct.comparison(this, y);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		return (this.value == null) ? "null" : this.value.toString();
	}

	@Override
	public Object toInternalValue(RootType t) {
		return this.value;
	}

	public static int comparison(Object x, Object y)
	{
		String xv = Struct.objectToString(x);
		String yv = Struct.objectToString(y);

		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		int cv = xv.compareTo(yv);

		//System.out.println("a: " + xv + " - b: " + yv + " x: " + cv);

		return cv;
	}
	
	@Override
	public boolean checkLogic(IParentAwareWork stack, XElement source) throws OperatingContextException {
		boolean isok = true;
		boolean condFound = false;
		
		if (this.value != null) {
			boolean caseinsensitive = StackUtil.boolFromElement(stack, source, "CaseInsensitive", false);
			
			if (!condFound && source.hasAttribute("Contains")) {
				String other = StackUtil.stringFromElement(stack, source, "Contains");
	            isok = caseinsensitive ? this.value.toString().toLowerCase().contains(other.toLowerCase()) : this.value.toString().contains(other);
	            condFound = true;
	        }
			if (! condFound && source.hasAttribute("In")) {
				Struct other = StackUtil.refFromElement(stack, source, "In");

				if ((other instanceof StringStruct) && ! other.isEmpty()) {
					String[] options = other.toString().split(",");

					for (String opt : options) {
						if (caseinsensitive ? this.value.toString().equalsIgnoreCase(opt) : this.value.toString().equals(opt))
							return true;
					}
				}

				return false;
			}

			if (!condFound && source.hasAttribute("StartsWith")) {
				String other = StackUtil.stringFromElement(stack, source, "StartsWith");
	            isok = caseinsensitive ? this.value.toString().toLowerCase().startsWith(other.toLowerCase()) : this.value.toString().startsWith(other);
	            condFound = true;
	        }
			
			if (!condFound && source.hasAttribute("EndsWith")) {
				String other = StackUtil.stringFromElement(stack, source, "EndsWith");
	            isok = caseinsensitive ? this.value.toString().toLowerCase().endsWith(other.toLowerCase()) : this.value.toString().endsWith(other);
	            condFound = true;
	        }
		}
		
		if (!condFound) 
			isok = Struct.objectToBooleanOrFalse(this.value);
		
		return isok;
	}
}
