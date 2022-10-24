package dcraft.db.proc.call;

import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.HexUtil;

public class KeySet implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();

		ListStruct keys = params.getFieldAsList("Keys");
		BaseStruct value = params.getField("Value");

		byte[] basekey = null;
		
		for (BaseStruct ss : keys.items())
			basekey =  ByteUtil.combineKeys(basekey, HexUtil.decodeHex(ss.toString())); 

		try {
			request.getInterface().set(basekey, value);
		}
		catch (DatabaseException x) {
			Logger.error("KeySetProc: Unable to set list: " + x);
		}

		callback.returnEmpty();
	}
}
