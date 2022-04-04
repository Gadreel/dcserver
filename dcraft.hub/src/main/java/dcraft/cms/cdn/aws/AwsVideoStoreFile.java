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
import dcraft.filestore.aws2.AwsStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.aws.AWSS3;
import dcraft.schema.SchemaHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.web.DateParser;
import dcraft.xml.XElement;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AwsVideoStoreFile extends AwsStoreFile {
	static public AwsVideoStoreFile of(AwsStore driver, CommonPath path, boolean confirmed) {
		AwsVideoStoreFile file = new AwsVideoStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.with("Path", path.toString());
		file.withConfirmed(confirmed);

		return file;
	}

	static public AwsVideoStoreFile of(AwsStore driver, CommonPath path, boolean folder, boolean confirmed) {
		AwsVideoStoreFile file = new AwsVideoStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.with("Path", path.toString());
		file.with("IsFolder", folder);
		file.withConfirmed(confirmed);

		return file;
	}

	static public AwsVideoStoreFile of(AwsStore driver, RecordStruct rec, boolean confirmed) {
		AwsVideoStoreFile file = new AwsVideoStoreFile();

		file.driver = driver;
		file.awsdriver = driver;

		file.copyFields(rec);
		file.withConfirmed(confirmed);

		return file;
	}

	public AwsVideoStoreFile() {
		this.withType(SchemaHub.getType("dcmAwsVideoStoreFile"));
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

					AwsVideoStoreFile.this
							.withModificationTime(Instant.ofEpochMilli(in).atZone(ZoneId.of("UTC")))
							.withSize(result.selectAsInteger("content-length"))
							.withExists(true)
							.withIsFolder(false)
							.with("AwsETag", result.getFieldAsString("etag").replace("\"", ""));

					callback.returnValue(AwsVideoStoreFile.this);
				}
				else {
					callback.returnEmpty();
				}
			}
		});
    }

	@Override
	public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		XElement connection = this.awsdriver.getConnectSettings();
		String region = this.awsdriver.getRegion();
		String bucket = this.awsdriver.getBucket();
		CommonPath rootPath = AwsVideoStoreFile.this.awsdriver.getRootPath();
		CommonPath localPath = this.getPathAsCommon();

		AWSS3.listObjects(connection, region, bucket, this.fullPath(), new OperationOutcome<>() {
			@Override
			public void callback(XElement result) throws OperatingContextException {
				System.out.println("Got: " + result);

				List<FileStoreFile> files = new ArrayList<>();

				if (this.isNotEmptyResult()) {
					List<String> readyflags = new ArrayList<>();

					// we don't care about files, except for the .ready file flags
					for (XElement srcfile : result.selectAll("Contents")) {
						CommonPath srcpath = CommonPath.from("/" + srcfile.selectFirstText("Key"));
						srcpath = rootPath.relativize(srcpath);

						if (srcpath.equals(localPath))
							continue;

						String fname = srcpath.getFileName();

						if (fname.endsWith(".ready"))
							readyflags.add(fname.substring(0, fname.length() - 6));
					}

					for (XElement srcfile : result.selectAll("CommonPrefixes")) {
						CommonPath srcpath = CommonPath.from("/" + srcfile.selectFirstText("Prefix"));

						srcpath = rootPath.relativize(srcpath);

						String fname = srcpath.getFileName();

						AwsVideoStoreFile folder = AwsVideoStoreFile.of(AwsVideoStoreFile.this.awsdriver, srcpath, true);

						folder
								.withExists(true)
								.withIsFolder(true)
								.with("FileName", fname);

						// .hls folders are our videos - treat as a file
						if (fname.endsWith(".hls"))  {
							String rdykey = fname.substring(0, fname.length() - 4);

							folder
									.withIsFolder(false)
									.with("FileName", rdykey)
									.with("VideoIndex", rdykey + ".m3u8")
									.with("Thumbnail", rdykey + ".0000000.jpg")
									.with("Ready", readyflags.contains(rdykey));
						}

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
    	
    	AwsVideoStoreFile nn = (AwsVideoStoreFile)n;
		nn.driver = this.driver;
    }
    
	@Override
	public AwsVideoStoreFile deepCopy() {
		AwsVideoStoreFile cp = new AwsVideoStoreFile();
		this.doCopy(cp);
		return cp;
	}
}
