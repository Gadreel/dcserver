package dcraft.db.fileindex;

import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.util.ByteUtil;
import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.Vault;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.IndexInfo;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class CustomIndexingAdapter {
	static public CustomIndexingAdapter of(IRequestContext request) {
		CustomIndexingAdapter adapter = new CustomIndexingAdapter();
		adapter.request = request;
		return adapter;
	}

	static public List<Object> pathToIndex(Vault vault, CommonPath path) {
		List<Object> indexkeys = new ArrayList<>();

		Site site = vault.getSite();

		indexkeys.add(site.getTenant().getAlias());
		indexkeys.add("dcCustomIndex");
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
	protected CustomIndexingAdapter() {
	}
	
	public IRequestContext getRequest() {
		return this.request;
	}

	/*
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
				List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

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
			List<Object> entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
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
				String locale = OperationContext.getOrThrow().getLocale();

				// state
				entrykeys.add("State");
				entrykeys.add(null);
				
				byte[] pkey = this.request.getInterface().nextPeerKey(entrykeys.toArray());
				
				if (pkey == null)
					return null;
				
				Object pval = ByteUtil.extractValue(pkey);
				
				entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
				entrykeys.add("State");
				entrykeys.add(pval);
				
				RecordStruct frec = RecordStruct.record();
				
				frec.with("State", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));


				BigDecimal stamp = Struct.objectToDecimal(pval);
				
				if (stamp == null)
					stamp = BigDecimal.ZERO;
				
				frec.with("Modified", stamp.negate());
				
				// public
				
				entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
				entrykeys.add("Public");
				
				frec.with("Public", Struct.objectToBoolean(this.request.getInterface().get(entrykeys.toArray()), true));
				
				// title
				
				entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
				entrykeys.add(locale);
				entrykeys.add("Title");
				
				frec.with("Title", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));
				
				// summary
				
				entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
				entrykeys.add(locale);
				entrykeys.add("Summary");
				
				frec.with("Summary", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));

				// sort hint
				
				entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
				entrykeys.add(locale);
				entrykeys.add("SortHint");
				
				frec.with("SortHint", Struct.objectToString(this.request.getInterface().get(entrykeys.toArray())));
				
				// badges
				
				entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
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
			List<Object> entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);

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

					entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);

					entrykeys.add("State");
					entrykeys.add(pval);

					String state = Struct.objectToString(this.request.getInterface().get(entrykeys.toArray()));

					if (! "Present".equals(state))
						return null;

					entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);

					entrykeys.add("History");
					entrykeys.add(pval);

					RecordStruct hist = Struct.objectToRecord(this.request.getInterface().get(entrykeys.toArray()));

					if ("Deposit".equals(hist.getFieldAsString("Source")))
						return hist;

					// go to next State
					entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);
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
			indexkeys.add("dcCustomIndex");
			indexkeys.add(site.getAlias());
			
			this.request.getInterface().kill(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to clea file index in site db: " + x);
		}
	}
	
	public void clearVaultIndex(Vault vault) {
		try {
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, null);
			
			this.request.getInterface().kill(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to clea file index in db: " + x);
		}
	}
	
	public void indexFile(Vault vault, CommonPath path, ZonedDateTime time, RecordStruct history) throws OperatingContextException {
		try {
			// set entry marker
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
			indexkeys.add("File");
			
			this.request.getInterface().set(indexkeys.toArray());
			
			// add state
			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
			indexkeys.add("State");
			indexkeys.add((time != null) ? ByteUtil.dateTimeToReverse(time) : BigDecimal.ZERO);
			indexkeys.add("Present");
			
			// don't use  ByteUtil.dateTimeToReverse(file.getModificationAsTime()) - using zero is better for eventual consistency across nodes
			this.request.getInterface().set(indexkeys.toArray());
			
			// add history
			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
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
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
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
					
					indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
					
					indexkeys.add("State");
					indexkeys.add(pval);
					
					String state = Struct.objectToString(this.request.getInterface().get(indexkeys.toArray()));
					
					if (! "Present".equals(state)) {
						needbase = true;
					}
				}
			}
			
			if (needbase) {
				// TODO prefer file mod with other Scan as well
				if (vault instanceof FileStoreVault) {
					FileStoreVault fsv = (FileStoreVault) vault;

					FileStore fs = fsv.getFileStore();

					if (fs instanceof LocalStore) {
						LocalStore ls = (LocalStore) fs;

						Path file = ls.resolvePath(path);

						if (Files.exists(file))
							now = ZonedDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.of("UTC"));
					}
				}

				this.indexFile(vault, path, now, RecordStruct.record()
								.with("Source", "Scan")
								.with("Op", "Write")
								.with("TimeStamp", now)
								.with("Node", ApplicationHub.getNodeId())
				);
			}
			else {
				this.indexFolderEnsure(vault, path.getParent());
			}
		}
		catch (DatabaseException | IOException x) {
			Logger.error("Unable to ensure index file " + path + " in db: " + x);
		}
	}
	
	public void indexFolderEnsure(Vault vault, CommonPath path) throws OperatingContextException {
		if (path.isRoot())
			return;
		
		try {
			// set public marker
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
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
		try {
			this.indexFileEnsure(vault, path);
			
			// set public marker
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
			indexkeys.add("Public");
			indexkeys.add(! indexer.isDenyIndex());
			
			this.request.getInterface().set(indexkeys.toArray());

			CharSequence title = indexer.getTitle();

			// set title
			if (StringUtil.isNotEmpty(title)) {
				indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

				indexkeys.add(indexer.getLang());
				indexkeys.add("Title");
				indexkeys.add(title);
				
				this.request.getInterface().set(indexkeys.toArray());
			}

			CharSequence summary = indexer.getSummary();

			// set summary
			if (StringUtil.isNotEmpty(summary)) {
				indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

				indexkeys.add(indexer.getLang());
				indexkeys.add("Summary");
				indexkeys.add(summary);
				
				this.request.getInterface().set(indexkeys.toArray());
			}

			CharSequence hint = indexer.getSortHint();

			// set hint
			if (StringUtil.isNotEmpty(hint)) {
				indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

				indexkeys.add(indexer.getLang());
				indexkeys.add("SortHint");
				indexkeys.add(hint);

				this.request.getInterface().set(indexkeys.toArray());
			}

			ListStruct badges = indexer.getBadges();

			// set badges
			if (badges != null) {
				indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

				indexkeys.add("Badges");
				indexkeys.add(badges);

				this.request.getInterface().set(indexkeys.toArray());
			}

			List<IndexInfo> index = indexer.getIndex();

			// set search
			if ((index != null) && (index.size() > 0)) {
				indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
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

	public void clearSearch(Vault vault, CommonPath path, String locale) throws OperatingContextException {
		try {
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			if (! this.request.getInterface().isSet(indexkeys.toArray())) {
				return;
			}

			// TODO remove indexes for Tags, Location, Area, Start, End

			// remove public marker
			indexkeys.add("Public");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add(locale);
			indexkeys.add("Title");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add(locale);
			indexkeys.add("Summary");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add(locale);
			indexkeys.add("SortHint");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Badges");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add(locale);
			indexkeys.add("Search");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Data");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("LocaleData");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Tags");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Location");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Area");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Start");

			this.request.getInterface().kill(indexkeys.toArray());

			indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("End");

			this.request.getInterface().kill(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to search index file " + path + " in db: " + x);
		}
	}

	public void deleteFile(Vault vault, CommonPath path, ZonedDateTime time, RecordStruct history) {
		try {
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
			Object fmarker = this.request.getInterface().get(indexkeys.toArray());
			
			boolean isfile = Struct.objectToBooleanOrFalse(fmarker) || "File".equals(Struct.objectToString(fmarker));

			if (isfile) {
				// state
				indexkeys.add("State");
				indexkeys.add((time != null) ? ByteUtil.dateTimeToReverse(time) : BigDecimal.ZERO);
				indexkeys.add("Deleted");
				
				this.request.getInterface().set(indexkeys.toArray());
				
				// history
				indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
				
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
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to delete index file " + path + " in db: " + x);
		}
	}
	
	public void hideFolder(Vault vault, CommonPath path, ZonedDateTime time, RecordStruct history) {
		try {
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);
			
			indexkeys.add("XFolder");
			
			this.request.getInterface().set(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to delete index file " + path + " in db: " + x);
		}
	}

	public BaseStruct getData(Vault vault, CommonPath path) throws OperatingContextException {
		try {
			List<Object> entrykeys = CustomIndexingAdapter.pathToIndex(vault, path);

			Object marker = this.request.getInterface().get(entrykeys.toArray());

			if ("File".equals(Struct.objectToString(marker))) {
				entrykeys.add("Data");

				return Struct.objectToStruct(this.request.getInterface().get(entrykeys.toArray()));
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to get index file " + path + " data in db: " + x);
		}
		
		return null;
	}

	public void setData(Vault vault, CommonPath path, BaseStruct data) throws OperatingContextException {
		try {
			this.indexFileEnsure(vault, path);

			// set public marker
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Data");
			indexkeys.add(data);

			this.request.getInterface().set(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to set index file " + path + " data in db: " + x);
		}
	}

	public void setTags(Vault vault, CommonPath path, ListStruct tags) throws OperatingContextException {
		try {
			this.indexFileEnsure(vault, path);

			// set public marker
			List<Object> indexkeys = CustomIndexingAdapter.pathToIndex(vault, path);

			indexkeys.add("Tags");
			indexkeys.add("|" + tags.join("|") + "|");

			this.request.getInterface().set(indexkeys.toArray());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to set index file " + path + " tags in db: " + x);
		}
	}

	*/
}
