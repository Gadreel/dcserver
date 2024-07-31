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

abstract public class LWAdapter extends BaseAdapter {
	protected boolean iamascript = false;

	// shared fields
	protected ResourceFileInfo summary = null;
	protected List<ResourceFileInfo> addendums = null;
	protected ResourceFileInfo detail = null;

	// variant fields
	protected ResourceFileInfo schema = null;
	protected ResourceFileInfo config = null;

	// folder fields
	protected List<CommonPath> subscripts = new ArrayList<>();
	protected List<CommonPath> subfolders = new ArrayList<>();

	@Override
	protected void init(TaskContext taskContext) throws OperatingContextException {
		super.init(taskContext);

		RecordStruct req = taskContext.getFieldAsRecord("Params");

		ListStruct locales = req.getFieldAsList("Locales");
		String domain = req.getFieldAsString("Domain");
		CommonPath path = CommonPath.from(req.getFieldAsString("Path"));

		CommonPath vpath = path.getParent().resolve(path.getFileName() + ".dcs.xml");

		ResourceFileInfo scriptInfo = this.findFile(vpath);

		if (scriptInfo != null) {
			this.iamascript = true;

			this.summary = this.findMarkdownFile(path.getParent(), path.getFileName(), locales);
		}
		else if (this.hasFolder(path)) {
			this.summary = this.findMarkdownFile(path, "summary", locales);
			this.detail = this.findMarkdownFile(path, "detail", locales);
			this.addendums = this.findAllMarkdownFiles(path, "addendum", locales);

			Set<CommonPath> subfolders = this.findSubFolderSet(path);

			for (CommonPath subpath : subfolders) {
				this.subfolders.add(subpath);
			}

			Set<CommonPath> subfiles = this.findSubFileSet(path);

			for (CommonPath subpath : subfiles) {
				this.subscripts.add(subpath);
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
