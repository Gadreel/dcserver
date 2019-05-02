/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2012 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;

/**
 *
 */
public class VaultServiceRequest extends ServiceRequest {
	// be in the calling context when you use this method, otherwise may miss the proper OpInfo def
	static public VaultServiceRequest of(String vault, String op) {
		VaultServiceRequest req = new VaultServiceRequest();
		
		req.name = "dcCoreServices";
		req.feature = "Vaults";
		req.op = op;

		req.withData(RecordStruct.record()
				.with("Vault", vault)
		);
		
		
		return req;
	}
	
	static public VaultServiceRequest ofListFiles(String vault) {
		return VaultServiceRequest.of(vault, "ListFiles");
	}
	
	static public VaultServiceRequest ofFileDetail(String vault) {
		return VaultServiceRequest.of(vault, "FileDetail");
	}
	
	static public VaultServiceRequest ofDeleteFile(String vault) {
		return VaultServiceRequest.of(vault, "DeleteFile");
	}
	
	static public VaultServiceRequest ofAddFolder(String vault) {
		return VaultServiceRequest.of(vault, "AddFolder");
	}
	
	static public VaultServiceRequest ofDeleteFolder(String vault) {
		return VaultServiceRequest.of(vault, "DeleteFolder");
	}
	
	static public VaultServiceRequest ofCustom(String vault) {
		return VaultServiceRequest.of(vault, "Custom");
	}
	
	static public VaultServiceRequest ofAllocateUploadToken(String vault) {
		return VaultServiceRequest.of(vault, "AllocateUploadToken");
	}
	
	static public VaultServiceRequest ofBeginTransaction(String vault) {
		return VaultServiceRequest.of(vault, "BeginTransaction");
	}
	
	static public VaultServiceRequest ofCommitTransaction(String vault) {
		return VaultServiceRequest.of(vault, "CommitTransaction");
	}
	
	static public VaultServiceRequest ofRollbackTransaction(String vault) {
		return VaultServiceRequest.of(vault, "RollbackTransaction");
	}
	
	static public VaultServiceRequest ofStartUpload(String vault) {
		return VaultServiceRequest.of(vault, "StartUpload");
	}
	
	static public VaultServiceRequest ofFinishUpload(String vault) {
		return VaultServiceRequest.of(vault, "FinishUpload");
	}
	
	static public VaultServiceRequest ofStartDownload(String vault) {
		return VaultServiceRequest.of(vault, "StartDownload");
	}
	
	static public VaultServiceRequest ofFinishDownload(String vault) {
		return VaultServiceRequest.of(vault, "FinishDownload");
	}
	
	protected VaultServiceRequest() {
	}
	
	public VaultServiceRequest withPath(CommonPath path) {
		this.getDataAsRecord().with("Path", path);
		return this;
	}
	
	public VaultServiceRequest withToken(String token) {
		this.getDataAsRecord().with("Token", token);
		return this;
	}
	
	public VaultServiceRequest withParams(RecordStruct params) {
		this.getDataAsRecord().with("Params", params);
		return this;
	}
	
	public VaultServiceRequest withTransactionId(String v) {
		this.getDataAsRecord().with("TransactionId", v);
		return this;
	}
	
	public VaultServiceRequest withSize(long size) {
		this.getDataAsRecord().with("Size", size);
		return this;
	}
	
	public VaultServiceRequest withOverwrite(boolean overwrite) {
		this.getDataAsRecord().with("Overwrite", overwrite);
		return this;
	}
	
	public VaultServiceRequest withChannel(String v) {
		this.getDataAsRecord().with("Channel", v);
		return this;
	}
	
	public VaultServiceRequest withStatus(String v) {
		this.getDataAsRecord().with("Status", v);
		return this;
	}
	
	public VaultServiceRequest withEvidence(String kind, Object evidence) {
		this.getDataAsRecord().with("Evidence", RecordStruct.record().with(kind, evidence));
		return this;
	}
}
