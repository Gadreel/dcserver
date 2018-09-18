package dcraft.db.request.query;

import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * A database foreign key field to use with a subquery.
 * 
 * @author Andy
 *
 */
public class SelectGroup extends SelectFields implements ISelectField {
	protected RecordStruct subquery = new RecordStruct();
	
	public SelectGroup with(String v) {
		this.subquery.with("Field", v);		
		return this;
	}
	
	public SelectGroup withName(String v) {
		this.subquery.with("Name", v);		
		return this;
	}
	
	public SelectGroup withKeyName(String v) {
		this.subquery.with("KeyName", v);
		return this;
	}
	
	public SelectGroup withSelect(SelectFields flds) {
		this.fields = flds.fields;
		return this;
	}

	@Override
	public SelectGroup withSelect(ISelectField... items) {
		super.withSelect(items);		
		return this;
	}
	
	@Override
	public Struct getParams() {
		this.subquery.with("Select", this.fields);
		
		return this.subquery;
	}
}
