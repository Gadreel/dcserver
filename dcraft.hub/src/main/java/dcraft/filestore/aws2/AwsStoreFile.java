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

import dcraft.aws.s3.S3Object;
import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.aws.AWSS3;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.stream.StreamFragment;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.HashUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.web.DateParser;
import dcraft.xml.XElement;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AwsStoreFile extends FileStoreFile {
	static public AwsStoreFile of(AwsStore driver, CommonPath path, boolean confirmed) {
		AwsStoreFile file = new AwsStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.with("Path", path.toString());
		file.withConfirmed(confirmed);

		return file;
	}

	static public AwsStoreFile of(AwsStore driver, CommonPath path, boolean folder, boolean confirmed) {
		AwsStoreFile file = new AwsStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.with("Path", path.toString());
		file.with("IsFolder", folder);
		file.withConfirmed(confirmed);

		return file;
	}

	static public AwsStoreFile of(AwsStore driver, RecordStruct rec, boolean confirmed) {
		AwsStoreFile file = new AwsStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.copyFields(rec);
		file.withConfirmed(confirmed);

		return file;
	}

	protected AwsStore awsdriver = null;
	
	public AwsStoreFile() {
		this.withType(SchemaHub.getType("dcAwsStoreFile"));
	}
	
	public CommonPath fullPath() {
		return this.awsdriver.resolvePath(this.getPathAsCommon());
	}

    public void loadDetails(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		AWSS3.getFileInfo(connection, region, bucket, this.fullPath(), new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct result) throws OperatingContextException {
				System.out.println("Got: " + result);

				if (this.isNotEmptyResult()) {
					String lm = result.selectAsString("last-modified");
					long in = new DateParser().convert(lm);

					AwsStoreFile.this
							.withModificationTime(Instant.ofEpochMilli(in).atZone(ZoneId.of("UTC")))
							.withSize(result.selectAsInteger("content-length"))
							.withExists(true)
							.withIsFolder(false)
							.with("AwsETag", result.getFieldAsString("etag").replace("\"", ""));

					callback.returnValue(AwsStoreFile.this);
				}
				else {
					callback.returnEmpty();
				}
			}
		});
    }
    
    public AwsStoreFile withPublic(boolean grantpublic) {
		this.with("AwsGrantPublic", grantpublic);
		return this;
	}

    @Override
	public AwsStore getDriver() {
		return this.awsdriver;
	}
	
	@Override
	public RecordStruct getExtra() {
		return null;  // not secure - new RecordStruct().with("FullPath", this.getFullPath());
	}
	
	@Override
	public void getAttribute(String name, OperationOutcome<Struct> callback) {
		// TODO fix this
		Logger.error("attrs not supported yet");
		callback.returnResult();
	}
	
	@Override
	public IFileCollection scanner() {
		if (this.isFolder())
			return AwsStoreScanner.of(this.awsdriver, this.getPathAsCommon());
		
		return null;
	}
	
	@Override
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();
		CommonPath rootPath = AwsStoreFile.this.awsdriver.getRootPath();
		CommonPath localPath = this.getPathAsCommon();

		AWSS3.listObjects(connection, region, bucket, this.fullPath(), new OperationOutcome<>() {
			@Override
			public void callback(XElement result) throws OperatingContextException {
				System.out.println("Got: " + result);

				List<FileStoreFile> files = new ArrayList<>();

				if (this.isNotEmptyResult()) {
					for (XElement srcfile : result.selectAll("Contents")) {
						CommonPath srcpath = CommonPath.from("/" + srcfile.selectFirstText("Key"));

						srcpath = rootPath.relativize(srcpath);

						if (srcpath.equals(localPath))
							continue;

						AwsStoreFile file = AwsStoreFile.of(AwsStoreFile.this.awsdriver, srcpath, true);

						file
								.withModificationTime(srcfile.selectAsDateTime("LastModified.0.#"))
								.withSize(srcfile.selectAsInteger("Size.0.#"))
								.withExists(true)
								.withIsFolder(false)
								.with("FileName", srcpath.getFileName())
								.with("AwsETag", srcfile.selectFirstText("ETag").replace("\"", ""));

						files.add(file);
					}

					for (XElement srcfile : result.selectAll("CommonPrefixes")) {
						CommonPath srcpath = CommonPath.from("/" + srcfile.selectFirstText("Prefix"));

						srcpath = rootPath.relativize(srcpath);

						AwsStoreFile folder = AwsStoreFile.of(AwsStoreFile.this.awsdriver, srcpath, true);

						folder
								.withExists(true)
								.withIsFolder(true)
								.with("FileName", srcpath.getFileName());

						files.add(folder);
					}
				}

				callback.returnValue(files);
			}
		});
	}

	public void getFolderListingDeep(OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();
		CommonPath rootPath = AwsStoreFile.this.awsdriver.getRootPath();
		CommonPath localPath = this.getPathAsCommon();

		AWSS3.listObjectsDeep(connection, region, bucket, this.fullPath(), new OperationOutcome<>() {
			@Override
			public void callback(XElement result) throws OperatingContextException {
				System.out.println("Got: " + result);

				List<FileStoreFile> files = new ArrayList<>();

				if (this.isNotEmptyResult()) {
					for (XElement srcfile : result.selectAll("Contents")) {
						CommonPath srcpath = CommonPath.from("/" + srcfile.selectFirstText("Key"));

						srcpath = rootPath.relativize(srcpath);

						if (srcpath.equals(localPath))
							continue;

						AwsStoreFile file = AwsStoreFile.of(AwsStoreFile.this.awsdriver, srcpath, true);

						file
								.withModificationTime(srcfile.selectAsDateTime("LastModified.0.#"))
								.withSize(srcfile.selectAsInteger("Size.0.#"))
								.withExists(true)
								.withIsFolder(false)
								.with("FileName", srcpath.getFileName())
								.with("AwsETag", srcfile.selectFirstText("ETag").replace("\"", ""));

						files.add(file);
					}

					for (XElement srcfile : result.selectAll("CommonPrefixes")) {
						CommonPath srcpath = CommonPath.from("/" + srcfile.selectFirstText("Prefix"));

						srcpath = rootPath.relativize(srcpath);

						AwsStoreFile folder = AwsStoreFile.of(AwsStoreFile.this.awsdriver, srcpath, true);

						folder
								.withExists(true)
								.withIsFolder(true)
								.with("FileName", srcpath.getFileName());

						files.add(folder);
					}
				}

				callback.returnValue(files);
			}
		});
	}

	// TODO
	@Override
	public StreamFragment allocStreamDest() {
		throw new RuntimeException("AWS2 does not support streams yet");
	}

	// TODO
	@Override
	public StreamFragment allocStreamSrc() {
		throw new RuntimeException("AWS2 does not support streams yet");
	}

	@Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	AwsStoreFile nn = (AwsStoreFile)n;
		nn.driver = this.driver;
    }
    
	@Override
	public AwsStoreFile deepCopy() {
		AwsStoreFile cp = new AwsStoreFile();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public BaseStruct getOrAllocateField(String name) {
		// TODO consider this
		//if ("TextReader".equals(name))
		//	return new FileSystemTextReader(this);
		
		return super.getOrAllocateField(name);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		// TODO add script support
		
		return super.operation(stack, code);
	}

	/*
	public Map<String, List<String>> buildHeaders() {
		Map<String, List<String>> hdrs = new HashMap<>();
		
		List<String> ctlist = new ArrayList<>();
		ctlist.add(ResourceHub.getResources().getMime().getMimeTypeForName(this.getPath()).getMimeType());
		hdrs.put("Content-Type", ctlist);
		
		if (this.getFieldAsBooleanOrFalse("AwsGrantPublic")) {
			List<String> gplist = new ArrayList<>();
			gplist.add("public-read");
			hdrs.put("x-amz-acl", gplist);
		}
		
		return hdrs;
	}

	 */
		
	@Override
	public void readAllText(OperationOutcome<String> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		AWSS3.getFileDirect(connection, region, bucket, this.fullPath(), new OperationOutcome<>() {
			@Override
			public void callback(Memory result) {
				if (this.isNotEmptyResult())
					callback.returnValue(result.toString());
				else
					callback.returnEmpty();
			}
		});
	}
	
	@Override
	public void writeAllText(String v, OperationOutcomeEmpty callback) throws OperatingContextException {
		Memory data = new Memory(v);

		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		AWSS3.putFileDirect(connection, region, bucket, this.fullPath(), data, new OperationOutcomeEmpty() {
			@Override
			public void callback() {
				callback.returnEmpty();
			}
		});
	}
	
	@Override
	public void readAllBinary(OperationOutcome<Memory> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		AWSS3.getFileDirect(connection, region, bucket, this.fullPath(), new OperationOutcome<>() {
			@Override
			public void callback(Memory result) {
				if (this.isNotEmptyResult())
					callback.returnValue(result);
				else
					callback.returnEmpty();
			}
		});
	}
	
	@Override
	public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		//  TODO permissions
		AWSS3.putFileDirect(connection, region, bucket, this.fullPath(), v, new OperationOutcomeEmpty() {
			@Override
			public void callback() {
				callback.returnEmpty();
			}
		});
	}

	// TODO no good for large files, rewrite
	@Override
	public void hash(String method, OperationOutcome<String> callback) throws OperatingContextException {
		this.readAllBinary(new OperationOutcome<>() {
			@Override
			public void callback(Memory result) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Unable to read file for hash");
					callback.returnEmpty();
					return;
				}
				
				try {
					String res = HashUtil.hash(method, new ByteArrayInputStream(result.toArray()));
					
					callback.returnValue(res);
				}
				catch (Exception x) {
					Logger.error("Unable to hash file: " + x);
					
					callback.returnEmpty();
				}
			}
		});
	}

	public RecordStruct presignUpload() throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		//  TODO permissions
		return AWSS3.putFilePresign(connection, region, bucket, this.fullPath()).getResult();
	}

	public void copyFile(String destBucket, AwsStoreFile dest, OperationOutcomeEmpty callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		if (StringUtil.isEmpty(destBucket))
			destBucket = bucket;

		AWSS3.copyFile(connection, region, CommonPath.from("/" + bucket).resolve(this.fullPath()), destBucket, dest.fullPath(), new OperationOutcome<XElement>() {
			@Override
			public void callback(XElement result) throws OperatingContextException {
				callback.returnEmpty();
			}
		});
	}

	@Override
	public void rename(String name, OperationOutcomeEmpty callback) {
		// TODO fix this
		Logger.error("rename not supported yet");
		callback.returnResult();
	}

	@Override
	public void remove(OperationOutcomeEmpty callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();

		if (this.isFolder()) {
			AWSS3.removeFolder(connection, region, bucket, this.fullPath(), new OperationOutcomeEmpty() {
				@Override
				public void callback() {
					callback.returnEmpty();
				}
			});
		}
		else  {
			AWSS3.removeFile(connection, region, bucket, this.fullPath(), new OperationOutcomeEmpty() {
				@Override
				public void callback() {
					callback.returnEmpty();
				}
			});
		}
	}
}
