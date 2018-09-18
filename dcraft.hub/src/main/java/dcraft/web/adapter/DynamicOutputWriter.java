package dcraft.web.adapter;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.web.WebController;
import dcraft.web.ui.HtmlPrinter;
import dcraft.web.ui.JsonPrinter;
import dcraft.xml.XElement;
import dcraft.xml.XmlPrinter;

import java.io.PrintStream;

public class DynamicOutputWriter implements IWork {
	static public DynamicOutputWriter of(Script script) {
		DynamicOutputWriter writer = new DynamicOutputWriter();
		writer.script = script;
		return writer;
	}
	
	protected Script script = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		OperationContext wctx = OperationContext.getOrThrow();
		
		boolean isDynamic = Struct.objectToBooleanOrFalse(StackUtil.queryVariable(null, "_Controller.Request.IsDynamic"));
		
		WebController wctrl = (WebController) wctx.getController();
		
		if (isDynamic) {
			wctrl.getResponse().setHeader("Content-Type", "application/javascript");
		}
		else {
			wctrl.getResponse().setHeader("Content-Type", "text/html; charset=utf-8");
			wctrl.getResponse().setHeader("X-UA-Compatible", "IE=Edge,chrome=1");
		}

		XElement doc = this.script.getXml();

		if ("Server".equalsIgnoreCase(doc.getAttribute("NoCache")))
			wctrl.getResponse().setHeader("Cache-Control", "private, no-store, max-age=0, no-cache, must-revalidate, post-check=0, pre-check=0");
		else
			wctrl.getResponse().setHeader("Cache-Control", "no-cache");

		PrintStream ps = wctrl.getResponse().getPrintStream();
		
		XmlPrinter prt = isDynamic ? new JsonPrinter() : new HtmlPrinter();
		
		try {
			prt.setFormatted(true);
			prt.setOut(ps);
			prt.print(doc);
		}
		catch (OperatingContextException x) {
			Logger.warn("output restricted: " + x);
		}
		
		wctrl.send();
		
		// TODO embed this script into the first request - thus making the page a single request
		//if (! wctrl.isDynamic())
		//	octx.getSession().setPageCache(DynamicOutputAdapter.this.webpath, fxr);
		
		taskctx.returnEmpty();
	}
}
