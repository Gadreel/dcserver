package dcraft.hub.resource;

import dcraft.cms.feed.db.HistoryFilter;
import dcraft.cms.meta.CustomVaultUtil;
import dcraft.cms.util.FeedUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.FeedVault;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.base.Vaults;
import dcraft.struct.*;
import dcraft.struct.scalar.NullStruct;
import dcraft.tenant.Site;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;

public class StandardIndexResource extends ResourceBase {
    public StandardIndexResource() {
        this.setName("StandardIndexing");
    }

    @Override
    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        /* TODO support reindexing ?
        if ("Reindex".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias", true));

            CustomVaultUtil.updateFileCacheAll(alias, new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }
         */

        /* TODO future
        if ("Search".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias", true));
            String term = Struct.objectToString(StackUtil.refFromElement(stack, code, "Term", true));
            String locale = Struct.objectToString(StackUtil.refFromElement(stack, code, "Locale", true));
            String taglist = Struct.objectToString(StackUtil.refFromElement(stack, code, "Tags", true));
            String result = StackUtil.stringFromElementClean(stack, code, "Result");

            if (StringUtil.isEmpty(locale))
                locale = OperationContext.getOrThrow().getLocale();

            ListStruct tags = ListStruct.list();

            if (StringUtil.isNotEmpty(taglist))
                tags.with(taglist.split(","));

            if (StringUtil.isNotEmpty(result))
                StackUtil.addVariable(stack, result, NullStruct.instance);

            CustomVaultUtil.searchFileCache(alias, term, locale, tags, new OperationOutcomeStruct() {
                @Override
                public void callback(BaseStruct found) throws OperatingContextException {
                    if (StringUtil.isNotEmpty(result) && (found != null))
                        StackUtil.addVariable(stack, result, found);

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

         */

        if ("LoadData".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias", true));
            String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path", true));
            String result = StackUtil.stringFromElementClean(stack, code, "Result");

            BaseStruct found = NullStruct.instance;

            Vault vault = OperationContext.getOrThrow().getSite().getVault(alias);

            if (vault == null) {
                Logger.error("Vault not found.");
            }
            else {
                IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

                FileIndexAdapter fileIndexAdapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

                RecordStruct data = Struct.objectToRecord(fileIndexAdapter.getData(vault, CommonPath.from(path)));

                if (data != null)
                    found = data;

                // TODO this is a good idea but at this time feeds, files, galleries, etc do not leave a Data structure in the file index
                // so it is not useful until they do, for feeds consider the workaround below
            }

            if (StringUtil.isNotEmpty(result))
                StackUtil.addVariable(stack, result, found);

            return ReturnOption.CONTINUE;
        }

        // TODO this is a workaruond since the above is not ready yet, this should be removed someday though
        // TODO or at least merge the ideas in dcraft.cms.feed.db.LoadMeta so we don't have duplicate code
        if ("LoadFeedData".equals(code.getName())) {
            String feed = Struct.objectToString(StackUtil.refFromElement(stack, code, "Feed", true));
            String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path", true));
            String result = StackUtil.stringFromElementClean(stack, code, "Result");

            BaseStruct found = NullStruct.instance;

            FileStoreVault feedsvault = OperationContext.getOrThrow().getSite().getFeedsVault();

            if (feedsvault == null) {
                Logger.error("Vault not found.");
            }
            else {
                LocalStore store = (LocalStore) feedsvault.getFileStore();

                LocalStoreFile fileStoreFile = store.fileReference(
                        CommonPath.from("/" + feed + path));

                CharSequence xml = IOUtil.readEntireFile(fileStoreFile.getLocalPath());

                if (StringUtil.isNotEmpty(xml)) {
                    XElement root = ScriptHub.parseInstructions(xml);

                    if (root == null) {
                        Logger.error("Feed file not well formed XML");
                    }
                    else {
                        IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

                        TablesAdapter db = TablesAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

                        CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + feed + path.substring(0, path.length() - 5));

                        Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmDraftPath", epath.toString(), Unique.unique().withNested(
                                CurrentRecord.current().withNested(HistoryFilter.forDraft())));

                        String hid = collector.isEmpty() ? null : collector.getOne().toString();

                        if (hid != null) {
                            for (String key : db.getListKeys("dcmFeedHistory", hid, "dcmModifications")) {
                                RecordStruct command = Struct.objectToRecord(db.getList("dcmFeedHistory", hid, "dcmModifications", key));

                                // check null, modification could be retired
                                if (command != null) {
                                    try (OperationMarker om = OperationMarker.create()) {
                                        FeedUtil.applyCommand(epath, root, command, false);

                                        if (om.hasErrors()) {
                                            // TODO break/skip
                                        }
                                    }
                                    catch (Exception x) {
                                        Logger.error("OperationMarker error - applying history");
                                        // TODO break/skip
                                    }
                                }
                            }
                        }

                        RecordStruct data = FeedUtil.metaToInfo(feed, null, root);

                        if (data != null) {
                            // return Name and Values pairs as a Record

                            found = new RecordStruct();

                            for (int i= 0; i < data.getFieldAsList("Fields").size(); i++){
                                RecordStruct fld = data.getFieldAsList("Fields").getItemAsRecord(i);
                                ((RecordStruct) found).with(fld.getFieldAsString("Name"),fld.getField("Value"));
                            }
                        }
                    }
                }
            }

            if (StringUtil.isNotEmpty(result))
                StackUtil.addVariable(stack, result, found);

            return ReturnOption.CONTINUE;
        }

        return super.operation(stack, code);
    }

}
