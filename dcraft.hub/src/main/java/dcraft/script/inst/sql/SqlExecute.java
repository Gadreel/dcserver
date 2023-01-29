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
import dcraft.xml.XNode;

import java.util.List;

public class SqlExecute extends Instruction {
	static public SqlExecute tag() {
		SqlExecute el = new SqlExecute();
		el.setName("dcdb.SqlExecute");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return SqlExecute.tag();
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
					FuncResult<Long> results = conn.executeUpdateFreestyle(query, writer.toParams());

					if (StringUtil.isNotEmpty(result))
						StackUtil.addVariable(state, result, IntegerStruct.of(results.getResult()));
				}
				catch (Exception x) {
					Logger.error("Unable to execute: " + query + " - " + x);
				}
			}
			else {
				Logger.error("Unable to execute, missing database or result parameters.");
			}
		}

		return ReturnOption.CONTINUE;
	}

	/*
	static public Object toParam(InstructionWork state, XElement param, RecordStruct source) throws OperatingContextException {
		if (param.hasNotEmptyAttribute("Name") && (source != null)) {
			String name = StackUtil.resolveValueToString(state, param.attr("Name"), true);

			// if the field is not present, do not set to NULL
			if ((source != null) && !source.hasField(name))
				continue;

			Object ref = (source != null)
					? source.getField(name)
					: StackUtil.resolveReference(state, param.getAttribute("Value"));

			return SqlExecute.toParam(state, ref, param);
		}
		else {
			Object ref = (source != null)
					? source.getField(name)
					: StackUtil.resolveReference(state, param.getAttribute("Value"));

			return SqlExecute.toParam(state, ref, param);
		}
	}

	 */

	static public void addParams(InstructionWork state, SqlWriter writer) throws OperatingContextException {
		List<XElement> paramx = state.getInstruction().selectAll("Param");

		BaseStruct paramAttr = StackUtil.queryVariable(state, StackUtil.stringFromSource(state, "Params"));

		if (paramAttr instanceof XElement) {
			paramx = ((XElement) paramAttr).selectAll("Param");
		}
		else if (paramAttr instanceof AnyStruct) {
			paramx = ((XElement) ((AnyStruct) paramAttr).getValue()).selectAll("Param");
		}

		CompositeStruct source = Struct.objectToComposite(StackUtil.refFromSource(state, "DataSource", true));
		RecordStruct recsource = Struct.objectToRecord(source);
		ListStruct listsource = Struct.objectToList(source);

		// TODO check to see if Type is available - if so we could autogenerate the Type for Params
		// source.getType();

		for (int i = 0; i < paramx.size(); i++) {
			XElement param = paramx.get(i);

			Object ref = null;
			String name = null;

			if (param.hasNotEmptyAttribute("Name")) {
				name = StackUtil.resolveValueToString(state, param.attr("Name"), true);

				// if the field is not present, do not set to NULL
				if ((recsource != null) && ! recsource.hasField(name))
					continue;

				ref = (recsource != null)
						? recsource.getField(name)
						: StackUtil.resolveReference(state, param.getAttribute("Value"));
			}
			else {
				// if the field is not present, do not set to NULL
				if ((listsource != null) && (i >= listsource.size()))
					continue;

				ref = (listsource != null)
					? listsource.getAt(i)
					: StackUtil.resolveReference(state, param.getAttribute("Value"));
			}

			Object value = SqlExecute.toParam(state, ref, param);

			if (value != null)
				writer.with(name, value);
		}
	}

	static public Object toParam(InstructionWork state, Object ref, XElement param) throws OperatingContextException {
		if (ref instanceof NullStruct)
			ref = null;

		String format = StackUtil.resolveValueToString(state, param.attr("Format"), true);

		if (StringUtil.isNotEmpty(format)) {
			ref = DataUtil.format(ref, format);
		}

		// sometimes we come in as a datatype not quite like the table (such as off a Template) when we should be a number or datetime
		// we may come in as boolean but then convert to integer

		String nullType = StackUtil.resolveValueToString(state, param.attr("Type"), true);

		if (StringUtil.isNotEmpty(nullType)) {
			if ("Integer".equals(nullType) || "Long".equals(nullType))
				ref = Struct.objectToInteger(ref);
			else if ("DateTime".equals(nullType))
				ref = Struct.objectToDateTime(ref);
			else if ("Double".equals(nullType))
				ref = Struct.objectToDecimal(ref);
			else if ("BigDecimal".equals(nullType))
				ref = Struct.objectToDecimal(ref);
			else if ("VarChar".equals(nullType))
				ref = Struct.objectToString(ref);
			else if ("Text".equals(nullType))
				ref = Struct.objectToString(ref);
		}

		// if null, try to switch to a SQL approved Null type

		if ((ref == null) && StringUtil.isNotEmpty(nullType)) {
			if ("Integer".equals(nullType) || "Long".equals(nullType))
				ref = SqlNull.Long;
			else if ("DateTime".equals(nullType))
				ref = SqlNull.DateTime;
			else if ("Double".equals(nullType))
				ref = SqlNull.Double;
			else if ("VarChar".equals(nullType))
				ref = SqlNull.VarChar;
			else if ("BigDecimal".equals(nullType))
				ref = SqlNull.BigDecimal;
			else if ("Text".equals(nullType))
				ref = SqlNull.Text;
		}

		// plain old "null" is not allowed in SQL parameters

		if (ref == null)
			return null;

		// special handling for LIKE

		if (StackUtil.boolFromElement(state, param, "EscLike")) {
			// Name LIKE ? ESCAPE '!'
			// "%" + SqlUtil.escLike(domain) + "%"

			ref = StringStruct.of(
					StackUtil.resolveValueToString(state, param.attr("Left"), true)
							+ SqlUtil.escLike(ref.toString())
							+ StackUtil.resolveValueToString(state, param.attr("Right"), true)
			);
		}

		return ref;
	}
}
