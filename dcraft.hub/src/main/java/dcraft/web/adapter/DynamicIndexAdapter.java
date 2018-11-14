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

import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.struct.ListStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.web.IIndexWork;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;

public class DynamicIndexAdapter extends ChainWork implements IIndexWork {
	public CommonPath webpath = null;
	public Path file = null;
	protected MimeInfo mime = null;
	protected Script script = null;
	protected DocumentIndexBuilder indexer = DocumentIndexBuilder.index();
	
	public Path getFile() {
		return this.file;
	}
	
	@Override
	public CommonPath getPath() {
		return this.webpath;
	}
	
	@Override
	public DocumentIndexBuilder getIndexer() {
		return this.indexer;
	}
	
	@Override
	public void init(Site site, Path file, CommonPath web, String view) {
		this.webpath = web;
		this.file = file;
		this.mime = ResourceHub.getResources().getMime().getMimeTypeForPath(this.file);
	}

	public Script getSource() throws OperatingContextException {
		if (this.script != null)
			return this.script;
		
		this.script = Script.of(this.file);

		return this.script;
	}
	
	@Override
	protected void init(TaskContext ctx) throws OperatingContextException {
		Script script = this.getSource();

		if (script == null) {
			ctx.clearExitCode();
			ctx.complete();
			return;
		}

		this
				.then(UIUtil.dynamicToWork(ctx, script))
				.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						XElement root = script.getXml();

						indexer.setTitle(root.selectFirst("head").selectFirst("title").getText());

						for (XElement meta : root.selectFirst("head").selectAll("meta")) {
							if ("robots".equals(meta.getAttribute("name"))) {
								indexer.setDenyIndex(meta.getAttribute("content", "index").contains("noindex"));
							}
							else if ("description".equals(meta.getAttribute("name"))) {
								indexer.setSummary(meta.getAttribute("content"));
							}
						}

						if (root.hasNotEmptyAttribute("Badges")) {
							String[] tags = root.getAttribute("Badges").split(",");

							indexer.setBadges(ListStruct.list(tags));
						}

						if (root.hasNotEmptyAttribute("SortHint")) {
							indexer.setSortHint(root.getAttribute("SortHint"));
						}

						UIUtil.indexFinishedDocument(root.selectFirst("body"), indexer);

						indexer.endSection();	// just in case

						taskctx.returnEmpty();
					}
				});
	}
}
