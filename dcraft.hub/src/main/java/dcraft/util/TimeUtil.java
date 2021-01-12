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
package dcraft.util;

import java.text.SimpleDateFormat;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.hub.time.CoreLocalTime;
import dcraft.log.Logger;
import dcraft.struct.Struct;

/**
 * DivConq uses the Joda date time library for nearly all date/time processing.
 * DivConq also assumes that date time in string format is typically in ISO format.
 * Joda has a setting to indicate which timezone the Hub is running in, all methods
 * that follow use that timezone setting.
 * 
 * @author Andy
 *
 */
public class TimeUtil {
	static public final DateTimeFormatter stampFmt = new DateTimeFormatterBuilder().appendPattern("yyyyMMdd'T'HHmmssSSS'Z'")
			.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
			.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
			.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
			.parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
			.toFormatter();
	
			//DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'");
	static public final DateTimeFormatter sqlStampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
	static public final SimpleDateFormat sqlStampReformat = new SimpleDateFormat("yyyyMMdd'T'HHmmssSSS");

	static public final DateTimeFormatter parseTimeFormat = new DateTimeFormatterBuilder()
		//.appendPattern("HH:mm")
		//.appendPattern("HH:mm:ss")
		.appendPattern("HH:mm:ss.SSS")
		.toFormatter();
	
	static public final String RFC_822_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss Z";
	static public final DateTimeFormatter rfc822DateFormatter = DateTimeFormatter.ofPattern(RFC_822_DATE_FORMAT);
	
	/*
	System.out.println("pt: " + ISODateTimeFormat.localTimeParser().parseDateTime("16:20:45"));
	
    DateTimeZone dtz = DateTimeZone.forID("America/Chicago");

    System.out.println(startDST(dtz, 2011));
    System.out.println(endDST(dtz, 2011));
    */
	
	static public ZonedDateTime now() {
		// TODO use system clock
		
		return ZonedDateTime.now(ZoneId.of("UTC"));
	}
	
	/**
	 * check if a date is before today, ignore the time just look at the date
	 * 
	 * @param d date to check
	 * @return true if it comes before today
	 */	
	static public boolean isBeforeToday(ZonedDateTime d) {
		return d.toLocalDate().isBefore(LocalDate.now());
	}
	
	/**
	 * try to supply a time for a date, if it fails it may be because of DST and that time (hour) is skipped on that date.
	 * So try again to supply a time +1 hour to see if it helps.
	 * 
	 * @param d date to set time into
	 * @param t time to set to
	 * @return datetime with the supplied time (maybe +1 hour) or null
	 */
    static public ZonedDateTime withTime(ZonedDateTime d, LocalTime t) {
    	try {
    		return d.with(t);
    	}
    	catch (Exception x) {
    		// TODO hour +1 is a hack, should work in USA/Canada - and probably lots of places - but maybe not everywhere
    		if (TimeUtil.checkDST(d) == DaylightTransition.START)    		
        		return d.with(t.plusHours(1));
    	}
    	
    	return null;
    }
	
	/**
	 * try to supply a time for a date, if it fails it may be because of DST and that time (hour) is skipped on that date.
	 * So try again to supply a time +1 hour to see if it helps.
	 * 
	 * @param dt date to set time into
	 * @param clt time to set to
	 * @return datetime with the supplied time (maybe +1 hour) or null
	 */
	static public ZonedDateTime withTime(ZonedDateTime dt, CoreLocalTime clt) {
		return dt.with(clt.toLocal());
	}

	/**
	 * try to get a date at midnight tomorrow, if no midnight due to DST then it may be 1am
	 * 
	 * @param d date from which to calculate tomorrow
	 * @return datetime of midnight, or closest to midnight, tomorrow
	 */
    static public ZonedDateTime nextDayAtMidnight(ZonedDateTime d) {
    	return d.with(LocalTime.of(0, 0, 0, 0)).plusDays(1);
    }
    
