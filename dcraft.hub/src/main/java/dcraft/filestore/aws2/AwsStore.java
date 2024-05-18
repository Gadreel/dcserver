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
package dcraft.filestore.aws2;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.aws.AWSS3;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

public class AwsStore extends FileStore {
	// use common path approach - first part of path is the bucket name
	static public AwsStore of(String keyid, String secretkey, CommonPath path) {
		AwsStore driver = new AwsStore();
		driver.with("AwsKeyId", keyid);
		driver.with("AwsSecretKey", secretkey);
		driver.with("AwsBucket", path.getName(0));

		driver.rootpath = path;
		driver.with("RootFolder", path.subpath(1));

		return driver;
	}

	// use with connect
	static public AwsStore aws() {
		AwsStore driver = new AwsStore();
		return driver;
	}

	protected CommonPath rootpath = null;

	public AwsStore() {
		this.withType(SchemaHub.getType("dcAwsStore"));
	}

	public CommonPath resolvePath(CommonPath path) {
		return this.rootpath.resolve(path);
	}

	public CommonPath getRootPath() {
		return this.rootpath;
	}
	
	@Override
	public AwsStore deepCopy() {
		AwsStore cp = new AwsStore();
		this.doCopy(cp);
		return cp;
	}
	
	@Override
	public void connect(RecordStruct params, OperationOutcomeEmpty callback) {
		if (params != null) {
			this.withConditional("RootFolder", params.getFieldAsString("RootFolder", "/"));
			this.withConditional("AwsBucket", params.getFieldAsString("AwsBucket"));

			this.rootpath = CommonPath.from(this.getRootFolder());

			if ("default".equals(params.getFieldAsString("AwsAccount", "default"))) {
				XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws");

				if (settings == null) {
					Logger.error("Missing settings Interchange-Aws");
					callback.returnResult();
					return;
				}

				this.with("AwsKeyId", settings.getAttribute("KeyId"));
				this.with("AwsSecretKey", settings.getAttribute("SecretKey"));
				this.with("AwsRegion", settings.getAttribute("StorageRegion", settings.getAttribute("Region")));
			}
		}

		if (callback != null)
			callback.returnResult();
	}
	
	@Override
	public boolean close(OperationOutcomeEmpty callback) {
		if (callback != null)
			callback.returnResult();
		
		return false;
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("PresignUpload".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			RecordStruct presignurl = this.presignUpload(CommonPath.from(spath.toString()));

			String handle = StackUtil.stringFromElement(stack, code, "Handle");

			if (handle != null) {
				StackUtil.addVariable(stack, handle, presignurl);
			}

			return ReturnOption.CONTINUE;
		}

		if ("FolderListingDeep".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			String handle = StackUtil.stringFromElement(stack, code, "Result");

			this.getFolderListingDeep(CommonPath.from(spath.toString()), new OperationOutcome<List<FileStoreFile>>() {
				@Override
				public void callback(List<FileStoreFile> result) throws OperatingContextException {
					stack.setState(ExecuteState.DONE);

					if (handle != null) {
						StackUtil.addVariable(stack, handle, ListStruct.list(result));
					}

					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("CopyFile".equals(code.getName())) {
			BaseStruct srcPath = StackUtil.refFromElement(stack, code, "SourcePath", true);

			if (srcPath == null) {
				Logger.error("Missing source path");
				return ReturnOption.CONTINUE;
			}

			String destBucket = Struct.objectToString(StackUtil.refFromElement(stack, code, "DestinationBucket", true));
			BaseStruct destPath = StackUtil.refFromElement(stack, code, "DestinationPath", true);

			if (destPath == null) {
				Logger.error("Missing destination path");
				return ReturnOption.CONTINUE;
			}

			this.copyFile(CommonPath.from(srcPath.toString()), destBucket, CommonPath.from(destPath.toString()), new OperationOutcomeEmpty() {
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

	public FileStoreFile wrapFileRecord(RecordStruct file) {
		return AwsStoreFile.of(this, file, false);
	}

	@Override
	public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		AwsStoreFile f = AwsStoreFile.of(this, path, false);
		
		f.loadDetails(callback);
	}

	public XElement getConnectSettings() {
		return XElement.tag("Settings")
				.attr("KeyId", this.getFieldAsString("AwsKeyId"))
				.attr("SecretKey", this.getFieldAsString("AwsSecretKey"));
	}

	public String getRegion() {
		return this.getFieldAsString("AwsRegion");
	}

	public String getBucket() {
		return this.getFieldAsString("AwsBucket");
	}

	public String getRootFolder() {
		return this.getFieldAsString("RootFolder");
	}

	public void setRootFolder(String path) {
		this.rootpath = CommonPath.from(path);
		this.with("RootFolder", path);
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
					AwsStoreFile folder = AwsStoreFile.of(AwsStore.this, path, true);

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
		AwsStoreFile f = AwsStoreFile.of(this, path, true);

		f.remove(callback);
	}

	@Override
	public void queryFeatures(OperationOutcome<RecordStruct> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void customCommand(RecordStruct params, OperationOutcome<RecordStruct> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IFileCollection scanner(CommonPath path) {
		return AwsStoreFile.of(this, path, true).scanner();
	}

	@Override
	public FileStoreFile rootFolder() {
		return AwsStoreFile.of(this, CommonPath.ROOT, true);
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path) {
		return AwsStoreFile.of(this, path, false);
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path, boolean isFolder) {
		return (FileStoreFile) AwsStoreFile.of(this, path, false).withIsFolder(isFolder);
	}
	
	@Override
	public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		AwsStoreFile f = AwsStoreFile.of(this, path, false);
		
		f.getFolderListing(callback);
	}

	public void getFolderListingDeep(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		AwsStoreFile f = AwsStoreFile.of(this, path, false);

		f.getFolderListingDeep(callback);
	}

	public RecordStruct presignUpload(CommonPath path) throws OperatingContextException {
		AwsStoreFile f = AwsStoreFile.of(this, path, false);

		return f.presignUpload();
	}

	public void copyFile(CommonPath sourcePath, String destBucket, CommonPath destPath, OperationOutcomeEmpty callback) throws OperatingContextException {
		AwsStoreFile source = AwsStoreFile.of(this, sourcePath, false);
		AwsStoreFile dest = AwsStoreFile.of(this, destPath, false);

		source.copyFile(destBucket, dest, callback);
	}
}
