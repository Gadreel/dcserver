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
package dcraft.filestore.aws;

import dcraft.aws.s3.AWSAuthConnection;
import dcraft.aws.s3.S3Object;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.Memory;
import dcraft.xml.XElement;

import java.io.IOException;
import java.util.List;

public class AwsStore extends FileStore {
	// use common path approach - first part of path is the bucket name
	static public AwsStore of(String keyid, String secretkey, CommonPath path) {
		AwsStore driver = new AwsStore();
		driver.with("AwsKeyId", keyid);
		driver.with("AwsSecretKey", secretkey);
		driver.with("AwsBucket", path.getName(0));
		driver.with("RootFolder", path.subpath(1).toString());
		
		driver.connection = new AWSAuthConnection(keyid, secretkey, true);
		
		return driver;
	}
	
	// TODO recode this to be more general and to be true async with netty
	protected AWSAuthConnection connection = null;
	
	public AwsStore() {
		this.withType(SchemaHub.getType("dcAwsStore"));
	}
	
	public AWSAuthConnection getConnection() {
		return this.connection;
	}
	
	public CommonPath resolvePath(CommonPath path) {
		return CommonPath.from(this.getFieldAsString("RootFolder")).resolve(path);
	}
	
	@Override
	public AwsStore deepCopy() {
		AwsStore cp = new AwsStore();
		this.doCopy(cp);
		return cp;
	}
	
	@Override
	public void connect(RecordStruct params, OperationOutcomeEmpty callback) {
		/* TODO support
		if ((params != null) && ! params.isFieldEmpty("RootFolder")) 
			this.setRootFolder(params.getFieldAsString("RootFolder"));
		
		// create root folder if we have one specified and it is not present
		if (! this.isFieldEmpty("RootFolder"))
			this.setPath(this.getFieldAsString("RootFolder"));
		*/
		
		try {
			if (! this.connection.checkBucketExists(this.getFieldAsString("AwsBucket")))
				Logger.error("Unable to connect to bucket: " + this.getFieldAsString("AwsBucket"));
		}
		catch (Exception x) {
			Logger.error("Unable to connect to bucket, error: " + x);
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
		// TODO add script support
		
		return super.operation(stack, code);
	}

	public FileStoreFile wrapFileRecord(RecordStruct file) {
		return AwsStoreFile.of(this, file, false);
	}

	@Override
	public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) {
		AwsStoreFile f = AwsStoreFile.of(this, path, false);
		
		f.loadDetails(callback);
	}
	
	public String getBucket() {
		return this.getFieldAsString("AwsBucket");
	}

	public String getRootFolder() {
		return this.getFieldAsString("RootFolder");
	}

	public void setRootFolder(String path) {
		this.with("RootFolder", path);
	}

	@Override
	public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) {
		OperationMarker ok = OperationMarker.create();
		
		CommonPath dest = this.resolvePath(path);
		
		Memory blob = new Memory();
		
		blob.write("placeholder for folders");
		
		try {
			dcraft.aws.s3.Response x = this.connection.put(this.getFieldAsString("AwsBucket"), dest.resolve( "dc-placeholder").toString(), new S3Object(blob.toArray(), null), null);
			
			int code = x.connection.getResponseCode();
			
			Logger.info("AWS folder created, response: " + code);
		}
		catch (IOException x) {
			Logger.error("Unable create AWS folder: " + x);
		}
		
		try {
			if (! ok.hasErrors()) {
				callback.returnValue(AwsStoreFile.of(this, dest, true));
				return;
			}
		}
		catch (OperatingContextException x) {
			Logger.error("Bad context after add: " + x);
		}
		
		callback.returnValue(null);
	}
	
	@Override
	public void removeFolder(CommonPath path, OperationOutcomeEmpty callback) {
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
	public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) {
		AwsStoreFile f = AwsStoreFile.of(this, path, false);
		
		f.getFolderListing(callback);
	}
}
