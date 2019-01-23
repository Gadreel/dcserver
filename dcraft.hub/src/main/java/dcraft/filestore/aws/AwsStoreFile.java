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

import dcraft.aws.s3.ListEntry;
import dcraft.aws.s3.S3Object;
import dcraft.filestore.*;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.stream.IStreamDest;
import dcraft.stream.IStreamSource;
import dcraft.stream.StreamFragment;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.*;
import dcraft.util.chars.Utf8Decoder;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	
	public String resolveToKey() {
		return this.awsdriver.getRootFolder().substring(1) + this.getFieldAsString("Path");
	}
	
    public void loadDetails(OperationOutcome<FileStoreFile> callback) {
		try {
			dcraft.aws.s3.InfoResponse x = this.awsdriver.connection.getInfo(this.awsdriver.getBucket(),
					this.resolveToKey(), null);
		
			int code = x.connection.getResponseCode();
		
			Logger.debug("AWS file detail, response: " + code);
		
			if ((x.object != null)) {
				this
					.with("Modified", x.object.lastModified)    // will be seen as TemporalAccessor
					.with("Size", x.object.size)
					.with("AwsETag", x.object.eTag)
					.with("IsFolder", false)
					.with("Exists", true);
				
				this.withConfirmed(true);
			
				if (callback != null)
					callback.returnValue(this);
			}
			else {
				if (callback != null)
					callback.returnEmpty();
			}
		}
		catch (IOException x) {
			Logger.error("Unable to load file details: " + x);
			
			if (callback != null)
				callback.returnEmpty();
		}
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
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) {
		try {
			dcraft.aws.s3.ListBucketResponse x = this.awsdriver.connection.listBucket(this.awsdriver.getBucket(),
					this.resolveToKey(), null, null, null);
			
			int code = x.connection.getResponseCode();
			
			Logger.debug("AWS folder listing, response: " + code);
			
			if ((x.entries != null)) {
				List<FileStoreFile> files = new ArrayList<>();
				
				for (ListEntry e : x.entries) {
					RecordStruct rec = RecordStruct.record();
					
					// trim the path down so it is relative to the Root of the Store
					rec
						.with("Path", e.key.substring(this.awsdriver.getRootFolder().length() - 1))
						.with("Modified", e.lastModified)    // will be seen as TemporalAccessor
						.with("Size", e.size)
						.with("AwsETag", e.eTag)
						.with("IsFolder", false)
						.with("Exists", true);
					
					files.add(AwsStoreFile.of(this.awsdriver, rec, true));
				}
				
				callback.returnValue(files);
			}
			else {
				callback.returnEmpty();
			}
		}
		catch (IOException x) {
			Logger.error("Unable to write the text: " + x);
			callback.returnEmpty();
		}
	}
	
	public StreamFragment allocStreamDest() {
		return StreamFragment.of(AwsDestStream.from(this));
	}

	@Override
	public StreamFragment allocStreamSrc() {
		if (this.isFolder())
			return StreamFragment.of(CollectionSourceStream.of(this.scanner()));

		return StreamFragment.of(AwsSourceStream.of(this));
	}

	@Override
    protected void doCopy(Struct n) {
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
	public Struct getOrAllocateField(String name) {
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
	
	public Map<String, List<String>> buildHeaders() {
		Map<String, List<String>> hdrs = new HashMap<>();
		
		List<String> ctlist = new ArrayList<>();
		ctlist.add(ResourceHub.getResources().getMime().getMimeTypeForName(this.getPath()).getType());
		hdrs.put("Content-Type", ctlist);
		
		if (this.getFieldAsBooleanOrFalse("AwsGrantPublic")) {
			List<String> gplist = new ArrayList<>();
			gplist.add("public-read");
			hdrs.put("x-amz-acl", gplist);
		}
		
		return hdrs;
	}
		
	@Override
	public void readAllText(OperationOutcome<String> callback) {
		try {
			dcraft.aws.s3.GetResponse x = this.awsdriver.connection.get(this.awsdriver.getBucket(),
					this.resolveToKey(), null);
			
			int code = x.connection.getResponseCode();
			
			Logger.debug("Text written to AWS, response: " + code);
			
			if ((x.object != null))
				callback.returnValue(Utf8Decoder.decode(x.object.data).toString());
			else
				callback.returnEmpty();
		}
		catch (IOException x) {
			Logger.error("Unable to write the text: " + x);
			callback.returnEmpty();
		}
	}
	
	@Override
	public void writeAllText(String v, OperationOutcomeEmpty callback) throws OperatingContextException {
		S3Object data = new S3Object(Utf8Encoder.encode(v), null);		//  TODO permissions, MIME
		
		try {
			dcraft.aws.s3.Response x = this.awsdriver.connection.put(this.awsdriver.getBucket(),
					this.resolveToKey(), data, this.buildHeaders());
			
			int code = x.connection.getResponseCode();
			
			Logger.debug("Text written to AWS, response: " + code);
			
			this.loadDetails(new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					callback.returnEmpty();
				}
			});
		}
		catch (IOException x) {
			Logger.error("Unable to write the text: " + x);
		}
		
		callback.returnResult();
	}
	
	@Override
	public void readAllBinary(OperationOutcome<Memory> callback) {
		try {
			dcraft.aws.s3.GetResponse x = this.awsdriver.connection.get(this.awsdriver.getBucket(),
					this.resolveToKey(), null);
			
			int code = x.connection.getResponseCode();
			
			Logger.debug("Text written to AWS, response: " + code);
			
			if ((x.object != null)) {
				Memory mem = new Memory(x.object.data);
				mem.setPosition(0);
				callback.returnValue(mem);
			}
			else {
				callback.returnEmpty();
			}
		}
		catch (IOException x) {
			Logger.error("Unable to write the text: " + x);
			callback.returnEmpty();
		}
	}
	
	@Override
	public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) throws OperatingContextException {
		S3Object data = new S3Object(v.toArray(), null);		//  TODO permissions, MIME
		
		try {
			dcraft.aws.s3.Response x = this.awsdriver.connection.put(this.awsdriver.getBucket(),
					this.resolveToKey(), data, this.buildHeaders());
			
			int code = x.connection.getResponseCode();
			
			Logger.debug("Text written to AWS, response: " + code);
			
			this.loadDetails(new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					callback.returnEmpty();
				}
			});
		}
		catch (IOException x) {
			Logger.error("Unable to write the text: " + x);
		}
		
		callback.returnResult();
	}

	// TODO no good for large files, rewrite
	@Override
	public void hash(String method, OperationOutcome<String> callback) throws OperatingContextException {
		this.readAllBinary(new OperationOutcome<Memory>() {
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

	@Override
	public void rename(String name, OperationOutcomeEmpty callback) {
		// TODO fix this
		Logger.error("rename not supported yet");
		callback.returnResult();
	}

	@Override
	public void remove(OperationOutcomeEmpty callback) {
		if (this.isFolder()) {
			/* TODO list all folders under the path then call delete for each
			CommonPath dest = this.resolvePath(path);
	
			this.connection.delete()
			callback.returnValue(FileUtil.deleteDirectory(localpath));
			*/
			
			Logger.error("AWS remove folder not yet implemented");
			callback.returnEmpty();
		}
		else  {
			try {
				dcraft.aws.s3.Response response = this.awsdriver.connection.delete(this.awsdriver.getBucket(),
						this.resolveToKey(), null);
				
				int code = response.connection.getResponseCode();
				
				Logger.debug("Text written to AWS, response: " + code);
			}
			catch (Exception x) {
				Logger.error("Unable to remove file: " + this.getPath() + " - Error: " + x);
			}
		}
		
		callback.returnResult();
	}

}
