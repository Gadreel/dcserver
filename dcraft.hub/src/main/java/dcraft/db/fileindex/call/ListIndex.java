package dcraft.db.fileindex.call;

import dcraft.db.ICallContext;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.Filter.StandardAccess;
import dcraft.db.fileindex.Filter.Term;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;

public class ListIndex implements IStoredProc {
	/*
	 ;  Vault           name
	 ;  Path            path in vault
	 ;	Term			search term
	 ;	Locale			to search in
	 ;
	 ; Result
	 ;		List of records
	 */
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		Vault vault = OperationContext.getOrThrow().getSite().getVault(params.getFieldAsString("Vault"));
		
		if (vault == null) {
			Logger.error("Invalid vault name");
			callback.returnEmpty();
			return;
		}
		
		CommonPath path = params.isNotFieldEmpty("Path") ? CommonPath.from(params.getFieldAsString("Path")) : CommonPath.ROOT;
		long depth = params.getFieldAsInteger("Depth", -1);

		FileIndexAdapter adapter = FileIndexAdapter.of(request);
		
		ICompositeBuilder out = new ObjectBuilder();

		IVariableAware scope = OperationContext.getOrThrow();

		IFilter filter = new StandardAccess()
					.withNested(
							new BasicFilter() {
								@Override
								public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
									file.with("Path", path);

									try {
										out.value(file);
									}
									catch (BuilderStateException x) {
									
									}
									
									return ExpressionResult.accepted();
								}
							});
		
		try (OperationMarker om = OperationMarker.create()) {
			out.startList();
			
			adapter.traverseIndex(vault, path, (int) depth, scope, filter);
			
			out.endList();
			
			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with list index: " + x);
		}
		
		callback.returnEmpty();
	}
}
