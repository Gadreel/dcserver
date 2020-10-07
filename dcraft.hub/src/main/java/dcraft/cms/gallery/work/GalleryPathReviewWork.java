package dcraft.cms.gallery.work;

import dcraft.cms.feed.work.FeedFileReviewWork;
import dcraft.cms.feed.work.FeedPathReviewWork;
import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class GalleryPathReviewWork extends StateWork {
	static public GalleryPathReviewWork work(CommonPath path) {
		GalleryPathReviewWork work = new GalleryPathReviewWork();
		work.path = path;

		return work;
	}

	protected CommonPath path = null;

	protected ListStruct reviewresults = ListStruct.list();
	protected RecordStruct reviewresult = RecordStruct.record();
	protected Deque<FileStoreFile> files = new ArrayDeque<>();

	protected StateWorkStep reviewFile = null;
	protected StateWorkStep done = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(reviewFile = StateWorkStep.of("Check File Index", this::doFile))
				.withStep(done = StateWorkStep.of("Done", this::doDone));

		FileStoreVault currentVault = OperationContext.getOrThrow().getSite().getGalleriesVault();
		
		this.files.addLast(currentVault.getFileStore().fileReference(this.path));
	}

	public StateWorkStep doFile(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		FileStoreFile file = this.files.pollFirst();

		if ((file == null) || ! file.exists())
			return StateWorkStep.NEXT;

		// prep to collect messages, return
		ListStruct messages = ListStruct.list();

		this.reviewresult = RecordStruct.record()
				.with("Path", file.getPath())
				.with("Messages", messages);

		this.reviewresults.with(GalleryPathReviewWork.this.reviewresult);

		// do the review
		String metapath = (file.getPath().endsWith(".v")) ? file.getPathAsCommon().getParent().toString() : file.getPath();

		RecordStruct meta = Struct.objectToRecord(OperationContext.getOrThrow().getSite().getJsonResource("galleries", metapath + "/meta.json", null));

		if (meta == null) {
			messages.with(RecordStruct.record()
				.with("Level", "Error")
				.with("Message", "Unable to review gallery file/folder - missing meta file")
			);
		}
		else if (file.isFolder()) {
			if (meta.isNotFieldEmpty("Variations")) {
				ListStruct varis = meta.getFieldAsList("Variations");

				boolean foundthumb = false;
				boolean foundfull = false;

				for (int i = 0; i < varis.size(); i++) {
					RecordStruct vari = varis.getItemAsRecord(i);

					if ("full".equals(vari.getFieldAsString("Alias")))
						foundfull = true;
					else if ("thumb".equals(vari.getFieldAsString("Alias")))
						foundthumb = true;
				}

				if (! foundthumb ) {
					messages.with(RecordStruct.record()
						.with("Level", "Error")
						.with("Message", "Missing 'thumb' variation, this should be added")
					);

				}

				if (! foundfull) {
					messages.with(RecordStruct.record()
						.with("Level", "Warn")
						.with("Message", "Missing 'full' variation, typically this is used")
					);
				}
			}
			else {
				messages.with(RecordStruct.record()
					.with("Level", "Error")
					.with("Message", "Meta file is missing variations")
				);
			}
		}
		else if (meta.isNotFieldEmpty("Variations")) {
			ListStruct varis = meta.getFieldAsList("Variations");

			for (int i = 0; i < varis.size(); i++) {
				RecordStruct vari = varis.getItemAsRecord(i);

				String alias = vari.getFieldAsString("Alias");

				if (StringUtil.isNotEmpty(alias)) {
					Path image = OperationContext.getOrThrow().getSite().findSectionFile("galleries", file.getPath() + "/" + alias + ".jpg", null);

					try {
						if ((image == null) || Files.notExists(image)) {
							messages.with(RecordStruct.record()
								.with("Level", "Error")
								.with("Message", "Missing variation file for: " + alias)
							);
						}
						// refine this to be WxH smart - compression smart
						else if (Files.size(image) > 1000000) {
							messages.with(RecordStruct.record()
								.with("Level", "Warn")
								.with("Message", "Variation file for: " + alias + " is larger than expected")
							);
						}
					}
					catch (IOException x) {
						messages.with(RecordStruct.record()
							.with("Level", "Error")
							.with("Message", "Trouble reading variation file: " + alias + " : " + x)
						);
					}
				}
			}
		}

		return StateWorkStep.REPEAT;
	}

	public StateWorkStep doDone(TaskContext trun) throws OperatingContextException {
		trun.setResult(this.reviewresult);

		return StateWorkStep.NEXT;
	}
}
