package dcraft.db.request.query;

import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * A database foreign key field to use with a subquery.
 * 
 * @author Andy
 *
 */
public class SelectReverseSubquery extends SelectFields implements ISelectField {
	protected RecordStruct subquery = new RecordStruct();
	
	public SelectReverseSubquery with(String v) {
		this.subquery.with("Field", v);		
		return this;
	}
	
	public SelectReverseSubquery withKeyField(String v) {
		this.subquery.with("KeyField", v);		
		return this;
	}
	
	public SelectReverseSubquery withName(String v) {
		this.subquery.with("Name", v);		
		return this;
	}
	
	public SelectReverseSubquery withTable(String v) {
		this.subquery.with("Table", v);		
		return this;
	}
	
	public SelectReverseSubquery withSelect(SelectFields flds) {
		this.fields = flds.fields;
		return this;
	}

	@Override
	public SelectReverseSubquery withSelect(ISelectField... items) {
		super.withSelect(items);		
		return this;
	}
	
	@Override
	public Struct getParams() {
		this.subquery.with("Select", this.fields);
		
		return this.subquery;
	}
}
