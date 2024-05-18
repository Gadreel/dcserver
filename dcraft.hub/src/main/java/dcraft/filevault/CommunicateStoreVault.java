package dcraft.filevault;

import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.work.VaultIndexLocalFilesWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.tenant.Site;
import dcraft.util.FileUtil;
import dcraft.util.cb.CountDownCallback;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class CommunicateStoreVault extends FileStoreVault {
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

					if (! file.isFolder())
						continue;

					boolean isEmail = file.getName().endsWith(".v");

					if (isEmail) {
						String path = file.getPath();

						file.with("Path", path.substring(0, path.length() - 2));
						file.with("IsFolder", false);

						// TODO set modified and size based on the `original` or 'full' variation
					}

					files.add(file);
				}

				callback.returnValue(files);
			}
		});
	}

}
