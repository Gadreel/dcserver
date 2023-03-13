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
import dcraft.sql.SqlNull;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.*;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class SqlExecuteInsert extends Instruction {
	static public SqlExecuteInsert tag() {
		SqlExecuteInsert el = new SqlExecuteInsert();
		el.setName("dcdb.SqlExecuteInsert");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SqlExecuteInsert.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String db = StackUtil.stringFromSourceClean(state, "Database");
			String result = StackUtil.stringFromSourceClean(state, "Result");
			String query = StackUtil.resolveValueToString(state, state.getInstruction().selectFirstText("Statement"), true);

			if (StringUtil.isNotEmpty(db)) {
				SqlWriter writer = SqlWriter.fields();

				SqlExecute.addParams(state, writer);

				try (SqlConnection conn = SqlUtil.getConnection(db)) {
					FuncResult<Long> results = conn.executeInsertFreestyle(query, writer.toParams());

					if (StringUtil.isNotEmpty(result))
						StackUtil.addVariable(state, result, IntegerStruct.of(results.getResult()));
				}
				catch (Exception x) {
					Logger.error("Unable to execute insert: " + query + " - " + x);
				}
			}
			else {
				Logger.error("Unable to execute, missing database or result parameters.");
			}
		}

		return ReturnOption.CONTINUE;
	}
}
