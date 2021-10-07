/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.script.inst.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.JavaValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.lib.db.JdbcDriverContext;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.StringStream;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.twilio.SmsUtil;
import dcraft.log.Logger;
import dcraft.quercus.DCQuercusEngine;
import dcraft.quercus.DCQuercusResult;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.util.IOUtil;
import dcraft.util.PeopleUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.php.ExportVarFunc;
import dcraft.util.php.PhpUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import z.tws.php.QuercusUtil;

import java.io.IOException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Consumer;

public class PHPScript extends Instruction {
	static public PHPScript tag() {
		PHPScript el = new PHPScript();
		el.setName("dcs.PHPScript");
		return el;
	}

	@Override
	public XElement newNode() {
		return PHPScript.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String features = StackUtil.stringFromSource(state, "Features", "Basic").toLowerCase();

			String code = StackUtil.resolveValueToString(state, state.getInstruction().getText());
			String precode = "";

			if (features.contains("basic"))
				precode += "error_reporting(E_ALL);\n"
						+ "ini_set('display_errors', 'on');\n"
						+ "chdir($dc_workingpath); \n\n";

			// Include the bootstrap for setting up WordPress environment
			if (features.contains("wp"))
				precode += "require('wp-config.php');\n\n";

			if (features.contains("basic")) {
				String tzname = TimeUtil.zoneInContext().getDisplayName(TextStyle.NARROW, Locale.ENGLISH);

				precode += "if (function_exists('date_default_timezone_set'))\n"
						+ "date_default_timezone_set('" + tzname + "');\n\n";
			}

			// TODO support a standard selection of profiles for PHP before and after

			code = "<?php\n" + precode + "?>\n\n" + code;

			Struct params = StackUtil.refFromSource(state, "Params");
			String resultname = StackUtil.stringFromSource(state, "Result");

			DCQuercusEngine engine = QuercusUtil.getEngine();

			ReadStream filein = StringStream.open(code);

			DCQuercusResult result = engine.dc_execute(filein, new Consumer<Env>() {
				@Override
				public void accept(Env env) {
                    env.addFunction("dc_export_var", new ExportVarFunc(state));

                    if (params != null)
						env.setGlobalValue("dc_params", PhpUtil.structToValue(env, params));

					try {
						env.setGlobalValue("dc_workingpath", env.createString(OperationContext.getOrThrow().getTenant().resolvePath("/php").toString()));
					} catch (OperatingContextException x) {
						System.out.println("cannot set working path: " + x);
					}
				}
			});

			//System.out.println("output: " + result.output);

			if (StringUtil.isNotEmpty(resultname)) {
				/* TODO convert result to "var" and publish
				StackUtil.addVariable(state, resultname, var);

				((OperationsWork) state).setTarget(var);
				 */
			}

			if (StringUtil.isNotEmpty(result.output)) {
				XElement layout = ScriptHub.parseInstructions("<div>" + result.output + "</div>");

				// find position of this instruction
				IParentAwareWork pw = state.getParent();
				XElement pel = ((InstructionWork) pw).getInstruction();
				int pos = pel.findIndex(state.getInstruction());

				int i = 1; // after

				for (XNode node : layout.getChildren()) {
					pel.add(pos + i, node);
					i++;
				}
			}
		}

		return ReturnOption.CONTINUE;
	}
}
