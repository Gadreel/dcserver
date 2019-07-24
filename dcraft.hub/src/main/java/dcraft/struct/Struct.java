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
package dcraft.struct;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.IDataExposer;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.LogicBlockState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.BigDateTimeStruct;
import dcraft.struct.scalar.BigIntegerStruct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.DateStruct;
import dcraft.struct.scalar.DateTimeStruct;
import dcraft.struct.scalar.DecimalStruct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.struct.scalar.TimeStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.Base64;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

abstract public class Struct implements IPartSelector {
	protected DataType explicitType = null;
	
	// override this to return implicit type if no explicit exists
	public DataType getType() {
		return this.explicitType;
	}

	public Struct withType(DataType v) {
		this.explicitType = v;
		return this;
	}	
	
	public boolean hasExplicitType() {
		return (this.explicitType != null);
	}
	
	public Struct() {
	}
	
	public Struct(DataType type) {
		this.explicitType = type;
	}
	
	/**
	 * A way to select a child or sub child structure similar to XPath but lightweight.
	 * Can select composites and scalars.  Use a . or / delimiter.
	 *
	 * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
	 * 4th toy in this person's Toys list.
	 *
	 * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
	 *
	 * @param path string holding the path to select
	 * @return selected structure if any, otherwise null
	 */
	@Override
	public Struct select(String path) {
		return this.select(PathPart.parse(path));
	}
	
