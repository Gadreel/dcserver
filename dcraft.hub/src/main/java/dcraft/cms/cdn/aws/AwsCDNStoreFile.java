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
import dcraft.filestore.IFileCollection;
import dcraft.filestore.aws2.AwsStore;
import dcraft.filestore.aws2.AwsStoreFile;
import dcraft.filestore.aws2.AwsStoreScanner;
import dcraft.hub.op.*;
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
import dcraft.util.web.DateParser;
import dcraft.xml.XElement;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AwsCDNStoreFile extends AwsStoreFile {
	static public AwsCDNStoreFile of(AwsCDNStore driver, CommonPath path, boolean confirmed) {
		AwsCDNStoreFile file = new AwsCDNStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.with("Path", path.toString());
		file.withConfirmed(confirmed);

		return file;
	}

	static public AwsCDNStoreFile of(AwsCDNStore driver, CommonPath path, boolean folder, boolean confirmed) {
		AwsCDNStoreFile file = new AwsCDNStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.with("Path", path.toString());
		file.with("IsFolder", folder);
		file.withConfirmed(confirmed);

		return file;
	}

	static public AwsCDNStoreFile of(AwsCDNStore driver, RecordStruct rec, boolean confirmed) {
		AwsCDNStoreFile file = new AwsCDNStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.copyFields(rec);
		file.withConfirmed(confirmed);

		return file;
	}

	public AwsCDNStoreFile() {
		this.withType(SchemaHub.getType("dcmAwsCDNStoreFile"));
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

					AwsCDNStoreFile.this
							.withModificationTime(Instant.ofEpochMilli(in).atZone(ZoneId.of("UTC")))
							.withSize(result.selectAsInteger("content-length"))
							.withExists(true)
							.withIsFolder(false)
							.with("AwsETag", result.getFieldAsString("etag").replace("\"", ""));

					callback.returnValue(AwsCDNStoreFile.this);
				}
				else {
					callback.returnEmpty();
				}
			}
		});
    }

    @Override
	public AwsStore getDriver() {
		return this.awsdriver;
	}

	@Override
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();
		CommonPath rootPath = AwsCDNStoreFile.this.awsdriver.getRootPath();
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

						AwsCDNStoreFile file = AwsCDNStoreFile.of((AwsCDNStore) AwsCDNStoreFile.this.awsdriver, srcpath, true);

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

						String fname = srcpath.getFileName();

						// don't expose the videos folder
						if ("videos".equals(fname) && srcpath.getParent().isRoot() && !OperationContext.getOrThrow().getUserContext().isTagged("Developer", "SysAdmin"))
							continue;

						AwsCDNStoreFile folder = AwsCDNStoreFile.of((AwsCDNStore) AwsCDNStoreFile.this.awsdriver, srcpath, true);

						folder
								.withExists(true)
								.withIsFolder(true)
								.with("FileName", fname);

						files.add(folder);
					}
				}

				callback.returnValue(files);
			}
		});
	}

	@Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	AwsCDNStoreFile nn = (AwsCDNStoreFile)n;
		nn.driver = this.driver;
    }
    
	@Override
	public AwsCDNStoreFile deepCopy() {
		AwsCDNStoreFile cp = new AwsCDNStoreFile();
		this.doCopy(cp);
		return cp;
	}
}
