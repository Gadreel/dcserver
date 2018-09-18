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

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.RndUtil;
import dcraft.xml.XElement;

public class IntegerStruct extends ScalarStruct {
	static public IntegerStruct of(Long v) {
		IntegerStruct struct = new IntegerStruct();
		struct.value = v;
		return struct;
	}

	static public IntegerStruct of(int v) {
		IntegerStruct struct = new IntegerStruct();
		struct.value = Long.valueOf(v);
		return struct;
	}

	static public IntegerStruct ofAny(Object v) {
		IntegerStruct struct = new IntegerStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected Long value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("Integer");
	}

	public IntegerStruct() {	}

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToInteger(v);
	}

	public Long getValue() {
		return this.value; 
	}
	
	public void setValue(Long v) { 
		this.value = v; 
	}
	
	@Override
	public boolean isEmpty() {
		return (this.value == null);
	}
	
	@Override
	public boolean isNull() {
		return (this.value == null);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		// we are loose on the idea of null/zero.  operations always perform on 0, except Validate
		if ((this.value == null) && !"Validate".equals(code.getName()))
			this.value = 0L;
		
		if ("Inc".equals(code.getName())) {
			this.value++;
			return ReturnOption.CONTINUE;
		}
		else if ("Dec".equals(code.getName())) {
			this.value--;
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			return ReturnOption.CONTINUE;
		}
		else if ("Add".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			Long it = Struct.objectToInteger(sref);
			
			try { 
				this.value += it;
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Subtract".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			try {
				this.value -= Struct.objectToInteger(sref);			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Multiply".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			try {
				this.value *= Struct.objectToInteger(sref);			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Divide".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			try {
				this.value /= Struct.objectToInteger(sref);			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Min".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			try {
				this.value = Math.min(this.value, Struct.objectToInteger(sref));			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Max".equals(code.getName())) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());

			try {
				this.value = Math.max(this.value, Struct.objectToInteger(sref));			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Abs".equals(code.getName())) {
			this.value = Math.abs(this.value);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Random".equals(code.getName())) {
			long from = 1;
			long to = 100;
			
			try {
				if (code.hasAttribute("From")) 
						from = Struct.objectToInteger(StackUtil.refFromElement(stack, code, "From"));
				
				if (code.hasAttribute("To")) 
						to = Struct.objectToInteger(StackUtil.refFromElement(stack, code, "To"));
				
				this.value = RndUtil.testrnd.nextInt((int) (to - from)) + from;
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	IntegerStruct nn = (IntegerStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public Struct deepCopy() {
		IntegerStruct cp = new IntegerStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (IntegerStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return IntegerStruct.comparison(this, y);
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
		Long xv = Struct.objectToInteger(x);
		Long yv = Struct.objectToInteger(y);

		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		return xv.compareTo(yv);
	}
	
	@Override
	public boolean checkLogic(IParentAwareWork stack, XElement source) {
		return Struct.objectToBooleanOrFalse(this.value);
	}
}
