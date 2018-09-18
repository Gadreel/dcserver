package dcraft.filestore;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.function.Predicate;

public interface IFileCollection {
	void next(OperationOutcome<FileStoreFile> callback) throws OperatingContextException;
	void forEach(OperationOutcome<FileStoreFile> callback) throws OperatingContextException;
	IFileCollection withFilter(Predicate<FileStoreFile> v) throws OperatingContextException;
	
	// scripts
	ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException;
}
