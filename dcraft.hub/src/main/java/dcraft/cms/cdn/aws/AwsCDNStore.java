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
package dcraft.cms.cdn.aws;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.aws2.AwsStore;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.interchange.aws.AWSS3;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

import java.util.List;

public class AwsCDNStore extends AwsStore {
	// use with connect
	static public AwsCDNStore awsCDN() {
		AwsCDNStore driver = new AwsCDNStore();
		return driver;
	}

	public AwsCDNStore() {
		this.withType(SchemaHub.getType("dcmAwsCDNStore"));
	}

	@Override
	public AwsCDNStore deepCopy() {
		AwsCDNStore cp = new AwsCDNStore();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public FileStoreFile wrapFileRecord(RecordStruct file) {
		return AwsCDNStoreFile.of(this, file, false);
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("DeleteFolder".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			this.removeFolder(CommonPath.from(spath.toString()), new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					stack.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		return super.operation(stack, code);
	}

	@Override
	public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		AwsCDNStoreFile f = AwsCDNStoreFile.of(this, path, false);
		
		f.loadDetails(callback);
	}

	@Override
	public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		CommonPath dest = this.resolvePath(path);

		XElement connection = this.getConnectSettings();
		String region = this.getRegion();
		String bucket = this.getBucket();

		AWSS3.createFolder(connection, region, bucket, dest, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
				}
				else {
					AwsCDNStoreFile folder = AwsCDNStoreFile.of(AwsCDNStore.this, path, true);

					folder
							.withExists(true)
							.withIsFolder(true);

					callback.returnValue(folder);
				}
			}
		});
	}
	
	@Override
	public void removeFile(CommonPath path, OperationOutcomeEmpty callback) throws OperatingContextException {
		AwsCDNStoreFile f = AwsCDNStoreFile.of(this, path, true);

		f.remove(callback);
	}

	public void removeFolder(CommonPath path, OperationOutcomeEmpty callback) throws OperatingContextException {
		AwsVideoStoreFile f = AwsVideoStoreFile.of(this, path, true);

		f.withIsFolder(true);

		f.remove(callback);
	}

	@Override
	public FileStoreFile fileReference(CommonPath path) {
		return AwsCDNStoreFile.of(this, path, false);
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path, boolean isFolder) {
		return (FileStoreFile) AwsCDNStoreFile.of(this, path, false).withIsFolder(isFolder);
	}
	
	@Override
	public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		AwsCDNStoreFile f = AwsCDNStoreFile.of(this, path, false);
		
		f.getFolderListing(callback);
	}

	@Override
	public RecordStruct presignUpload(CommonPath path) throws OperatingContextException {
		AwsCDNStoreFile f = AwsCDNStoreFile.of(this, path, false);

		return f.presignUpload();
	}
}
