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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.schema.TypeOptionsList;
import dcraft.script.work.ReturnOption;
import dcraft.script.StackUtil;
import dcraft.script.work.StackWork;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

/**
 * DivConq uses a specialized type system that provides type consistency across services 
 * (including web services), database fields and stored procedures, as well as scripting.
 * 
 * All scalars (including primitives) and composites (collections) are wrapped by some
 * subclass of Struct.  List/array collections are expressed by this class.  
 * This class is analogous to an Array in JSON but may contain type information as well, 
 * similar to Yaml.
 * 
 *  TODO link to blog entries.
 * 
 * @author Andy
 *
 */
public class ListStruct extends CompositeStruct implements Iterable<Object> {
	static public ListStruct list(Object... items) {
		ListStruct ret = new ListStruct();
		
		ret.withItem(items);
		
		return ret;
	}
	
	static public ListStruct typedList(DataType type, Object... items) {
		ListStruct ret = new ListStruct(type);
		
		ret.withItem(items);
		
		return ret;
	}
	
	protected List<Struct> items = new CopyOnWriteArrayList<Struct>();		// TODO can we make a more efficient list (one that allows modifications but won't crash an iterator)

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();

		// implied only, not explicit
		return SchemaHub.getTypeOrError("AnyList");
	}
	
	/**
	 * Provide data type info (schema for fields) and a list of initial items
	 * 
	 * @param type field schema
	 */
	public ListStruct(DataType type) {
		super(type);
	}
	
	/**
	 * Optionally provide a list of initial items
	 * 
	 */
	public ListStruct() {
	}
	
	/* (non-Javadoc)
	 * @see dcraft.struct.CompositeStruct#select(dcraft.struct.PathPart[])
	 */
	@Override
	public Struct select(PathPart... path) {
		if (path.length == 0)
			return this;
		
		PathPart part = path[0];
		
		String fld = part.getField();
		
		if ("Length".equals(fld))
			return IntegerStruct.of((long)this.items.size());
		
		if ("Last".equals(fld))
			return IntegerStruct.of((long)this.items.size() - 1);
		
		if (fld != null) {
			Logger.warnTr(501, this);
			return null;
		}
		
		int idx = part.getIndex();
		
		if (idx >= this.items.size()) {
			//Logger.warnTr(502, part.getIndex());
			return null;
		}
		
		Struct o = this.items.get(idx);
		
		if (path.length == 1) 
			return o;			
		
		if (o instanceof IPartSelector)
			return ((IPartSelector)o).select(Arrays.copyOfRange(path, 1, path.length));
		
		Logger.warnTr(503, o);
		return null;
	}
	
	public Stream<Struct> structStream() {
		return this.items.stream();
	}
	
	public Stream<RecordStruct> recordStream() {
		return this.items.stream().map(p -> (RecordStruct)p);
	}
	
	public Stream<String> stringStream() {
		return this.items.stream().map(p -> Struct.objectToString(p));
	}
	
	public Stream<Long> integerStream() {
		return this.items.stream().map(p -> Struct.objectToInteger(p));
	}
	
	/* (non-Javadoc)
	 * @see dcraft.struct.Struct#isBlank()
	 */
	@Override
	public boolean isEmpty() {
		return (this.items.size() == 0);
	}
	
	/* (non-Javadoc)
	 * @see dcraft.struct.builder.ICompositeOutput#toBuilder(dcraft.struct.builder.ICompositeBuilder)
	 */
	@Override
	public void toBuilder(ICompositeBuilder builder) throws BuilderStateException {
		builder.startList();
		
		for (Object o : this.items) 
			builder.value(o);
		
		builder.endList();
	}

	/**
	 * Attempt to add items to the list, but if there is a schema the items
	 * must match the schema.
	 * 
	 * @param items to add
	 * @return log of the result of the call (check hasErrors)
	 */
	public ListStruct withItem(Object... items) {
		this.with(items);
		
		return this;
	}
	
	public ListStruct with(Object... items) {
		for (Object o : items) {
			Object value = o;
			Struct svalue = null;
			
			if (value instanceof ICompositeBuilder)
				value = ((ICompositeBuilder)value).toLocal();
			
			if (this.explicitType != null) {
				TypeOptionsList itms = this.explicitType.getItems();
				
				if (itms != null) {
					Struct sv = itms.wrap(value);
					
					if (sv != null)
						svalue = sv;
				}
			}
			
			if (svalue == null) 
				svalue = Struct.objectToStruct(value); 
			
			this.items.add(svalue);
		}
		
		return this;
	}
	
	public ListStruct withCollection(Collection<? extends Object> coll) {
		if (coll != null) {
			for (Object o : coll)
				this.with(o);        // extra slow, enhance TODO
		}
		
		return this;
	}
	
	public ListStruct withCollection(ListStruct coll) {
		for (Struct o : coll.items())
			this.with(o);		// extra slow, enhance TODO
		
		return this;
	}

	public void replaceItem(int i, Struct o) {
		if (i < this.items.size())
			this.items.set(i, o);
	}
	
	/**
	 * 
	 * @return collection of all the items the list holds
	 */
	public Iterable<Struct> items() {
		return this.items;
	}
	
	// TODO support .items in Groovy
	
	/**
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return the struct for that field
	 */
	public Struct getItem(int idx) {
		if ((idx >= this.items.size()) || (idx < 0))
			return null;
		
		return this.items.get(idx);
	}
	
	public Object getAt(int idx) {
		if ((idx >= this.items.size()) || (idx < 0))
			return null;
		
		Struct v = this.items.get(idx);
		
		if (v == null)
			return null;
		
		if (v instanceof CompositeStruct)
			return v;
		
		return ((ScalarStruct) v).getGenericValue();
	}
	
	/**
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return true if an item is at that position
	 */
	public boolean hasItem(int idx) {
		if (idx >= this.items.size())
			return false;
		
		return true;
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Integer (DivConq thinks of integers as 64bit)
	 */
	public Long getItemAsInteger(int idx) {
		return Struct.objectToInteger(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as BigInteger 
	 */
	public BigInteger getItemAsBigInteger(int idx) {
		return Struct.objectToBigInteger(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as BigDecimal
	 */
	public BigDecimal getItemAsDecimal(int idx) {
		return Struct.objectToDecimal(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Boolean
	 */
	public Boolean getItemAsBoolean(int idx) {
		return Struct.objectToBoolean(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Datetime
	 */
	public ZonedDateTime getItemAsDateTime(int idx) {
		return Struct.objectToDateTime(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Datetime
	 */
	public BigDateTime getItemAsBigDateTime(int idx) {
		return Struct.objectToBigDateTime(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Date
	 */
	public LocalDate getItemAsDate(int idx) {
		return Struct.objectToDate(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Time
	 */
	public LocalTime getItemAsTime(int idx) {
		return Struct.objectToTime(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as String
	 */
	public String getItemAsString(int idx) {
		return Struct.objectToString(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Binary
	 */
	public Memory getItemAsBinary(int idx) {
		return Struct.objectToBinary(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as CompositeStruct
	 */
	public CompositeStruct getItemAsComposite(int idx) {
		return Struct.objectToComposite(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as RecordStruct
	 */
	public RecordStruct getItemAsRecord(int idx) {
		return Struct.objectToRecord(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as ListStruct
	 */
	public ListStruct getItemAsList(int idx) {
		return Struct.objectToList(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Struct
	 */
	public Struct getItemAsStruct(int idx) {
		return Struct.objectToStruct(this.getItem(idx));
	}
	
	/**
	 * Unlike getItem, this returns the value (inner) rather than struct wrapping 
	 * the value.
	 * 
	 * @param idx position in list of the item desired (0 based)
	 * @return field's "inner" value as Xml (will parse if value is string)
	 */
	public XElement getItemAsXml(int idx) {
		return Struct.objectToXml(this.getItem(idx));
	}
	
	/**
	 * @param idx position in list of the item desired (0 based)
	 * @return true if item does not exist or if item is string and its value is empty 
	 */
	public boolean isItemEmpty(int idx) {
		if (idx >= this.items.size())
			return true;
		
		Object o = this.items.get(idx);
		
		if (o == null)
			return true;
		
		if (o instanceof CharSequence)
			return o.toString().isEmpty();
		
		return false;
	}
	
	/**
	 * @return number of items in this list
	 */
	public int size() {
		return this.items.size();
	}

	// support .size in groovy
	public int getSize() {
		return this.items.size();
	}

	/**
	 * @param idx position in list of the item to remove from list
	 */
	public void removeItem(int idx) {		
		if (idx >= this.items.size())
			return;
		
		this.items.remove(idx);
	}

	/*
	 * @param idx position in list of the item to remove from list
	 */
	public void removeItem(Struct itm) {
		// TODO dispose
		//Struct old = this.items.get(itm);
		
		//if (old != null)
		//	old.dispose();
		
		this.items.remove(itm);
	}

    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	ListStruct nn = (ListStruct)n;
    	
   		nn.withCollection(this.items);
    }
    
	@Override
	public Struct deepCopy() {
		ListStruct cp = new ListStruct();
		this.doCopy(cp);
		return cp;
	}

	/**
	 * 
	 * @return schema for the primary/default data type of the list items
	 */
	public DataType getChildType() {
		if (this.explicitType != null) 
			return this.explicitType.getPrimaryItemType();
		
		return null;
	}

	@Override
	public void clear() {
		this.items.clear();
	}

	@Override
	public boolean checkLogic(IParentAwareWork stack, XElement source) throws OperatingContextException {
		if (source.hasAttribute("Contains")) {
			Struct other = StackUtil.refFromElement(stack, source, "Contains", true);

			if (this.items.contains(other))
				return true;

			if ((other instanceof StringStruct) && ! other.isEmpty()) {
				String[] options = other.toString().split(",");

				for (String opt : options) {
					if (this.items.contains(StringStruct.of(opt)))
						return true;
				}
			}

			return false;
		}

		return false;
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Set".equals(code.getName())) {
			this.clear();
			
			String json = StackUtil.resolveValueToString(stack, code.getText());
			
			if (StringUtil.isNotEmpty(json)) {
				ListStruct pjson = (ListStruct) CompositeParser.parseJson(" [ " + json + " ] ");

				for (Struct s : pjson.items())
					this.items.add(s);
			}
			
			// TODO else check for Xml or Yaml
			
			return ReturnOption.CONTINUE;
		}
		else if ("AddItem".equals(code.getName())) {
			Struct sref = StackUtil.refFromElement(stack, code, "Value", true);
			this.withItem(sref);
			return ReturnOption.CONTINUE;
		}
		else if ("AddAll".equals(code.getName())) {
			Struct sref = StackUtil.refFromElement(stack, code, "Value", true);
			if (sref instanceof ListStruct)
				this.withCollection((ListStruct) sref);
			else
				Logger.error("Unable to add to list, invalid data type.");

			return ReturnOption.CONTINUE;
		}
		else if ("RemoveItem".equals(code.getName())) {
			long idx = StackUtil.intFromElement(stack, code, "Index", -1);
			
			if (idx > -1)
				this.removeItem((int) idx);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Clear".equals(code.getName())) {
			this.clear();
			
			return ReturnOption.CONTINUE;
		}
		else if ("Sort".equals(code.getName())) {
			String field = StackUtil.stringFromElement(stack, code, "ByField");
			boolean descending = StackUtil.boolFromElement(stack, code, "Desc", false);

			this.sortRecords(field, descending);
			
			return ReturnOption.CONTINUE;
		}
		else if ("Join".equals(code.getName())) {
			String delim = StackUtil.stringFromElement(stack, code, "Delim", ",");
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				StringStruct res = StringStruct.ofEmpty();
				
				boolean first = true;
				
				for (Struct o : this.items) {
					if (first)
						first = false;
					else
						res.append(delim);
					
					if (o != null)
						res.append(Struct.objectToString(o));
				}
				
				StackUtil.addVariable(stack, result, res);
			}
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}

	public List<String> toStringList() {
		List<String> nlist = new ArrayList<>();
		
		for (Struct s : this.items) 
			if (s != null)
				nlist.add(s.toString());
		
		return nlist;
	}

	public boolean contains(Struct v) {
		return this.items.contains(v);
	}

	public String join(String delim) {
		boolean first = true;
		StringBuilder res = new StringBuilder();

		for (Struct o : this.items) {
			if (first)
				first = false;
			else
				res.append(delim);

			if (o != null)
				res.append(Struct.objectToString(o));
		}

		return res.toString();
	}

	public List<Object> toObjectList() {
		List<Object> nlist = new ArrayList<>();
		
		for (Struct s : this.items) 
			if (s instanceof ScalarStruct)
				nlist.add(((ScalarStruct)s).getGenericValue());
		
		return nlist;
	}
	
	public void sort(Comparator<Struct> comparator) {
		this.items.sort(comparator);
	}
	
	public void sortRecords(String field, boolean descending) {
		if (StringUtil.isEmpty(field))
			return;
		
		this.items.sort(new Comparator<Struct>() {
			@Override
			public int compare(Struct o1, Struct o2) {
				Struct fld1 = ((RecordStruct)o1).getField(field);
				Struct fld2 = ((RecordStruct)o2).getField(field);
				
				if ((fld1 == null) && (fld2 == null))
					return 0;
				
				if (fld1 == null)
					return descending ? -1 : 1;
				
				if (fld2 == null)
					return descending ? 1 : -1;
				
				if ((fld1 instanceof ScalarStruct) && (fld2 instanceof ScalarStruct)) {
					int cp = ((ScalarStruct) fld1).compareTo(fld2);
					
					if (descending)
						cp = -cp;
					
					return cp;
				}
				
				return 0;
			}
		});
	}

	@Override
	public Iterator<Object> iterator() {
		return new Iterator<Object>() {
			protected int cnt = 0;
			
			@Override
			public boolean hasNext() {
				return (cnt < ListStruct.this.items.size());
			}

			@Override
			public Object next() {
				Object o = ListStruct.this.getAt(cnt);
				
				this.cnt++;
				
				return o;
			}
		};
	}
}