	/** _Tr
	 * A way to select a child or sub child structure similar to XPath but lightweight.
	 * Can select composites and scalars.  Use a . or / delimiter.
	 *
	 * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
	 * 4th toy in this person's Toys list.
	 *
	 * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
	 *
	 * @param path parts of the path holding a list index or a field name
	 * @return selected structure if any, otherwise null
	 */
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 0)
			return this;
		
		return null;
	}
	
	// just a reminder of the things to override in types
	
	@Override
	public Object clone() {
		return this.deepCopy();
	}
	
	@Override
	abstract public String toString();
	
    protected void doCopy(Struct n) {
    	n.explicitType = this.explicitType;
    }
    
	abstract public Struct deepCopy();
	
	/**
	 * @return true if contains no data or insufficient data to constitute a complete value
	 */
	abstract public boolean isEmpty();
	
	/**
	 * 
	 * @return true if it really is null (scalars only, composites are never null)
	 */
	abstract public boolean isNull();
		
	public boolean validate() {
		return this.validate(this.explicitType);
	}
	
	public boolean validate(String type) {
		return this.validate(SchemaHub.getTypeOrError(type));
	}
	
	public boolean validate(DataType type) {
		if (type == null) {
			Logger.errorTr(522);
			return false;
		}
		
		return type.validate(this);
	}
	
	// statics
	
	static public Long objectToInteger(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToInteger(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		else if (o instanceof BigIntegerStruct) 
			o = ((BigIntegerStruct)o).getValue();
		else if (o instanceof DecimalStruct) 
			o = ((DecimalStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		else if (o instanceof DateTimeStruct)
			o = ((DateTimeStruct)o).getValue();
		else if (o instanceof DateStruct)
			o = ((DateStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof Long)
			return (Long)o;
		
		if (o instanceof Number)
			return ((Number)o).longValue();
		
		if (o instanceof TemporalAccessor)
			o = Instant.from((TemporalAccessor)o);
		
		if (o instanceof java.sql.Timestamp)
			o = ((java.sql.Timestamp)o).toInstant();
		
		if (o instanceof Instant)
			return ((Instant)o).toEpochMilli();
		
		if (o instanceof CharSequence) {
			try {
				return Long.parseLong(o.toString());
			}
			catch (Exception x) {
			}
		}
		
		return null;
	}

	public void checkLogic(IParentAwareWork stack, XElement source, LogicBlockState logicState) throws OperatingContextException {
		if (source.hasAttribute("IsNull")) {
			if (logicState.pass)
				logicState.pass = StackUtil.boolFromElement(stack, source, "IsNull") ? this.isNull() : ! this.isNull();
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("IsEmpty")) {
			if (logicState.pass)
				logicState.pass = StackUtil.boolFromElement(stack, source, "IsEmpty") ? this.isEmpty() : ! this.isEmpty();
			
			logicState.checked = true;
		}
	}
	
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Validate".equals(code.getName()))
			this.validate();
		else
			Logger.error("operation failed, op name not recoginized: " + code.getName());
		
		return ReturnOption.CONTINUE;
	}

	// static utility

	static public BigInteger objectToBigInteger(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToBigInteger(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof BigIntegerStruct)
			o = ((BigIntegerStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof BigInteger)
			return (BigInteger)o;
		
		if (o instanceof Number)
			return new BigInteger(o.toString());
		
		if (o instanceof java.sql.Timestamp)
			return BigInteger.valueOf(((java.sql.Timestamp)o).getTime());
		
		if (o instanceof CharSequence) {
			try {
				return new BigInteger(o.toString());
			}
			catch (Exception x) {
			}
		}
		
		return null;
	}
	
	static public BigDecimal objectToDecimal(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToDecimal(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		else if (o instanceof BigIntegerStruct)
			o = ((BigIntegerStruct)o).getValue();
		else if (o instanceof DecimalStruct)
			o = ((DecimalStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof Number) {
			if (o instanceof BigDecimal)
				return (BigDecimal)o;
			
			return new BigDecimal(((Number)o).doubleValue());
		}
		
		if (o instanceof CharSequence) {
			try {
				return new BigDecimal(o.toString());
			}
			catch (Exception x) {
			}
		}
		
		return null;
	}

	// returns true only if can be a valid Number types - Long, BigInteger or BigDecimal
	public static boolean objectIsNumber(Object o) {
		if (o == null)
			return false;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o instanceof FieldStruct)
			return Struct.objectIsNumber(((FieldStruct)o).getValue());
		
		if (o instanceof DecimalStruct)
			return true;
		
		if (o instanceof IntegerStruct)
			return true;
		
		if (o instanceof BigIntegerStruct)
			return true;
		
		if (o instanceof Number) {
			if (o instanceof BigDecimal)
				return true;
			
			if (o instanceof BigInteger)
				return true;
			
			if (o instanceof Long)
				return true;
			
			if (o instanceof Integer)
				return true;
			
			if (o instanceof Short)
				return true;
			
			if (o instanceof Byte)
				return true;
			
			if (o instanceof Float)
				return true;
			
			if (o instanceof Double)
				return true;
			
			return false;
		}
		
		if (o instanceof CharSequence) {
			try {
				new BigDecimal(o.toString());
				return true;
			}
			catch (Exception x) {
			}
		}
		
		return false;
	}

	// returns only valid Number types - Long, BigInteger or BigDecimal
	public static Number objectToNumber(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToNumber(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		else if (o instanceof BigIntegerStruct)
			o = ((BigIntegerStruct)o).getValue();
		else if (o instanceof DecimalStruct)
			o = ((DecimalStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof Number) {
			if (o instanceof BigDecimal)
				return (Number) o;
			
			if (o instanceof BigInteger)
				return (Number) o;
			
			if (o instanceof Long)
				return (Number) o;
			
			if (o instanceof Integer)
				return ((Number) o).longValue();
			
			if (o instanceof Short)
				return ((Number) o).longValue();
			
			if (o instanceof Byte)
				return ((Number) o).longValue();
			
			if (o instanceof Float)
				return new BigDecimal(((Number) o).floatValue());
			
			if (o instanceof Double)
				return new BigDecimal(((Number) o).doubleValue());
			
			return null;
		}
		
		if (o instanceof CharSequence) {
			String num = o.toString();
			
			if (StringUtil.isNotEmpty(num)) {
				if (!num.contains(".")) {
					// try to fit in 64 bit
					try {
						return new Long(num);
					}
					catch (Exception x) {
					}
					
					// otherwise try to fit in big int
					try {
						return new BigInteger(num);
					}
					catch (Exception x) {
					}
				}
				else {
					try {
						return new BigDecimal(num);
					}
					catch (Exception x) {
					}
				}
			}
		}
		
		return null;
	}
	
	static public boolean objectIsBoolean(Object o) {
		if (o == null)
			return false;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o instanceof FieldStruct)
			return Struct.objectIsBoolean(((FieldStruct)o).getValue());
		
		if (o instanceof Boolean)
			return true;
		
		if (o instanceof BooleanStruct)
			return true;
		
		if (o instanceof StringStruct)
			o = ((StringStruct) o).getValue();
		
		if (o instanceof CharSequence) {
			try {
				new Boolean(o.toString());
				return true;
			}
			catch (Exception x) {
			}
		}
		
		return false;
	}
	
	static public boolean objectToBooleanOrFalse(Object o) {
		Boolean v = Struct.objectToBoolean(o);
		
		if (v == null)
			return false;
		
		return v;
	}
	
	static public boolean objectToBoolean(Object o, boolean defval) {
		Boolean v = Struct.objectToBoolean(o);
		
		if (v == null)
			return defval;
		
		return v;
	}
	
	static public Boolean objectToBoolean(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToBoolean(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof BooleanStruct)
			o = ((BooleanStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof Boolean)
			return (Boolean)o;
		
		if (o instanceof Number)
			return ((Number)o).intValue() != 0;
		
		if (o instanceof CharSequence) {
			try {
				if (StringUtil.isNotEmpty((CharSequence) o))
					return new Boolean(o.toString().toLowerCase().trim());
			}
			catch (Exception x) {
			}
		}

		return null;
	}
	
	static public ZonedDateTime objectToDateTime(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToDateTime(((FieldStruct)o).getValue());

		if (o instanceof DateStruct)
			return Struct.objectToDateTime(((DateStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof DateTimeStruct)
			o = ((DateTimeStruct)o).getValue();
		else if (o instanceof DateStruct)
			o = ((DateStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		// sql dates should just be instants
		if (o instanceof java.sql.Timestamp)
			o = ((java.sql.Timestamp)o).toInstant();
		
		// prefer instant over taccess to ensure UTC
		if (o instanceof Instant)
			return ZonedDateTime.ofInstant((Instant) o, ZoneId.of("UTC"));
		
		if (o instanceof TemporalAccessor)
			return ZonedDateTime.from((TemporalAccessor) o);
		
		if (o instanceof CharSequence) {
			try {
				return TimeUtil.parseDateTime(o.toString());
			}
			catch (Exception x) {
			}
		}
		
		return null;
	}
	
	static public BigDateTime objectToBigDateTime(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToBigDateTime(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof DateTimeStruct)
			o = ((DateTimeStruct)o).getValue();
		else if (o instanceof BigDateTimeStruct)
			o = ((BigDateTimeStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		else if (o instanceof java.sql.Timestamp)
			o = ZonedDateTime.ofInstant(((java.sql.Timestamp)o).toInstant(), ZoneId.of("UTC"));
		
		if (o == null)
			return null;
		
		if (o instanceof TemporalAccessor)
			return BigDateTime.of((TemporalAccessor) o);
		
		if (o instanceof BigDateTime)
			return (BigDateTime)o;
		
		if (o instanceof CharSequence) 
			return TimeUtil.parseBigDateTime(o.toString());
		
		return null;
	}
	
	static public LocalDate objectToDate(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToDate(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof DateStruct)
			o = ((DateStruct)o).getValue();
		else if (o instanceof TimeStruct)
			o = ((TimeStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof LocalDate)
			return (LocalDate)o;

		if (o instanceof ZonedDateTime)
			return ((ZonedDateTime)o).toLocalDate();

		if (o instanceof java.sql.Timestamp)
			return LocalDate.from(((java.sql.Timestamp)o).toInstant());
		
		if (o instanceof CharSequence) {
			try {
				return LocalDate.parse(o.toString());
			}
			catch (Exception x) {
			}
		}

		return null;
	}
	
	static public LocalTime objectToTime(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToTime(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof DateStruct)
			o = ((DateStruct)o).getValue();
		else if (o instanceof TimeStruct)
			o = ((TimeStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof LocalTime)
			return (LocalTime)o;

		if (o instanceof ZonedDateTime)
			return ((ZonedDateTime)o).toLocalTime();

		if (o instanceof CharSequence) {
			try {
				return LocalTime.parse(o.toString()); 	//, TimeUtil.parseTimeFormat);
			}
			catch (Exception x) {
			}
		}
		
		return null;
	}
	
	static public String objectToString(Object o) {
		if (o == null)
			return null;
		
		CharSequence x = Struct.objectToCharsStrict(o);
		
		if (x != null)
			return x.toString();
		
		return o.toString();		
	}
	
	// if it is any of our common types it can become a string, but otherwise not
	static public boolean objectIsCharsStrict(Object o) {
		if (o == null)
			return false;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o instanceof FieldStruct)
			return Struct.objectIsCharsStrict(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			o = null;
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		else if (o instanceof DateTimeStruct)
			o = ((DateTimeStruct)o).getValue();
		else if (o instanceof DateStruct)
			o = ((DateStruct)o).getValue();
		else if (o instanceof TimeStruct)
			o = ((TimeStruct)o).getValue();
		else if (o instanceof BigDateTimeStruct)
			o = ((BigDateTimeStruct)o).getValue();
		else if (o instanceof BigIntegerStruct)
			o = ((BigIntegerStruct)o).getValue();
		else if (o instanceof DecimalStruct)
			o = ((DecimalStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		else if (o instanceof BinaryStruct)
			o = ((BinaryStruct)o).getValue();
		else if (o instanceof BooleanStruct)
			o = ((BooleanStruct)o).getValue();
		else if (o instanceof IDataExposer)
			o = ((IDataExposer)o).exposeData();
		
		if (o == null)
			return false;
		
		if (o instanceof java.sql.Timestamp)
			return true;
		
		if (o instanceof java.sql.Clob) 
			return true;
		
		if (o instanceof CharSequence)		
			return true;
		
		if (o instanceof TemporalAccessor)
			return true;
		
		if (o instanceof BigDateTime)
			return true;
		
		if (o instanceof LocalDate)
			return true;
		
		if (o instanceof BigDecimal)
			return true;
		
		if (o instanceof BigInteger)
			return true;
		
		if (o instanceof Long)
			return true;
		
		if (o instanceof Integer)
			return true;
		
		if (o instanceof Boolean)
			return true;
		
		if (o instanceof Memory)		
			return true;
		
		return false;
	}
	
	static public CharSequence objectToCharsStrict(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o instanceof FieldStruct)
			return Struct.objectToCharsStrict(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			o = null;
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		else if (o instanceof DateTimeStruct)
			o = ((DateTimeStruct)o).getValue();
		else if (o instanceof DateStruct)
			o = ((DateStruct)o).getValue();
		else if (o instanceof TimeStruct)
			o = ((TimeStruct)o).getValue();
		else if (o instanceof BigDateTimeStruct)
			o = ((BigDateTimeStruct)o).getValue();
		else if (o instanceof BigIntegerStruct)
			o = ((BigIntegerStruct)o).getValue();
		else if (o instanceof DecimalStruct)
			o = ((DecimalStruct)o).getValue();
		else if (o instanceof IntegerStruct)
			o = ((IntegerStruct)o).getValue();
		else if (o instanceof BinaryStruct)
			o = ((BinaryStruct)o).getValue();
		else if (o instanceof BooleanStruct)
			o = ((BooleanStruct)o).getValue();
		else if (o instanceof IDataExposer)
			o = ((IDataExposer)o).exposeData();
		
		if (o == null)
			return null;
		
		if (o instanceof java.sql.Timestamp)
			o = TimeUtil.convertSqlDate((java.sql.Timestamp)o);
		
		if (o instanceof java.sql.Clob) {
			try {
				BufferedReader reader = new BufferedReader(((java.sql.Clob)o).getCharacterStream());
				
				StringBuilder builder = new StringBuilder();
				String aux = "";

				while ((aux = reader.readLine()) != null) {
				    builder.append(aux);
				}

				o = builder.toString();			} 
			catch (Exception x) {
				return null;
			}
		}
		
		if (o instanceof CharSequence)		
			return (CharSequence)o;
		
		if (o instanceof BigDateTime)
			return ((BigDateTime)o).toString();
		
		if (o instanceof LocalDate)
			return ((LocalDate)o).toString();
		
		if (o instanceof LocalTime)
			return ((LocalTime)o).toString();
		
		if (o instanceof TemporalAccessor) {
			TemporalAccessor to = (TemporalAccessor) o;
			
			if (to.isSupported(ChronoField.DAY_OF_MONTH) && to.isSupported(ChronoField.HOUR_OF_DAY))
				return TimeUtil.stampFmt.format((TemporalAccessor) o);
			
			// TODO support others?
		}
		
		if (o instanceof BigDecimal)
			return ((BigDecimal)o).toPlainString();
		
		if (o instanceof BigInteger)
			return ((BigInteger)o).toString();
		
		if (o instanceof Long)
			return ((Long)o).toString();
		
		if (o instanceof Integer)
			return ((Integer)o).toString();
		
		if (o instanceof Boolean)
			return ((Boolean)o).toString();
		
		if (o instanceof Memory)		
			return o.toString();
		
		return null;
	}
	
	static public RecordStruct objectToRecord(Object o) {
		CompositeStruct cs = Struct.objectToComposite(o);
		
		if (cs instanceof RecordStruct)
			return (RecordStruct)cs;
		
		return null;
	}
	
	static public ListStruct objectToList(Object o) {
		CompositeStruct cs = Struct.objectToComposite(o);
		
		if (cs instanceof ListStruct)
			return (ListStruct)cs;
		
		return null;
	}
	
	static public CompositeStruct objectToComposite(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToComposite(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof CompositeStruct)
			return (CompositeStruct)o;
		
		if (o instanceof ICompositeBuilder)
			return ((ICompositeBuilder)o).toLocal();
		
		if (o instanceof Memory) {
			((Memory)o).setPosition(0);			
			return CompositeParser.parseJson((Memory)o);
		}
		
		if (o instanceof StringStruct)
			return CompositeParser.parseJson(((StringStruct)o).getValue());
		
		if (o instanceof CharSequence)
			return CompositeParser.parseJson(o.toString());
		
		// TODO add some other obvious types - List, Array, Map?, etc
		return null;		
	}
	
	static public XElement objectToXml(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToXml(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		// TODO add some other obvious types - List, Array, Map?, etc
		
		if (o == null)
			return null;
		
		if (o instanceof XElement)
			return (XElement)o;
		
		CharSequence xml = null;
		
		if (o instanceof CharSequence)
			xml = (CharSequence)o;
		
		if (xml == null)
			return null;
		
		return XmlReader.parse(xml, false, true);
	}

	public static boolean objectIsBinary(Object o) {
		if (o == null)
			return false;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o == null)
			return false;
		
		if (o instanceof FieldStruct)
			return Struct.objectIsBinary(((FieldStruct)o).getValue());
		
		if (o instanceof Memory)
			return true;
		
		if (o instanceof BinaryStruct)
			return true;
		
		if (o instanceof byte[])
			return true;
		
		if (o instanceof ByteBuffer)
			return true;
		
		return false;
	}

	public static Memory objectToBinary(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof FieldStruct)
			return Struct.objectToBinary(((FieldStruct)o).getValue());
		
		if (o instanceof NullStruct)
			return null;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		else if (o instanceof BinaryStruct)
			o = ((BinaryStruct)o).getValue();
		else if (o instanceof StringStruct)
			o = ((StringStruct)o).getValue();
		
		if (o == null)
			return null;
		
		if (o instanceof Memory)
			return (Memory)o;
		
		if (o instanceof byte[])
			return new Memory((byte[])o);
		
		if (o instanceof CharSequence) 
			return new Memory(Base64.decodeFast((CharSequence)o));
		
		if (o instanceof ByteBuffer)
			return new Memory(((ByteBuffer)o).array());
		
		return null;
	}
	
	static public Struct objectToStruct(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof Struct)
			return (Struct)o;
		
		if (o instanceof FieldStruct)
			return Struct.objectToStruct(((FieldStruct)o).getValue());

		Struct svalue = null;
		
		// going to be returned in the local server's timezone, need to make that into UTC			  
		if (o instanceof java.sql.Timestamp) 
			o = ZonedDateTime.ofInstant(((java.sql.Timestamp)o).toInstant(), ZoneId.of("UTC"));
		
		if (o instanceof java.sql.Clob) {
			try {
				o = ((java.sql.Clob)o).getSubString(1L, (int)((java.sql.Clob)o).length());
			} 
			catch (SQLException x) {
				return null;
			}
		}
		
		if (o instanceof LocalTime) {
			svalue = TimeStruct.of((LocalTime) o);
			return svalue;
		}
		
		if (o instanceof LocalDate) {
			svalue = DateStruct.of((LocalDate) o);
			return svalue;
		}

		// if not a local time or local date then try as a datetime
		if (o instanceof TemporalAccessor) {
			svalue = DateTimeStruct.ofAny(o);
			return svalue;
		}
		
		if (o instanceof BigDateTime) {
			svalue = BigDateTimeStruct.of((BigDateTime) o);
			return svalue;
		}
		
		if ((o instanceof BigDecimal) || (o instanceof Double) || (o instanceof Float)) {
			svalue = DecimalStruct.ofAny(o);
			return svalue;
		}
		
		if (o instanceof BigInteger) {
			svalue = BigIntegerStruct.of((BigInteger) o);
			return svalue;
		}
		
		if (o instanceof Number) {
			svalue = IntegerStruct.ofAny(o);
			return svalue;
		}
		
		if (o instanceof Boolean) {
			svalue = BooleanStruct.of((Boolean) o);
			return svalue;
		}
		
		if (o instanceof CharSequence) {
			svalue = StringStruct.of((CharSequence) o);
			return svalue;
		}
		
		if (o instanceof Memory) {
			svalue = new BinaryStruct();
			((BinaryStruct)svalue).adaptValue(o);
			return svalue;
		}
		
		if (o instanceof List) {
			ListStruct list = ListStruct.list();
			
			for (Object v : (List) o)
				list.with(v);
			
			return list;
		}
		
		// TODO add some other obvious types - List, Array, Map?, etc 
		// bytebuffer, bytearray, memory...
		
		svalue = AnyStruct.of(o);
		return svalue;
	}
	
	// return the most appropriate core type for this object
	// core types are: String, DateTime, BigDateTime, BigDecimal, BigInteger, Long (aka Integer), Boolean, Memory
	// XElement and CompositeStructs too!
	static public Object objectToCore(Object o) {
		if (o == null)
			return null;
		
		if (o instanceof CompositeStruct)
			return o;
		
		if (o instanceof FieldStruct)
			return Struct.objectToCore(((FieldStruct)o).getValue());
		
		if (o instanceof ScalarStruct)
			return Struct.objectToCore(((ScalarStruct)o).getGenericValue());
		
		if (o instanceof java.sql.Timestamp) {
			String t = ((java.sql.Timestamp)o).toString();
			
			// going to be returned in the local server's timezone, need to make that into UTC			  
			return TimeUtil.sqlStampFmt.parse(t);
		}
		
		if (o instanceof java.sql.Clob) {
			try {
				return ((java.sql.Clob)o).getSubString(1L, (int)((java.sql.Clob)o).length());
			} 
			catch (SQLException x) {
				return null;
			}
		}
		
		if (o instanceof ZonedDateTime) 
			return o;
		
		if (o instanceof LocalDate) 
			return o;
		
		if (o instanceof LocalTime) 
			return o;
		
		if (o instanceof BigDateTime) 
			return o;
		
		if (o instanceof BigDecimal) 
			return o;
		
		if ((o instanceof Double) || (o instanceof Float)) 
			return Struct.objectToDecimal(o);
		
		if (o instanceof BigInteger) 
			return o;
		
		if (o instanceof Number) 
			return Struct.objectToInteger(o);
		
		if (o instanceof Boolean) 
			return o;
		
		if (o instanceof CharSequence) 
			return o.toString();
		
		if (o instanceof Memory) 
			return o;
		
		if (o instanceof XElement) 
			return o;
		
		// TODO add some other obvious types - List, Array, Map?, etc 
		// bytebuffer, bytearray, memory...
		
		// could not convert
		return null;
	}
	
	// if it is any of our common types it can become a string, but otherwise not
	static public boolean objectIsEmpty(Object o) {
		if (o == null)
			return true;
		
		if (o instanceof AnyStruct)
			o = ((AnyStruct)o).getValue();
		
		if (o instanceof FieldStruct)
			return Struct.objectIsEmpty(((FieldStruct)o).getValue());
		
		if (o == null)
			return true;
		
		if (o instanceof Struct)
			return ((Struct)o).isEmpty();
		
		if (o instanceof java.sql.Clob)
			try {
				return ((java.sql.Clob)o).length() == 0;
			} 
			catch (SQLException x) {
				return true;
			}
		
		if (o instanceof CharSequence)		
			return StringUtil.isEmpty((CharSequence)o);
		
		if (o instanceof Memory)		
			return ((Memory)o).getLength() == 0;

		// no one else has a special Empty condition
		return false;
	}
	
}
