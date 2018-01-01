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

import dcraft.filestore.local.LocalStore;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.scriptold.inst.With;
import dcraft.util.StringUtil;

public class LocalFileStore extends With {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        String name = stack.stringFromSource("Name");
        
        if (StringUtil.isEmpty(name))
        	name = "FileStore_" + stack.getActivity().tempVarName();
        
        String vname = name;
        
        String folder = stack.stringFromSource("RootFolder");
        String path = stack.stringFromSource("RootPath");
        
        if (StringUtil.isEmpty(folder)) {
        	Logger.errorTr(534);
			this.nextOpResume(stack);
			return;
        }
        
        Path lpath = null;
        
        try {
        	lpath = StringUtil.isNotEmpty(path) ? Paths.get(folder, path.substring(1)) : Paths.get(folder);
        }
        catch (Exception x) {
        	Logger.errorTr(535, x);
			this.nextOpResume(stack);
			return;
        }

        LocalStore drv = LocalStore.of(lpath);
        
        stack.addVariable(vname, drv);
        
        this.setTarget(stack, drv);
		
		this.nextOpResume(stack);
	}
}
