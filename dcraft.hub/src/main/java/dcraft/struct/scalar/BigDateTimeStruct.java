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
import dcraft.hub.time.BigDateTime;
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

public class BigDateTimeStruct extends ScalarStruct {
	static public BigDateTimeStruct of(BigDateTime v) {
		BigDateTimeStruct struct = new BigDateTimeStruct();
		struct.value = v;
		return struct;
	}

	static public BigDateTimeStruct ofAny(Object v) {
		BigDateTimeStruct struct = new BigDateTimeStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected BigDateTime value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("BigDateTime");
	}

	public BigDateTimeStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToBigDateTime(v);
	}

	public BigDateTime getValue() {
		return this.value; 
	}
	
	public void setValue(BigDateTime v) { 
		this.value = v; 
	}
	
	@Override
	public boolean isEmpty() {
		return ((this.value == null) || (this.value.getYear() == null));
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
			this.value = BigDateTime.nowDateTime();
		
		/* TODO
		if ("Inc".equals(op)) {
			this.value = this.value.plusDays(1);
			stack.resume();
			return;
		}
		else if ("Dec".equals(op)) {
			this.value = this.value.minusDays(1);
			stack.resume();
			return;
		}
		else */
		if ("Set".equals(op)) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			return ReturnOption.CONTINUE;
		}
		/*
		else if ("Add".equals(op)) {
			try { 
				if (code.hasAttribute("Years")) 
					this.value.plusYears((int)stack.intFromElement(code, "Years"));
				else if (code.hasAttribute("Months")) 
					this.value.plusMonths((int)stack.intFromElement(code, "Months"));
				else if (code.hasAttribute("Days")) 
					this.value.plusDays((int)stack.intFromElement(code, "Days"));
				else if (code.hasAttribute("Hours")) 
					this.value.plusHours((int)stack.intFromElement(code, "Hours"));
				else if (code.hasAttribute("Minutes")) 
					this.value.plusMinutes((int)stack.intFromElement(code, "Minutes"));
				else if (code.hasAttribute("Seconds")) 
					this.value.plusSeconds((int)stack.intFromElement(code, "Seconds"));
				else if (code.hasAttribute("Weeks")) 
					this.value.plusWeeks((int)stack.intFromElement(code, "Weeks"));
				else if (code.hasAttribute("Period")) {
					Period p = ISOPeriodFormat.standard().parsePeriod(stack.stringFromElement(code, "Period"));
					this.value.plus(p);
				}
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			stack.resume();
			return;
		}
		else if ("Subtract".equals(op)) {
			try { 
				if (code.hasAttribute("Years")) 
					this.value.minusYears((int)stack.intFromElement(code, "Years"));
				else if (code.hasAttribute("Months")) 
					this.value.minusMonths((int)stack.intFromElement(code, "Months"));
				else if (code.hasAttribute("Days")) 
					this.value.minusDays((int)stack.intFromElement(code, "Days"));
				else if (code.hasAttribute("Hours")) 
					this.value.minusHours((int)stack.intFromElement(code, "Hours"));
				else if (code.hasAttribute("Minutes")) 
					this.value.minusMinutes((int)stack.intFromElement(code, "Minutes"));
				else if (code.hasAttribute("Seconds")) 
					this.value.minusSeconds((int)stack.intFromElement(code, "Seconds"));
				else if (code.hasAttribute("Weeks")) 
					this.value.minusWeeks((int)stack.intFromElement(code, "Weeks"));
				else if (code.hasAttribute("Period")) {
					Period p = ISOPeriodFormat.standard().parsePeriod(stack.stringFromElement(code, "Period"));
					this.value.minus(p);
				}
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			stack.resume();
			return;
		}
		*/
		
		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	BigDateTimeStruct nn = (BigDateTimeStruct)n;
    	nn.value = this.value;		
    }
    
	@Override
	public Struct deepCopy() {
		BigDateTimeStruct cp = new BigDateTimeStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (BigDateTimeStruct.comparison(this, obj) == 0);
	}

	@Override
	public Object toInternalValue(RootType t) {
		if ((this.value != null) && (t == RootType.String))
			return this.value.toString();
		
		return this.value;
	}

	@Override
	public int compareTo(Object y) {
		return BigDateTimeStruct.comparison(this, y);
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
		BigDateTime xv = Struct.objectToBigDateTime(x);
		BigDateTime yv = Struct.objectToBigDateTime(y);

		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		return 0;  // TODO xv.compareTo(yv);
	}
}
