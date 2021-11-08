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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import dcraft.example.StreamTwo;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperationMarker;
import dcraft.locale.IndexInfo;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.schema.Field.ReqTypes;
import dcraft.struct.*;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class DataType {
	enum DataKind {
		Scalar(1),
		List(2),
		Record(3),
		Flexible(4);
	    
	    private int code;

	    private DataKind(int c) {
	      code = c;
	    }

	    public int getCode() {
	      return code;
	    }
	}

	protected Schema schema = null;
	protected String id = null;
	protected DataKind kind = null;
	protected XElement definition = null;	
	protected List<XElement> xtraDefinitions = null;  //new ArrayList<XElement>();
	
	// for record
	protected HashMap<String,Field> fields = null; 
	protected boolean anyRec = false;
	
	// for list
	protected TypeOptionsList items = null;
	protected int minItems = 0;
	protected int maxItems = 0;
	
	// for flex
	protected Field flexfield = null; 
	
	// for scalar
	protected CoreType core = null;
	
	protected boolean compiled = false;

	public String getId() {
		return this.id;
	}
	
	public DataKind getKind() {
		return this.kind;
	}

	public DataType getPrimaryItemType() {
		if (this.items != null) 
			return this.items.getPrimaryType();
		
		return null;
	}
	
	public CoreType getCoreType() {
		return this.core;
	}
	
	public boolean isAnyRecord() {
		return this.anyRec;
	}
	
	public Collection<Field> getFields() {
		if (this.fields == null)
			return new ArrayList<Field>();
		
		return this.fields.values();
	}
	
	public Field getField(String name) {
		if (this.fields == null)
			return null;
		
		return this.fields.get(name);
	}
	
	public TypeOptionsList getItems() {
		return this.items;
	}
	
	public DataType(Schema schema) {
		this.schema = schema;
	}

	// default to 13 levels
	public RecordStruct toJsonDef() {
		return this.toJsonDef(13);
	}

	public RecordStruct toJsonDef(int lvl) {
		if (lvl == 0) {
			RecordStruct def = new RecordStruct();
			def.with("Kind", DataKind.Scalar);
			def.with("CoreType", new CoreType(RootType.Any).toJsonDef());
			return def;
		}
		
		RecordStruct def = new RecordStruct();
		
		if (StringUtil.isNotEmpty(this.id))
			def.with("Id", this.id);
		
		def.with("Kind", this.kind.getCode());
		
		if (this.kind == DataKind.Record) {
			if (this.anyRec)
				def.with("AnyRec", true);
			
			ListStruct fields = new ListStruct();
			
			for (Field fld : this.fields.values()) 
				fields.withItem(fld.toJsonDef(lvl - 1));
			
			def.with("Fields", fields);
		}
		else if (this.kind == DataKind.List) {
			if (this.maxItems > 0)
				def.with("MaxItems", this.maxItems);
			
			if (this.minItems > 0)
				def.with("MinItems", this.minItems);
			
			if (this.items != null)
				def.with("Items", this.items.toJsonDef(lvl - 1));
		}
		else if (this.kind == DataKind.Scalar) {
			if (this.core != null)
				def.with("CoreType", this.core.toJsonDef());
		}
		
		// TODO flexible?
		
		return def;
	}
	
	public void load(XElement dtel) {
		if (this.definition != null) {
			if (this.xtraDefinitions == null)
				this.xtraDefinitions = new ArrayList<XElement>();
			
			this.xtraDefinitions.add(dtel);			
			return;
		}
		
		this.definition = dtel;
		
		String elname = dtel.getName();
		
		if ("Record".equals(elname) || "Table".equals(elname) || "RecordRequest".equals(elname) || "RecordResponse".equals(elname)) 
			this.kind = DataKind.Record;
		else if ("List".equals(elname) || "ListRequest".equals(elname) || "ListResponse".equals(elname)) 
			this.kind = DataKind.List;
		else if ("Request".equals(elname) || "Response".equals(elname))
			this.kind = DataKind.Flexible;
		else if ("RequestStream".equals(elname) || "ResponseStream".equals(elname))
			this.kind = DataKind.Record;		// only applies to records, not to files streams
		else 
			this.kind = DataKind.Scalar;
		
		this.id = dtel.getAttribute("Id");
	}

	public void compile() {
		if (this.compiled)
			return;
		
		this.compiled = true;
		
		if (this.kind == DataKind.Record)
			this.compileRecord();
		else if (this.kind == DataKind.List)
			this.compileList();
		else if (this.kind == DataKind.Flexible) 
			this.compileFlex();
		else
			this.compileScalar();
	}

	public boolean isSearchable() {
		if (this.definition.hasAttribute("Searchable"))
			return this.definition.getAttributeAsBooleanOrFalse("Searchable");

		if (this.kind == DataKind.Record) {
			for (Field fld : this.fields.values()) {
				DataType ftype = fld.getPrimaryType();
				
				if (ftype == null)
					continue;
				
				CoreType ctype = ftype.getCoreType();

				// not just searchable
				if ((ctype != null) && (ctype.getType() == RootType.String)) {
					return true;
				}
			}
		}
		else if (this.kind == DataKind.List) {
			DataType ftype = this.items.getPrimaryType();
			
			if (ftype == null)
				return false;
			
			CoreType ctype = ftype.getCoreType();
			
			if ((ctype != null) && (ctype.getType() == RootType.String)) {
				return true;
			}
		}
		else if ((this.core != null) && this.core.isSearchable()) {
			return true;
		}

		return false;
	}

	// validate before this call
	public Object toSearch(Object val, String lang) {
		if (val == null)
			return null;

		if ((this.kind == DataKind.Record) || (this.kind == DataKind.List)) {
			return LocaleUtil.toSearch(this.toIndexTokens(val, lang));
		}
		else if ((this.core != null) && this.core.isSearchable()) {
			return LocaleUtil.toSearch(this.toIndexTokens(val, lang));
		}
		
		if (this.core != null)
			val = this.core.normalize(this, val);

		return Struct.objectToCore(val);
	}

	// validate before this call
	public Object toIndex(Object val, String lang) {
		if (val == null)
			return null;
		
		if ((this.kind == DataKind.Record) || (this.kind == DataKind.List)) {
			return LocaleUtil.toIndex(this.toIndexTokens(val, lang));
		}
		else if ((this.core != null) && this.core.isSearchable()) {
			return LocaleUtil.toIndex(this.toIndexTokens(val, lang));
		}
		
		if (this.core != null)
			val = this.core.normalize(this, val);

		val = Struct.objectToCore(val);

		// empty strings are "null" in index
		if ((val instanceof CharSequence) && (((CharSequence) val).length() == 0))
			return null;

		return val;
	}
	
	public List<IndexInfo> toIndexTokens(Object val, String lang) {
		if (val == null)
			return null;

		// although the type might be record, incoming val might be a string (search term) so just test the types instead of assuming the type
		if ((this.kind == DataKind.Record) || (this.kind == DataKind.List) || (this.core != null) && this.core.isSearchable()) {
			List<IndexInfo> tokens = new ArrayList<>();

			if (val instanceof RecordStruct) {
				RecordStruct data = Struct.objectToRecord(val);

				for (Field fld : this.fields.values()) {
					DataType ftype = fld.getPrimaryType();
					
					if (ftype == null)
						continue;
					
					CoreType ctype = ftype.getCoreType();
					
					if (ctype == null)
						continue;
					
					if (ctype.isSearchable()) {
						tokens.addAll(LocaleUtil.full(data.getFieldAsString(fld.name), lang));
					}
					else if (ctype.getType() == RootType.String) {
						tokens.addAll(LocaleUtil.simple(data.getFieldAsString(fld.name), lang));
					}
				}
			}
			else if (val instanceof ListStruct) {
				ListStruct data = Struct.objectToList(val);

				DataType ftype = this.items.getPrimaryType();
				
				if (ftype == null)
					return null;
				
				CoreType ctype = ftype.getCoreType();
				
				if (ctype == null)
					return null;
				
				if ((ctype != null) && ctype.isSearchable()) {
					for (int i = 0; i < data.size(); i++) {
						tokens.addAll(LocaleUtil.full(data.getItemAsString(i), lang));
					}
				}
				else if (ctype.getType() == RootType.String) {
					for (int i = 0; i < data.size(); i++) {
						tokens.addAll(LocaleUtil.simple(data.getItemAsString(i), lang));
					}
				}
			}
			else {
				tokens.addAll(LocaleUtil.full(Struct.objectToString(val), lang));
			}

			return tokens;
		}
		
		return null;
	}

	public boolean isRequired() {
		return this.definition.getAttributeAsBooleanOrFalse("Required");
	}

	protected void compileRecord() {
		List<String> inhlist = new ArrayList<>();
		
		if ("AnyRecord".equals(this.definition.getAttribute("Type")))
			this.anyRec = true;
		
		if ("AnyRecord".equals(this.id))
			this.anyRec = true;
		
		String inherits = this.definition.getAttribute("Inherits");
		
		if (StringUtil.isNotEmpty(inherits)) {
			String[] ilist = inherits.split(",");
			
			for (int i = 0; i < ilist.length; i++)
				inhlist.add(ilist[i]);
		}		

		List<DataType> inheritTypes = new ArrayList<>();
		
		for (String iname : inhlist) {
			DataType dtype = this.schema.manager.getType(iname);
			
			if (dtype == null) {
				Logger.errorTr(413, iname);
				continue;
			}
			
			dtype.compile();
			
			inheritTypes.add(dtype);
		}
		
		this.fields = new HashMap<>();
		
		for (XElement fel : this.definition.selectAll("Field")) {
			Field f = new Field(this.schema);
			f.compile(fel);
			this.fields.put(f.name, f);
		}
		
		// TODO Review how we use xtraDefinitions 
		if (this.xtraDefinitions != null) {
			for (XElement el : this.xtraDefinitions) {
				for (XElement fel : el.selectAll("Field")) {
					Field f = new Field(this.schema);
					f.compile(fel);
					this.fields.put(f.name, f);
				}
			}
		}
		
		for (DataType dt : inheritTypes) {
			for (Field fld : dt.getFields()) {
				if (! this.fields.containsKey(fld.name))
					this.fields.put(fld.name, fld);
			}
		}
	}

	protected void compileList() {
		this.items = new TypeOptionsList(this.schema);		
		this.items.compile(this.definition);
		
		if (this.definition.hasAttribute("MinCount"))
			this.minItems = (int)StringUtil.parseInt(this.definition.getAttribute("MinCount"), 0);
		
		if (this.definition.hasAttribute("MaxCount"))
			this.maxItems = (int)StringUtil.parseInt(this.definition.getAttribute("MaxCount"), 0);
	}

	protected void compileFlex() {
		Field f = new Field(this.schema);
		f.compile(this.definition);
		this.flexfield = f;
	}

	protected void compileScalar() {
		this.core = new CoreType(this.schema);
		this.core.compile(this.definition);
	}

	// don't call this with data == null from a field if field required - required means "not null" so put the error in
	public boolean match(boolean isfinal, Object data) {
		if (this.kind == DataKind.Record) {
			if (data instanceof RecordStruct)
				return this.matchRecord(isfinal, (RecordStruct)data);
			
			return false;
		}
		
		if (this.kind == DataKind.List) {
			if (data instanceof ListStruct)
				return this.matchList(isfinal, (ListStruct)data);

			return false;
		}
		
		if (this.kind == DataKind.Flexible) {
			if (data instanceof BaseStruct)
				return this.matchFlex(isfinal, (BaseStruct)data);

			Logger.errorTr(420, data);		
			return false;
		}

		return this.matchScalar(data);
	}

	protected boolean matchRecord(boolean isfinal, RecordStruct data) {
		if (this.fields != null) {
			
			// match only if all required fields are present 
			for (Field fld : this.fields.values()) {
				// check if empty, if required
				if (data.isFieldEmpty(fld.name)) {
					if (fld.required == ReqTypes.True)
						return false;
					
					if ((fld.required == ReqTypes.Final) && isfinal)
						return false;
				}
				
				// only check empty if field is present
				if (data.hasField(fld.name) && data.isFieldEmpty(fld.name)) {
					if ((fld.required == ReqTypes.IfPresent) || (fld.required == ReqTypes.Final))
						return false;
				}
			}
			
			return true;
		}
		
		// this is an exception to the rule, there is no "non-null" state to return from this method
		return this.anyRec;
	}

	protected boolean matchList(boolean isfinal, ListStruct data) {
		return true;
	}

	protected boolean matchFlex(boolean isfinal, BaseStruct data) {
		// if no data,check if required
		if ((data == null) || data.isEmpty()) {
			if (this.flexfield.required == ReqTypes.True)
				return false;
			
			if ((this.flexfield.required == ReqTypes.Final) && isfinal)
				return false;
		}
		
		// required only if present
		if ((data != null) && data.isEmpty()) {
			if ((this.flexfield.required == ReqTypes.IfPresent) || (this.flexfield.required == ReqTypes.Final))
				return false;
		}
		
		return true;
	}

	protected boolean matchScalar(Object data) {
		if (this.core == null) 
			return false;
		
		return this.core.match(data);
	}
	
	// don't call this with data == null from a field if field required - required means "not null" so put the error in
	// returns true only if there was a non-null value present that conforms to the expected structure (record, list or scalar) 
	// null values that do not conform should not cause an false
	public boolean validate(boolean isfinal, boolean selectmode, BaseStruct data) {
		if (data == null)
			return false;
		
		if (this.kind == DataKind.Record) {
			if (this.anyRec)
				return true;
			
			if (data instanceof ICompositeBuilder)
				data = ((ICompositeBuilder)data).toLocal();		
			
			if (data instanceof RecordStruct)
				return this.validateRecord(isfinal, selectmode, (RecordStruct)data);

			Logger.errorTr(414, data);
			return false;
		}
		
		if (this.kind == DataKind.List) {
			if (data instanceof ListStruct)
				return this.validateList(isfinal, selectmode, (ListStruct)data);

			Logger.errorTr(415, data);		
			return false;
		}
		
		if (this.kind == DataKind.Flexible) {
			if (data instanceof BaseStruct)
				return this.validateFlex(isfinal, selectmode, (BaseStruct)data);

			Logger.errorTr(420, data);		
			return false;
		}
		
		if (this.core == null) {
			Logger.errorTr(420, data);   
			return false;
		}
		
		if (this.core.root == RootType.Any)
			return true;
		
		if (data instanceof ScalarStruct)
			return this.core.validate(this, (ScalarStruct) data);
		
		Logger.error("Data should be scalar: " + data);		
		return false;
	}
	
	public BaseStruct normalizeValidate(boolean isfinal, boolean selectmode, BaseStruct data) {
		if (data == null)
			return null;
		
		data.withType(this);
		
        //if (Logger.isDebug())
        //	Logger.debug("Validating: " + this.kind + " against: " + data);
		
		if (this.kind == DataKind.Record) {
			if (data instanceof ICompositeBuilder)
				data = ((ICompositeBuilder)data).toLocal();	
			
			if (data instanceof RecordStruct) 
				return normalizeValidateRecord(isfinal, selectmode, (RecordStruct)data);

			Logger.errorTr(414, data);
			return null;
		}
		
		if (this.kind == DataKind.List) {
			if (data instanceof ListStruct) 
				return this.normalizeValidateList(isfinal, selectmode, (ListStruct)data);
			
			Logger.errorTr(415, data);		
			return null;
		}
		
		if (this.kind == DataKind.Flexible) {
			if (data instanceof BaseStruct)
				return this.normalizeValidateFlex(isfinal, selectmode, (BaseStruct)data);

			Logger.errorTr(420, data);		
			return null;
		}
		
		if (this.core == null) {
			Logger.errorTr(420, data);   
			return null;
		}
		
		if (this.core.root == RootType.Any)
			return data;
		
		if (data instanceof ScalarStruct) 
			return this.core.normalizeValidate(this, (ScalarStruct) data);
		
		Logger.error("Data should be scalar: " + data);		
		return null;
	}

	protected boolean validateRecord(boolean isfinal, boolean selectmode, RecordStruct data) {
		if (this.fields != null) {
			// handles all but the case where data holds a field not allowed 
			for (Field fld : this.fields.values()) {
				if (! fld.validate(data.hasField(fld.name), isfinal, selectmode, data.getField(fld.name)))
					return false;
			}
			
			if (! this.anyRec) {
				for (FieldStruct fld : data.getFields()) {
					if (! this.fields.containsKey(fld.getName())) {
						Logger.errorTr(419, fld.getName(), data);	
						return false;
					}
				}
			}
		}
		
		return true;
	}

	protected RecordStruct normalizeValidateRecord(boolean isfinal, boolean selectmode, RecordStruct data) {
		if (this.fields != null) {
			// handles all but the case where data holds a field not allowed 
			for (Field fld : this.fields.values()) {
		        //if (Logger.isDebug())
		        //	Logger.debug("Validating field: " + fld.name);

				BaseStruct s = data.getField(fld.name);
				BaseStruct o  = fld.normalizeValidate(data.hasField(fld.name), isfinal, selectmode, data.getField(fld.name));
				
				if (s != o)
					data.with(fld.name, o);
			}
			
			if (! this.anyRec)
				for (FieldStruct fld : data.getFields()) {
					if (! this.fields.containsKey(fld.getName()))
						Logger.errorTr(419, fld.getName(), data);	
				}
		}
		
		return data;
	}

	protected boolean validateList(boolean isfinal, boolean selectmode, ListStruct data) {
		if (this.items == null) {
			Logger.errorTr(416, data);  
			return false;
		}
		else {
			for (BaseStruct obj : data.items()) {
				if (! this.items.validate(isfinal, selectmode, obj))
					return false;
			}
		}
		
		if ((this.minItems > 0) && (data.size() < this.minItems)) {
			Logger.errorTr(417, data);   
			return false;
		}
		
		if ((this.maxItems > 0) && (data.size() > this.maxItems)) {
			Logger.errorTr(418, data);   
			return false;
		}
		
		return true;		
	}

	protected ListStruct normalizeValidateList(boolean isfinal, boolean selectmode, ListStruct data) {
		if (this.items == null) 
			Logger.errorTr(416, data);   
		else
			for (int i = 0; i < data.size(); i++) {
				BaseStruct s = data.getItem(i);
				BaseStruct o = this.items.normalizeValidate(isfinal, selectmode, s);
				
				if (s != o)
					data.replaceItem(i, o);
			}
		
		if ((this.minItems > 0) && (data.size() < this.minItems))
			Logger.errorTr(417, data);   
		
		if ((this.maxItems > 0) && (data.size() > this.maxItems))
			Logger.errorTr(418, data);   
		
		return data;
	}

	protected boolean validateFlex(boolean isfinal, boolean selectmode, BaseStruct data) {
		return this.flexfield.validate((data != null), isfinal, selectmode, data);
	}

	protected BaseStruct normalizeValidateFlex(boolean isfinal, boolean selectmode, BaseStruct data) {
		return this.flexfield.normalizeValidate((data != null), isfinal, selectmode, data);
	}
	
	public BaseStruct wrap(Object data) {
		if (data == null) 
			return null;
		
		if (this.kind == DataKind.Record) {
			if (data instanceof RecordStruct) {
				BaseStruct s = (BaseStruct)data;

				if (!s.hasExplicitType())
					s.withType(this);
				
				return s;
			}
			
			Logger.errorTr(421, data);		
			return null;
		}
		
		if (this.kind == DataKind.List) {
			// TODO support Collection<Object> and Array<Object> as input too
			
			if (data instanceof ListStruct) {
				BaseStruct s = (BaseStruct)data;
				
				if (!s.hasExplicitType())
					s.withType(this);
				
				return s;
			}
			
			Logger.errorTr(439, data);		
			return null;
		}
		
		if ((this.core.root == RootType.Any) && (data instanceof BaseStruct))
			return (BaseStruct) data;
		
		return this.core.normalize(this, data);
	}
	
	public String getClassName() {
		if (this.definition.hasAttribute("Class"))
			return this.definition.getAttribute("Class");
		
		if (this.kind == DataKind.Record)
			return "dcraft.struct.RecordStruct";
		
		else if (this.kind == DataKind.List)
			return "dcraft.struct.ListStruct";
		
		return this.core.getClassName();
	}
	
	public BaseStruct create() {
		BaseStruct st = (BaseStruct) ResourceHub.getResources().getClassLoader().getInstance(this.getClassName());
		
		if (st == null) {
			Logger.error("Unable to create data type: " + this.id);
			return null;
		}

		st.withType(this);
		
		return st; 
	}
}
