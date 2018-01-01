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
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.scriptold.inst.With;
import dcraft.struct.Struct;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;

/**
 * There is an implicit temp folder available to any scriptold, it is only created on demand
 * and is a global variable _TempFolder  
 * 
 * @author andy
 *
 */
public class TempFile extends With {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        String name = stack.stringFromSource("Name");
        
        if (StringUtil.isEmpty(name))
        	name = "TempFile_" + stack.getActivity().tempVarName();
        
        String vname = name;
        
        String tpath = stack.stringFromSource("Path");
        String text = stack.stringFromSource("Ext");
        
        if (StringUtil.isEmpty(tpath))
        	tpath = "/" + (StringUtil.isNotEmpty(text) ? FileUtil.randomFilename(text) : FileUtil.randomFilename());
        
        CommonPath path = null;
        
        try {
            path = new CommonPath(tpath);
        }
        catch (Exception x) {
        	Logger.errorTr(539);
			this.nextOpResume(stack);
			return;
        }

        Struct tf = stack.getActivity().queryVariable("_TempFolder");
        LocalStore drv = null;
        
        if (tf instanceof LocalStore) {
        	drv = (LocalStore)tf;
        }
        else {
            Path tfpath = FileUtil.allocateTempFolder();

            drv = LocalStore.of(tfpath);
            drv.isTemp(true);
            
            stack.getActivity().addVariable("_TempFolder", drv);
        }
        
        LocalStoreFile fh = LocalStoreFile.of(drv, path, false);
        
        stack.addVariable(vname, fh);
        this.setTarget(stack, fh);
		
		this.nextOpResume(stack);
	}
}
