package dcraft.script.inst.sql;

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
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class SqlSelect extends Instruction {
	static public SqlSelect tag() {
		SqlSelect el = new SqlSelect();
		el.setName("dcdb.SqlSelect");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SqlSelect.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String db = StackUtil.stringFromSourceClean(state, "Database");
			String result = StackUtil.stringFromSourceClean(state, "Result");
			String query = StackUtil.resolveValueToString(state, state.getInstruction().selectFirstText("Statement"), true);

			if (StringUtil.isNotEmpty(db) && StringUtil.isNotEmpty(result)) {
				SqlWriter writer = SqlWriter.fields();

				SqlExecute.addParams(state, writer);

				try (SqlConnection conn = SqlUtil.getConnection(db)) {
					ListStruct results = conn.getResults(query, writer.toParams());

					StackUtil.addVariable(state, result, results);
				}
				catch (Exception x) {
					Logger.error("Unable to select: " + query + " - " + x);
				}
			}
			else {
				Logger.error("Unable to select, missing database or result parameters.");
			}
		}

		return ReturnOption.CONTINUE;
	}

}
