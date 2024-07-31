package dcraft.core.doc.adapter;

import dcraft.core.doc.DocUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.TaskContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

abstract public class VAdapter extends BaseAdapter {
	protected boolean iamavariant = false;

	// shared fields
	protected ResourceFileInfo summary = null;
	protected List<ResourceFileInfo> addendums = null;
	protected ResourceFileInfo detail = null;
	protected ResourceFileInfo access = null;

	// variant fields
	protected ResourceFileInfo schema = null;
	protected ResourceFileInfo config = null;

	// folder fields
	protected List<CommonPath> subvariants = new ArrayList<>();
	protected List<CommonPath> subfolders = new ArrayList<>();

	@Override
	protected void init(TaskContext taskContext) throws OperatingContextException {
		super.init(taskContext);

		RecordStruct req = taskContext.getFieldAsRecord("Params");

		ListStruct locales = req.getFieldAsList("Locales");
		String domain = req.getFieldAsString("Domain");
		CommonPath path = CommonPath.from(req.getFieldAsString("Path"));

		CommonPath vpath = DocUtil.folderToVFolder(path);

		if (this.hasFolder(vpath)) {
			this.iamavariant = true;

			this.summary = this.findMarkdownFile(vpath, "summary", locales);
			this.detail = this.findMarkdownFile(vpath, "detail", locales);
			this.addendums = this.findAllMarkdownFiles(vpath, "addendum", locales);
			this.schema = this.findFile(vpath.resolve("schema.xml"));
			this.config = this.findFile(vpath.resolve("config.json"));

			this.access = this.findClosestFile(vpath, "access.json");
		}
		else if (this.hasFolder(path)) {
			this.summary = this.findMarkdownFile(path, "summary", locales);
			this.detail = this.findMarkdownFile(path, "detail", locales);

			this.access = this.findClosestFile(path, "access.json");

			Set<CommonPath> subfolders = this.findSubFolderSet(path);

			for (CommonPath subpath : subfolders) {
				//ResourceFileInfo info = this.findMarkdownFile(subpath, "summary", locales);

				if (DocUtil.isVFolder(subpath)) {
					this.subvariants.add(subpath);
				}
				else {
					this.subfolders.add(subpath);
				}
			}
		}
		else {
			Logger.error("Missing or bad document path: " + path + " in " + domain);
			taskContext.returnEmpty();
			return;
		}

		/*
		CharSequence text = MailUtil.processSSIIncludes(IOUtil.readEntireFile(VAdapter.this.file));

		this.textToScript(proc, resp, text);

		 */
	}
}
