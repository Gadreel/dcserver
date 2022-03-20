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
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.script.inst.LogicBlockState;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.util.HexUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
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
	public BaseStruct select(PathPart... path) {
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
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Format".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			String pat = StackUtil.stringFromElement(stack, code, "Pattern");
			
			this.value = Struct.objectToString(DataUtil.format(sref, pat));
			
			return ReturnOption.CONTINUE;
		}
		else if ("AppendLine".equals(code.getName())) {
			if (this.value == null)
				this.value = "";
			
			this.value = this.value + "\n";
			
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
		else if ("GenerateUuid".equals(code.getName())) {
			this.value = RndUtil.nextUUId();

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
		else if ("LastIndexOf".equals(code.getName())) {
			String find = StackUtil.stringFromElement(stack, code, "Find", ",");
			String result = StackUtil.stringFromElement(stack, code, "Result");

			if (StringUtil.isNotEmpty(result)) {
				IntegerStruct res = IntegerStruct.of(-1);

				if ( StringUtil.isNotEmpty(this.value)) {
					long from = StackUtil.intFromElement(stack, code, "From", this.value.length());

					res.adaptValue(this.value.toString().lastIndexOf(find, (int) from));
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
		else if ("Markdown".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			String mode = StackUtil.stringFromElement(stack, code, "Mode", "Unsafe");

			if (StringUtil.isNotEmpty(this.value)) {
				XElement root = MarkdownUtil.process(this.value.toString(), "safe".equals(mode.toLowerCase()));

				if (root == null) {
					Logger.warn("inline md error: ");
				}
				else {
					StackUtil.addVariable(stack, result, root);
				}
			}

			return ReturnOption.CONTINUE;
		}
		else if ("Summarize".equals(code.getName())) {
			if (StringUtil.isNotEmpty(this.value)) {
				String mode = StackUtil.stringFromElement(stack, code, "Mode", "First");  // First (Paragraph), Max, FirstMax
				Long maxChars = StackUtil.intFromElement(stack, code, "MaxChars");
				String maxTrail = StackUtil.stringFromElement(stack, code, "MaxTrail");

				// trim to first paragraph if applicable
				if (! "Max".equals(mode)) {
					boolean firstNL = false;

					for (int i = 0; i < this.value.length(); i++) {
						if (this.value.charAt(i) == '\n') {
							if (firstNL) {
								this.value = this.value.subSequence(0, i - 2);
								break;
							}
							else {
								firstNL = true;
							}
						}
						else {
							firstNL = false;
						}
					}
				}

				if (! "First".equals(mode) && (maxChars != null) && (this.value.length() > maxChars)) {
					int max = maxChars.intValue();
					int downto = max > 12 ? max - 12 : 1;
					boolean fnd = false;

					for (int i = max; i >= downto; i--) {
						if (! Character.isLetterOrDigit(this.value.charAt(i))) {
							this.value = this.value.subSequence(0, i);
							fnd = true;
							break;
						}
					}

					// if more than 12 digits/chars in a row then just trim in middle
					if (! fnd)
						this.value = this.value.subSequence(0, downto);

					// clear spaces, punc, etc
					for (int i = this.value.length() - 1; i >= 1; i--) {
						if (Character.isLetterOrDigit(this.value.charAt(i))) {
							this.value = this.value.subSequence(0, i + 1);
							break;
						}
					}

					if (StringUtil.isNotEmpty(maxTrail))
						this.value += maxTrail;
				}
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
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	StringStruct nn = (StringStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public BaseStruct deepCopy() {
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
	public int compareToIgnoreCase(Object y) {
		return StringStruct.comparisonIgnoreCase(this, y);
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

	public static int comparisonIgnoreCase(Object x, Object y)
	{
		String xv = Struct.objectToString(x);
		String yv = Struct.objectToString(y);

		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		int cv = xv.compareToIgnoreCase(yv);

		//System.out.println("a: " + xv + " - b: " + yv + " x: " + cv);

		return cv;
	}
	
	@Override
	public void checkLogic(IParentAwareWork stack, XElement source, LogicBlockState logicState) throws OperatingContextException {
		boolean caseinsensitive = StackUtil.boolFromElement(stack, source, "CaseInsensitive", false);
		
		if (source.hasAttribute("Contains")) {
			if (logicState.pass) {
				if (this.value == null) {
					logicState.pass = false;
				}
				else {
					String other = StackUtil.stringFromElement(stack, source, "Contains");
					logicState.pass = caseinsensitive ? this.value.toString().toLowerCase().contains(other.toLowerCase()) : this.value.toString().contains(other);
				}
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("In")) {
			logicState.checked = true;
			
			if (logicState.pass) {
				if (this.value == null) {
					logicState.pass = false;
				}
				else {
					boolean fnd = false;

					BaseStruct other = StackUtil.refFromElement(stack, source, "In");
					
					if ((other instanceof StringStruct) && !other.isEmpty()) {
						String[] options = other.toString().split(",");
						
						for (String opt : options) {
							if (caseinsensitive ? this.value.toString().equalsIgnoreCase(opt) : this.value.toString().equals(opt))
								fnd = true;
						}
					}
					
					logicState.pass = fnd;
				}
			}
		}

		if (source.hasAttribute("StartsWith")) {
			if (logicState.pass) {
				if (this.value == null) {
					logicState.pass = false;
				}
				else {
					String other = StackUtil.stringFromElement(stack, source, "StartsWith");
					logicState.pass = caseinsensitive ? this.value.toString().toLowerCase().startsWith(other.toLowerCase()) : this.value.toString().startsWith(other);
				}
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("EndsWith")) {
			if (logicState.pass) {
				if (this.value == null) {
					logicState.pass = false;
				}
				else {
					String other = StackUtil.stringFromElement(stack, source, "EndsWith");
					logicState.pass = caseinsensitive ? this.value.toString().toLowerCase().endsWith(other.toLowerCase()) : this.value.toString().endsWith(other);
				}
			}
			
			logicState.checked = true;
		}
		
		super.checkLogic(stack, source, logicState);
	}
}
