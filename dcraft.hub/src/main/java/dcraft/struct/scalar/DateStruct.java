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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import dcraft.hub.ResourceHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.CompositeParser;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import org.threeten.extra.PeriodDuration;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class DateStruct extends ScalarStruct {
	static public DateStruct of(LocalDate v) {
		DateStruct struct = new DateStruct();
		struct.value = v;
		return struct;
	}

	static public DateStruct ofAny(Object v) {
		DateStruct struct = new DateStruct();
		struct.adaptValue(v);
		return struct;
	}

	protected LocalDate value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("LocalDate");
	}

	public DateStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = Struct.objectToDate(v);
	}

	public LocalDate getValue() {
		return this.value; 
	}
	
	public void setValue(LocalDate v) { 
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
			this.value = LocalDate.now();
		
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
					? StackUtil.refFromElement(stack, code, "Value", true)
					: StackUtil.resolveReference(stack, code.getText(), true);
			
			this.adaptValue(sref);
			return ReturnOption.CONTINUE;
		}
		else if ("SetYear".equals(op)) {
			try {
				if (code.hasAttribute("Value"))
					this.value = this.value.withYear((int)StackUtil.intFromElement(stack, code, "Value"));
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("SetMonth".equals(op)) {
			try {
				if (code.hasAttribute("Value"))
					this.value = this.value.withMonth((int)StackUtil.intFromElement(stack, code, "Value"));
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("SetDay".equals(op)) {
			try {
				if (code.hasAttribute("Value"))
					this.value = this.value.withDayOfMonth((int)StackUtil.intFromElement(stack, code, "Value"));
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Add".equals(op)) {
			try { 
				if (code.hasAttribute("Years")) 
					this.value = this.value.plusYears((int)StackUtil.intFromElement(stack, code, "Years"));
				
				if (code.hasAttribute("Months"))
					this.value = this.value.plusMonths((int)StackUtil.intFromElement(stack, code, "Months"));
				
				if (code.hasAttribute("Days"))
					this.value = this.value.plusDays((int)StackUtil.intFromElement(stack, code, "Days"));
				
				if (code.hasAttribute("Weeks"))
					this.value = this.value.plusWeeks((int)StackUtil.intFromElement(stack, code, "Weeks"));
				
				if (code.hasAttribute("Period")) {
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
				
				if (code.hasAttribute("Months"))
					this.value = this.value.minusMonths((int)StackUtil.intFromElement(stack, code, "Months"));
				
				if (code.hasAttribute("Days"))
					this.value = this.value.minusDays((int)StackUtil.intFromElement(stack, code, "Days"));
				
				if (code.hasAttribute("Weeks"))
					this.value = this.value.minusWeeks((int)StackUtil.intFromElement(stack, code, "Weeks"));
				
				if (code.hasAttribute("Period")) {
					PeriodDuration p = PeriodDuration.parse(StackUtil.stringFromElement(stack, code, "Period"));
					this.value = this.value.minus(p);
				}
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Difference".equals(op)) {
			try {
				Struct sref = StackUtil.refFromElement(stack, code, "Value", true);
				
				if (sref instanceof DateStruct) {
					DateStruct ref = (DateStruct) sref;
					
					PeriodDuration period = PeriodDuration.between(ref.value, this.value);
					
					String result = StackUtil.stringFromElement(stack, code, "Result");
					
					if (StringUtil.isNotEmpty(result)) {
						RecordStruct res = RecordStruct.record();
						
						res
								.with("Years", period.getPeriod().getYears())
								.with("Months", period.getPeriod().getMonths())
								.with("Days", period.getPeriod().getDays());
						
						StackUtil.addVariable(stack, result, res);
					}
				}
			}
			catch (Exception x) {
				Logger.error("Error doing " + op + ": " + x);
			}
			
			return ReturnOption.CONTINUE;
		}
		else if ("Now".equals(op)) {
			this.value = LocalDate.now();

			return ReturnOption.CONTINUE;
		}
		else if ("Format".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			String format = StackUtil.stringFromElement(stack, code, "Pattern");

			String out = DateTimeFormatter.ofPattern(format)
					.withZone(ZoneId.of(ResourceHub.getResources().getLocale().getDefaultChronology()))
					.format(this.value);

			StackUtil.addVariable(stack, result, StringStruct.of(out));

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	DateStruct nn = (DateStruct)n;
    	nn.value = this.value;
    }
    
	@Override
	public Struct deepCopy() {
		DateStruct cp = new DateStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (DateStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return DateStruct.comparison(this, y);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		return (this.value == null) ? "null" : DateTimeFormatter.ofPattern("yyyy-MM-dd").format(this.value);
	}

	@Override
	public Object toInternalValue(RootType t) {
		if ((this.value != null) && (t == RootType.String))
			return this.toString();
		
		return this.value;
	}

	public static int comparison(Object x, Object y)
	{
		LocalDate xv = Struct.objectToDate(x);
		LocalDate yv = Struct.objectToDate(y);
		
		if ((yv == null) && (xv == null))
			return 0;

		if (yv == null)
			return 1;

		if (xv == null)
			return -1;

		return xv.compareTo(yv);
	}
}
