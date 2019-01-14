package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IExpression;
import dcraft.db.proc.IFilter;
import dcraft.db.request.query.WhereAnd;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class And implements IExpression {
	/*
	static public And of(String table, WhereAnd expression) throws OperatingContextException {
		And obj = new And();
		
		obj.init(table, expression.getParams());
		
		return obj;
	}
	*/
	
	protected ListStruct children = null;
	protected IFilter nested = null;

	@Override
	public IFilter withNested(IFilter v) {
		this.nested = v;
		return this;
	}

	@Override
	public IFilter shiftNested(IFilter v) {
		v.withNested(this.nested);

		this.nested = v;
		return this;
	}

	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		this.children = where.getFieldAsList("Children");
		
		if (this.children == null)
			return;
		
		for (Struct s : this.children.items())
			ExpressionUtil.initExpression(table, Struct.objectToRecord(s));
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		if (this.children == null)
			return ExpressionResult.REJECTED;
		
		for (Struct s : children.items()) {
			RecordStruct where = Struct.objectToRecord(s);
			
			IExpression expression = (IExpression) where.getFieldAsAny("_Expression");
			
			if (expression == null) {
				Logger.error("bad expression");
				return ExpressionResult.HALT;
			}
			
			if (! expression.check(adapter, scope, table, id).accepted)
				return ExpressionResult.REJECTED;
		}
		
		return this.nestOrAccept(adapter, scope, table, id);
	}

	public ExpressionResult nestOrAccept(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
		if (this.nested != null)
			return this.nested.check(adapter, scope, table, val);

		return ExpressionResult.ACCEPTED;
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
