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
package dcraft.hub.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

import dcraft.log.Logger;
import dcraft.schema.CoreType;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.util.StringUtil;

/**
 * BigDateTime can handle dates into the billions of years, both past and future.  It is used to store dates 
 * in the database (dcDb) and for sorting.  It is not (yet) useful for date manipulation, for most
 * common date manipulation use the Joda (provided with DivConq) instead.
 * 
 * This is designed to be a versatile way to handle historical data.  Dates are based off proleptic Gregorian, 
 * time is optional but if present then is based off UTC zone.  If only date is present then it is assumed 
 * to be incomplete and not zone based.
 * 
 * Only year is required.  Year may be 0 - 99,999,999,999.  However, DivConq treats year 50,000,000,001
 * as 1 CE (aka AD).  Therefore if you wish to refer to a Common Era (AD) date then add 50 billion to the
 * year.  If you wish to refer to a BCE (aka BC) date then subtract that year from 50,000,000,001.  So
 * 1 BCE is 50,000,000,000.  Year is 11 digits, 0 padded.
 * 
 * Month is optional, if missing then it is understood that the date is for "sometime this year".
 * Month is in traditional 01 - 12 format.
 * 
 * Day is optional, if missing but month is present then the date is for "sometime this month".
 * Day is in traditional 01 - 31 format.
 * 
 * Hour is optional, if missing but day is present then the date is for "sometime this day" 
 * and is not linked to a timezone.  If an hour is present it must be in UTC time.
 * Hour is in traditional 00 - 23 format.
 * 
 * Minute is optional, if missing but hour is present then the date is for "sometime this hour" 
 * and is linked to the UTC timezone.  If an minute is present it must be in UTC time.
 * Minute is in traditional 00 - 59 format.
 * 
 * Second is optional, if missing but minute is present then the date is for "sometime this minute" 
 * and is linked to the UTC timezone.  
 * Second is in traditional 00 - 59 format.
 * 
 * Internal format of datetime is "tYYYYYYYYYYYMMDDhhmmss".  For example, Sept 2004 is
 * "t5000000200409".  
 * 
 * BigDateTime does not carry any information about which TimeZone it was translated from
 * for which Chronology (Calendar) it was converted from.  Always use strict UTC and 
 * proleptic Gregorian conversions so that all dates collate/index correctly relative
 * to each other.
 * 
 */

// TODO can we make a TemporalAccessor from this?
public class BigDateTime implements Comparable<BigDateTime> {
	static public BigDateTime nowDateTime() {
		return BigDateTime.of(ZonedDateTime.now());
	}
	
	static public BigDateTime nowDate() {
		return BigDateTime.of(LocalDate.now());	// TODO fix, we don't want to zone adjust this - as we would below
	}
	
	/**
	 * @param date internal big datetime format
	 * @return BigDateTime object plus warnings, if any
	 */
	static public BigDateTime parse(String date) {
		if (StringUtil.isEmpty(date)) {
			Logger.errorTr(230);
			return null;
		}
		
		// getCurrentSchema - get from operating context if any
		DataType dt = SchemaHub.getTypeOrError("BigDateTime");
		
		if (dt == null) {
			Logger.errorTr(232);
			return null;
		}
		
		CoreType ct = dt.getCoreType();
		
		if (ct == null) {
			Logger.errorTr(232);
			return null;
		}
		
		if (! ct.validateData(date)) {
			Logger.errorTr(231, date);
			return null;
		}
		
		BigDateTime bd = new BigDateTime();
		
		bd.year = Long.parseLong(date.substring(1, 12));
		
		if (date.length() > 12)
			bd.month = Integer.parseInt(date.substring(12, 14));
		
		if (date.length() > 14)
			bd.day = Integer.parseInt(date.substring(14, 16));
		
		if (date.length() > 16)
			bd.hour = Integer.parseInt(date.substring(16, 18));
		
		if (date.length() > 18)
			bd.minute = Integer.parseInt(date.substring(18, 20));
		
		if (date.length() > 20)
			bd.second = Integer.parseInt(date.substring(20, 22));
		
		return bd;
	}

	/**
	 * @param date translates into BigDateTime
	 */
	static public BigDateTime of(TemporalAccessor date) {
		if (date == null)
			return null;

		BigDateTime bdt = new BigDateTime();

		// make sure we are using ISO and UTC
		date = ZonedDateTime.ofInstant(Instant.from(date), ZoneId.of("UTC"));

		// must support year or error
		bdt.year = 50000000000L + date.getLong(ChronoField.YEAR);		// ISO says 1 BCE = 0, 2 BCE = -1, etc

		if (date.isSupported(ChronoField.MONTH_OF_YEAR))
			bdt.month = date.get(ChronoField.MONTH_OF_YEAR);

		if (date.isSupported(ChronoField.DAY_OF_MONTH))
			bdt.day = date.get(ChronoField.DAY_OF_MONTH);

		if (date.isSupported(ChronoField.HOUR_OF_DAY))
			bdt.hour = date.get(ChronoField.HOUR_OF_DAY);

		if (date.isSupported(ChronoField.MINUTE_OF_HOUR))
			bdt.minute = date.get(ChronoField.MINUTE_OF_HOUR);

		if (date.isSupported(ChronoField.SECOND_OF_MINUTE))
			bdt.second = date.get(ChronoField.SECOND_OF_MINUTE);

		return bdt;
	}

