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
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SqlInsertWriter extends Instruction {
	static public SqlInsertWriter tag() {
		SqlInsertWriter el = new SqlInsertWriter();
		el.setName("dcdb.SqlInsertWriter");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SqlInsertWriter.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String db = StackUtil.stringFromSourceClean(state, "Database");
			String table = StackUtil.stringFromSourceClean(state, "Table");
			String result = StackUtil.stringFromSourceClean(state, "Result");

			if (StringUtil.isNotEmpty(db) && StringUtil.isNotEmpty(table)) {
				SqlWriter writer = SqlWriter.insert(table);

				SqlExecute.addParams(state, writer);

				try (SqlConnection conn = SqlUtil.getConnection(db)) {
					FuncResult<Long> results = conn.executeInsertReturnId(writer);

					if (StringUtil.isNotEmpty(result))
						StackUtil.addVariable(state, result, IntegerStruct.of(results.getResult()));
				}
				catch (Exception x) {
					Logger.error("Unable to insert into table: " + table + " - " + x);
				}
			}
			else {
				Logger.error("Unable to insert into table, missing database, table parameters.");
			}
		}

		return ReturnOption.CONTINUE;
	}

}
