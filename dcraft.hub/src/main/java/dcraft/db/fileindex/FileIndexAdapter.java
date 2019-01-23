package dcraft.db.fileindex;

import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.util.ByteUtil;
import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.locale.IndexInfo;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileIndexAdapter {
	static public FileIndexAdapter of(IRequestContext request) {
		FileIndexAdapter adapter = new FileIndexAdapter();
		adapter.request = request;
		return adapter;
	}
	
	static public List<Object> pathToIndex(Vault vault, CommonPath path) {
		List<Object> indexkeys = new ArrayList<>();
		
		Site site = vault.getSite();
		
		indexkeys.add(site.getTenant().getAlias());
		indexkeys.add("dcFileIndex");
		indexkeys.add(site.getAlias());
		indexkeys.add(vault.getName());
		
		if (path != null) {
			for (String part : path.getParts())
				indexkeys.add(part);
		}
		
		return indexkeys;
	}
	
	protected IRequestContext request = null;
	
	// don't call for general code...
	protected FileIndexAdapter() {
	}
	
	public IRequestContext getRequest() {
		return this.request;
	}
	
	// -1 means recursive, 1 = just this level, > 1 means just this many levels
	public void traverseIndex(Vault vault, CommonPath path, int depth, IVariableAware scope, IFilter filter) throws OperatingContextException {
		try {
			RecordStruct frec = this.fileInfo(vault, path, scope);

			if ((frec != null) && ! frec.getFieldAsBooleanOrFalse("IsFolder")) {
				//System.out.println("Found: " + path + " - " + state + " - " + title);

				if (! filter.check(this, scope, vault, path, frec).resume)
					return;
			}
			else if (depth != 0) {
				List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);

				// start at top
				indexkeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				
				while (pkey != null) {
					Object pval = ByteUtil.extractValue(pkey);
					
					if (pval instanceof String) {
						this.traverseIndex(vault, path.resolve((String) pval), depth - 1, scope, filter);
					}
					
					indexkeys.remove(indexkeys.size() - 1);
					indexkeys.add(pval);
					
					pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				}
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to delete index file " + path + " in db: " + x);
		}
	}
	
	//TODO support files too - look at EncryptedFileStore getInfo - also fix the code above to ignore folders
	public RecordStruct fileInfo(Vault vault, CommonPath path, IVariableAware scope) throws OperatingContextException {
		try {
			List<Object> entrykeys = FileIndexAdapter.pathToIndex(vault, path);
			
			Object marker = this.request.getInterface().get(entrykeys.toArray());
			
			if ("Folder".equals(Struct.objectToString(marker))) {
				RecordStruct frec = RecordStruct.record();
				frec.with("State", "Present");
				frec.with("IsFolder", true);
				return frec;
			}
			else if ("XFolder".equals(Struct.objectToString(marker))) {
				return null;
			}
			else if (marker != null) {			// Either is true or "File"
				// state
				entrykeys.add("State");
				entrykeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(entrykeys.toArray());
				
				if (pkey == null)
					return null;
				
				Object pval = ByteUtil.extractValue(pkey);
				
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);
				
				entrykeys.add("State");
				entrykeys.add(pval);
				
				RecordStruct frec = RecordStruct.record();
				
				frec.with("State", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));

				/*
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);

				entrykeys.add("History");
				entrykeys.add(pval);

				frec.with("History", this.request.getInterface().get(entrykeys.toArray()));
				*/

				BigDecimal stamp = Struct.objectToDecimal(pval);
				
				if (stamp == null)
					stamp = BigDecimal.ZERO;
				
				frec.with("Modified", stamp.negate());
				
				// public
				
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);
				
				entrykeys.add("Public");
				
				frec.with("Public", Struct.objectToBoolean(this.request.getInterface().get(entrykeys.toArray()), true));
				
				// title
				
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);
				
				entrykeys.add("eng");        // TODO current locale
				entrykeys.add("Title");
				
				frec.with("Title", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));
				
				// summary
				
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);
				
				entrykeys.add("eng");        // TODO current locale
				entrykeys.add("Summary");
				
				frec.with("Summary", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));

				/*
				// search

				indexkeys = FileIndexAdapter.pathToIndex(vault, path);

				indexkeys.add("eng");		// TODO current locale
				indexkeys.add("Search");

				frec.with("Search", Struct.objectToString(this.request.getInterface().get(indexkeys.toArray())));
				*/
				
				// sort hint
				
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);
				
				entrykeys.add("eng");        // TODO current locale
				entrykeys.add("SortHint");
				
				frec.with("SortHint", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));
				
				// badges
				
				entrykeys = FileIndexAdapter.pathToIndex(vault, path);
				
				entrykeys.add("Badges");
				
				frec.with("Badges", Struct.objectToList(this.request.getInterface().get(entrykeys.toArray())));
				
				return frec;
			}
			else {
				entrykeys.add(null);
				
				byte[] ekey = this.request.getInterface().nextPeerKey(entrykeys.toArray());
				
				if (ekey != null) {
					// the folder is implied by path
					RecordStruct frec = RecordStruct.record();
					frec.with("State", "Present");
					frec.with("Implied", true);
					frec.with("IsFolder", true);
					return frec;
				}
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to get index file info " + path + " in db: " + x);
		}

		return null;
	}

	public RecordStruct fileDeposit(Vault vault, CommonPath path, IVariableAware scope) throws OperatingContextException {
		try {
			List<Object> entrykeys = FileIndexAdapter.pathToIndex(vault, path);

			Object marker = this.request.getInterface().get(entrykeys.toArray());

			if ("Folder".equals(Struct.objectToString(marker))) {
				return null;
			}
			else if ("XFolder".equals(Struct.objectToString(marker))) {
				return null;
			}
			else if (marker != null) {			// Either is true or "File"
				// state
				entrykeys.add("State");
				entrykeys.add(null);

				byte[] pkey = this.request.getInterface().nextPeerKey(entrykeys.toArray());

				while (pkey != null) {
					Object pval = ByteUtil.extractValue(pkey);

					entrykeys = FileIndexAdapter.pathToIndex(vault, path);

					entrykeys.add("State");
					entrykeys.add(pval);

					String state = Struct.objectToString(this.request.getInterface().get(entrykeys.toArray()));

					if (! "Present".equals(state))
						return null;

					entrykeys = FileIndexAdapter.pathToIndex(vault, path);

					entrykeys.add("History");
					entrykeys.add(pval);

					RecordStruct hist = Struct.objectToRecord(this.request.getInterface().get(entrykeys.toArray()));

					if ("Deposit".equals(hist.getFieldAsString("Source")))
						return hist;

					// go to next State
					entrykeys = FileIndexAdapter.pathToIndex(vault, path);
					entrykeys.add("State");
					entrykeys.add(pval);

					pkey = this.request.getInterface().nextPeerKey(entrykeys.toArray());
				}
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to get index file deposit " + path + " in db: " + x);
		}

		return null;
	}

	public void clearSiteIndex(Site site) {
		try {
			List<Object> indexkeys = new ArrayList<>();
			
			indexkeys.add(site.getTenant().getAlias());
			indexkeys.add("dcFileIndex");
			indexkeys.add(site.getAlias());
			
			this.request.getInterface().kill(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to clea file index in site db: " + x);
		}
	}
	
	public void clearVaultIndex(Vault vault) {
		try {
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, null);
			
			this.request.getInterface().kill(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to clea file index in db: " + x);
		}
	}
	
	public void indexFile(Vault vault, CommonPath path, ZonedDateTime time, RecordStruct history) throws OperatingContextException {
		try {
			// set entry marker
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			indexkeys.add("File");
			
			this.request.getInterface().set(indexkeys.toArray());
			
			// add state
			indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			indexkeys.add("State");
			indexkeys.add((time != null) ? ByteUtil.dateTimeToReverse(time) : BigDecimal.ZERO);
			indexkeys.add("Present");
			
			// don't use  ByteUtil.dateTimeToReverse(file.getModificationAsTime()) - using zero is better for eventual consistency across nodes
			this.request.getInterface().set(indexkeys.toArray());
			
			// add history
			indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			indexkeys.add("History");
			indexkeys.add((time != null) ? ByteUtil.dateTimeToReverse(time) : BigDecimal.ZERO);
			indexkeys.add(history);
			
			// don't use  ByteUtil.dateTimeToReverse(file.getModificationAsTime()) - using zero is better for eventual consistency across nodes
			this.request.getInterface().set(indexkeys.toArray());
			
			this.indexFolderEnsure(vault, path.getParent());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to index file " + path + " in db: " + x);
		}
	}
	
	public void indexFileEnsure(Vault vault, CommonPath path) throws OperatingContextException {
		try {
			// set public marker
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			ZonedDateTime now = TimeUtil.now();
			
			boolean needbase = false;
			
			if (! this.request.getInterface().isSet(indexkeys.toArray())) {
				needbase = true;
			}
			else {
				// state
				indexkeys.add("State");
				indexkeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				
				if (pkey != null) {
					Object pval = ByteUtil.extractValue(pkey);
					
					indexkeys = FileIndexAdapter.pathToIndex(vault, path);
					
					indexkeys.add("State");
					indexkeys.add(pval);
					
					String state = Struct.objectToString(this.request.getInterface().get(indexkeys.toArray()));
					
					if (! "Present".equals(state)) {
						needbase = true;
					}
				}
			}
			
			if (needbase) {
				this.indexFile(vault, path, now, RecordStruct.record()
								.with("Source", "Scan")
								.with("Op", "Write")
								.with("TimeStamp", now)        // TODO prefer file mod
								.with("Node", ApplicationHub.getNodeId())
				);
			}
			else {
				this.indexFolderEnsure(vault, path.getParent());
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to ensure index file " + path + " in db: " + x);
		}
	}
	
	public void indexFolderEnsure(Vault vault, CommonPath path) throws OperatingContextException {
		if (path.isRoot())
			return;
		
		try {
			// set public marker
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			ZonedDateTime now = TimeUtil.now();
			
			Object fmarker = this.request.getInterface().get(indexkeys.toArray());
			
			//boolean isfile = Struct.objectToBooleanOrFalse(fmarker) || "File".equals(Struct.objectToString(fmarker));
			
			if (fmarker == null) {
				// check for implied folder
				indexkeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				
				// if none implied then force
				if (pkey == null) {
					indexkeys.remove(indexkeys.size() - 1);
					
					indexkeys.add("Folder");
					this.request.getInterface().set(indexkeys.toArray());
				}
			}
			else if ("XFolder".equals(Struct.objectToString(fmarker))) {
				indexkeys.add("Folder");
				this.request.getInterface().set(indexkeys.toArray());
			}
			
			this.indexFolderEnsure(vault, path.getParent());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to ensure index folder " + path + " in db: " + x);
		}
	}
	
	public void indexSearch(Vault vault, CommonPath path, DocumentIndexBuilder indexer) throws OperatingContextException {
		/*
		System.out.println("ib title: " + indexer.getTitle());
		System.out.println("summ: " + indexer.getSummary());
		System.out.println("hint: " + indexer.getSortHint());
		System.out.println("deny: " + indexer.isDenyIndex());
		System.out.println("badges: " + indexer.getBadges());

		for (StringBuilder sb : indexer.getSections()) {
			System.out.println();
			System.out.println(sb);
		}

		// TODO

		System.out.println(" ----- ");
		*/
		try {
			this.indexFileEnsure(vault, path);
			
			// set public marker
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			indexkeys.add("Public");
			indexkeys.add(! indexer.isDenyIndex());
			
			this.request.getInterface().set(indexkeys.toArray());

			CharSequence title = indexer.getTitle();

			// set title
			if (StringUtil.isNotEmpty(title)) {
				indexkeys = FileIndexAdapter.pathToIndex(vault, path);

				indexkeys.add(indexer.getLang());
				indexkeys.add("Title");
				indexkeys.add(title);
				
				this.request.getInterface().set(indexkeys.toArray());
			}

			CharSequence summary = indexer.getSummary();

			// set summary
			if (StringUtil.isNotEmpty(summary)) {
				indexkeys = FileIndexAdapter.pathToIndex(vault, path);

				indexkeys.add(indexer.getLang());
				indexkeys.add("Summary");
				indexkeys.add(summary);
				
				this.request.getInterface().set(indexkeys.toArray());
			}

			CharSequence hint = indexer.getSortHint();

			// set hint
			if (StringUtil.isNotEmpty(hint)) {
				indexkeys = FileIndexAdapter.pathToIndex(vault, path);

				indexkeys.add(indexer.getLang());
				indexkeys.add("SortHint");
				indexkeys.add(hint);

				this.request.getInterface().set(indexkeys.toArray());
			}

			ListStruct badges = indexer.getBadges();

			// set badges
			if (badges != null) {
				indexkeys = FileIndexAdapter.pathToIndex(vault, path);

				indexkeys.add("Badges");
				indexkeys.add(badges);

				this.request.getInterface().set(indexkeys.toArray());
			}

			List<IndexInfo> index = indexer.getIndex();

			// set search
			if ((index != null) && (index.size() > 0)) {
				indexkeys = FileIndexAdapter.pathToIndex(vault, path);
				
				indexkeys.add(indexer.getLang());
				indexkeys.add("Search");
				indexkeys.add(LocaleUtil.toSearch(index));
				
				this.request.getInterface().set(indexkeys.toArray());
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to search index file " + path + " in db: " + x);
		}
	}
	
	public void deleteFile(Vault vault, CommonPath path, ZonedDateTime time, RecordStruct history) {
		try {
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			Object fmarker = this.request.getInterface().get(indexkeys.toArray());
			
			boolean isfile = Struct.objectToBooleanOrFalse(fmarker) || "File".equals(Struct.objectToString(fmarker));

			if (isfile) {
				// state
				indexkeys.add("State");
				indexkeys.add((time != null) ? ByteUtil.dateTimeToReverse(time) : BigDecimal.ZERO);
				indexkeys.add("Deleted");
				
				this.request.getInterface().set(indexkeys.toArray());
				
				// history
				indexkeys = FileIndexAdapter.pathToIndex(vault, path);
				
				indexkeys.add("History");
				indexkeys.add((time != null) ? ByteUtil.dateTimeToReverse(time) : BigDecimal.ZERO);
				indexkeys.add(history);
				
				this.request.getInterface().set(indexkeys.toArray());
			}
			else if ("Folder".equals(Struct.objectToString(fmarker))) {
				indexkeys.add("XFolder");
				
				this.request.getInterface().set(indexkeys.toArray());
			}
			else if (fmarker == null) {
				// check if this is an implied folder
				
				indexkeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				
				if (pkey != null) {
					indexkeys.remove(indexkeys.size() - 1);
					indexkeys.add("XFolder");
					
					this.request.getInterface().set(indexkeys.toArray());
				}
				
				/*
				// start at top
				indexkeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				
				while (pkey != null) {
					Object pval = ByteUtil.extractValue(pkey);
					
					if (pval instanceof String) {
						this.deleteFile(vault, path.resolve((String) pval), time, history);
					}
					
					indexkeys.remove(indexkeys.size() - 1);
					indexkeys.add(pval);
					
					pkey = this.request.getInterface().nextPeerKey(indexkeys.toArray());
				}
				*/
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to delete index file " + path + " in db: " + x);
		}
	}
	
	public void hideFolder(Vault vault, CommonPath path, ZonedDateTime time, RecordStruct history) {
		try {
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(vault, path);
			
			indexkeys.add("XFolder");
			
			this.request.getInterface().set(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to delete index file " + path + " in db: " + x);
		}
	}
}
