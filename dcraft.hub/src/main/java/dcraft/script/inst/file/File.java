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
package dcraft.script.inst.file;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.OperationsInstruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.xml.XElement;

public class File extends OperationsInstruction {
	static public File tag() {
		File el = new File();
		el.setName("dcs.File");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return File.tag();
	}
	
	// like BlockInstruction return DONE means done with this block/ops - not done with Script as with other instructions
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			String name = StackUtil.stringFromSource(state, "Name");

			if (this.hasNotEmptyAttribute("In")) {
				Struct var3 = StackUtil.refFromSource(state, "In");
				
				// TODO support FileSystemDriver, FileSystemFile too
				
				if (var3 == null) {
					Logger.errorTr(522);
					return ReturnOption.DONE;
				}
				
				// if string then implied this is a Vault
				if (var3 instanceof StringStruct) {
					Vault vault = OperationContext.getOrThrow().getSite().getVault(((StringStruct) var3).getValueAsString());
					
					if (vault == null) {
						Logger.errorTr(522);
						return ReturnOption.DONE;
					}
					
					String path = StackUtil.stringFromSource(state, "Path");

					vault.getFileStore().getFileDetail(CommonPath.from(path), new OperationOutcome<FileStoreFile>() {
						@Override
						public void callback(FileStoreFile result) throws OperatingContextException {
							StackUtil.addVariable(state, name, result);

							state.getStore().with("Second", true);
							state.setState(ExecuteState.RESUME);
							
							OperationContext.getAsTaskOrThrow().resume();
						}
					});
					
					return ReturnOption.AWAIT;
				}
			}

			Logger.errorTr(520);
			return ReturnOption.DONE;
		}
		else if (state.getStore().hasField("Second")) {
			state.getStore().removeField("Second");

			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}

		return ReturnOption.DONE;
	}
}
