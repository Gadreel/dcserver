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

import java.nio.file.Path;
import java.nio.file.Paths;

import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.scriptold.inst.With;
import dcraft.util.StringUtil;

public class LocalFile extends With {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        String name = stack.stringFromSource("Name");
        
        if (StringUtil.isEmpty(name))
        	name = "LocalFile_" + stack.getActivity().tempVarName();
        
        String vname = name;
        
        String path = stack.stringFromSource("Path");
        
        if (StringUtil.isEmpty(path)) {
        	Logger.errorTr(523);
			this.nextOpResume(stack);
			return;
        }
        
        Path lpath = null;
        
        try {
        	lpath = Paths.get(path);
        }
        catch (Exception x) {
        	Logger.errorTr(524, x);
			this.nextOpResume(stack);
			return;
        }

        LocalStore drv = LocalStore.of(lpath.getParent());
        LocalStoreFile fh = LocalStoreFile.of(drv, new CommonPath("/" + lpath.getFileName().toString()), false);
        
        stack.addVariable("LocalFS_" + stack.getActivity().tempVarName(), drv);
        stack.addVariable(vname, fh);
        this.setTarget(stack, fh);
		
		this.nextOpResume(stack);
	}
}
