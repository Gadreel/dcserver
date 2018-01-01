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

import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.StackEntry;
import dcraft.scriptold.inst.With;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;

public class TempFolder extends With {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        String name = stack.stringFromSource("Name");
        
        if (StringUtil.isEmpty(name))
        	name = "TempFolder_" + stack.getActivity().tempVarName();
        
        String vname = name;
        
        Path path = FileUtil.allocateTempFolder();

        LocalStore drv = LocalStore.of(path);
        LocalStoreFile fh = LocalStoreFile.of(drv, CommonPath.ROOT, true);
        
        drv.isTemp(true);
        
        stack.addVariable("TempFS_" + stack.getActivity().tempVarName(), drv);
        stack.addVariable(vname, fh);
        this.setTarget(stack, fh);
		
		this.nextOpResume(stack);
	}
}