    /*
	System.out.println("1: " + ISODateTimeFormat.localTimeParser().parseDateTime("T15:00:00").toLocalTime());
	System.out.println("2: " + ISODateTimeFormat.localTimeParser().parseDateTime("15:00:00").toLocalTime());
	System.out.println("3: " + ISODateTimeFormat.localTimeParser().parseDateTime("15").toLocalTime());
	
	System.out.println("1: " + ISODateTimeFormat.timeParser().parseDateTime("T15:00:00Z").toLocalTime());
	System.out.println("2: " + ISODateTimeFormat.timeParser().parseDateTime("15:00:00Z").toLocalTime());
	System.out.println("3: " + ISODateTimeFormat.timeParser().parseDateTime("15").toLocalTime());
	*/
	
    /**
     * parse a string assuming ISO format
     * 
     * @param t string with iso formatted date
     * @return datetime if parsed, else null 
     */
    static public ZonedDateTime parseDateTime(String t) {
		if (StringUtil.isEmpty(t))
    		return null;
    	
		try {
			return ZonedDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(t));
		}
		catch (Exception x) {			
			try {
				return ZonedDateTime.from(DateTimeFormatter.ISO_DATE.parse(t));
			}
			catch (Exception x3) {
				// Java 8 doesn't seem to do yyyyMMdd'T'HHmmssSSS'Z' format because they thought fractional was cooler than milli
				
				if (t.length() == 19) {
					try {
						int year = Integer.parseInt(t.substring(0, 4));
						int month = Integer.parseInt(t.substring(4, 6));
						int day = Integer.parseInt(t.substring(6, 8));
						int hour = Integer.parseInt(t.substring(9, 11));
						int minute = Integer.parseInt(t.substring(11, 13));
						int second = Integer.parseInt(t.substring(13, 15));
						int nano = Integer.parseInt(t.substring(15, 18)) * 1000000;
						
						return ZonedDateTime.of(year, month, day, hour, minute, second, nano, ZoneId.of("UTC"));
					}
					catch (Exception x2) {
						Logger.error("Error parsing date time: " + x2);
					}
				}
			}
		}
		
