package dcraft.db.proc.expression;

import dcraft.db.Constants;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.List;

public class Is extends OneExpression {
	@Override
	public boolean check(TablesAdapter adapter, String id, BigDateTime when, boolean historical) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, when, historical);
		
		if (data == null)
			return false;
		
		for (int i = 0; i < data.size(); i++) {
			byte[] d = data.get(i);
			
			if (d == null)
				continue;
			
			if (ByteUtil.compareKeys(d, Constants.DB_EMPTY_ARRAY) != 0)
				return true;
		}
		
		return false;
	}
}
