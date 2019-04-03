package dcraft.cms.feed.work;

import dcraft.db.ICallContext;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.IndexTransaction;
import dcraft.filevault.Vault;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckCmsReadyWork extends StateWork {
	static public CheckCmsReadyWork work(String feed, ICallContext request) {
		CheckCmsReadyWork work = new CheckCmsReadyWork();
		work.feed = feed;
		
		work.db = TablesAdapter.ofNow(request);
		
		return work;
	}
	
	protected Deque<FileStoreFile> folders = new ArrayDeque<>();
	
	protected Site currentSite = null;
	protected FileStoreVault currentVault = null;
	protected String feed = null;
	protected ZonedDateTime now = TimeUtil.now();
	
	protected StateWorkStep indexFolder = null;
	protected StateWorkStep transaction = null;
	protected StateWorkStep done = null;
	
	protected TablesAdapter db = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(indexFolder = StateWorkStep.of("Scan Folder", this::doFolder))
				.withStep(done = StateWorkStep.of("Done", this::done));
		
		this.currentSite = OperationContext.getOrThrow().getSite();
		
		this.currentVault = this.currentSite.getFeedsVault();
		
		this.folders.addLast(this.currentVault.getFileStore().fileReference(CommonPath.from("/" + this.feed)));
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
						System.out.println(" - " + file.isFolder() + " : " + file);
						
						if (file.isFolder()) {
							CheckCmsReadyWork.this.folders.addLast(file);
						}
						else if (file.getName().endsWith(".html")) {
							XElement root = XmlReader.loadFile(((LocalStoreFile) file).getLocalPath(), true, true);
							
							Map<String, Integer> counters = new HashMap<>();
							
							checkNode(counters, root);
							
							//boolean fnd = false;
							
							for (String id : counters.keySet()) {
								if (counters.get(id) > 1) {
									//fnd = true;
									//break;
									
									System.out.println("     **** " + id + " - " + counters.get(id));
								}
							}
							
							//if (fnd) {
							//	System.out.println("");
							//}
						}
					}
				}
				
				trun.resume();	// try next folder
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.NEXT;
	}
	
	public void checkNode(Map<String, Integer> counters, XElement node) {
		String id = node.attr("id");
		
		if (StringUtil.isNotEmpty(id)) {
			if (! counters.containsKey(id))
				counters.put(id, 1);
			else
				counters.put(id, counters.get(id) + 1);
		}
		
		for (int i = 0; i <= node.children(); i++) {
			XNode child = node.getChild(i);
			
			if (child instanceof XElement)
				this.checkNode(counters, (XElement) child);
		}
	}
}