		return null;
    }
    
    /**
     * parse just the time
     * 
     * @param t string with iso formatted time
     * @return time if parsed, or null
     */
	static public LocalTime parseLocalTime(String t) {		
		if (StringUtil.isEmpty(t))
			return null;

		try {
			return LocalTime.from(DateTimeFormatter.ISO_TIME.parse(t));
		}
		catch (Exception x) {			
		}
		
		return null;
	}
	
	/**
	 * return number of weeks since Jan 5, 1970
	 * 
	 * @param v date to calculate week number off of
	 * @return number of weeks
	 */
	static public long getWeekNumber(ZonedDateTime v) {
		ZonedDateTime root = ZonedDateTime.of(1970, 1, 5, 0, 0, 0, 0, ZoneId.of("UTC"));   
		
		return ChronoUnit.WEEKS.between(root, v);
	}
	
	/**
	 * return datetime at start of week number relative to Jan 5, 1970
	 * 
	 * @param weekNum the week number to use
	 * @return datetime that week started
	 */
	static public ZonedDateTime getStartOfWeek(int weekNum) {
		ZonedDateTime root = ZonedDateTime.of(1970, 1, 5, 0, 0, 0, 0, ZoneId.of("UTC"));   
		
		return root.plusWeeks(weekNum);
	}
	
	/**
	 * return number of months since Jan 1, 1970
	 * 
	 * @param v date to calculate month number off of
	 * @return number of months
	 */
	static public long getMonthNumber(ZonedDateTime v) {
		ZonedDateTime root = ZonedDateTime.of(1970, 1, 5, 0, 0, 0, 0, ZoneId.of("UTC"));   
		
		return ChronoUnit.MONTHS.between(root, v);
	}
	
	/**
	 * return datetime at start of month number relative to Jan 1, 1970
	 * 
	 * @param monthNum the month number to use
	 * @return datetime that month started
	 */
	static public ZonedDateTime getStartOfMonth(int monthNum) {
		ZonedDateTime root = ZonedDateTime.of(1970, 1, 5, 0, 0, 0, 0, ZoneId.of("UTC"));   
		
		return root.plusMonths(monthNum);
	}

	static public ZoneId zoneInContext() throws OperatingContextException {
		return ZoneId.of(OperationContext.getOrThrow().getChronology());
	}

	static public ZonedDateTime getStartOfDayInContext(LocalDate date) throws OperatingContextException {
		return date.atStartOfDay(TimeUtil.zoneInContext());
	}

	/**
	 * Format date and time in Long format
	 * 
	 * @param at datetime to format
	 * @param zone which timezone to use
	 * @param locale which locale to use 
	 * @return formatted datetime
	 */
	static public String fmtDateTimeLong(ZonedDateTime at, String zone, String locale) {
		if (at == null)
			return null;
		
		return TimeUtil.fmtDateTimeLong(at.toLocalDateTime().atZone(ZoneId.of(zone)), locale);
	}
	
	/**
	 * Format date and time in Long format
	 * 
	 * @param at datetime to format
	 * @param zone which timezone to use
	 * @param locale which locale to use 
	 * @return formatted datetime
	 */
	static public String fmtDateTimeLong(ZonedDateTime at, ZoneId zone, String locale) {
		if (at == null)
			return null;
		
		return TimeUtil.fmtDateTimeLong(at.toLocalDateTime().atZone(zone) , locale);
	}
	
	/**
	 * Format date and time in Long format
	 * 
	 * @param at datetime to format
	 * @param locale which locale to use 
	 * @return formatted datetime
	 */
	static public String fmtDateTimeLong(ZonedDateTime at, String locale) {
		if (at == null)
			return null;
		
		// TODO user locale to look up format
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM dd yyyy hh:mm:ss a z");
		
		return fmt.format(at);
	}
	
	/**
	 * Format date in Long format
	 * 
	 * @param at datetime to format
	 * @param zone which timezone to use
	 * @param locale which locale to use 
	 * @return formatted date
	 */
	static public String fmtDateLong(ZonedDateTime at, String zone, String locale) {
		if (at == null)
			return null;
		
		return TimeUtil.fmtDateLong(at.toLocalDateTime().atZone(TimeUtil.selectZone(zone)) , locale);
	}	
	
	/**
	 * Format date in Long format
	 * 
	 * @param at datetime to format
	 * @param zone which timezone to use
	 * @param locale which locale to use 
	 * @return formatted date
	 */
	static public String fmtDateLong(ZonedDateTime at, ZoneId zone, String locale) {
		if (at == null)
			return null;
		
		return TimeUtil.fmtDateLong(at.toLocalDateTime().atZone(zone) , locale);
	}
	
	/**
	 * Format date in Long format
	 * 
	 * @param at datetime to format
	 * @param locale which locale to use 
	 * @return formatted date
	 */
	static public String fmtDateLong(ZonedDateTime at, String locale) {
		if (at == null)
			return null;
		
		// TODO user locale to look up format
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM dd yyyy");
		return fmt.format(at);
	}
	
	/**
	 * try to lookup a timezone, but use default if it fails 
	 * 
	 * @param zoneId id of the timezone desired
	 * @return timezone to use
	 */
	static public ZoneId selectZone(String zoneId) {
		ZoneId zone = ZoneId.of("UTC");
		
		try {
			if (StringUtil.isNotEmpty(zoneId))
				zone = ZoneId.of(zoneId);
		}
		catch (Exception x) {			
		}
		
		return zone;
	}
	
	/**
	 * Parse string to CoreLocalTime - a flexible format that allows hours &gt; 23, useful for some settings.
	 * 
	 * @param t string holding hours:minutes:seconds
	 * @return time object
	 */
	static public CoreLocalTime parseCoreLocalTime(String t) {		
		if (StringUtil.isEmpty(t))
			return null;

		String[] parts = t.trim().split(":");
		
		int h = 0;
		int m = 0;
		int s = 0;
		
		if (parts.length >= 1)
			h = (int)StringUtil.parseInt(parts[0], 0);
		
		if (parts.length >= 2)
			m = (int)StringUtil.parseInt(parts[1], 0);
		
		if (parts.length >= 3)
			s = (int)StringUtil.parseInt(parts[2], 0);
		
		try {
			return new CoreLocalTime(h, m, s, 0);
		}
		catch (Exception x) {			
		}
		
		return null;
	}

	/**
	 * detect if given datetime lands on a DST transition
	 * 
	 * @param n date to check
	 * @return START if is a start of DST, END if is a end of DST, NA means day is not a transition
	 */
	public static DaylightTransition checkDST(ZonedDateTime n) {
		ZonedDateTime start = TimeUtil.startDST(n.getYear());
		
		if (start.toLocalDate().equals(n.toLocalDate()))
			return DaylightTransition.START;
		
		ZonedDateTime end = TimeUtil.startDST(n.getYear());
		
		if (end.toLocalDate().equals(n.toLocalDate()))
			return DaylightTransition.END;
		
		return DaylightTransition.NA;
	}
	
	public enum DaylightTransition {
		NA,
		START,
		END
	}
	
	/**
	 * get the DST start transition for a given year
	 * 
	 * @param zone timezone to use for DST rules
	 * @param year the year to use, e.g. 2012
	 * @return datetime of the start transition
	 */
    public static ZonedDateTime startDST(ZoneId zone, int year) {
        return ZonedDateTime.ofInstant(zone.getRules().nextTransition(ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, zone).toInstant()).getInstant(), zone);
    }

	/**
	 * get the DST start transition for a given year
	 * 
	 * @param year the year to use, e.g. 2012
	 * @return datetime of the start transition
	 */
    public static ZonedDateTime startDST(int year) {
    	ZoneId zone = ZoneId.of("UTC");
        return ZonedDateTime.ofInstant(zone.getRules().nextTransition(ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, zone).toInstant()).getInstant(), zone);
    }

	/**
	 * get the DST end transition for a given year
	 * 
	 * @param zone timezone to use for DST rules
	 * @param year the year to use, e.g. 2012
	 * @return datetime of the end transition
	 */
    public static ZonedDateTime endDST(ZoneId zone, int year) {
        return ZonedDateTime.ofInstant(zone.getRules().previousTransition(ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, zone).toInstant()).getInstant(), zone);
    }	

	/**
	 * get the DST end transition for a given year
	 * 
	 * @param year the year to use, e.g. 2012
	 * @return datetime of the end transition
	 */
    public static ZonedDateTime endDST(int year) {
    	ZoneId zone = ZoneId.of("UTC");
        return ZonedDateTime.ofInstant(zone.getRules().previousTransition(ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, zone).toInstant()).getInstant(), zone);
    }
    
	/**
	 * BigDateTime can handle dates into the billions of years, both past and future.  It is designed to be
	 * a versatile way to handle historical data.  Dates are based off proleptic Gregorian, time is optional
	 * but if present then is based off UTC zone.  
	 * 
	 * @param date 		in string format 
	 * @return 			converted to object or null if not able to parse
	 * 
	 * @see 			dcraft.hub.time.BigDateTime
	 */
	public static BigDateTime parseBigDateTime(String date) {
		return BigDateTime.parse(date);
	}	
	
	// this assumes that the time stamp - as formatted - will be in UTC time.  All DC values should be store in UTC time.
	public static ZonedDateTime convertSqlDate(java.sql.Timestamp v) {
		if (v == null)
			return null;
		
		String dt = TimeUtil.sqlStampReformat.format(v) + "Z";
		
		return ZonedDateTime.from(TimeUtil.parseDateTime(dt));
	}
}
