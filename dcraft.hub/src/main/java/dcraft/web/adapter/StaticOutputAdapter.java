package dcraft.web.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
import dcraft.xml.XElement;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.stream.ChunkedNioFile;

public class StaticOutputAdapter implements IOutputWork {
	public CommonPath webpath = null;
	public Path file = null;
	protected MimeInfo mime = null;

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
	
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		WebController wctrl = (WebController) ctx.getController();
		
		try {
			long when = Files.getLastModifiedTime(this.file).toMillis();
			
			CountHub.countObjects("dcWebOutStaticCount-" + OperationContext.getOrThrow().getTenant().getAlias(), this);
			
			Response resp = wctrl.getResponse();
			RecordStruct request = wctrl.getFieldAsRecord("Request");
			
			String mtype = this.mime.getMimeType();
			
			resp.setHeader("Content-Type", mtype);
			resp.setDateHeader("Date", System.currentTimeMillis());
			resp.setDateHeader("Last-Modified", when);
			resp.setHeader("X-UA-Compatible", "IE=Edge,chrome=1");
			resp.setHeader("Access-Control-Allow-Origin", "*");		// needed for Edge 17 loading fonts
			resp.setHeader("Vary", "Origin");		// needed for Edge 17 loading fonts
			
			// TODO configure this someday

			// dc-cache indicates max cache life - 1 yr
			if (request.getFieldAsRecord("Parameters").isNotFieldEmpty("dc-cache"))
				resp.setHeader("Cache-Control", "public, max-age=31536000");
			else if ("text/css".equals(mtype) || "application/javascript".equals(mtype) || "text/javascript".equals(mtype) || "application/json".equals(mtype))
				resp.setHeader("Cache-Control", "no-cache");
			else
				resp.setHeader("Cache-Control", "max-age=900");
			
			if (request.getFieldAsRecord("Headers").isNotFieldEmpty("If-Modified-Since")) {
				long dd = when - UIUtil.getDateHeader(request.getFieldAsRecord("Headers"), "If-Modified-Since");
				
				// getDate does not return consistent results because milliseconds
				// are not cleared correctly see:
				// https://sourceforge.net/tracker/index.php?func=detail&aid=3162870&group_id=62369&atid=500353
				// so ignore differences of less than 1000, they are false positives
				if (dd < 1000) {
					wctrl.sendNotModified();
					ctx.returnEmpty();
					return;
				}
			}

			boolean suggestdownload = request.getFieldAsRecord("Headers").getFieldAsBooleanOrFalse("X-For-Download");

			if (! suggestdownload && request.getFieldAsRecord("Parameters").isNotFieldEmpty("dc-download"))
				suggestdownload = true;

			if (request.getFieldAsRecord("Parameters").isNotFieldEmpty("dc-download-as")) {
				String fname = request.selectAsString("Parameters.dc-download-as.0");

				resp.setHeader("Content-Disposition", "attachment; filename=\"" + fname + "\"");

				// TODO probably support mime type here too
			}
			else if (request.getFieldAsRecord("Parameters").isNotFieldEmpty("dc-inline-as")) {
				String fname = request.selectAsString("Parameters.dc-inline-as.0");

				resp.setHeader("Content-Disposition", "inline; filename=\"" + fname + "\"");

				// TODO probably support mime type here too
			}
			else if (suggestdownload) {
				resp.setHeader("Content-Disposition", "attachment; filename=\"" + webpath.getFileName() + "\"");
			}

			// send file size if we aren't compressing
			if (! this.mime.isCompress()) {
				resp.setHeader(HttpHeaders.Names.CONTENT_ENCODING, HttpHeaders.Values.IDENTITY);
				wctrl.sendStart(Files.size(this.file));
			} else {
				wctrl.sendStart(0);
			}
			
			// TODO send from memory cache if small enough
			try {
				wctrl.send(new HttpChunkedInput(new ChunkedNioFile(this.file.toFile())));        // TODO not ideal, cleanup so direct reference to path is not needed
			}
			catch (IOException x) {
				// TODO improve support
			}
			
			wctrl.sendEnd();
		}
		catch (IOException x) {
			Logger.error("Error executing static adapter: " + x);
		}
		
		ctx.returnEmpty();
	}
}
