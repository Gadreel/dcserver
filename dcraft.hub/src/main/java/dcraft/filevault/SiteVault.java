package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

import java.util.ArrayList;
import java.util.List;

/*
 * restrict so /feeds, /files, /galleries and /vault do not show
 */
public class SiteVault extends FileStoreVault {
	
	@Override
	public void getFolderListing(FileDescriptor file, RecordStruct params, OperationOutcome<List<? extends FileDescriptor>> callback) throws OperatingContextException {
		this.fsd.getFolderListing(file.getPathAsCommon(), new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				boolean showHidden = OperationContext.getOrThrow().getUserContext().isTagged("Admin");
				
				List<FileDescriptor> files = new ArrayList<>();
				
				for (FileDescriptor file : this.getResult()) {
					if (file.getName().equals(".DS_Store"))
						continue;
					
					if (! showHidden && file.getName().startsWith("."))
						continue;
					
					CommonPath fpath = file.getPathAsCommon();
					
					// do not list for certain folders that are supplied by other feeds
					if (fpath.getName(0).equals("files"))
						continue;
					
					if (fpath.getName(0).equals("vault"))
						continue;
					
					if (fpath.getName(0).equals("feeds"))
						continue;
					
					if (fpath.getName(0).equals("meta"))
						continue;

					if (fpath.getName(0).equals("galleries"))
						continue;

					if (fpath.getName(0).equals("sites"))
						continue;
					
					files.add(file);
				}
				
				callback.returnValue(files);
			}
		});
	}
}
