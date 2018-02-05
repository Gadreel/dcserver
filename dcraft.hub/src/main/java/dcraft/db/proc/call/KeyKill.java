package dcraft.db.proc.call;

import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.HexUtil;

public class KeyKill implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();

		ListStruct keys = params.getFieldAsList("Keys");
		
		byte[] basekey = null;
		
		for (Struct ss : keys.items()) 
			basekey =  ByteUtil.combineKeys(basekey, HexUtil.decodeHex(ss.toString())); 

		request.getInterface().kill(basekey);
		
		callback.returnEmpty();
	}
}
