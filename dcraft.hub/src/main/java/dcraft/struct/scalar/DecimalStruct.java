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

import java.math.BigDecimal;
import java.math.RoundingMode;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class DecimalStruct extends ScalarStruct {
	static public DecimalStruct of(BigDecimal v) {
		DecimalStruct struct = new DecimalStruct();
		struct.value = v;
		return struct;
	}

	static public DecimalStruct of(double v) {
		DecimalStruct struct = new DecimalStruct();
		struct.value = BigDecimal.valueOf(v);
		return struct;
	}

	static public DecimalStruct ofAny(Object v) {
		DecimalStruct struct = new DecimalStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected BigDecimal value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("Decimal");
	}

	public DecimalStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToDecimal(v);
	}

	public BigDecimal getValue() {
		return this.value; 
	}
	
	public void setValue(BigDecimal v) { 
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
		if ((this.value == null) && ! "Validate".equals(code.getName()))
			this.value = BigDecimal.ZERO;
		
		if ("Inc".equals(code.getName())) {
			this.value = this.value.add(BigDecimal.ONE);
			return ReturnOption.CONTINUE;
		}
		else if ("Dec".equals(code.getName())) {
			this.value = this.value.subtract(BigDecimal.ONE);
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);
			
			this.adaptValue(sref);			
			
			return ReturnOption.CONTINUE;
		}
		else if ("Add".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);

			try {
				this.value = this.value.add(Struct.objectToDecimal(sref));			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Subtract".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);

			try {
				this.value = this.value.subtract(Struct.objectToDecimal(sref));			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Multiply".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);

			try {
				this.value = this.value.multiply(Struct.objectToDecimal(sref));			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Divide".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);

			// TODO support other rounding modes and scales - see integer

			try {
				this.value = this.value.divide(Struct.objectToDecimal(sref), 6, RoundingMode.HALF_EVEN);
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Min".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);

			try {
				this.value = this.value.min(Struct.objectToDecimal(sref));
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Max".equals(code.getName())) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);
		
			try {
				this.value = this.value.max(Struct.objectToDecimal(sref));			
			}
			catch (Exception x) {
				Logger.error("Error doing " + code.getName() + ": " + x);
			}
				
			return ReturnOption.CONTINUE;
		}
		else if ("Abs".equals(code.getName())) {
			this.value = this.value.abs();			
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	DecimalStruct nn = (DecimalStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public BaseStruct deepCopy() {
		DecimalStruct cp = new DecimalStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (DecimalStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return DecimalStruct.comparison(this, y);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		return (this.value == null) ? "null" : this.value.toPlainString();
	}

	@Override
	public Object toInternalValue(RootType t) {
		return this.value;
	}

	public static int comparison(Object x, Object y)
	{
		BigDecimal xv = Struct.objectToDecimal(x);
		BigDecimal yv = Struct.objectToDecimal(y);

		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		return xv.compareTo(yv);
	}
}
