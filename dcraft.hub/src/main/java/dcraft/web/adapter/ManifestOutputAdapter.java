package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.IStreamSource;
import dcraft.stream.StreamWork;
import dcraft.stream.file.GzipStream;
import dcraft.stream.file.JoinStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.web.HttpDestStream;
import dcraft.web.IOutputWork;
import dcraft.web.Response;
import dcraft.web.WebController;

import java.nio.file.Path;

public class ManifestOutputAdapter extends ChainWork implements IOutputWork {
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
		resp.setHeader("Cache-Control", "max-age=86400");  // 1 day

		wctrl.sendMessage(RecordStruct.record()
				.with("name", "")
				.with("short_name", "")
				.with("theme_color", "#ffffff")
				.with("background_color", "#ffffff")
				.with("display", "standalone")
				.with("short_name", "")
				.with("icons", ListStruct.list()
						.with(RecordStruct.record()
								.with("src", "/imgs/logo192solid.png")
								.with("sizes", "192x192")
								.with("type", "image/png")
						)
						.with(RecordStruct.record()
								.with("src", "/imgs/logo512solid.png")
								.with("sizes", "512x512")
								.with("type", "image/png")
						)
				)
		);
	}
}
