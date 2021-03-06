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
package dcraft.schema;

import java.util.ArrayList;
import java.util.List;

import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class TypeOptionsList {
	protected Schema schema = null;
	protected List<DataType> options = new ArrayList<DataType>();
	
	public TypeOptionsList(Schema schema) {
		this.schema = schema;
	}

	public RecordStruct toJsonDef(int lvl) {
		RecordStruct def = new RecordStruct();
		
		ListStruct rests = new ListStruct();
		
		for (DataType dt : this.options) 
			rests.withItem(dt.toJsonDef(lvl));
		
		def.with("Options", rests);
		
		return def;
	}
	
	public void compile(XElement def) {		
		String t1 = def.getAttribute("Type");
		
		if (StringUtil.isNotEmpty(t1)) {
			this.options = this.schema.manager.lookupOptionsType(t1);
			return;
		}
		
		for (XElement dtel : def.selectAll("*")) { 
			DataType dt = new DataType(this.schema);
			dt.load(dtel);
			dt.compile();
			this.options.add(dt);
		}
	}
	
	// don't call this with data == null from a field if field required - required means "not null" so put the error in
	public boolean validate(boolean isfinal, boolean selectmode, Struct data) {
		if (data == null)
			return false;
		
		if (this.options.size() == 0) {
			Logger.errorTr(437, data);			
			return false;
		}
		
		if (this.options.size() == 1) 
			return this.options.get(0).validate(isfinal, selectmode, data);
		
		for (DataType dt : this.options) {
			if (dt.match(isfinal, data))
				return dt.validate(isfinal, selectmode, data);
		}
		
		Logger.errorTr(438, data);			
		return false;
	}
	
	public Struct normalizeValidate(boolean isfinal, boolean selectmode, Struct data) {
		if (data == null)
			return null;
		
		if (this.options.size() == 0) {
			Logger.errorTr(437, data);			
			return null;
		}
		
		if (this.options.size() == 1) 
			return this.options.get(0).normalizeValidate(isfinal, selectmode, data);
		
		for (DataType dt : this.options) {
			if (dt.match(isfinal, data))
				return dt.normalizeValidate(isfinal, selectmode, data);
		}
		
		Logger.errorTr(438, data);			
		return null;
	}
	
	public Struct wrap(Object data) {
		if (data == null) 
			return null;
		
		if (this.options.size() == 0) 
			return null;
		
		if (this.options.size() == 1) 
			return this.options.get(0).wrap(data);
		
		for (DataType dt : this.options) {
			if (dt.match(true, data))
				return dt.wrap(data);
		}
		
		return null;
	}
	
	public DataType getPrimaryType() {
		if (this.options.size() == 0) 
			return null;
		
		return this.options.get(0);
	}
}
