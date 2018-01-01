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

import dcraft.filestore.FileStoreFile;
import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.IFileCollection;
import dcraft.filestore.FileStore;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.scriptold.Ops;
import dcraft.scriptold.StackEntry;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.FunnelStream;
import dcraft.stream.file.GzipStream;
import dcraft.stream.file.HashStream;
import dcraft.stream.IStreamDest;
import dcraft.stream.IStreamSource;
import dcraft.stream.IStreamUp;
import dcraft.stream.file.JoinStream;
import dcraft.stream.file.NullDest;
import dcraft.stream.file.PgpEncryptStream;
import dcraft.stream.file.SplitStream;
import dcraft.stream.file.TarStream;
import dcraft.stream.file.UngzipStream;
import dcraft.stream.file.UntarStream;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class FileOps extends Ops {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
		this.nextOpResume(stack);
	}

	@Override
	public void runOp(StackEntry stack, XElement op, Struct target) throws OperatingContextException {
		if ("Copy".equals(op.getName())) 
			this.copy(stack, op);					
		else if ("XCopy".equals(op.getName())) 
			this.xcopy(stack, op);
		else if ("Tar".equals(op.getName())) 
			this.injectStream(stack, op, new TarStream());					
		else if ("Untar".equals(op.getName())) 
			this.injectStream(stack, op, new UntarStream());					
		else if ("Gzip".equals(op.getName())) 
			this.injectStream(stack, op, GzipStream.create());		// TODO switch others to create - use configuration resource for the names
		else if ("Ungzip".equals(op.getName())) 
			this.injectStream(stack, op, new UngzipStream());
		else if ("Hash".equals(op.getName())) 
			this.injectStream(stack, op, HashStream.create());
		else if ("Funnel".equals(op.getName())) 
			this.injectStream(stack, op, new FunnelStream());
		else if ("Split".equals(op.getName())) 
			this.injectStream(stack, op, new SplitStream());
		else if ("Join".equals(op.getName())) 
			this.injectStream(stack, op, new JoinStream());
		else if ("PGPEncrypt".equals(op.getName())) 
			this.injectStream(stack, op, new PgpEncryptStream());
		else {
			Logger.error("Unknown FileOp: " + op.getName());
			this.nextOpResume(stack);
		}
	}

	protected void copy(StackEntry stack, XElement el) throws OperatingContextException {
		IStreamSource streamin = this.getSourceStream(stack, el);
		
		el.withAttribute("Relative", "false");
		
		if (streamin != null)
			this.executeDest(stack, el, streamin, true);
	}

	protected void xcopy(StackEntry stack, XElement el) throws OperatingContextException {
		IStreamSource streamin = this.getSourceStream(stack, el);
		
		el.withAttribute("Relative", "true");
		
		if (streamin != null)
			this.executeDest(stack, el, streamin, true);
	}

	protected void injectStream(StackEntry stack, XElement el, IStreamUp add) throws OperatingContextException {
		IStreamSource streamin = this.getSourceStream(stack, el);
		
		if (streamin == null) 
			return;
		
		add.init(stack, el);
		
		add.setUpstream(streamin);
		
		this.registerUpStream(stack, el, add);
		
		el.withAttribute("Relative", "true");

        this.executeDest(stack, el, add, false);
	}

	protected IStreamSource getSourceStream(StackEntry stack, XElement el) throws OperatingContextException {
        Struct src = stack.refFromElement(el, "Source");
        
        if ((src == null) || (src instanceof NullStruct)) {
        	src = stack.queryVariable("_LastStream");
        	
            if ((src == null) || (src instanceof NullStruct)) {
            	Logger.error("Missing source");
				this.nextOpResume(stack);
	        	return null;
            }
        }
        
        if (src instanceof IStreamSource)
        	return (IStreamSource) src;
        
        if (!(src instanceof FileStoreFile) && !(src instanceof FileStore) && !(src instanceof IFileCollection)) {
        	Logger.error("Invalid source type");
			this.nextOpResume(stack);
        	return null;
        }
        
        IStreamSource filesrc = null;
		
        if (src instanceof FileStoreFile)
        	filesrc = ((FileStoreFile)src).allocStreamSrc();
        else if (src instanceof FileStore)
       		filesrc = ((FileStore)src).rootFolder().allocStreamSrc();
        else 
        	filesrc = CollectionSourceStream.of((IFileCollection) src);
        
        if (filesrc == null) {
        	Logger.error("Invalid source type");
			this.nextOpResume(stack);
        	return null;
        }
        
        filesrc.init(stack, el);
        
		return filesrc;
	}

	@SuppressWarnings("unchecked")
	protected IStreamDest<FileSlice> getDestStream(StackEntry stack, XElement el) throws OperatingContextException {
        Struct dest = stack.refFromElement(el, "Dest");
        
        if ((dest == null) || (dest instanceof NullStruct)) 
        	return null;
        
        if ((dest instanceof StringStruct) && "NULL".equals(((StringStruct)dest).getValue()))
        	return new NullDest();
        
        if (dest instanceof IStreamDest<?>)
        	return (IStreamDest<FileSlice>) dest;
        
        if (!(dest instanceof FileStoreFile) && !(dest instanceof FileStore)) {
        	Logger.error("Invalid dest type");
			this.nextOpResume(stack);
        	return null;
        }
        
        IStreamDest<FileSlice> deststrm = null;
        
        if (dest instanceof FileStore)
        	deststrm = ((FileStore)dest).rootFolder().allocStreamDest();
        else 
        	deststrm = ((FileStoreFile)dest).allocStreamDest();
        
        if (deststrm == null) {
        	Logger.error("Unable to create destination stream");
			this.nextOpResume(stack);
        	return null;
        }
        
        deststrm.init(stack, el);
        return deststrm;
	}
	
	protected void executeDest(StackEntry stack, XElement el, IStreamUp src, boolean destRequired) throws OperatingContextException {
		stack.addVariable("_LastStream", (Struct)src);
		
		/* TODO get StreamFragment
		IStreamDest<FileSlice> streamout = this.getDestStream(stack, el);
        
        if (streamout != null) {
    		streamout.setUpstream(src);
    		
    		IWork sw = new StreamWork(streamout);
    		
    		Task t = Task.ofSubtask(OperationContext.getAsTaskOrThrow(), "Streaming", "Stream")
    				.withWork(sw);

    		TaskHub.submit(t, new OperationObserver() {
    			@Override
    			public void completed(OperationContext ctx) {
    				try {
						FileOps.this.nextOpResume(stack);
					} 
    				catch (OperatingContextException x) {
    					Logger.error("Unable to continue scriptold: " + x);
					}
    			}
    		});
        }
        else {
        	if (destRequired)
        		Logger.error("Missing dest for " + el.getName());
        	
			this.nextOpResume(stack);
        	return;
        }
        */
	}

	protected void registerUpStream(StackEntry stack, XElement el, IStreamUp src) throws OperatingContextException {
        String name = stack.stringFromElement(el, "Name");
        
        if (StringUtil.isEmpty(name))
        	name = "Stream_" + stack.getActivity().tempVarName();
        
        // to be sure we cleanup the stream, all variables added will later be disposed of
        stack.addVariable(name, (Struct)src);
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// TODO review after we make operations
	}
}
