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

import dcraft.hub.ResourceHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.task.IParentAwareWork;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.Field;
import dcraft.schema.SchemaHub;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

/**
 * DivConq uses a specialized type system that provides type consistency across services 
 * (including web services), database fields and stored procedures, as well as scripting.
 * 
 * All scalars (including primitives) and composites (collections) are wrapped by some
 * subclass of Struct.  Map collections are expressed by this class - records have fields
 * and fields are a name value pair.  This class is analogous to an Object in JSON but may
 * contain type information as well, similar to Yaml.
 * 
 *  TODO link to blog entries.
 * 
 * @author Andy
 *
 */
public class RecordStruct extends CompositeStruct {
	/* groovy

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.InvokerHelper;
	
	 implements GroovyObject
	 */
	// this defines valid field name pattern (same as json)
	// start with number should be ok
	static protected final Pattern FIELD_NAME_PATTERN =
			Pattern.compile("(^[a-zA-Z0-9\\$_\\-]*$)|(^[\\$_][a-zA-Z0-9\\$_\\-]*$)");
			//Pattern.compile("(^[a-zA-Z][a-zA-Z0-9\\$_\\-]*$)|(^[\\$_][a-zA-Z][a-zA-Z0-9\\$_\\-]*$)");

	// TODO check field names inside of "set field" etc.
	static public boolean validateFieldName(String v) {
		if (StringUtil.isEmpty(v))
			return false;

		return ! StringUtil.containsRestrictedChars(v);  //RecordStruct.FIELD_NAME_PATTERN.matcher(v).matches();
	}	
	
	static public RecordStruct record() {
		return new RecordStruct();
	}
	
	static public RecordStruct typedRecord(DataType type) {
		return new RecordStruct(type);
	}
	
	// -------- instance ---------
	
	protected Map<String,FieldStruct> fields = new HashMap<String,FieldStruct>();

	// generate only on request - for groovy
	/* groovy
	protected transient MetaClass metaClass = null;
	*/

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();

