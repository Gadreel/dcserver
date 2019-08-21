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

public class Field {
	enum ReqTypes {
		True(1),
		False(2),
		IfPresent(3),
		Final(4);
	    
	    private int code;

	    ReqTypes(int c) {
	      code = c;
	    }

	    public int getCode() {
	      return code;
	    }
	}

	protected Schema schema = null;
	protected List<DataType> options = new ArrayList<DataType>();
	protected String name = null;
	protected ReqTypes required = ReqTypes.False;
	
	public Field(Schema schema) {
		this.schema = schema;
	}

	public RecordStruct toJsonDef(int lvl) {
		RecordStruct def = new RecordStruct();
		
		def.with("Name", this.name);
		
		ListStruct rests = new ListStruct();
		
		for (DataType dt : this.options) 
			rests.withItem(dt.toJsonDef(lvl));
		
		def.with("Options", rests);
		
		def.with("Required", this.required.getCode());
		
		return def;
	}
	
	public void compile(XElement fel) {
		this.name = fel.getAttribute("Name");
		
		String req = fel.getAttribute("Required", "false").toLowerCase();
		
		if ("true".equals(req))
			this.required = ReqTypes.True;
		else if ("ifpresent".equals(req))
			this.required = ReqTypes.IfPresent;
		else if ("final".equals(req))
			this.required = ReqTypes.Final;

		String t1 = fel.getAttribute("Type");
		
		if (StringUtil.isNotEmpty(t1)) {
			this.options = this.schema.manager.lookupOptionsType(t1);
			return;
		}
		
		String f1 = fel.getAttribute("ForeignKey");
		
		if (StringUtil.isNotEmpty(f1)) {
			this.options = this.schema.manager.lookupOptionsType("Id");
			return;
		}
		
		for (XElement dtel : fel.selectAll("*")) { 
			DataType dt = new DataType(this.schema);
			dt.load(dtel);
			dt.compile();
			this.options.add(dt);
		}
	}
		
	// don't call this with data == null from a field if field required - required means "not null" so put the error in
	public boolean validate(boolean present, boolean isfinal, boolean selectmode, Struct data) {
		if (data == null) 
			return this.valueUnresolved(present, isfinal, selectmode, data);
		
		if (this.options.size() == 0) {
			Logger.errorTr(423, data);			
			return false;
		}
		
		if (this.options.size() == 1) { 
			if (! this.options.get(0).validate(isfinal, selectmode, data))
				return this.valueUnresolved(present, isfinal, selectmode, data);
			
			return true;
		}
		
		for (DataType dt : this.options) {
			if (dt.match(isfinal, data)) {
				if (! dt.validate(isfinal, selectmode, data))
					return this.valueUnresolved(present, isfinal, selectmode, data);
				
				return true;
			}
		}
		
		Logger.errorTr(440, data);			
		return false;
	}
		
	// don't call this with data == null from a field if field required - required means "not null" so put the error in
	public Struct normalizeValidate(boolean present, boolean isfinal, boolean selectmode, Struct data) {
		if (data == null) {
			this.valueUnresolved(present, isfinal, selectmode, data);
			return null;
		}   
		
		if (this.options.size() == 0) {
			Logger.errorTr(423, data);			
			return null;
		}
		
		if (this.options.size() == 1) { 
			Struct nv = this.options.get(0).normalizeValidate(isfinal, selectmode, data);
			
			if (nv == null) {
				this.valueUnresolved(present, isfinal, selectmode, data);
				return null;
			}
			
			return nv;
		}
		
		for (DataType dt : this.options) {
			if (dt.match(isfinal, data)) {
				Struct nv = dt.normalizeValidate(isfinal, selectmode, data);
				
				if (nv == null) {
					this.valueUnresolved(present, isfinal, selectmode, data);
					return null;
				}
				
				return nv;
			}
		}
		
		Logger.errorTr(440, data);			
		return null;
	}
	
	protected boolean valueUnresolved(boolean present, boolean isfinal, boolean selectmode, Object data) {
		if (data != null) {
			Logger.errorTr(440, data);			
			return false;
		}
		
		if (this.required == ReqTypes.False)
			return true;
		
		if (((this.required == ReqTypes.IfPresent) || (this.required == ReqTypes.Final)) && ! present)
			return true;
		
		if ((this.required == ReqTypes.Final) && selectmode)
			return true;
		
		Logger.errorTr(424, data, this.name);
		return false;
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
	
	public Struct create() {
		if (this.options.size() == 0) 
			return null;
		
		return this.options.get(0).create();
	}
	
	public DataType getPrimaryType() {
		if (this.options.size() == 0) 
			return null;
		
		return this.options.get(0);
	}
}
