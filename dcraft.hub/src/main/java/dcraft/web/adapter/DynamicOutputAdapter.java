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
package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.count.CountHub;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.web.IOutputWork;
import dcraft.web.WebController;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.PrintStream;
import java.nio.file.Path;

public class DynamicOutputAdapter extends ChainWork implements IOutputWork {
	protected Script script = null;
	public CommonPath webpath = null;
	public Path file = null;
	protected MimeInfo mime = null;
	protected boolean runonce = false;
	
	@Override
	public Path getFile() {
		return this.file;
	}
	
	@Override
	public CommonPath getPath() {
		return this.webpath;
	}
	
	@Override
	public void init(Site site, Path file, CommonPath web, String view) {
		this.webpath = web;
		this.file = file;
		this.mime = ResourceHub.getResources().getMime().getMimeTypeForPath(this.file);
	}
	
	
	public Script getSource() throws OperatingContextException {
		if (this.script != null)
			return this.script;
		
		this.script = Script.of(this.file);

		return this.script;
	}

	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		if (this.runonce) {
			super.run(ctx);
			return;
		}
		
		this.runonce = true;
		
		OperationContext wctx = OperationContext.getOrThrow();

		CountHub.countObjects("dcWebOutDynamicCount-" + wctx.getTenant().getAlias(), this);

		Script script = this.getSource();

		if (script == null) {
			ctx.clearExitCode();

			// no source - then run as a SSI
			/* TODO restore
			if (ctx.getSite().getHtmlMode() == HtmlMode.Strict) {
				this.source = xr = new Html();

				xr.with(W3.tag("h1")
					.withText("Unable to parse page error!!")
				);
			}
			else {
				super.run(ctx);
			}
			*/
			
			super.run(ctx);
			return;
		}
		
		BooleanStruct isDynamic = (BooleanStruct) StackUtil.queryVariable(null, "_Controller.Request.IsDynamic");
		
		WebController wctrl = (WebController) wctx.getController();

		if (script.getXml().hasAttribute("Badges")) {
			String[] tags = script.getXml().getAttribute("Badges").split(",");
			
			boolean auth = ((tags == null) || wctx.getUserContext().isTagged(tags));
			
			if (!auth) {
				// TODO replace with pages
				if ((isDynamic != null) && isDynamic.getValue()) {
					wctrl.getResponse().setHeader("Content-Type", "application/javascript");
					PrintStream ps = wctrl.getResponse().getPrintStream();
					ps.println("dc.pui.Loader.failedPageLoad(1);");
					wctrl.send();
				}
				else {
					wctrl.getResponse().setStatus(HttpResponseStatus.FOUND);
					wctrl.getResponse().setHeader("Location", "/");
					wctrl.send();
				}
		
				// TODO fix
				ctx.returnEmpty();
				return;
			}
		}
		
		RecordStruct req = wctrl.getFieldAsRecord("Request");

		// TODO cleanup everything about wctrl - including making this part more transparent
		RecordStruct page = RecordStruct.record()
				.with("Path", req.getFieldAsString("Path"))
				.with("PathParts", ListStruct.list(req.getFieldAsString("Path").substring(1).split("/")))
				.with("OriginalPath", req.getFieldAsString("OriginalPath"))
				.with("OriginalPathParts", ListStruct.list(req.getFieldAsString("OriginalPath").substring(1).split("/")))
				.with("PageClass", req.getFieldAsString("Path").substring(1).replace('/', '-'));
		
		OperationContext.getOrThrow().getController().addVariable("Page", page);
		
		this
				.then(script.toWork())
				.then(DynamicOutputWriter.of(script));
		
		super.run(ctx);
	}
}
