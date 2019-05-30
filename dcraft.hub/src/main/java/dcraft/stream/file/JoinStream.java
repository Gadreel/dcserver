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
package dcraft.stream.file;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.stream.ReturnOption;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class JoinStream extends TransformFileStream {
	static public JoinStream of(String name) {
		return new JoinStream().withNameHint(name);
	}

	protected FileDescriptor jfile = null;
	protected String namehint = null;
	protected boolean dashnummod = true;
	
	public JoinStream withNameHint(String v) {
		this.namehint = v;
		return this;
	}
	
	public JoinStream withDashNumMode(boolean v) {
		this.dashnummod = v;
		return this;
	}
	
	public JoinStream() {
    }

	@Override
	public void init(IParentAwareWork stack, XElement el) {
	}
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) 
    		return this.consumer.handle(slice);
    	
		// TODO add support this.tabulator
    	
    	if (this.jfile == null) {
    		// create the output file desc
    		this.jfile = new FileDescriptor();
    		
    		this.jfile.withModificationTime(ZonedDateTime.now(ZoneId.of("UTC")));
		
			if (this.dashnummod) {
				String cpath = slice.getFile().getPath();
			
				/*
				int idx = cpath.lastIndexOf('-');
				int idx2 = cpath.indexOf('.', idx);
			
				String bpath = cpath.substring(0, idx) + cpath.substring(idx2);
				*/

				// join can be used for things other than split files, so don't require the -
				int idx = cpath.lastIndexOf('-');
				
				String bpath = (idx != -1) ? cpath.substring(0, idx) : cpath;
			
				this.jfile.withPath(bpath);
			}
			else {
				// keep the path, just vary the name to the template
				this.jfile.withPath(slice.file.getPathAsCommon().resolvePeer(StringUtil.isNotEmpty(this.namehint) ? this.namehint : "/file.bin"));        // TODO support other names, currently assumes we are writing to a file dest instead of folder dest so name ignored
			}
			
    		this.jfile.withSize(0);						// don't know size ahead of time
    	}
    	
    	FileSlice sliceout = FileSlice.allocate(this.jfile, slice.data, 0, false);

		return this.consumer.handle(sliceout);
    }
}
