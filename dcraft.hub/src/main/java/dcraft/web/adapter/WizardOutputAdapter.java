package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.struct.*;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.util.StringUtil;
import dcraft.web.HttpDestStream;
import dcraft.web.IOutputWork;
import dcraft.web.Response;
import dcraft.web.WebController;
import dcraft.xml.XElement;

import java.nio.file.Path;

public class WizardOutputAdapter extends ChainWork implements IOutputWork {
	public CommonPath webpath = null;
	protected MimeInfo mime = null;

	@Override
	public CommonPath getPath() {
		return this.webpath;
	}
	
	@Override
	public void init(Site site, Path file, CommonPath web, String view) {
		this.webpath = web;
		this.mime = ResourceHub.getResources().getMime().getMimeTypeForName(web.getFileName());
	}
	
	@Override
	public void init(TaskContext ctx) throws OperatingContextException {
		WebController wctrl = (WebController) ctx.getController();
		
		Response resp = wctrl.getResponse();
		
		String mtype = this.mime.getMimeType();
		
		resp.setHeader("Content-Type", mtype);
		resp.setDateHeader("Date", System.currentTimeMillis());
		resp.setHeader("X-UA-Compatible", "IE=Edge,chrome=1");

		wctrl.sendStart(0);

		String script = "/* no script */";
		String form = "none";

		RecordStruct request = wctrl.getFieldAsRecord("Request");
		RecordStruct params = request.getFieldAsRecord("Parameters");
		ListStruct fpraram = params.getFieldAsList("form");

		if (fpraram != null) {
			String tform = fpraram.getItemAsString(0);

			// TODO check tform is a valid file name and not some hack
			if (StringUtil.isNotEmpty(tform)) {
				form = tform;

				script = "/* missing form: " + form + "  */";

				XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);

				if (mform != null) {
					XElement wiz = mform.selectFirst("Wizard");

					if (wiz != null) {
						RecordStruct wform = Struct.objectToRecord(CompositeParser.parseJson(wiz.getText()));

						if (wform != null) {
							StringBuilder sb = new StringBuilder();
							
							String falias = wform.getFieldAsString("Alias", "default");
							ListStruct tabs = wform.getFieldAsList("Tabs");
							String starttab = null;
							
							if (tabs != null) {
								for (BaseStruct ts : tabs.items()) {
									RecordStruct tab = Struct.objectToRecord(ts);
									
									String talias = tab.getFieldAsString("Alias", "default");
									
									if (starttab == null)
										starttab = talias;
									
									tab.with("Path", "/dcm/forms/w/" + falias + "/" + talias);
								}
							}
							
							sb.append("dc.pui.Apps.Menus.z" + form + " = " + wform.toPrettyString() + "\n\n");
							
							String type = wform.getFieldAsString("DataType");
							
							DataType dt = SchemaHub.getType(type);
							
							if (dt != null)
								sb.append("dc.schema.Manager.load([ " + dt.toJsonDef().toPrettyString() + " ]);\n\n");
							
							sb.append("dc.pui.FormManager.registerForm('" + falias + "', 'z" + form + "');\n\n");
							
							sb.append("dc.pui.App.startTab({\n" +
									"\tTab: '" + (starttab != null ? starttab : "default") + "',\n" +
									"\tMenu: 'z" + form + "'\n" +
									"});\n\n");
							
							script = sb.toString();
						}
					}
				}
			}
		}

		StreamFragment fragment = MemoryStoreFile.of(CommonPath.from(webpath + "/" + form +  ".js"))
						.with(script).allocStreamSrc();
		
		HttpDestStream dest = HttpDestStream.dest();
		dest.setHeaderSent(true);
		
		fragment.withAppend(dest);
		
		// the stream work should happen after `resume` in decoder above
		this.then(StreamWork.of(fragment));
	}
}
