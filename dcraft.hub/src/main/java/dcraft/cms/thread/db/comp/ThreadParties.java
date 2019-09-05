package dcraft.cms.thread.db.comp;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;

public class ThreadParties implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{
		RecordStruct params = field.getFieldAsRecord("Params");
		
		boolean meToo = (params != null) ? params.getFieldAsBooleanOrFalse("MeToo") : false;
		boolean noOrigin = (params != null) ? params.getFieldAsBooleanOrFalse("NoOrigin") : false;
		
		try {
			out.startList();
			
			for (String name : ThreadUtil.formatParties(db, scope, id, meToo, noOrigin))
				out.value(name);
			
			out.endList();
		}
		catch (BuilderStateException x) {
			Logger.error("Unable to write db response");
		}
	}

}
