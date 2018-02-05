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

import java.math.BigInteger;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class BigIntegerStruct extends ScalarStruct {
	static public BigIntegerStruct of(BigInteger v) {
		BigIntegerStruct struct = new BigIntegerStruct();
		struct.value = v;
		return struct;
	}

	static public BigIntegerStruct ofAny(Object v) {
		BigIntegerStruct struct = new BigIntegerStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected BigInteger value = null;

	public BigIntegerStruct() { }
	
	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("BigInteger");
	}

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToBigInteger(v);
	}

	public BigInteger getValue() {
		return this.value; 
	}
	
	public void setValue(BigInteger v) { 
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
	public ReturnOption operation(IParentAwareWork stack, XElement code) throws OperatingContextException {
		// we are loose on the idea of null/zero.  operations always perform on 0, except Validate
		if ((this.value == null) && ! "Validate".equals(code.getName()))
			this.value = BigInteger.ZERO;
		
		if ("Inc".equals(code.getName())) {
			this.value = this.value.add(BigInteger.ONE);
			return ReturnOption.CONTINUE;
		}
		else if ("Dec".equals(code.getName())) {
			this.value = this.value.subtract(BigInteger.ONE);
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

			try {
				this.value = this.value.add(Struct.objectToBigInteger(sref));			
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
				this.value = this.value.subtract(Struct.objectToBigInteger(sref));			
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
				this.value = this.value.multiply(Struct.objectToBigInteger(sref));			
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
				this.value = this.value.divide(Struct.objectToBigInteger(sref));			
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
				this.value = this.value.min(Struct.objectToBigInteger(sref));			
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
				this.value = this.value.max(Struct.objectToBigInteger(sref));			
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
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	BigIntegerStruct nn = (BigIntegerStruct)n;
    	nn.value = this.value;		
    }
    
	@Override
	public Struct deepCopy() {
		BigIntegerStruct cp = new BigIntegerStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (BigIntegerStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return BigIntegerStruct.comparison(this, y);
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
		BigInteger xv = Struct.objectToBigInteger(x);
		BigInteger yv = Struct.objectToBigInteger(y);

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
