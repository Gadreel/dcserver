package dcraft.chrono.filesets;

public class FilesetVault extends dcraft.filevault.EncryptedVault {
	/*
	@Override
	public boolean checkReadAccess(String op, String path, RecordStruct params) throws OperatingContextException {
		UserContext usr = OperationContext.getOrThrow().getUserContext();
		
		//not sure this is a great idea, why do we have this? (could guess their personal file names?)
		if ("StartDownload".equals(op) && ! usr.looksLikeGuest() &&  path.startsWith("/user/" + usr.getUserId()))
			return true;
			
		return super.checkReadAccess(op, path, params);
	}
	*/
}
