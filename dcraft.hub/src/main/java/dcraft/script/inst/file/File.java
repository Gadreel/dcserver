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
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.OperationsInstruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.stream.StreamUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.FileUtil;
import dcraft.util.Memory;
import dcraft.xml.XElement;

import java.nio.file.Path;

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

			if (this.hasNotEmptyAttribute("In") || this.hasNotEmptyAttribute("Of")) {
				Struct var3 = StackUtil.refFromSource(state, "In");
				
				if (var3 == null)
					var3 = StackUtil.refFromSource(state, "Of");
				
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

					vault.getFileDetail(CommonPath.from(path), null, new OperationOutcome<FileDescriptor>() {
						@Override
						public void callback(FileDescriptor result) throws OperatingContextException {
							StackUtil.addVariable(state, name, result);
							
							((OperationsWork) state).setTarget(result);

							state.getStore().with("Second", true);
							state.setState(ExecuteState.RESUME);
							
							OperationContext.getAsTaskOrThrow().resume();
						}
					});
					
					return ReturnOption.AWAIT;
				}
				else if (var3 instanceof BinaryStruct) {
					String path = StackUtil.stringFromSource(state, "Path", "/temp.bin");

					Memory mem = ((BinaryStruct)var3).getValue();
					mem.setPosition(0);

					MemoryStoreFile result = MemoryStoreFile.of(CommonPath.from(path))
							.with(mem);
					
					StackUtil.addVariable(state, name, result);
					
					((OperationsWork) state).setTarget(result);
					
					state.getStore().with("Second", true);
					return ReturnOption.CONTINUE;
				}
			}
			else if (this.getAttributeAsBooleanOrFalse("Temp")) {
				String ext = StackUtil.stringFromSource(state, "TempExt", "bin");

				Path tempfile = FileUtil.allocateTempFile(ext);

				//String path = StackUtil.stringFromSource(state, "Path", "/temp.bin");

				LocalStoreFile result = StreamUtil.localFile(tempfile);

				StackUtil.addVariable(state, name, result);

				((OperationsWork) state).setTarget(result);

				state.getStore().with("Second", true);
				return ReturnOption.CONTINUE;
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
