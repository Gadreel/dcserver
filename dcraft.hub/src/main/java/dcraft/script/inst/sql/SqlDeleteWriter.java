package dcraft.script.inst.sql;

import dcraft.hub.op.FuncResult;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SqlDeleteWriter extends Instruction {
	static public SqlDeleteWriter tag() {
		SqlDeleteWriter el = new SqlDeleteWriter();
		el.setName("dcdb.SqlDeleteWriter");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SqlDeleteWriter.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String db = StackUtil.stringFromSourceClean(state, "Database");
			String table = StackUtil.stringFromSourceClean(state, "Table");
			Long id = StackUtil.intFromSource(state, "Id");
			String result = StackUtil.stringFromSourceClean(state, "Result");

			if (StringUtil.isNotEmpty(db) && StringUtil.isNotEmpty(table) && (id != null)) {
				SqlWriter writer = SqlWriter.delete(table, id);

				try (SqlConnection conn = SqlUtil.getConnection(db)) {
					FuncResult<Long> results = conn.executeDelete(writer);

					if (StringUtil.isNotEmpty(result))
						StackUtil.addVariable(state, result, IntegerStruct.of(results.getResult()));
				}
				catch (Exception x) {
					Logger.error("Unable to delete from table: " + table + " : " + id + " - " + x);
				}
			}
			else {
				Logger.error("Unable to delete from table, missing database, table or id parameters.");
			}
		}

		return ReturnOption.CONTINUE;
	}

}
