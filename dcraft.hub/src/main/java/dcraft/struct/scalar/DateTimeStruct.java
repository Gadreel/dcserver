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

import java.time.ZonedDateTime;

import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.task.IParentAwareWork;
import org.threeten.extra.PeriodDuration;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

public class DateTimeStruct extends ScalarStruct {
	static public DateTimeStruct of(ZonedDateTime v) {
		DateTimeStruct struct = new DateTimeStruct();
		struct.value = v;
		return struct;
	}

	static public DateTimeStruct ofAny(Object v) {
		DateTimeStruct struct = new DateTimeStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected ZonedDateTime value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("DateTime");
	}

	public DateTimeStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToDateTime(v);
	}

	public ZonedDateTime getValue() {
		return this.value; 
	}
	
	public void setValue(ZonedDateTime v) { 
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
		String op = code.getName();
		
		// we are loose on the idea of null/zero.  operations always perform on now, except Validate
		if ((this.value == null) && ! "Validate".equals(op))
			this.value = ZonedDateTime.now();
		
		if ("Inc".equals(op)) {
			this.value = this.value.plusDays(1);
			return ReturnOption.CONTINUE;
		}
		else if ("Dec".equals(op)) {
			this.value = this.value.minusDays(1);
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(op)) {
			Struct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			return ReturnOption.CONTINUE;
		}
		else if ("Add".equals(op)) {
			try { 
				if (code.hasAttribute("Years")) 
					this.value = this.value.plusYears((int)StackUtil.intFromElement(stack, code, "Years"));
				else if (code.hasAttribute("Months")) 
					this.value = this.value.plusMonths((int)StackUtil.intFromElement(stack, code, "Months"));
				else if (code.hasAttribute("Days")) 
					this.value = this.value.plusDays((int)StackUtil.intFromElement(stack, code, "Days"));
				else if (code.hasAttribute("Hours")) 
					this.value = this.value.plusHours((int)StackUtil.intFromElement(stack, code, "Hours"));
				else if (code.hasAttribute("Minutes")) 
					this.value = this.value.plusMinutes((int)StackUtil.intFromElement(stack, code, "Minutes"));
				else if (code.hasAttribute("Seconds")) 
					this.value = this.value.plusSeconds((int)StackUtil.intFromElement(stack, code, "Seconds"));
				else if (code.hasAttribute("Weeks")) 
					this.value = this.value.plusWeeks((int)StackUtil.intFromElement(stack, code, "Weeks"));
				else if (code.hasAttribute("Period")) {
					PeriodDuration p = PeriodDuration.parse(StackUtil.stringFromElement(stack, code, "Period"));
					this.value = this.value.plus(p);
				}
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Subtract".equals(op)) {
			try { 
				if (code.hasAttribute("Years")) 
					this.value = this.value.minusYears((int)StackUtil.intFromElement(stack, code, "Years"));
				else if (code.hasAttribute("Months")) 
					this.value = this.value.minusMonths((int)StackUtil.intFromElement(stack, code, "Months"));
				else if (code.hasAttribute("Days")) 
					this.value = this.value.minusDays((int)StackUtil.intFromElement(stack, code, "Days"));
				else if (code.hasAttribute("Hours")) 
					this.value = this.value.minusHours((int)StackUtil.intFromElement(stack, code, "Hours"));
				else if (code.hasAttribute("Minutes")) 
					this.value = this.value.minusMinutes((int)StackUtil.intFromElement(stack, code, "Minutes"));
				else if (code.hasAttribute("Seconds")) 
					this.value = this.value.minusSeconds((int)StackUtil.intFromElement(stack, code, "Seconds"));
				else if (code.hasAttribute("Weeks")) 
					this.value = this.value.minusWeeks((int)StackUtil.intFromElement(stack, code, "Weeks"));
				else if (code.hasAttribute("Period")) {
					PeriodDuration p = PeriodDuration.parse(StackUtil.stringFromElement(stack, code, "Period"));
					this.value = this.value.minus(p);
				}
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	DateTimeStruct nn = (DateTimeStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public Struct deepCopy() {
		DateTimeStruct cp = new DateTimeStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (DateTimeStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return DateTimeStruct.comparison(this, y);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		return (this.value == null) ? "null" : TimeUtil.stampFmt.format(this.value);
	}

	@Override
	public Object toInternalValue(RootType t) {
		if ((this.value != null) && (t == RootType.String))
			return this.toString();
		
		return this.value;
	}

	public static int comparison(Object x, Object y)
	{
		ZonedDateTime xv = Struct.objectToDateTime(x);
		ZonedDateTime yv = Struct.objectToDateTime(y);

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
