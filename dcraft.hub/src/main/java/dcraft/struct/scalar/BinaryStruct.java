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
import dcraft.schema.RootType;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.Memory;
import dcraft.xml.XElement;

public class BinaryStruct extends ScalarStruct {
	static public BinaryStruct of(Memory v) {
		BinaryStruct struct = new BinaryStruct();
		struct.value = v;
		return struct;
	}

	static public BinaryStruct ofAny(Object v) {
		BinaryStruct struct = new BinaryStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected Memory value = null;

	public BinaryStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToBinary(v);
	}

	public Memory getValue() {
		return this.value; 
	}
	
	public void setValue(Memory v) { 
		this.value = v; 
	}
	
	@Override
	public boolean isEmpty() {
		return (this.value == null) || (this.value.getLength() == 0);
	}
	
	@Override
	public boolean isNull() {
		return (this.value == null);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		String op = code.getName();
		
		// we are loose on the idea of null/zero.  operations always perform on now, except Validate
		if ((this.value == null) && ! "Validate".equals(op))
			this.value = new Memory();
		
		if ("Position".equals(op)) {
			this.value.setPosition((int) StackUtil.intFromElement(stack, code, "At", 0));
			return ReturnOption.CONTINUE;
		}
		else if ("Capacity".equals(op)) {
			this.value.setCapacity((int) StackUtil.intFromElement(stack, code, "At", 0));
			return ReturnOption.CONTINUE;
		}
		else if ("Length".equals(op)) {
			this.value.setLength((int) StackUtil.intFromElement(stack, code, "At", 0));
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(op)) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			return ReturnOption.CONTINUE;
		}
		
		// TODO support more
		
		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	BinaryStruct nn = (BinaryStruct)n;
    	nn.value = this.value;		// TODO copy
    }
    
	@Override
	public Struct deepCopy() {
		BinaryStruct cp = new BinaryStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (BinaryStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return BinaryStruct.comparison(this, y);
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

	public static int comparison(Object x, Object y) {
		Memory xv = Struct.objectToBinary(x);
		Memory yv = Struct.objectToBinary(y);
		
		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		// TODO return xv.compareTo(yv);

		return 0;
	}
	
	@Override
	public boolean checkLogic(IParentAwareWork stack, XElement source) {
		return Struct.objectToBooleanOrFalse(this.value);
	}
}
