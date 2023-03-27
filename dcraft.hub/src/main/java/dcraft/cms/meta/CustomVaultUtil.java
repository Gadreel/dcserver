package dcraft.cms.meta;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.Filter.StandardAccess;
import dcraft.db.fileindex.Filter.Tags;
import dcraft.db.fileindex.Filter.Term;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.DocumentIndexBuilder;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.CustomLocalVault;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.hub.resource.TagResource;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaResource;
import dcraft.struct.*;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import jxl.biff.RecordData;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CustomVaultUtil {
	static public void saveVault(RecordStruct vaultinfo, OperationOutcomeStruct callback) throws OperatingContextException {
		if (vaultinfo == null) {
			Logger.error("No custom vault to save");
			callback.returnEmpty();
			return;
		}

		String alias = vaultinfo.selectAsString("Vault/Id");

		if (StringUtil.isEmpty(alias)) {
			Logger.error("Custom vault missing alias, could not save");
			callback.returnEmpty();
			return;
		}

		vaultinfo.with("Version", TimeUtil.stampFmt.format(TimeUtil.now()));

		Vault metavault = OperationContext.getOrThrow().getSite().getVault("Meta");

		if (metavault == null) {
			Logger.error("Meta vault missing.");
			callback.returnEmpty();
			return;
		}

		CommonPath path = CommonPath.from("/vaults/" + alias + ".vault.json");

		MemoryStoreFile msource = MemoryStoreFile.of(path).with(vaultinfo.toPrettyString());

		VaultUtil.transfer("Meta", msource, path, null, new OperationOutcomeStruct() {
			@Override
			public void callback(BaseStruct result) throws OperatingContextException {
				callback.returnEmpty();
			}
		});
	}

	static public void deleteVault(String alias, OperationOutcomeStruct callback) throws OperatingContextException {
		if (StringUtil.isEmpty(alias)) {
			Logger.error("Custom vault missing alias, could not save");
			callback.returnEmpty();
			return;
		}

		Vault metavault = OperationContext.getOrThrow().getSite().getVault("Meta");

		if (metavault == null) {
			Logger.error("Meta vault missing.");
			callback.returnEmpty();
			return;
		}

		String path = "/vaults/" + alias + ".vault.json";

		metavault.getMappedFileDetail(path, null, new OperationOutcome<>() {
			@Override
			public void callback(FileDescriptor result) throws OperatingContextException {
				if (this.hasErrors()) {
					callback.returnEmpty();
					return;
				}

				if (this.isEmptyResult() || ! result.exists()) {
					Logger.info("Your request appears valid but does not map to a file. Nothing to delete.");
					callback.returnEmpty();
					return;
				}

				List<FileDescriptor> files = new ArrayList<>();

				files.add(result);

				metavault.deleteFiles(files, null, new OperationOutcomeEmpty() {
					@Override
					public void callback() throws OperatingContextException {
						System.out.println("custom vault info deleted from Meta");

						callback.returnEmpty();
					}
				});
			}
		});

		// TODO remove files from the vault folder as well
	}

	static public void updateFileCacheAll(String vaultname, OperationOutcomeEmpty callback)
			throws OperatingContextException
	{
		Vault cv = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (cv == null) {
			Logger.error("Custom Vault class cannot be created.");
			callback.returnEmpty();
			return;
		}

		if (! (cv instanceof CustomLocalVault)) {
			Logger.error("Custom Vault class is not local.");
			callback.returnEmpty();
			return;
		}

		CustomLocalVault localVault = (CustomLocalVault) cv;

		List<CommonPath> updateFiles = new ArrayList<>();

		Path localpath = ((LocalStore) localVault.getFileStore()).resolvePath("/");

		try {
			Files.walkFileTree(localpath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path sfile, BasicFileAttributes attrs) throws IOException {
					String relpath = "/" + localpath.relativize(sfile).toString();

					if (relpath.endsWith(".json"))
						updateFiles.add(CommonPath.from(relpath));

					return FileVisitResult.CONTINUE;
				}
			});

			CustomVaultUtil.updateFileCache(vaultname, updateFiles, null, callback);
		}
		catch (IOException x) {
			Logger.error("Unable to complete folder listing: " + x);
			callback.returnEmpty();
		}
	}

	static public void updateFileCache(String vaultname, List<CommonPath> updateFiles, List<CommonPath> deleteFiles, OperationOutcomeEmpty callback)
			throws OperatingContextException
	{
		CustomVaultUtil.updateFileCache(vaultname, ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname) , updateFiles, deleteFiles, callback);
	}

	static public void updateFileCache(String vaultname, RecordStruct vaultinfo, List<CommonPath> updateFiles, List<CommonPath> deleteFiles, OperationOutcomeEmpty callback)
			throws OperatingContextException
	{
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			callback.returnEmpty();
			return;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			callback.returnEmpty();
			return;
		}

		RecordStruct handlerConfig = vaultinfo.getFieldAsRecord("DataHandlerConfig");

		if (handlerConfig == null) {
			Logger.error("Custom Vault DataHandler not configured.");
			callback.returnEmpty();
			return;
		}

		if (! handlerConfig.selectAsBooleanOrFalse("Searchable")) {
			Logger.info("Skipping Custom Vault DataHandler, not searchable.");
			callback.returnEmpty();
			return;
		}

		if (! handlerConfig.selectAsBooleanOrFalse("Index.StandardMap")) {
			Logger.error("Skipping Custom Vault DataHandler, only StandardMap supported currently.");
			callback.returnEmpty();
			return;
		}

		ListStruct fields = handlerConfig.getFieldAsList("Fields");

		if (fields == null) {
			Logger.error("Custom Vault DataHandler not configured with fields.");
			callback.returnEmpty();
			return;
		}

		boolean fndTitle = false;
		ListStruct nonStandardFields = ListStruct.list();
		ListStruct nonStandardLocaleFields = ListStruct.list();

		for (int i = 0; i < fields.size(); i++) {
			RecordStruct fld = fields.getItemAsRecord(i);

			switch (fld.getFieldAsString("Name")) {
				case "Title":
					fndTitle = true;
				case "Description":
				case "Keywords":
				case "Tags":
					// skip standard fields
					break;
				default: {
					// track data fields
					if (fld.selectAsBooleanOrFalse("LocaleInput"))
						nonStandardLocaleFields.with(fld);
					else
						nonStandardFields.with(fld);

					break;
				}
			}
		}

		if (! fndTitle) {
			Logger.error("Custom Vault DataHandler not configured with a Title field.");
			callback.returnEmpty();
			return;
		}

		Vault cv = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (cv == null) {
			Logger.error("Custom Vault class cannot be created.");
			callback.returnEmpty();
			return;
		}

		if (! (cv instanceof CustomLocalVault)) {
			Logger.error("Custom Vault class is not local.");
			callback.returnEmpty();
			return;
		}

		Set<String> locales = ResourceHub.getSiteResources().getLocale().getAllLocales();

		CustomLocalVault localVault = (CustomLocalVault) cv;

		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

		FileIndexAdapter fileIndexAdapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

		// delete search work

		if (deleteFiles != null) {
			for (CommonPath deleteFile : deleteFiles) {
				for (String locale : locales) {
					fileIndexAdapter.clearSearch(localVault, deleteFile, locale);
				}
			}
		}

		// search index for file Updates

		if (updateFiles != null) {
			TagResource tagResource = ResourceHub.getResources().getTag();

			for (CommonPath updateFile : updateFiles) {
				//System.out.println("indexing vault file: " + updateFile);

				Path localpath = ((LocalStore) localVault.getFileStore()).resolvePath(updateFile);

				CharSequence json = IOUtil.readEntireFile(localpath);

				if (StringUtil.isEmpty(json)) {
					Logger.warn("Unable to read source file: " + localpath);
					continue;
				}

				System.out.println("indexing file: " + localpath);

				RecordStruct data = Struct.objectToRecord(json);
				RecordStruct olddata = Struct.objectToRecord(fileIndexAdapter.getData(localVault, updateFile));

				for (String locale : locales) {
					DocumentIndexBuilder indexer = DocumentIndexBuilder.index(locale);

					indexer.setTitle(data.selectAsString("Title." + locale));

					StringBuilder sb = new StringBuilder();

					String keywords = data.selectAsString("Keywords." + locale);

					if (StringUtil.isNotEmpty(keywords))
						sb.append(keywords);

					if (tagResource != null) {
						ListStruct tags = data.getFieldAsList("Tags");

						//System.out.println("found tags: " + tags);

						if (tags != null) {
							for (int i = 0; i < tags.size(); i++) {
								String tag = tags.getItemAsString(i);

								//System.out.println("tag: " + tag);

								if (StringUtil.isNotEmpty(tag)) {
									RecordStruct node = tagResource.selectNode(tag);

									//System.out.println("found node: " + node);

									if (node != null) {
										keywords = node.selectAsString("Locale." + locale + ".Keywords");

										if (StringUtil.isNotEmpty(keywords)) {
											sb.append(" ");
											sb.append(keywords);
										}
									}
								}
							}
						}
					}

					//System.out.println("set 2");

					indexer.setKeywords(sb.toString());
					indexer.setSummary(data.selectAsString("Description." + locale));

					//System.out.println("set 3");

					indexer.setDenyIndex(!handlerConfig.selectAsBooleanOrFalse("Index.Public"));
					indexer.setBadges(handlerConfig.selectAsList("Vault.ReadBadges"));

					//System.out.println("set 4");

                    /* TODO figure this out
                    if (root.hasNotEmptyAttribute("SortHint")) {
                        indexer.setSortHint(root.getAttribute("SortHint"));
                    }
                     */

					indexer.endSection();    // just in case

					//System.out.println("set 5");

					fileIndexAdapter.indexSearch(localVault, updateFile, indexer);

					//System.out.println("set 6");
				}

				if (data.isNotFieldEmpty("Tags"))
					fileIndexAdapter.setTags(localVault, updateFile, data.getFieldAsList("Tags"));

				//System.out.println("set 7");

				fileIndexAdapter.setData(localVault, updateFile, data);

				//System.out.println("set 8");

				CustomIndexUtil.updateCustomIndexEntry(localVault.getName(), updateFile, data, olddata);

				//System.out.println("set 9");
			}
		}

		callback.returnEmpty();
	}

	/*
	 ;  Vault           name
	 ;	Path			path to list
	 ;	Locale			to search in
	 ;
	 ; Result
	 ;		List of records
	 */
	static public void listFileCacheFolder(String vaultname, CommonPath path, OperationOutcomeStruct callback) throws OperatingContextException {
		Vault vault = OperationContext.getOrThrow().getSite().getVault(vaultname);
		
		if (vault == null) {
			Logger.error("Invalid vault name");
			callback.returnEmpty();
			return;
		}

		long depth = 1;

		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

		FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		ICompositeBuilder out = new ObjectBuilder();

		IVariableAware scope = CustomScope.of(OperationContext.getOrThrow());

		BasicFilter addFilter = new BasicFilter() {
			@Override
			public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
				file.with("Path", path);

				RecordStruct rcache = (RecordStruct) scope.queryVariable("_RecordCache");

				if (rcache != null) {
					file.with("Score", rcache.getFieldAsInteger("TermScore"));
				}

				try {
					List<Object> entrykeys = FileIndexAdapter.pathToIndex(vault, path);

					entrykeys.add("Data");

					BaseStruct data = Struct.objectToComposite(adapter.getRequest().getInterface().get(entrykeys.toArray()));

					file.with("Data", data);

					out.value(file);
				}
				catch (BuilderStateException x) {

				}
				catch (DatabaseException x) {
					// TODO
				}

				return ExpressionResult.accepted();
			}
		};

		IFilter filter = new StandardAccess()
					.withNested(
						addFilter
					);

		try (OperationMarker om = OperationMarker.create()) {
			out.startList();
			
			adapter.traverseIndex(vault, path, (int) depth, scope, filter);
			
			out.endList();
			
			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with select direct: " + x);
		}
		
		callback.returnValue(out.toLocal());
	}

	static public void countFileCacheFolder(String vaultname, CommonPath path, OperationOutcomeStruct callback) throws OperatingContextException {
		Vault vault = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (vault == null) {
			Logger.error("Invalid vault name");
			callback.returnEmpty();
			return;
		}

		long depth = 1;

		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

		FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

		IntegerStruct out = new IntegerStruct();

		IVariableAware scope = CustomScope.of(OperationContext.getOrThrow());

		BasicFilter addFilter = new BasicFilter() {
			@Override
			public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) {

					out.setValue(out.getValue() + 1);

				return ExpressionResult.accepted();
			}
		};

		IFilter filter = new StandardAccess()
				.withNested(
						addFilter
				);

		try (OperationMarker om = OperationMarker.create()) {

			adapter.traverseIndex(vault, path, (int) depth, scope, filter);

			if (! om.hasErrors()) {
				callback.returnValue(out);
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with select direct: " + x);
		}

		callback.returnEmpty();
	}

	/*
	 ;  Vault           name
	 ;	Term			search term
	 ;	Locale			to search in
	 ;
	 ; Result
	 ;		List of records
	 */
	static public void searchFileCache(String vaultname, String term, String locale, ListStruct tags, OperationOutcomeStruct callback) throws OperatingContextException {
		Vault vault = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (vault == null) {
			Logger.error("Invalid vault name");
			callback.returnEmpty();
			return;
		}

		// search params

		CommonPath path = CommonPath.ROOT;
		long depth = -1;

		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

		FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

		ICompositeBuilder out = new ObjectBuilder();

		IVariableAware scope = CustomScope.of(OperationContext.getOrThrow());

		Term termfilter = new Term();

		termfilter.init(term, locale);

		BasicFilter addFilter = new BasicFilter() {
			@Override
			public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
				file.with("Path", path);

				RecordStruct rcache = (RecordStruct) scope.queryVariable("_RecordCache");

				if (rcache != null) {
					file.with("Score", rcache.getFieldAsInteger("TermScore"));
				}


				try {
					List<Object> entrykeys = FileIndexAdapter.pathToIndex(vault, path);

					entrykeys.add("Data");

					BaseStruct data = Struct.objectToComposite(adapter.getRequest().getInterface().get(entrykeys.toArray()));

					file.with("Data", data);

					out.value(file);
				}
				catch (BuilderStateException x) {

				}
				catch (DatabaseException x) {
					// TODO
				}

				return ExpressionResult.accepted();
			}
		};

		IFilter filter = new StandardAccess()
					.withNested(
							termfilter.withNested(
									addFilter
							)
					);

		if ((tags != null) && ! tags.isEmpty()) {
			Tags tagfilter = new Tags();

			tagfilter.init(tags);

			termfilter.shiftNested(
				tagfilter
			);
		}

		try (OperationMarker om = OperationMarker.create()) {
			out.startList();

			adapter.traverseIndex(vault, path, (int) depth, scope, filter);

			out.endList();

			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with select direct: " + x);
		}

		callback.returnValue(out.toLocal());
	}

	static public void interiateFileCache(String vaultname, CommonPath path, long depth, IFilter filter, OperationOutcomeEmpty callback) throws OperatingContextException {
		Vault vault = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (vault == null) {
			Logger.error("Invalid vault name");
			callback.returnEmpty();
			return;
		}

		// search params

		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

		FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

		IVariableAware scope = OperationContext.getOrThrow();

		try (OperationMarker om = OperationMarker.create()) {
			adapter.traverseIndex(vault, path, (int) depth, scope, filter);
		}
		catch (Exception x) {
			Logger.error("Issue with select direct: " + x);
		}

		callback.returnEmpty();
	}

	static public void loadDataFile(String vaultname, CommonPath file, OperationOutcomeComposite callback)
			throws OperatingContextException
	{
		CustomVaultUtil.loadDataFile(vaultname, ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname) , file, callback);
	}

	static public void loadDataFile(String vaultname, RecordStruct vaultinfo, CommonPath file, OperationOutcomeComposite callback)
			throws OperatingContextException
	{
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			callback.returnEmpty();
			return;
		}

		if (file == null) {
			Logger.error("Custom Vault path not provided");
			callback.returnEmpty();
			return;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			callback.returnEmpty();
			return;
		}

		Vault cv = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (cv == null) {
			Logger.error("Custom Vault class cannot be created.");
			callback.returnEmpty();
			return;
		}

		if (! (cv instanceof CustomLocalVault)) {
			Logger.error("Custom Vault class is not local.");
			callback.returnEmpty();
			return;
		}

		CustomLocalVault localVault = (CustomLocalVault) cv;

		Path localpath = ((LocalStore) localVault.getFileStore()).resolvePath(file);

		CharSequence json = IOUtil.readEntireFile(localpath);

		if (StringUtil.isEmpty(json)) {
			Logger.warn("Unable to read index file: " + localpath);
			callback.returnEmpty();
		}
		else {
			callback.returnValue(Struct.objectToRecord(json));
		}
	}

	static public void saveDataFile(String vaultname, CommonPath file, CompositeStruct data, OperationOutcomeEmpty callback)
			throws OperatingContextException
	{
		CustomVaultUtil.saveDataFile(vaultname, ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname), file, data, callback);
	}

	static public void saveDataFile(String vaultname, RecordStruct vaultinfo, CommonPath file, CompositeStruct data, OperationOutcomeEmpty callback)
			throws OperatingContextException
	{
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			callback.returnEmpty();
			return;
		}

		if (file == null) {
			Logger.error("Custom Vault path not provided");
			callback.returnEmpty();
			return;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			callback.returnEmpty();
			return;
		}

		Vault cv = OperationContext.getOrThrow().getSite().getVault(vaultname);

		if (cv == null) {
			Logger.error("Custom Vault class cannot be created.");
			callback.returnEmpty();
			return;
		}

		if (! (cv instanceof CustomLocalVault)) {
			Logger.error("Custom Vault class is not local.");
			callback.returnEmpty();
			return;
		}

		System.out.println("saving: " + data.toPrettyString());

		MemoryStoreFile msource = MemoryStoreFile.of(file).with(data.toPrettyString());

		VaultUtil.transfer(vaultname, msource, file, null, new OperationOutcomeStruct() {
			@Override
			public void callback(BaseStruct result) throws OperatingContextException {
				callback.returnEmpty();
			}
		});
	}

	static public CompositeStruct localizeDataFile(String vaultname, CompositeStruct file) throws OperatingContextException {
		String currlocale = OperationContext.getOrThrow().getLocale();
		String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		return CustomVaultUtil.localizeDataFile(ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname), file, currlocale, deflocale);
	}

	static public CompositeStruct localizeDataFile(RecordStruct vaultinfo, CompositeStruct file, String currlocale, String deflocale) throws OperatingContextException {
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			return null;
		}

		if (file == null) {
			Logger.error("Custom Vault file not provided");
			return null;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			return null;
		}

		RecordStruct handlerConfig = vaultinfo.getFieldAsRecord("DataHandlerConfig");

		if (handlerConfig == null) {
			Logger.error("Custom Vault DataHandler not configured.");
			return null;
		}

		ListStruct fields = handlerConfig.getFieldAsList("Fields");

		if (fields == null) {
			Logger.error("Custom Vault DataHandler not configured with fields.");
			return null;
		}

		RecordStruct input = Struct.objectToRecord(file);

		if (input == null) {
			Logger.error("Custom Vault file not in correct format");
			return null;
		}

		RecordStruct output = RecordStruct.record();

		for (int i = 0; i < fields.size(); i++) {
			RecordStruct fld = fields.getItemAsRecord(i);

			String fname = fld.getFieldAsString("Name");

			if (input.hasField(fname)) {
				boolean localefld = fld.getFieldAsBooleanOrFalse("LocaleInput");

				if (localefld) {
					RecordStruct localedata = input.getFieldAsRecord(fname);

					if (localedata != null) {
						if (localedata.hasField(currlocale))
							output.with(fname, localedata.getField(currlocale));
						else if (localedata.hasField(deflocale))
							output.with(fname, localedata.getField(deflocale));
					}
					else {
						output.with(fname, null);
					}
				}
				else {
					output.with(fname, input.getField(fname));
				}
			}
		}

		return output;
	}

	static public CompositeStruct mergeDataFile(String vaultname, CompositeStruct file, CompositeStruct input) throws OperatingContextException {
		return CustomVaultUtil.mergeDataFile(ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname), file, input);
	}

	static public CompositeStruct mergeDataFile(RecordStruct vaultinfo, CompositeStruct file, CompositeStruct input) throws OperatingContextException {
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			return null;
		}

		if (file == null) {
			Logger.error("Custom Vault file not provided");
			return null;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			return null;
		}

		RecordStruct handlerConfig = vaultinfo.getFieldAsRecord("DataHandlerConfig");

		if (handlerConfig == null) {
			Logger.error("Custom Vault DataHandler not configured.");
			return null;
		}

		ListStruct fields = handlerConfig.getFieldAsList("Fields");

		if (fields == null) {
			Logger.error("Custom Vault DataHandler not configured with fields.");
			return null;
		}

		RecordStruct originalfile = Struct.objectToRecord(file);

		if (originalfile == null) {
			Logger.error("Custom Vault file not in correct format");
			return null;
		}

		RecordStruct inputfile = Struct.objectToRecord(input);

		if (inputfile == null) {
			Logger.error("Custom Vault input file not in correct format");
			return null;
		}

		// merge in the new values

		for (int i = 0; i < fields.size(); i++) {
			RecordStruct fld = fields.getItemAsRecord(i);

			String fname = fld.getFieldAsString("Name");

			if (inputfile.hasField(fname)) {
				originalfile.with(fname, inputfile.getField(fname));
			}
		}

		RecordStruct datameta = originalfile.getFieldAsRecord("Meta");

		if (datameta == null) {
			datameta = RecordStruct.record();
			originalfile.with("Meta", datameta);
		}

		datameta.with("Version", TimeUtil.stampFmt.format(TimeUtil.now()));

		return originalfile;
	}

	static public CompositeStruct mergeLocaleDataFile(String vaultname, CompositeStruct file, CompositeStruct input, String locale) throws OperatingContextException {
		return CustomVaultUtil.mergeLocaleDataFile(ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname), file, input, locale);
	}

	static public CompositeStruct mergeLocaleDataFile(RecordStruct vaultinfo, CompositeStruct file, CompositeStruct input, String locale) throws OperatingContextException {
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			return null;
		}

		if (file == null) {
			Logger.error("Custom Vault file not provided");
			return null;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			return null;
		}

		RecordStruct handlerConfig = vaultinfo.getFieldAsRecord("DataHandlerConfig");

		if (handlerConfig == null) {
			Logger.error("Custom Vault DataHandler not configured.");
			return null;
		}

		ListStruct fields = handlerConfig.getFieldAsList("Fields");

		if (fields == null) {
			Logger.error("Custom Vault DataHandler not configured with fields.");
			return null;
		}

		RecordStruct originalfile = Struct.objectToRecord(file);

		if (originalfile == null) {
			Logger.error("Custom Vault file not in correct format");
			return null;
		}

		RecordStruct inputfile = Struct.objectToRecord(input);

		if (inputfile == null) {
			Logger.error("Custom Vault input file not in correct format");
			return null;
		}

		// merge in the new values

		for (int i = 0; i < fields.size(); i++) {
			RecordStruct fld = fields.getItemAsRecord(i);

			String fname = fld.getFieldAsString("Name");

			if (inputfile.hasField(fname)) {
				boolean localefld = fld.getFieldAsBooleanOrFalse("LocaleInput");

				if (localefld) {
					RecordStruct localedata = originalfile.getFieldAsRecord(fname);

					if (localedata == null) {
						localedata = RecordStruct.record();
						originalfile.with(fname, localedata);
					}

					localedata.with(locale, inputfile.getField(fname));
				}
				else {
					originalfile.with(fname, inputfile.getField(fname));
				}
			}
		}

		RecordStruct datameta = originalfile.getFieldAsRecord("Meta");

		if (datameta == null) {
			datameta = RecordStruct.record();
			originalfile.with("Meta", datameta);
		}

		datameta.with("Version", TimeUtil.stampFmt.format(TimeUtil.now()));

		return originalfile;
	}

	static public CompositeStruct validateNormalize(String vaultname, CompositeStruct input) throws OperatingContextException {
		return CustomVaultUtil.validateNormalize(ResourceHub.getResources().getCustomVault().getVaultInfo(vaultname), input);
	}

	static public CompositeStruct validateNormalize(RecordStruct vaultinfo, CompositeStruct input) throws OperatingContextException {
		if (vaultinfo == null) {
			Logger.error("Custom Vault not found");
			return null;
		}

		if (input == null) {
			Logger.error("Data not provided");
			return null;
		}

		// TODO eventually make DataHandler an extendable feature
		if (! "Basic".equals(vaultinfo.getFieldAsString("DataHandler"))) {
			Logger.error("Custom Vault DataHandler not supported.");
			return null;
		}

		RecordStruct handlerConfig = vaultinfo.getFieldAsRecord("DataHandlerConfig");

		if (handlerConfig == null) {
			Logger.error("Custom Vault DataHandler not configured.");
			return null;
		}

		ListStruct fields = handlerConfig.getFieldAsList("Fields");

		if (fields == null) {
			Logger.error("Custom Vault DataHandler not configured with fields.");
			return null;
		}

		RecordStruct inputfile = Struct.objectToRecord(input);

		if (inputfile == null) {
			Logger.error("Custom Vault input file not in correct format");
			return null;
		}

		SchemaResource sr = OperationContext.getOrThrow().getOrCreateResources().getOrCreateTierSchema();

		String customRecordType = "zCustomVaultRecordType" + vaultinfo.selectAsString("Vault/Id");

		Set<String> locales = ResourceHub.getSiteResources().getLocale().getAllLocales();

		XElement shared = XElement.tag("Shared")
				.with(CustomVaultUtil.fieldsToDefinition(customRecordType, fields, locales));

		XElement schema = XElement.tag("Schema").with(shared);

		//System.out.println("temp schema: " + schema.toPrettyString());

		sr.loadSchema("/custom-vault-data-type/" + customRecordType, schema);

		sr.compile();

		DataType dataType = sr.getType(customRecordType);

		inputfile = Struct.objectToRecord(dataType.normalizeValidate(true, false, inputfile));

		return inputfile;
	}

	static public XElement fieldsToDefinition(String defid, ListStruct fields, Set<String> locales) throws OperatingContextException {
		XElement def = XElement.tag("Record");

		// this is top level
		if (StringUtil.isNotEmpty(defid)) {
			def.attr("Id", defid);

			def.with(
					XElement.tag("Field")
							.attr("Name", "Meta")
							.attr("Type", "dcmCustomVaultBasicRecordMetaDefinition")
							.attr("Required",  "true")
			);
		}

		for (int i = 0; i < fields.size(); i++) {
			RecordStruct fld = fields.getItemAsRecord(i);

			String fname = fld.getFieldAsString("Name");

			XElement fdef = XElement.tag("Field")
					.attr("Name", fname)
					.attr("Required",  fld.getFieldAsBooleanOrFalse("Required") ? "true" : "false");

			boolean localeInput = fld.selectAsBooleanOrFalse("LocaleInput");

			String datatype = fld.selectAsString("DataType");

			if (localeInput) {
				XElement record = XElement.tag("Record");

				for (String locale : locales) {
					XElement lfdef = XElement.tag("Field")
							.attr("Name", locale)
							.attr("Required",  fld.getFieldAsBooleanOrFalse("Required") ? "IfPresent" : "false");

					// should only be variants on String
					if (StringUtil.isNotEmpty(datatype))
						lfdef.attr("Type", datatype);

					record.with(lfdef);
				}

				fdef.with(record);
			}
			else {
				String fieldType = fld.selectAsString("FieldType");

				if ("Tags".equals(fieldType)) {
					fdef.with(
							XElement.tag("List")
									.attr("Type", StringUtil.isNotEmpty(datatype) ? datatype : "String")
					);
				}
				else if ("RecordList".equals(fieldType)) {
					// TODO untested

					fdef.with(
							XElement.tag("List")
									.with(CustomVaultUtil.fieldsToDefinition(null, fld.getFieldAsList("Fields"), locales))
					);
				}
				else if (StringUtil.isNotEmpty(datatype)) {
					fdef.attr("Type", datatype);
				}

				// TODO support  TextPattern, CheckGroup
			}

			def.with(fdef);
		}

		return def;
	}

	/*
	static public Set<String> vaultLocales(RecordStruct vaultInfo) throws OperatingContextException {
		Set<String> locales = ResourceHub.getResources().getLocale().getAlternateLocales();

		String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		locales.add(deflocale);

		return locales;
	}

	 */
}
