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
import dcraft.web.ui.UIUtil;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.PrintStream;
import java.nio.file.Path;

public class DynamicOutputAdapter extends SsiOutputAdapter implements IOutputWork {
	protected Script script = null;

	public Script getSource() throws OperatingContextException {
		if (this.script != null)
			return this.script;
		
		this.script = Script.of(this.file);

		return this.script;
	}

	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		if (! this.init) {
			super.init(ctx);
			this.init = true;
		}

		if (this.runonce) {
			super.run(ctx);
			return;
		}

		CountHub.countObjects("dcWebOutDynamicCount-" + ctx.getTenant().getAlias(), this);

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

		this.runonce = true;
		
		BooleanStruct isDynamic = (BooleanStruct) StackUtil.queryVariable(null, "_Controller.Request.IsDynamic");
		
		WebController wctrl = (WebController) ctx.getController();

		if (script.getXml().hasAttribute("Badges")) {
			String[] tags = script.getXml().getAttribute("Badges").split(",");
			
			boolean auth = ((tags == null) || ctx.getUserContext().isTagged(tags));
			
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
					wctrl.getResponse().setHeader("Cache-Control", "no-cache");		// in case they login later, FireFox was using cache
					wctrl.send();
				}
		
				// TODO fix
				ctx.returnEmpty();
				return;
			}
		}

		// set at least one header so that the Headers struct is available in script
		wctrl.getResponse().setHeader("Cache-Control", "no-cache");

		this
				.then(UIUtil.dynamicToWork(ctx, script))
				.then(DynamicOutputWriter.of(script));
		
		super.run(ctx);
	}
}