	/**
	 * @param year where 1 = 1 CE (AD), 0 = 1 BCE (BC)
	 */
	static public BigDateTime of(long year) {
		if ((year < -50000000000L) || (year > 49999999999L))
			return null;

		BigDateTime bdt = new BigDateTime();

		bdt.year = 50000000000L + year;		// ISO says 1 BCE = 0, 2 BCE = -1, etc

		return bdt;
	}

	/**
	 * @param year where 1 = 1 CE (AD), 0 = 1 BCE (BC)
	 * @param month where 1 = Jan
	 */
	static public BigDateTime of(long year, int month) {
		if ((month < 1) || (month > 12))
			return null;

		BigDateTime bdt = BigDateTime.of(year);

		if (bdt == null)
			return null;

		bdt.month = month;

		return bdt;
	}

	/**
	 * @param year where 1 = 1 CE (AD), 0 = 1 BCE (BC)
	 * @param month where 1 = Jan
	 * @param day of month
	 */
	static public BigDateTime of(long year, int month, int day) {
		if ((day < 1) || (day > 31))		// not 100% accurate, but ballpark
			return null;

		BigDateTime bdt = BigDateTime.of(year, month);

		if (bdt == null)
			return null;

		bdt.day = day;

		return bdt;
	}

	/**
	 * @param year where 1 = 1 CE (AD), 0 = 1 BCE (BC)
	 * @param month where 1 = Jan
	 * @param day of month
	 * @param hour of day
	 */
	static public BigDateTime of(long year, int month, int day, int hour) {
		if ((hour < 0) || (hour > 23))
			return null;

		BigDateTime bdt = BigDateTime.of(year, month, day);

		if (bdt == null)
			return null;

		bdt.hour = hour;

		return bdt;
	}

	/**
	 * @param year where 1 = 1 CE (AD), 0 = 1 BCE (BC)
	 * @param month where 1 = Jan
	 * @param day of month
	 * @param hour of day
	 * @param minute of hour
	 */
	static public BigDateTime of(long year, int month, int day, int hour, int minute) {
		if ((minute < 0) || (minute > 59))
			return null;

		BigDateTime bdt = BigDateTime.of(year, month, day, hour);

		if (bdt == null)
			return null;

		bdt.minute = minute;

		return bdt;
	}

	/**
	 * @param year where 1 = 1 CE (AD), 0 = 1 BCE (BC)
	 * @param month where 1 = Jan
	 * @param day of month
	 * @param hour of day
	 * @param minute of hour
	 * @param second of minute
	 */
	static public BigDateTime of(long year, int month, int day, int hour, int minute, int second) {
		if ((second < 0) || (second > 59))
			return null;

		BigDateTime bdt = BigDateTime.of(year, month, day, hour, minute);

		if (bdt == null)
			return null;

		bdt.second = second;

		return bdt;
	}

	protected Long year = null;
	protected Integer month = null;
	protected Integer day = null;
	protected Integer hour = null;
	protected Integer minute = null;
	protected Integer second = null;

	protected BigDateTime() { }

	public Long getYear() {
		return this.year;
	}
	
	public Integer getMonth() {
		return this.month;
	}
	
	public Integer getDay() {
		return this.day;
	}
	
	public Integer getHour() {
		return this.hour;
	}
	
	public Integer getMinute() {
		return this.minute;
	}
	
	public Integer getSecond() {
		return this.second;
	}

	/**
	 * @return in internal format
	 */
	@Override
	public String toString() {
		if (this.year == null)
			return null;
		
		String res = "t" + StringUtil.leftPad(this.year + "", 11, '0');
		
		if (this.month != null) {
			res += StringUtil.leftPad(this.month + "", 2, '0');
			
			if (this.day != null) {
				res += StringUtil.leftPad(this.day + "", 2, '0');
				
				if (this.hour != null) {
					res += StringUtil.leftPad(this.hour + "", 2, '0');
					
					if (this.minute != null) {
						res += StringUtil.leftPad(this.minute + "", 2, '0');
						
						if (this.second != null) 
							res += StringUtil.leftPad(this.second + "", 2, '0');
					}
				}
			}
		}
		
		return res;
	}

	@Override
	public int compareTo(BigDateTime o) {
		if (o == null)
			return 1;
		
		if (this.year < o.year)
			return -1;
		
		if (this.year > o.year)
			return 1;
		
		if (this.month < o.month)
			return -1;
		
		if (this.month > o.month)
			return 1;
		
		if (this.day < o.day)
			return -1;
		
		if (this.day > o.day)
			return 1;
		
		if (this.hour < o.hour)
			return -1;
		
		if (this.hour > o.hour)
			return 1;
		
		if (this.minute < o.minute)
			return -1;
		
		if (this.minute > o.minute)
			return 1;
		
		if (this.second < o.second)
			return -1;
		
		if (this.second > o.second)
			return 1;
		
		return 0;
	}
}
