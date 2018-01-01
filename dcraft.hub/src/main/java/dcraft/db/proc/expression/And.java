package dcraft.db.proc.expression;

import dcraft.db.proc.IExpression;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class And implements IExpression {
	protected ListStruct children = null;
	
	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		this.children = where.getFieldAsList("Children");
		
		if (this.children == null)
			return;
		
		for (Struct s : this.children.items())
			ExpressionUtil.initExpression(table, Struct.objectToRecord(s));
	}
	
	@Override
	public boolean check(TablesAdapter adapter, String id, BigDateTime when, boolean historical) throws OperatingContextException {
		if (this.children == null)
			return false;
		
		for (Struct s : children.items()) {
			RecordStruct where = Struct.objectToRecord(s);
			
			IExpression expression = (IExpression) where.getFieldAsAny("_Expression");
			
			if (expression == null) {
				Logger.error("bad expression");
				return false;
			}
			
			if (! expression.check(adapter, id, when, historical))
				return false;
		}
		
		return true;
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		ListStruct children = ListStruct.list();
		
		clause.with("Children", children);
		
		//for (XElement child : code.selectAll("*")) {
			Query.addWhere(children, state, code);
		//}
	}
}
