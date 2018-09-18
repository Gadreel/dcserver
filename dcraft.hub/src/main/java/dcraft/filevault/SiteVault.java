package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

import java.util.List;

/*
 * restrict so /feeds, /files, /galleries and /vault do not show
 */
public class SiteVault extends Vault {
	@Override
	public void listFiles(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		// check bucket security
		if (checkAuth && ! this.checkReadAccess("ListFiles", request)) {
			Logger.errorTr(434);
			fcb.returnEmpty();
			return;
		}
		
		this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile result) throws OperatingContextException {
				if (this.hasErrors()) {
					fcb.returnEmpty();
					return;
				}
				
				if (this.isEmptyResult()) {
					Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
					fcb.returnEmpty();
					return;
				}
				
				FileStoreFile fi = this.getResult();
				
				if (!fi.exists()) {
					fcb.returnEmpty();
					return;
				}

				SiteVault.this.fsd.getFolderListing(fi.getPathAsCommon(), new OperationOutcome<List<FileStoreFile>>() {
					@Override
					public void callback(List<FileStoreFile> result) throws OperatingContextException {
						if (this.hasErrors()) {
							fcb.returnEmpty();
							return;					
						}
						
						boolean showHidden = OperationContext.getOrThrow().getUserContext().isTagged("Admin");
						
						ListStruct files = new ListStruct();
						
						for (FileStoreFile file : this.getResult()) {
							if (file.getName().equals(".DS_Store"))
								continue;
							
							if (! showHidden && file.getName().startsWith("."))
								continue;

							CommonPath fpath = file.getPathAsCommon();

							// do not list for certain folders that are supplied by other feeds
							if (fpath.getName(0).equals("files"))
								break;
							if (fpath.getName(0).equals("vault"))
								break;
							if (fpath.getName(0).equals("feeds"))
								break;
							if (fpath.getName(0).equals("galleries"))
								break;
							if (fpath.getName(0).equals("sites"))
								break;

							RecordStruct fdata = new RecordStruct();

							fdata.with("FileName", file.getName());
							fdata.with("IsFolder", file.isFolder());
							fdata.with("Modified", file.getModificationAsTime());
							fdata.with("Size", file.getSize());
							fdata.with("Extra", file.getExtra());

							files.withItem(fdata);
						}
						
						fcb.returnValue(files);
					}
				});
			}
		});
	}
}
