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
package dcraft.log.count;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;

import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class CountHub {
	static protected ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
	
	static public Counter removeCounter(String name) {
		Counter c = CountHub.counters.remove(name);
		//c.removeObserver(tshi);
		return c;
	}
	
	static public Counter getCounter(String name) {
		return CountHub.counters.get(name);
	}
	
	static public boolean hasCounter(String name) {
		return CountHub.counters.containsKey(name);
	}
	
	static public Collection<Counter> getCounters() {
		return CountHub.counters.values();
	}
	
	static public Map<String, Counter> resetReturnCounters() {
		Map<String, Counter> ret = CountHub.counters;
		
		CountHub.counters = new ConcurrentHashMap<>();
		
		return ret;
	}
	
	static public NumberCounter allocateNumberCounter(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		Counter c = CountHub.counters.get(name);
		
		if (c == null) {
			c = new NumberCounter(name);
			//c.addObserver(this);
			CountHub.counters.put(name, c);
		}
		
		if (! (c instanceof NumberCounter))
			return null;
		
		return (NumberCounter)c;
	}
	
	static public NumberCounter allocateSetNumberCounter(String name, long value) {
		NumberCounter nc = CountHub.allocateNumberCounter(name);
	
		if (nc != null)
			nc.setValue(value);
		
		return nc;
	}
	
	static public NumberCounter countObjects(String name, Object obj) {
		NumberCounter nc = CountHub.allocateNumberCounter(name);
		
		if (nc != null) {
			// TODO - find a better way for memory and GC - nc.setCurrentObject(obj);  -- we used to get Task or Session or such in here!! and hold it for indefinite periods
			nc.increment();
		}
		
		return nc;
	}
	
	static public NumberCounter allocateSetNumberCounter(String name, double value) {
		NumberCounter nc = CountHub.allocateNumberCounter(name);
		
		if (nc != null)
			nc.setValue(value);
		
		return nc;
	}
	
	static public NumberCounter allocateSetNumberCounter(String name, BigDecimal value) {
		NumberCounter nc = CountHub.allocateNumberCounter(name);
		
		if (nc != null)
			nc.setValue(value);
		
		return nc;
	}
	
	static public StringCounter allocateStringCounter(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		Counter c = CountHub.counters.get(name);
		
		if (c == null) {
			c = new StringCounter(name);
			//c.addObserver(this);
			CountHub.counters.put(name, c);
		}
		
		if (! (c instanceof StringCounter))
			return null;
		
		return (StringCounter)c;
	}
	
	static public StringCounter allocateSetStringCounter(String name, String value) {
		StringCounter sc = CountHub.allocateStringCounter(name);
		
		if (sc != null)
			sc.setValue(value);
		
		return sc;
	}
	
	static public BooleanCounter allocateBooleanCounter(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		Counter c = CountHub.counters.get(name);
		
		if (c == null) {
			c = new BooleanCounter(name);
			//c.addObserver(this);
			CountHub.counters.put(name, c);
		}
		
		if (! (c instanceof BooleanCounter))
			return null;
		
		return (BooleanCounter)c;
	}
	
	static public BooleanCounter allocateSetBooleanCounter(String name, Boolean value) {
		BooleanCounter bc = CountHub.allocateBooleanCounter(name);
		
		if (bc != null)
			bc.setValue(value);
		
		return bc;
	}
}
