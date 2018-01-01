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
package dcraft.scriptold.inst.file;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.scriptold.inst.With;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class File extends With {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        String name = stack.stringFromSource("Name");
        
        if (StringUtil.isEmpty(name))
        	name = "Folder_" + stack.getActivity().tempVarName();
        
        String vname = name;
        
        Struct ss = stack.refFromSource("In");
        
        if ((ss == null) || (!(ss instanceof FileStore) && !(ss instanceof FileStoreFile))) {
        	Logger.errorTr(536);
    		this.nextOpResume(stack);
        	return;
        }
        
        CommonPath path = null;
        
        try {
            path = new CommonPath(stack.stringFromSource("Path", "/"));
        }
        catch (Exception x) {
        	Logger.errorTr(537);
			this.nextOpResume(stack);
			return;
        }

        FileStore drv = null;
        
        if (ss instanceof FileStore) {
            drv = (FileStore)ss;
        }
        else {
        	drv = ((FileStoreFile)ss).getDriver();
        	path = ((FileStoreFile)ss).resolvePath(path);
        }
        
        drv.getFileDetail(path, new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile fh) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.errorTr(538);
					File.this.nextOpResume(stack);
					return;
				}
	            
	            if (! fh.exists() && stack.getInstruction().getXml().getName().equals("Folder"))
	            	fh.withIsFolder(true);
				
	            stack.addVariable(vname, (Struct)fh);
	            
	            File.this.setTarget(stack, (Struct)fh);
	            
	    		File.this.nextOpResume(stack);
			}
		});
	}
}
