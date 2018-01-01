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
package dcraft.service.base;

import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;
import dcraft.tenant.Site;

public class Vaults  {
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct rec = request.getDataAsRecord();
		
		Site site = OperationContext.getOrThrow().getUserContext().getSite();
		
		Vault vault = site.getVault(rec.getFieldAsString("Vault"));

		if (vault == null) {
			Logger.error("Missing vault.");
			return true;
		}
		
		String op = request.getOp();
		
		if ("AllocateUploadToken".equals(op)) {
			vault.allocateUploadToken(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("BeginTransaction".equals(op)) {
			vault.beginTransaction(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("CommitTransaction".equals(op)) {
			vault.commitTransaction(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("RollbackTransaction".equals(op)) {
			vault.rollbackTransaction(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("FileDetail".equals(op)) {
			vault.getFileDetail(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("DeleteFile".equals(op) || "DeleteFolder".equals(op)) {
			vault.deleteFile(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("AddFolder".equals(op)) {
			vault.addFolder(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("ListFiles".equals(op)) {
			vault.listFiles(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("Custom".equals(op)) {
			vault.executeCustom(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("StartUpload".equals(op)) {
			vault.startUpload(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("FinishUpload".equals(op)) {
			vault.finishUpload(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("StartDownload".equals(op)) {
			vault.startDownload(rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("FinishDownload".equals(op)) {
			vault.finishDownload(rec, request.isFromRpc(), callback);
			return true;
		}
		
		return false;
	}
}
