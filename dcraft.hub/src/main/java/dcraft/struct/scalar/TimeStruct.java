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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.task.IParentAwareWork;
import org.threeten.extra.PeriodDuration;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class TimeStruct extends ScalarStruct {
	static public TimeStruct of(LocalTime v) {
		TimeStruct timeStruct = new TimeStruct();
		timeStruct.value = v;
		return timeStruct;
	}

	static public TimeStruct ofAny(Object v) {
		TimeStruct timeStruct = new TimeStruct();
		timeStruct.adaptValue(v);
		return timeStruct;
	}

	protected LocalTime value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("LocalTime");
	}

	public TimeStruct() {
	}

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToTime(v);
	}

	public LocalTime getValue() {
		return this.value; 
	}
	
	public void setValue(LocalTime v) { 
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
		
		// we are loose on the idea of null/zero.  operations always perform on now, except Validate
		if ((this.value == null) && ! "Validate".equals(op))
			this.value = LocalTime.now();
		
		if ("Inc".equals(op)) {
			this.value = this.value.plusSeconds(1);
			return ReturnOption.CONTINUE;
		}
		else if ("Dec".equals(op)) {
			this.value = this.value.minusSeconds(1);
			return ReturnOption.CONTINUE;
		}
		else if ("Set".equals(op)) {
			BaseStruct sref = code.hasAttribute("Value")
					? StackUtil.refFromElement(stack, code, "Value")
					: StackUtil.resolveReference(stack, code.getText());
			
			this.adaptValue(sref);
			return ReturnOption.CONTINUE;
		}
		else if ("Add".equals(op)) {
			try { 
				if (code.hasAttribute("Seconds")) 
					this.value = this.value.plusSeconds((int)StackUtil.intFromElement(stack, code, "Seconds", 0));
				else if (code.hasAttribute("Minutes")) 
					this.value = this.value.plusMinutes((int)StackUtil.intFromElement(stack, code, "Minutes", 0));
				else if (code.hasAttribute("Hours")) 
					this.value = this.value.plusHours((int)StackUtil.intFromElement(stack, code, "Hours", 0));
				else if (code.hasAttribute("Millis")) 
					this.value = this.value.plus((int)StackUtil.intFromElement(stack, code, "Millis", 0), ChronoUnit.MILLIS);
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
				if (code.hasAttribute("Seconds")) 
					this.value = this.value.minusSeconds((int)StackUtil.intFromElement(stack, code, "Seconds", 0));
				else if (code.hasAttribute("Minutes")) 
					this.value = this.value.minusMinutes((int)StackUtil.intFromElement(stack, code, "Minutes", 0));
				else if (code.hasAttribute("Hours")) 
					this.value = this.value.minusHours((int)StackUtil.intFromElement(stack, code, "Hours", 0));
				else if (code.hasAttribute("Millis")) 
					this.value = this.value.minus((int)StackUtil.intFromElement(stack, code, "Millis", 0), ChronoUnit.MILLIS);
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
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	TimeStruct nn = (TimeStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public BaseStruct deepCopy() {
		TimeStruct cp = new TimeStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (TimeStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return TimeStruct.comparison(this, y);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		//return (this.value == null) ? "null" : this.value.toString("HH:mm:ss.sss");  -- because LocalTime.parse is messing up millsec values
		return (this.value == null) ? "null" : DateTimeFormatter.ofPattern("HH:mm").format(this.value);
	}

	@Override
	public Object toInternalValue(RootType t) {
		if ((this.value != null) && (t == RootType.String))
			return this.toString();
		
		return this.value;
	}

	public static int comparison(Object x, Object y)
	{
		LocalTime xv = Struct.objectToTime(x);
		LocalTime yv = Struct.objectToTime(y);
		
		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		return xv.compareTo(yv);
	}
}
