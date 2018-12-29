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
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;

public class SearchIndex implements IStoredProc {
	/*
	 ;  Vault           name
	 ;  Path            path in vault
	 ;	Term			search term
	 ;  Depth			levels to search
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
		
		Term termfilter = new Term();
		
		termfilter.init(params);

		IFilter filter = new StandardAccess()
					.withNested(
							termfilter.withNested(
							new BasicFilter() {
								@Override
								public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
									if (file.getFieldAsBooleanOrFalse("Public")) {
										file.with("Path", path);

										RecordStruct rcache = (RecordStruct) scope.queryVariable("_RecordCache");

										if (rcache != null) {
											file.with("Score", rcache.getFieldAsInteger("TermScore"));
										}

										try {
											out.value(file);
										}
										catch (BuilderStateException x) {

										}
									}

									return ExpressionResult.accepted();
								}
							})
					);
		
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
			Logger.error("Issue with select direct: " + x);
		}
		
		callback.returnEmpty();
	}
}
