package dcraft.web.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.IOUtil;
import dcraft.util.MimeInfo;
import dcraft.util.io.ByteBufWriter;
import dcraft.web.IOutputWork;
import dcraft.web.Response;
import dcraft.web.WebController;

public class SsiOutputAdapter extends ChainWork implements IOutputWork {
	public static final Pattern SSI_VIRTUAL_PATTERN = Pattern.compile("<!--#include virtual=\"(.*)\" -->");
	
	public CharSequence processIncludes(WebController wctrl, CharSequence content) throws OperatingContextException {
		OperationContext ctx = OperationContext.getOrThrow();
		
		boolean checkmatches = true;
		
		while (checkmatches) {
			checkmatches = false;
			Matcher m = SsiOutputAdapter.SSI_VIRTUAL_PATTERN.matcher(content);

			while (m.find()) {
				String grp = m.group();

				String vfilename = grp.substring(1, grp.length() - 1);
				
				vfilename = vfilename.substring(vfilename.indexOf('"') + 1);
				vfilename = vfilename.substring(0, vfilename.indexOf('"'));

				//System.out.println("include v file: " + vfilename);
				
				Path sf = ctx.getSite().findSectionFile("www", vfilename, wctrl.getFieldAsRecord("Request").getFieldAsString("View"));
				
				if (sf == null) 
					continue;
				
				CharSequence val = IOUtil.readEntireFile(sf);
				
				if (val == null)
					val = "";
				
				content = content.toString().replace(grp, val);
				checkmatches = true;
			}
		}	
		
		return content;
	}
	
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
	
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		if (this.runonce) {
			super.run(ctx);
			return;
		}
		
		this.runonce = true;
		
		WebController wctrl = (WebController) ctx.getController();
		
		try {
			long when = Files.getLastModifiedTime(this.file).toMillis();
			
			CountHub.countObjects("dcWebOutSsiCount-" + OperationContext.getOrThrow().getTenant().getAlias(), this);
			
			Response resp = wctrl.getResponse();
			
			resp.setHeader("Content-Type", this.mime.getType());
			resp.setDateHeader("Date", System.currentTimeMillis());
			resp.setDateHeader("Last-Modified", when);
			resp.setHeader("X-UA-Compatible", "IE=Edge,chrome=1");
			resp.setHeader("Cache-Control", "no-cache");
			
			// because of Macro support we need to rebuild this page every time it is requested
			CharSequence content = this.processIncludes(wctrl, IOUtil.readEntireFile(this.file));
			
			// TODO restore - content = ctx.expandMacros(content);
			
			// TODO add compression
			//if (asset.getCompressed())
			//	resp.setHeader("Content-Encoding", "gzip");
			
			ByteBufWriter buffer = ByteBufWriter.createLargeHeap();
			
			buffer.write(content);
			
			wctrl.sendStart(buffer.readableBytes());
			// this does get closed/released by send
			
			wctrl.send(buffer);
			
			wctrl.sendEnd();
		}
		catch (IOException x) {
			Logger.error("Error executing static adapter: " + x);
		}
		
		//ctx.returnEmpty();
		super.run(ctx);
	}
}
