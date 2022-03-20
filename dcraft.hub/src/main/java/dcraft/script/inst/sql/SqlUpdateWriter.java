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
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class SqlUpdateWriter extends Instruction {
	static public SqlUpdateWriter tag() {
		SqlUpdateWriter el = new SqlUpdateWriter();
		el.setName("dcdb.SqlUpdateWriter");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SqlUpdateWriter.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String db = StackUtil.stringFromSourceClean(state, "Database");
			String table = StackUtil.stringFromSourceClean(state, "Table");
			Long id = StackUtil.intFromSource(state, "Id");
			String result = StackUtil.stringFromSourceClean(state, "Result");

			if (StringUtil.isNotEmpty(db) && StringUtil.isNotEmpty(table) && (id != null)) {
				SqlWriter writer = SqlWriter.update(table, id);

				SqlExecute.addParams(state, writer);

				try (SqlConnection conn = SqlUtil.getConnection(db)) {
					FuncResult<Long> results = conn.executeWrite(writer);

					if (StringUtil.isNotEmpty(result))
						StackUtil.addVariable(state, result, IntegerStruct.of(results.getResult()));
				}
				catch (Exception x) {
					Logger.error("Unable to update table: " + table + " : " + id + " - " + x);
				}
			}
			else {
				Logger.error("Unable to update table, missing database, table or id parameters.");
			}
		}

		return ReturnOption.CONTINUE;
	}
}
