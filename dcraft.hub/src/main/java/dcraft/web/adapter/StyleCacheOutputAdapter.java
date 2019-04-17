package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.IStreamSource;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.GzipStream;
import dcraft.stream.file.JoinStream;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.util.chars.Utf8Encoder;
import dcraft.web.HttpDestStream;
import dcraft.web.IOutputWork;
import dcraft.web.Response;
import dcraft.web.WebController;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.file.Path;

public class StyleCacheOutputAdapter extends ChainWork implements IOutputWork {
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
		resp.setHeader("Content-Encoding", "gzip");

		wctrl.sendStart(0);
		
		Site site = OperationContext.getOrThrow().getSite();
		
		IStreamSource styles = site.webCacheStyles();
		
		HttpDestStream dest = HttpDestStream.dest();
		dest.setHeaderSent(true);

		// the stream work should happen after `resume` in decoder above
		this.then(StreamWork.of(styles, JoinStream.of("dc.cache.css"), GzipStream.create(), dest));
	}
}
