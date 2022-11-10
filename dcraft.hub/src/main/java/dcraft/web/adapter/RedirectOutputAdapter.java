package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.struct.RecordStruct;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.web.IOutputWork;
import dcraft.web.Response;
import dcraft.web.WebController;
import dcraft.web.ui.UIUtil;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedNioFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RedirectOutputAdapter implements IOutputWork {
	static public RedirectOutputAdapter of(CommonPath path) {
		RedirectOutputAdapter adapter = new RedirectOutputAdapter();
		adapter.webpath = path;
		return adapter;
	}

	public CommonPath webpath = null;

	@Override
	public CommonPath getPath() {
		return this.webpath;
	}
	
	@Override
	public void init(Site site, Path file, CommonPath web, String view) {
		this.webpath = web;
	}
	
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		WebController wctrl = (WebController) ctx.getController();

		Response resp = wctrl.getResponse();

		resp.setStatus(HttpResponseStatus.FOUND);	// not permanent
		resp.setHeader(HttpHeaderNames.LOCATION.toString(), this.webpath.toString());
		resp.setHeader("Cache-Control", "no-cache");		// in case they login later, FireFox was using cache

		wctrl.sendRead();

		ctx.returnEmpty();
	}
}
