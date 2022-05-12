package dcraft.db.fileindex;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class BasicFilter implements IFilter {
	protected IFilter nested = null;
	
	public BasicFilter withNested(IFilter v) {
		this.nested = v;
		return this;
	}

	public IFilter shiftNested(IFilter v) {
		v.withNested(this.nested);

		this.nested = v;
		return this;
	}

	public ExpressionResult nestOrAccept(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
		if (this.nested != null)
			return this.nested.check(adapter, scope, vault, path, file);
		
		return ExpressionResult.ACCEPTED;
	}
	
	@Override
	public void init(RecordStruct filter) throws OperatingContextException {
	
	}
	
	@Override
	public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
		return ExpressionResult.accepted();
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct filter) throws OperatingContextException {
	
	}
}
