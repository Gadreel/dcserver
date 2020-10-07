package dcraft.cms.gallery.work;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class GalleryReviewWork extends GalleryPathReviewWork {
	static public GalleryReviewWork work() {
		GalleryReviewWork work = new GalleryReviewWork();

		return work;
	}

	protected Deque<FileStoreFile> folders = new ArrayDeque<>();

	protected StateWorkStep scanFolder = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(scanFolder = StateWorkStep.of("Scan Folder", this::doFolder))
				.withStep(reviewFile = StateWorkStep.of("Check File Index", this::doFile))
				.withStep(done = StateWorkStep.of("Done", this::doDone));

		FileStoreVault currentVault = OperationContext.getOrThrow().getSite().getGalleriesVault();
		
		this.folders.addLast(currentVault.getFileStore().fileReference(CommonPath.from("/")));
	}
	
	public StateWorkStep doFolder(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();
		
		FileStoreFile folder = this.folders.pollFirst();
		
		if (folder == null)
			return StateWorkStep.NEXT;

		folder.getFolderListing(new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				if (result != null) {
					for (FileStoreFile file : result) {
						//System.out.println(" - " + file.isFolder() + " : " + file);
						
						if (file.isFolder())
							GalleryReviewWork.this.folders.addLast(file);

						if (file.isFolder() || file.getName().endsWith(".v"))
							GalleryReviewWork.this.files.addLast(file);
					}
				}
				
				trun.resume();	// try next folder
			}
		});
		
		return StateWorkStep.WAIT;
	}

	public StateWorkStep doDone(TaskContext trun) throws OperatingContextException {
		trun.setResult(this.reviewresults);

		return StateWorkStep.NEXT;
	}
}
