package dcraft.db.fileindex.Filter;

import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.RecordScope;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class StandardAccess extends BasicFilter {
	static public StandardAccess standard() {
		return new StandardAccess();
	}
	
	@Override
	public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
		RecordScope rscope =  RecordScope.of(scope);
		
		if (! "Present".equals(file.getFieldAsString("State")))
			return ExpressionResult.rejected();
		
		if (file.getFieldAsBooleanOrFalse("Public"))
			return this.nestOrAccept(adapter, rscope, vault, path, file);
		
		ListStruct badges = file.getFieldAsList("Badges");
		
		if (badges != null) {
			for (int i = 0; i < badges.size(); i++) {
				if (OperationContext.getOrThrow().getUserContext().isTagged(badges.getItemAsString(i)))
					return this.nestOrAccept(adapter, rscope, vault, path, file);
			}
			
			return ExpressionResult.rejected();
		}

		return this.nestOrAccept(adapter, rscope, vault, path, file);
	}
}
