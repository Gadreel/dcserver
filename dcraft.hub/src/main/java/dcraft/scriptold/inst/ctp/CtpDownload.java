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
package dcraft.scriptold.inst.ctp;

import java.nio.file.Path;
import java.nio.file.Paths;

import dcraft.api.ApiSession;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationObserver;
import dcraft.log.Logger;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.Instruction;
import dcraft.scriptold.StackEntry;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;

public class CtpDownload extends Instruction {
	@Override
	public void run(StackEntry stack) throws OperatingContextException {
        String service = stack.stringFromSource("Service");
        
        if (StringUtil.isEmpty(service))
        	service = "dcFileServer";
        
        String fname = stack.stringFromSource("Dest");
        
        if (StringUtil.isEmpty(fname)) {
			stack.setState(ExecuteState.Done);
			Logger.error("Missing Dest");
        	stack.resume();
        	return;
        }
        
    	Path dest = null;
    	
    	try {
    		dest = Paths.get(fname);
    	}
    	catch (Exception x) {
			stack.setState(ExecuteState.Done);
			Logger.error("Dest error: " + x);
        	stack.resume();
        	return;
    	}
        
        String dname = stack.stringFromSource("Source");
        
        if (StringUtil.isEmpty(dname)) {
			stack.setState(ExecuteState.Done);
			Logger.error("Missing Source");
        	stack.resume();
        	return;
        }
    	
    	CommonPath src = null;
    	
    	try {
    		src = new CommonPath(dname);
    	}
    	catch (Exception x) {
			stack.setState(ExecuteState.Done);
			Logger.error("Source error: " + x);
        	stack.resume();
        	return;
    	}
        
        Struct ss = stack.refFromSource("Session");
        
        if ((ss == null) || !(ss instanceof ApiSession)) {
			stack.setState(ExecuteState.Done);
			Logger.errorTr(531);
        	stack.resume();
        	return;
        }
        
		Task t = Task.ofSubtask(OperationContext.getAsTaskOrThrow(), "Downloading", "Download");
		
		t.withParams(new RecordStruct()
				.with("LocalPath", dest)
				.with("RemotePath", src)
				.with("ServiceName", service)
				//new FieldStruct("TransferParams", storeParams),
				//new FieldStruct("ForceOverwrite", !allowResume)
		);

		/* TODO
		ApiSession sess = (ApiSession) ss;
        
		DownloadFile work = new DownloadFile();
		work.setSession(sess);
		
		t.withWork(work);
		*/
		
		TaskHub.submit(t, new OperationObserver() {
			@Override
			public void completed(OperationContext ctx) {
				try {
					stack.setState(ExecuteState.Done);
					stack.resume();
				} 
				catch (OperatingContextException x) {
					Logger.error("Unable to resume scriptold within Observer");
				}
				
	        	return;
			}
		});
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
}
