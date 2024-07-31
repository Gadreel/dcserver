package dcraft.mail.dcc;

import dcraft.db.util.DocumentIndexBuilder;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.OutputWrapper;
import dcraft.util.web.DateParser;
import dcraft.web.WebController;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.inst.*;
import dcraft.xml.*;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UIUtil {

	static public Task mockWebRequestTask(String tenant, String site, String title) throws OperatingContextException {
		WebController wctrl = WebController.forChannel(null, null);		// TODO someday load service settings if needed

		OperationContext wctx = OperationContext.context(UserContext.rootUser(tenant, site), wctrl);

		return Task.of(wctx)
				.withTitle(title);
	}

	static public Task mockWebRequestTask(Site site, String title) throws OperatingContextException {
		WebController wctrl = WebController.forChannel(null, null);		// TODO someday load service settings if needed

		OperationContext wctx = OperationContext.context(UserContext.rootUser(site), wctrl);

		return Task.of(wctx)
				.withTitle(title);
	}

	static public IWork dynamicToWork(TaskContext ctx, Path script) throws OperatingContextException {
		return UIUtil.dynamicToWork(ctx, Script.of(script));
	}

	static public IWork dynamicToWork(TaskContext ctx, Script script) throws OperatingContextException {
		WebController wctrl = (WebController) ctx.getController();

		RecordStruct req = wctrl.getFieldAsRecord("Request");

		String pathclass = req.getFieldAsString("Path").substring(1).replace('/', '-');

		if (pathclass.endsWith(".html"))
			pathclass = pathclass.substring(0, pathclass.length() - 5);
		else if (pathclass.endsWith(".dcs.xml"))
			pathclass = pathclass.substring(0, pathclass.length() - 8);

		pathclass = pathclass.replace('.', '_');

		// TODO cleanup everything about wctrl - including making this part more transparent
		RecordStruct page = RecordStruct.record()
				.with("Path", req.getFieldAsString("Path"))
				.with("PathParts", ListStruct.list((Object[]) req.getFieldAsString("Path").substring(1).split("/")))
				.with("OriginalPath", req.getFieldAsString("OriginalPath"))
				.with("OriginalPathParts", ListStruct.list((Object[]) req.getFieldAsString("OriginalPath").substring(1).split("/")))
				.with("PageClass", pathclass);

		wctrl.addVariable("Page", page);

		wctrl.addVariable("_Page", script.getXml());

		return script.toWork();
	}

	static public String formatText(RecordStruct proc, XElement body) {
		if (body != null) {
			return StringUtil.stripWhitespace(body.getText());
		}

		return null;
	}

	static public String formatHtml(RecordStruct proc, XElement body) {
		if (body != null) {
			body.setName("body");
			body.attr("style", "font-size: 14pt;");

			XElement root = XElement.tag("html")
					.with(XElement.tag("head")
							.with(XElement.tag("meta")
									.attr("name", "viewport")
									.attr("content", "width=device-width")
							)
							.with(XElement.tag("meta")
									.attr("http-equiv", "Content-Type")
									.attr("content", "text/html; charset=UTF-8")
							)
							.with(XElement.tag("title").withText(proc.selectAsString("Config.Subject").trim()))
					)
					.with(body);

			Memory content = new Memory();

			try (PrintStream ps = new PrintStream(new OutputWrapper(content), true, "UTF-8")) {
				XmlPrinter prt = new HtmlPrinter();

				prt.setFormatted(true);
				prt.setOut(ps);
				prt.print(root);

				return content.toString();
			}
			catch (OperatingContextException | UnsupportedEncodingException x) {
				Logger.warn("output restricted: " + x);
			}
		}

		return null;
	}

}
