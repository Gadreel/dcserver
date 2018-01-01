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

import dcraft.hub.op.OperatingContextException;
import dcraft.log.count.CountHub;
import dcraft.script.Script;
import dcraft.task.TaskContext;

public class MarkdownOutputAdapter extends DynamicOutputAdapter  {

	@Override
	public Script getSource() throws OperatingContextException {
		/* TODO recreate all
		if (this.source != null)
			return this.source;

		CharSequence md = IOUtil.readEntireFile(this.file);

		if (md.length() == 0) 
			return null;
		
		md = this.processIncludes(wctx, md);
		
		try {
			Html html = new Html();
			
			/* TODO create a mixin
			BufferedReader bufReader = new BufferedReader(new StringReader(md.toString()));
	
			String line = bufReader.readLine();
			
			while (StringUtil.isNotEmpty(line)) {
				int pos = line.indexOf(':');
				
				if (pos == -1)
					break;
				
				html.withAttribute(line.substring(0, pos), line.substring(pos + 1).trim());
	
				line = bufReader.readLine();
			}
			
			// see if there is more - the body
			if (line != null) {
				Base body = html.hasNotEmptyAttribute("Skeleton")
						? (Base) PagePart.tag().withAttribute("For", "article")
						: Body.tag();
				
				html.with(body);
				
				Markdown mtag = new Markdown();
				
				body.with(mtag);  
				
				XText mdata = new XText();
				mdata.setCData(true);
				
				mtag.with(mdata);
				
				line = bufReader.readLine();
				
				while (line != null) {
					mdata.appendBuffer(line);
					mdata.appendBuffer("\n");
		
					line = bufReader.readLine();
				}
				
				mdata.closeBuffer();
			}
			* /
			
			this.source = html;
		}
		catch (Exception x) {
			System.out.println("md parse issue");
		}
		
		return this.source;
		*/
		
		return null;
	}

	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		// be sure we count only once
		CountHub.countObjects("dcWebOutMarkdownCount-" + ctx.getTenant().getAlias(), this);
		
		super.run(ctx);
	}
}