		// implied only, not explicit
		return SchemaHub.getTypeOrError("AnyRecord");
	}

	/**
	 * Provide data type info (schema for fields) and a list of initial fields
	 * 
	 * @param type field schema
	 */
	public RecordStruct(DataType type) {
		super(type);
	}
	
	/**
	 * Optionally provide a list of initial fields
	 */
	public RecordStruct() {
	}
	
	/* (non-Javadoc)
	 * @see dcraft.struct.CompositeStruct#select(dcraft.struct.PathPart[])
	 */
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 0)
			return this;
		
		PathPart part = path[0];
		
		if (! part.isField()) {			
			Logger.warnTr(504, this);
			
			return null;
		}
		
		String fld = part.getField();
		
		if (!this.fields.containsKey(fld)) {
			//OperationResult log = part.getLog();
			
			//if (log != null) 
			//	log.warnTr(505, fld);
			
			return null;
		}
		
		Struct o = this.getField(fld);

		if (path.length == 1) 
			return (o != null) ? o : null;
		
		if (o instanceof IPartSelector)
			return ((IPartSelector)o).select(Arrays.copyOfRange(path, 1, path.length));
		
		//Logger.warnTr(503, o);
		
		return null;
	}
	
	/* (non-Javadoc)
	 * @see dcraft.struct.CompositeStruct#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return (this.fields.size() == 0);
	}
	
	@Override
	public void toBuilder(ICompositeBuilder builder) throws BuilderStateException {
		builder.startRecord();
		
		for (FieldStruct f : this.fields.values()) 
			f.toBuilder(builder);
		
		builder.endRecord();
	}

	/**
	 * Adds or replaces a list of fields within the record.
	 * 
	 * @param fields to add or replace
	 * @return self
	 */
	public RecordStruct with(FieldStruct... fields) {
		for (FieldStruct f : fields) {
			Struct svalue = f.getValue();
			
			if (! f.prepped) {
				// take the original value and convert to a struct, fields hold structures
				Object value = f.orgvalue;
				
				if (value instanceof ICompositeBuilder)
					value = ((ICompositeBuilder)value).toLocal();
				
				if (this.explicitType != null) {
					Field fld = this.explicitType.getField(f.getName());
					
					if (fld != null) {
						Struct sv = fld.wrap(value);
						
						if (sv != null)
							svalue = sv;
					}
				}
				
				if (svalue == null) 
					svalue = Struct.objectToStruct(value);
				
				f.setValue(svalue);
				f.prepped = true;
			}
			
			//FieldStruct old = this.fields.get(f.getName());
			
			//if (old != null)
			//	old.dispose();
			
			this.fields.put(f.getName(), f);
		}
		
		return this;
	}
	
	/**
	 * Add or replace a specific field with a value.
	 * 
	 * @param name of field
	 * @param value to store with field
	 * @return self
	 */
	public RecordStruct with(String name, Object value) {
		this.with(new FieldStruct(name, value));
		return this;
	}
	
	/**
	 * 
	 * @return collection of all the fields this record holds
	 */
	public Iterable<FieldStruct> getFields() {
		return this.fields.values();
	}
	
	/**
	 * 
	 * @param name of the field desired
	 * @return the struct for that field
	 */
	public Struct getField(String name) {
		if (! this.fields.containsKey(name)) 
			return null;
		
		FieldStruct fs = this.fields.get(name);
		
		if (fs == null)
			return null;
		
		return fs.value;
	}
	
	/**
	 * 
	 * @param name of the field desired
	 * @return the struct for that field
	 */
	public FieldStruct getFieldStruct(String name) {
		if (!this.fields.containsKey(name)) 
			return null;
		
		return this.fields.get(name);
	}
	
	/**
	 * 
	 * @param from original name of the field 
	 * @param to new name for field
	 */
	public void renameField(String from, String to) {
		FieldStruct f = this.fields.remove(from);
		
		if (f != null) {
			f.setName(to);
			this.fields.put(to, f);
		}
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as an Object
	 */
	public Object getFieldAsAny(String name) {
		Struct st = this.getField(name);
		
		if (st == null)
			return null;
		
		if (st instanceof ScalarStruct) 
			return ((ScalarStruct)st).getGenericValue();
		
		if (st instanceof CompositeStruct) 
			return ((CompositeStruct)st).toString();
		
		return st;
	}
	
	/**
	 * If the record has schema, lookup the schema for a given field.
	 * 
	 * @param name of the field desired
	 * @return field's schema
	 */
	public DataType getFieldType(String name) {
		// look first at the field value, if it has schema return
		Struct fs = this.getField(name);
		
		if ((fs != null) && (fs.hasExplicitType()))
				return fs.getType();
		
		// look next at this records schema
		if (this.explicitType != null) {
			Field fld = this.explicitType.getField(name);
			
			if (fld != null)
				return fld.getPrimaryType();
		}
		
		// give up, we don't know the schema
		return null;
	}
	
	/**
	 * Like getField, except if the field does not exist it will be created and added
	 * to the Record (unless that field name violates the schema).
	 * 
	 * @param name of the field desired
	 * @return log of messages from call plus the requested structure
	 */
	public Struct getOrAllocateField(String name) {
		if (! this.fields.containsKey(name)) {
			Struct value = null;
			
			if (this.explicitType != null) {
				Field fld = this.explicitType.getField(name);
				
				if (fld != null) 
					return fld.create();
				
				if (this.explicitType.isAnyRecord()) 
					value = NullStruct.instance;
			}
			else
				value = NullStruct.instance;
			
			if (value != null) {
				FieldStruct f = new FieldStruct(name, value);
				f.value = value;
				this.fields.put(name, f);
				
				return value;
			}
		}
		else {
			Struct value = this.getField(name);
			
			if (value == null)
				value = NullStruct.instance;
			
			return value;
		}
		
		return null;
	}
	
	/**
	 * 
	 * @param name of field
	 * @return true if field exists 
	 */
	public boolean hasField(String name) {
		return this.fields.containsKey(name);
	}
	
	/**
	 * 
	 * @param name of field
	 * @return true if field does not exist or if field is string and its value is empty 
	 */
	public boolean isFieldEmpty(String name) {
		Struct f = this.getField(name);
		
		if (f == null) 
			return true;
		
		return f.isEmpty();
	}
	
	public boolean isNotFieldEmpty(String name) {
		return ! this.isFieldEmpty(name);
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Integer (DivConq thinks of integers as 64bit)
	 */
	public Long getFieldAsInteger(String name) {
		return Struct.objectToInteger(this.getField(name));
	}
	
	public long getFieldAsInteger(String name, long defaultval) {
		Long x = Struct.objectToInteger(this.getField(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as BigInteger 
	 */
	public BigInteger getFieldAsBigInteger(String name) {
		return Struct.objectToBigInteger(this.getField(name));
	}
	
	public BigInteger getFieldAsBigInteger(String name, BigInteger defaultval) {
		BigInteger x = Struct.objectToBigInteger(this.getField(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as BigDecimal
	 */
	public BigDecimal getFieldAsDecimal(String name) {
		return Struct.objectToDecimal(this.getField(name));
	}
	
	public BigDecimal getFieldAsDecimal(String name, BigDecimal defaultval) {
		BigDecimal x = Struct.objectToDecimal(this.getField(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Boolean
	 */
	public Boolean getFieldAsBoolean(String name) {
		return Struct.objectToBoolean(this.getField(name));
	}

	public boolean getFieldAsBooleanOrFalse(String name) {
		Boolean b = Struct.objectToBoolean(this.getField(name));
		
		return (b == null) ? false : b.booleanValue();
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as DateTime
	 */
	public ZonedDateTime getFieldAsDateTime(String name) {
		return Struct.objectToDateTime(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as BigDateTime
	 */
	public BigDateTime getFieldAsBigDateTime(String name) {
		return Struct.objectToBigDateTime(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Date
	 */
	public LocalDate getFieldAsDate(String name) {
		return Struct.objectToDate(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Time
	 */
	public LocalTime getFieldAsTime(String name) {
		return Struct.objectToTime(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as a String
	 */
	public String getFieldAsString(String name) {
		return Struct.objectToString(this.getField(name));
	}
	
	public String getFieldAsString(String name, String defaultval) {
		String x = Struct.objectToString(this.getField(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Memory 
	 */
	public Memory getFieldAsBinary(String name) {
		return Struct.objectToBinary(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as a Record
	 */
	public RecordStruct getFieldAsRecord(String name) {
		return Struct.objectToRecord(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as a List
	 */
	public ListStruct getFieldAsList(String name) {
		return Struct.objectToList(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as CompositeStruct
	 */
	public CompositeStruct getFieldAsComposite(String name) {
		return Struct.objectToComposite(this.getField(name));
	}
	
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Struct
	 */
	public Struct getFieldAsStruct(String name) {
		return Struct.objectToStruct(this.getField(name));
	}
	
    public <T extends Object> T getFieldAsStruct(String name, Class<T> type) {
    	Struct s = this.getField(name);
    	
          if (type.isAssignableFrom(s.getClass()))
                return type.cast(s);
         
          return null;
    }
    
	/**
	 * Unlike getField, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param name of the field desired
	 * @return field's "inner" value as Xml (will parse if value is string)
	 */
	public XElement getFieldAsXml(String name) {
		return Struct.objectToXml(this.getField(name));
	}
	
	/**
	 * 
	 * @return number of fields held by this record
	 */
	public int getFieldCount() {
		return this.fields.size();
	}
	
	/*
	public String checkRequiredFields(String... fields) {
		for (String fld : fields) {
			if (this.isFieldBlank(fld))
				return fld;
		}
		
		return null;
	}
	
	public String checkRequiredIfPresentFields(String... fields) {
		for (String fld : fields) {
			if (this.hasField(fld) && this.isFieldBlank(fld))
				return fld;
		}
		
		return null;
	}
	
	public String checkFieldRange(String... fields) {
		for (FieldStruct fld : this.getFields()) {
			boolean fnd = false;
			
			for (String fname : fields) {
				if (fld.getName().equals(fname)) {
					fnd = true;
					break;
				}
			}
			
			if (!fnd)
				return fld.getName();
		}
		
		return null;
	}
	*/

	/**
	 * 
	 * @param name of field to remove
	 */
	public FieldStruct removeField(String name) {
		return this.fields.remove(name);
	}

	public Struct sliceField(String name) {
		FieldStruct fld = this.fields.get(name);
		
		this.fields.remove(name);
		
		return fld.sliceValue();
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	RecordStruct nn = (RecordStruct)n;
    	
    	for (FieldStruct fld : this.fields.values())
    		nn.with(fld.deepCopy());
    }
    
	@Override
	public RecordStruct deepCopy() {
		RecordStruct cp = new RecordStruct();
		this.doCopy(cp);
		return cp;
	}
	
	public RecordStruct deepCopyFields(String... include) {
		RecordStruct cp = new RecordStruct();
    	super.doCopy(cp);
    	
    	for (String fld : include) {
    		if (this.hasField(fld))
				cp.with(this.fields.get(fld).deepCopy());				
		}    	
		
		return cp;
	}
	
	public RecordStruct deepCopyExclude(String... exclude) {
		RecordStruct cp = new RecordStruct();
    	super.doCopy(cp);
    	
    	for (FieldStruct fld : this.fields.values()) {
			boolean fnd = false;
			
			for (String x : exclude)
				if (fld.getName().equals(x)) {
					fnd = true;
					break;
				}
			
			if (!fnd)
				cp.with(fld.deepCopy());				
		}    	
		
		return cp;
	}

	/**
	 * Remove all child fields.
	 */
	@Override
	public void clear() {		
		this.fields.clear();
	}

	@Override
	public boolean checkLogic(IParentAwareWork stack, XElement source) throws OperatingContextException {
		boolean isok = true;
		boolean condFound = false;

		if (! condFound && source.hasAttribute("HasField")) {
			String other = StackUtil.stringFromElement(stack, source, "HasField");
			isok = this.isNotFieldEmpty(other);
			condFound = true;
		}

		if (! condFound)
			isok = Struct.objectToBooleanOrFalse(this);

		return isok;
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Set".equals(code.getName())) {
			this.clear();
			
			String json = StackUtil.resolveValueToString(stack, code.getText());
			
			if (StringUtil.isNotEmpty(json)) {
				json = json.trim();

				if (! json.startsWith("{")) {
					json = "{ " + json + " }";
				}

				RecordStruct pjson = (RecordStruct) CompositeParser.parseJson(json);

				this.copyFields(pjson);
			}
			
			return ReturnOption.CONTINUE;
		}

		if ("SetField".equals(code.getName())) {
            String def = StackUtil.stringFromElement(stack, code, "Type");
            String name = StackUtil.stringFromElement(stack, code, "Name");
			
			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in SetField");
				return ReturnOption.CONTINUE;
			}
            
            Struct var = null;
            
            if (StringUtil.isNotEmpty(def))
            	var = ResourceHub.getResources().getSchema().getType(def).create();

			if (code.hasAttribute("Value")) {
		        Struct var3 = StackUtil.refFromElement(stack, code, "Value", true);
		        
				if ((var == null) && (var3 != null))
	            	var = var3.getType().create();
				
				if (var instanceof ScalarStruct) 
					((ScalarStruct) var).adaptValue(var3);
				else
					var = var3;
			}

			// not an error if null
			this.with(name, var);
			
			return ReturnOption.CONTINUE;
		}

		if ("RemoveField".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			
			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in RemoveField");
				return ReturnOption.CONTINUE;
			}
			
			this.removeField(name);
			
			return ReturnOption.CONTINUE;
		}

		if ("NewList".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			
			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in NewList");
				return ReturnOption.CONTINUE;
			}
			
			this.removeField(name);
			
			this.with(name, new ListStruct());
			
			return ReturnOption.CONTINUE;
		}

		if ("NewRecord".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			
			if (StringUtil.isEmpty(name)) {
				Logger.error("Missing field name in NewRecord");
				return ReturnOption.CONTINUE;
			}
			
			this.removeField(name);
			
			this.with(name, new RecordStruct());
			
			return ReturnOption.CONTINUE;
		}

		if ("HasField".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			String handle = StackUtil.stringFromElement(stack, code, "Handle");
			
			if (handle != null) {
				if (StringUtil.isEmpty(name))
					StackUtil.addVariable(stack, handle, BooleanStruct.of(false));
				else
					StackUtil.addVariable(stack, handle, BooleanStruct.of(this.hasField(name)));
			}
			
			return ReturnOption.CONTINUE;
		}

		if ("IsFieldEmpty".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			String handle = StackUtil.stringFromElement(stack, code, "Handle");
			
			if (handle != null) {
				if (StringUtil.isEmpty(name))
					StackUtil.addVariable(stack, handle, BooleanStruct.of(true));
				else
					StackUtil.addVariable(stack, handle, BooleanStruct.of(this.isFieldEmpty(name)));
			}
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}

	public void copyFields(RecordStruct src, String... except) {
		if (src != null)
			for (FieldStruct fld : src.getFields()) {
				boolean fnd = false;
				
				for (String x : except)
					if (fld.getName().equals(x)) {
						fnd = true;
						break;
					}
				
				if (!fnd)
					this.with(fld);				
			}
	}

	// TODO support Groovy .items usage
	
	@Override
	public boolean equals(Object obj) {
		// TODO go deep
		if (obj instanceof RecordStruct) {
			RecordStruct data = (RecordStruct) obj;
			
			for (FieldStruct fld : this.fields.values()) {
				if (!data.hasField(fld.name))
					return false;
				
				Struct ds = data.getField(fld.name);
				Struct ts = fld.value;
				
				if ((ds == null) && (ts == null))
					continue;
				
				if ((ds == null) && (ts != null))
					return false;
				
				if ((ds != null) && (ts == null))
					return false;
				
				if (!ts.equals(ds))
					return false;
			}
			
			// don't need to check match the other way around, we already know matching fields have good values  
			for (FieldStruct fld : data.fields.values()) {
				if (!this.hasField(fld.name))
					return false;
			}
			
			return true;
		}
		
		return super.equals(obj);
	}
	
	/* groovy
	@Override
    public Object getProperty(String name) { 
		Struct v = this.getField(name);
		
		if (v == null)
			return null;
		
		if (v instanceof CompositeStruct)
			return v;
		
		return ((ScalarStruct) v).getGenericValue();
    }
    
	@Override
    public void setProperty(String name, Object value) { 
		this.with(name, value);
    }

	@Override
	public void setMetaClass(MetaClass v) {
		this.metaClass = v;
	}
	
	@Override
	public MetaClass getMetaClass() {
        if (this.metaClass == null) 
        	this.metaClass = InvokerHelper.getMetaClass(getClass());
        
        return this.metaClass;
	}

	@Override
	public Object invokeMethod(String name, Object arg1) {
		// is really an object array
		Object[] args = (Object[])arg1;
		
		if (args.length > 0)
			System.out.println("G2: " + name + " - " + args[0]);
		else
			System.out.println("G2: " + name);
		
		return null;
	}
	*/
}
