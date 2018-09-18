package dcraft.script.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableProvider;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.script.work.BlockWork;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.tenant.WebFindResult;
import dcraft.util.IOUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Include extends Instruction {
	static public Include tag() {
		Include el = new Include();
		el.setName("dcs.Include");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Include.tag();
	}

	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		// remove the children
		this.clearChildren();

		// find and include the fragment file
		if (this.hasAttribute("Path")) {
			String tpath = StackUtil.stringFromSource(state, "Path");

			CommonPath pp = new CommonPath(tpath + ".dcs.xml");

			Script script = ResourceHub.getResources().getScripts().loadScript(pp);

			// TODO require vs include - only error on require

			if (script != null) {
				XElement layout = script.getXml();

				// find position of this instruction
				IParentAwareWork pw = state.getParent();
				XElement pel = ((InstructionWork) pw).getInstruction();
				int pos = pel.findIndex(state.getInstruction());

				Collection<XNode> children = layout.getChildren();
				int i = 1; // after

				for (XNode node : children) {
					pel.add(pos + i, node);
					i++;
				}

				//System.out.println("code: " + pel.toPrettyString());
			}
		}

		return ReturnOption.CONTINUE;
	}
}