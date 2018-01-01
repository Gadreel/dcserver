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
package dcraft.web;

import dcraft.hub.op.OperatingContextException;
import io.netty.handler.codec.http.HttpContent;

public interface IContentDecoder {
	void offer(HttpContent chunk) throws OperatingContextException;
	void release() throws OperatingContextException;
}
