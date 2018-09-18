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
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class BooleanStruct extends ScalarStruct {
	static public BooleanStruct of(Boolean v) {
		BooleanStruct struct = new BooleanStruct();
		struct.value = v;
		return struct;
	}

	static public BooleanStruct ofAny(Object v) {
		BooleanStruct struct = new BooleanStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected Boolean value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("Boolean");
	}

	public BooleanStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToBoolean(v);
	}

	public Boolean getValue() {
		return this.value; 
	}
	
	public void setValue(Boolean v) { 
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
		String op = code.getName();
		
		// we are loose on the idea of null/zero.  operations always perform on false, except Validate
		if ((this.value == null) && ! "Validate".equals(op))
			this.value = false;
		
		if ("Flip".equals(op)) {
			this.value = !this.value;
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(op)) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	BooleanStruct nn = (BooleanStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public Struct deepCopy() {
		BooleanStruct cp = new BooleanStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (BooleanStruct.comparison(this, obj) == 0);
	}

	@Override
	public Object toInternalValue(RootType t) {
		return this.value;
	}

	@Override
	public int compareTo(Object y) {
		return BooleanStruct.comparison(this, y);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		return (this.value == null) ? "null" : this.value.toString();
	}

	public static int comparison(Object x, Object y)
	{
		Boolean xv = Struct.objectToBoolean(x);
		Boolean yv = Struct.objectToBoolean(y);

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
