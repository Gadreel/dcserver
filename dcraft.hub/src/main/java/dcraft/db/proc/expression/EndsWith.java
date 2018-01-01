package dcraft.db.proc.expression;

import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;

import java.util.List;

public class EndsWith extends TwoExpression {
	@Override
	public boolean check(TablesAdapter adapter, String id, BigDateTime when, boolean historical) throws OperatingContextException {
		List<byte[]> data = adapter.getRawIndex(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, when, historical);
		
		if ((this.values == null) && (data == null))
			return true;
		
		// rule out one being null
		if ((this.values == null) || (data == null))
			return false;
		
		for (int i = 0; i < data.size(); i++) {
			for (int i2 = 0; i2 < this.values.size(); i2++) {
				if (ByteUtil.dataEndsWith(data.get(i), this.values.get(i2)))
					return true;
			}
		}
		
		return false;
	}
}
