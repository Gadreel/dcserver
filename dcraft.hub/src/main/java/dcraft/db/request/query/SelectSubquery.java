package dcraft.db.request.query;

import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * A database foreign key field to use with a subquery.
 * 
 * @author Andy
 *
 */
public class SelectSubquery extends SelectFields implements ISelectField {
	protected RecordStruct subquery = new RecordStruct();
	
	public SelectSubquery with(String v) {
		this.subquery.with("Field", v);		
		return this;
	}
	
	public SelectSubquery withName(String v) {
		this.subquery.with("Name", v);		
		return this;
	}
	
	public SelectSubquery withSelect(SelectFields flds) {
		this.fields = flds.fields;
		return this;
	}

	@Override
	public SelectSubquery withSelect(ISelectField... items) {
		super.withSelect(items);		
		return this;
	}
	
	@Override
	public BaseStruct getParams() {
		this.subquery.with("Select", this.fields);
		
		return this.subquery;
	}
}
