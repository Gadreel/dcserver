package dcraft.db.proc;

import dcraft.db.proc.filter.Max;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class BasicFilter implements IFilter {
	protected IFilter nested = null;
	
	public BasicFilter withNested(IFilter v) {
		this.nested = v;
		return this;
	}
	
	@Override
	public void init(String table, RecordStruct filter) {
	
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct filter) {
	
	}
}
