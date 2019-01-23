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
import dcraft.scriptold.StackEntry;
import dcraft.stream.ReturnOption;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class FilterStream extends TransformFileStream {
	// allow just one file
	static public FilterStream of(String name) {
		return new FilterStream().withNameFilter(name);
	}

	protected FileDescriptor jfile = null;
	protected String namefilter = null;

	public FilterStream withNameFilter(String v) {
		this.namefilter = v;
		return this;
	}

	public FilterStream() {
    }

	@Override
	public void init(StackEntry stack, XElement el) {
	}
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) 
    		return this.consumer.handle(slice);
    	
    	if ((this.jfile != null) && (this.jfile == slice.file)) {
			return this.consumer.handle(slice);
		}

		if ((this.jfile == null) && this.namefilter.equals(slice.file.getName())) {
			this.jfile = slice.file;
			return this.consumer.handle(slice);
		}

		slice.release();

		return ReturnOption.CONTINUE;
    }

    // should we close after got the one file?
}
