package dcraft.struct;

import dcraft.hub.time.BigDateTime;
import dcraft.util.Memory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public interface IPartSelector {
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
	Struct select(String path);
	
	/**
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
	Struct select(PathPart... path);
	
	/*
	BigDateTime selectAsBigDateTime(String name);
	BigDecimal selectAsDecimal(String name);
	BigDecimal selectAsDecimal(String name, BigDecimal defaultval);
	BigInteger selectAsBigInteger(String name);
	BigInteger selectAsBigInteger(String name, BigInteger defaultval);
	Boolean selectAsBoolean(String name);
	boolean selectAsBooleanOrFalse(String name);
	CompositeStruct selectAsComposite(String name);
	ListStruct selectAsList(String name);
	LocalDate selectAsDate(String name);
	LocalTime selectAsTime(String name);
	Long selectAsInteger(String name);
	long selectAsInteger(String name, long defaultval);
	Memory selectAsBinary(String name);
	RecordStruct selectAsRecord(String name);
	String selectAsString(String name);
	String selectAsString(String name, String defaultval);
	ZonedDateTime selectAsDateTime(String name);
	*/
}
