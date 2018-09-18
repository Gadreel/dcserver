package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

public interface ICMSAware {
	// convert to standard form for cms editor
	default void canonicalize() throws OperatingContextException {
		// leave as is by default
	}
	
	default void setEditBadges(InstructionWork state) throws OperatingContextException {
		// no special badges by default
	}
	
	default boolean canEdit(InstructionWork state) throws OperatingContextException {
		return true;		// no special limits beyond normal
	}
	
	// return true only if you can apply the command
	default boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		return false;
	}
}
