package dcraft.hub.resource;

import dcraft.cms.meta.CustomIndexUtil;
import dcraft.cms.meta.CustomVaultUtil;
import dcraft.db.fileindex.Filter.*;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.*;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomIndexResource extends ResourceBase {
    protected Map<String, RecordStruct> indexes = new HashMap<>();

    public CustomIndexResource() {
        this.setName("CustomIndexing");
    }

    public void loadInfo(Path file) {
        CharSequence json = IOUtil.readEntireFile(file);

        if (StringUtil.isNotEmpty(json)) {
            RecordStruct tree = Struct.objectToRecord(json);

            if (tree != null) {
                String alias = tree.getFieldAsString("Alias");

                if (StringUtil.isNotEmpty(alias)) {
                    this.indexes.put(alias, tree);
                    return;
                }
            }
        }

        Logger.error("Unable to load custom index: " + file);
    }

    public ListStruct getAllIndexInfo() {
        return ListStruct.list(this.indexes.values());
    }

    public RecordStruct getIndexInfo(String alias) {
        return this.indexes.get(alias);
    }

    @Override
    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        if ("ListAll".equals(code.getName())) {
            String result = StackUtil.stringFromElement(stack, code, "Result");

            if (StringUtil.isNotEmpty(result)) {
                String currlocale = OperationContext.getOrThrow().getLocale();
                String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

                ListStruct res = ListStruct.list();

                for (String key : this.indexes.keySet()) {
                    RecordStruct vault = this.indexes.get(key);

                    if (vault != null) {
                        RecordStruct titleinfo = vault.getFieldAsRecord("Title");

                        if (titleinfo != null) {
                            String title = titleinfo.selectAsString(currlocale);

                            if (StringUtil.isEmpty(title))
                                title = titleinfo.selectAsString(deflocale);

                            if (StringUtil.isNotEmpty(title)) {
                                res.with(
                                        RecordStruct.record()
                                                .with("Alias", key)
                                                .with("Title", title)
                                );
                            }
                        }
                    }
                }

                StackUtil.addVariable(stack, result, res);
            }

            return ReturnOption.CONTINUE;
        }

        /*
        if ("LoadVault".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));
            String result = StackUtil.stringFromElement(stack, code, "Result");

            if (StringUtil.isNotEmpty(result) && StringUtil.isNotEmpty(alias)) {
                RecordStruct tree = this.vaults.get(alias);

                if (tree != null)
                    StackUtil.addVariable(stack, result, tree);
            }

            return ReturnOption.CONTINUE;
        }

        if ("SaveVault".equals(code.getName())) {
            RecordStruct vaultinfo = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Value"));

            CustomVaultUtil.saveVault(vaultinfo, new OperationOutcomeStruct() {
                @Override
                public void callback(BaseStruct result) throws OperatingContextException {
                    if (! this.hasErrors()) {
                        String alias = vaultinfo.selectAsString("Vault/Id");

                        CustomIndexResource.this.vaults.put(alias, vaultinfo);

                        CustomIndexResource.this.rebuildConfigLayer();
                    }

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("DeleteVault".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));

            CustomVaultUtil.deleteVault(alias, new OperationOutcomeStruct() {
                @Override
                public void callback(BaseStruct result) throws OperatingContextException {
                    if (! this.hasErrors()) {
                        if (CustomIndexResource.this.vaults.containsKey(alias))
                            CustomIndexResource.this.vaults.remove(alias);

                        CustomIndexResource.this.rebuildConfigLayer();
                    }

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }
        */

        if ("Reindex".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));

            CustomIndexUtil.updateCustomIndexAll(alias, new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("Search".equals(code.getName())) {
            System.out.println("Search a");

            String result = StackUtil.stringFromElement(stack, code, "Result");

            RecordStruct params = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Params"));

            if (params == null) {
                String index = Struct.objectToString(StackUtil.refFromElement(stack, code, "Index"));

                params = RecordStruct.record()
                        .with("IndexName", index);

                String access = Struct.objectToString(StackUtil.refFromElement(stack, code, "Access"));     // Private or Public
                String term = Struct.objectToString(StackUtil.refFromElement(stack, code, "Term"));
                String locale = Struct.objectToString(StackUtil.refFromElement(stack, code, "Locale"));
                String taglist = Struct.objectToString(StackUtil.refFromElement(stack, code, "Tags"));      // better to use a tag index key
                long max = StackUtil.intFromElement(stack, code, "Max", 0);

                params.with("Max", max);

                if (StringUtil.isNotEmpty(term))
                    params.with("Term", term);

                if (StringUtil.isNotEmpty(locale))
                    params.with("Locale", locale);

                // add filters after here

                if (StringUtil.isNotEmpty(access))
                    params.with("Access", access);

                if (StringUtil.isNotEmpty(taglist)) {
                    ListStruct tags = ListStruct.list();
                    tags.with(taglist.split(","));
                    params.with("Tags", tags);
                }

                List<XElement> vaults = code.selectAll("IncludeVault");

                if (vaults.size() > 0) {
                    ListStruct allowed = ListStruct.list();

                    for (XElement vault : vaults) {
                        String vaultName = Struct.objectToString(StackUtil.refFromElement(stack, vault, "Name"));

                        if (StringUtil.isNotEmpty(vaultName))
                            allowed.with(vaultName);
                    }

                    params.with("VaultsAllowed", allowed);
                }

                vaults = code.selectAll("ExcludeVault");

                if (vaults.size() > 0) {
                    ListStruct excluded = ListStruct.list();

                    for (XElement vault : vaults) {
                        String vaultName = Struct.objectToString(StackUtil.refFromElement(stack, vault, "Name"));

                        if (StringUtil.isNotEmpty(vaultName))
                            excluded.with(vaultName);
                    }

                    params.with("VaultsExcluded", excluded);
                }

                List<XElement> keys = code.selectAll("SearchKey");

                if (keys.size() > 0) {
                    ListStruct keylist = ListStruct.list();

                    for (XElement key : keys) {
                        String keyName = Struct.objectToString(StackUtil.refFromElement(stack, key, "Name"));

                        if (StringUtil.isNotEmpty(keyName)) {
                            RecordStruct param = RecordStruct.record()
                                    .with("KeyName", keyName);

                            if (key.hasNotEmptyAttribute("Values")) {
                                String values = Struct.objectToString(StackUtil.refFromElement(stack, key, "Values"));

                                param.with("Values", ListStruct.list(values.split("\\|")));
                            } else if (key.hasNotEmptyAttribute("From")) {
                                String from = Struct.objectToString(StackUtil.refFromElement(stack, key, "From"));
                                param.with("From", from);

                                String to = Struct.objectToString(StackUtil.refFromElement(stack, key, "To"));
                                param.with("To", to);
                            }

                            keylist.with(param);
                        }
                    }

                    params.with("SearchKeys", keylist);
                }
            }

            long max = params.getFieldAsInteger("Max", 0);

            OrderedUnique collector = OrderedUnique.unique(OrderedUnique.ResultMode.DataInfo).withMax(max);

            String term = params.getFieldAsString("Term");

            String locale = params.getFieldAsString("Locale");

            if (StringUtil.isEmpty(locale))
                locale = OperationContext.getOrThrow().getLocale();

            if (StringUtil.isNotEmpty(term)) {
                // TODO use ScoredOrderedUnique

                Term termfilter = new Term();

                termfilter.init(term, locale);

                collector
                        .shiftNested(termfilter);
            }

            // add filters after here

            if (! "Private".equals(params.getFieldAsString("Access", "Public")))
                collector
                        .shiftNested(StandardAccess.standard());

            ListStruct tags = params.getFieldAsList("Tags");

            if ((tags != null) && (tags.size() > 0)) {
                Tags tagfilter = new Tags();
                tagfilter.init(tags);
                collector.shiftNested(tagfilter);
            }

            System.out.println("Search b: " + params);

            CustomIndexUtil.searchCustomIndex(params, collector, new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    //System.out.println("found: " + collector.getValues());

                    if (StringUtil.isNotEmpty(result))
                        StackUtil.addVariable(stack, result, ListStruct.list(collector.getValues()));

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("SearchCount".equals(code.getName())) {
            System.out.println("Search a");

            String result = StackUtil.stringFromElement(stack, code, "Result");

            RecordStruct params = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Params"));

            if (params == null) {
                String index = Struct.objectToString(StackUtil.refFromElement(stack, code, "Index"));

                params = RecordStruct.record()
                        .with("IndexName", index);

                String access = Struct.objectToString(StackUtil.refFromElement(stack, code, "Access"));     // Private or Public
                String term = Struct.objectToString(StackUtil.refFromElement(stack, code, "Term"));
                String locale = Struct.objectToString(StackUtil.refFromElement(stack, code, "Locale"));
                String taglist = Struct.objectToString(StackUtil.refFromElement(stack, code, "Tags"));      // better to use a tag index key

                if (StringUtil.isNotEmpty(term))
                    params.with("Term", term);

                if (StringUtil.isNotEmpty(locale))
                    params.with("Locale", locale);

                // add filters after here

                if (StringUtil.isNotEmpty(access))
                    params.with("Access", access);

                if (StringUtil.isNotEmpty(taglist)) {
                    ListStruct tags = ListStruct.list();
                    tags.with(taglist.split(","));
                    params.with("Tags", tags);
                }

                List<XElement> vaults = code.selectAll("IncludeVault");

                if (vaults.size() > 0) {
                    ListStruct allowed = ListStruct.list();

                    for (XElement vault : vaults) {
                        String vaultName = Struct.objectToString(StackUtil.refFromElement(stack, vault, "Name"));

                        if (StringUtil.isNotEmpty(vaultName))
                            allowed.with(vaultName);
                    }

                    params.with("VaultsAllowed", allowed);
                }

                vaults = code.selectAll("ExcludeVault");

                if (vaults.size() > 0) {
                    ListStruct excluded = ListStruct.list();

                    for (XElement vault : vaults) {
                        String vaultName = Struct.objectToString(StackUtil.refFromElement(stack, vault, "Name"));

                        if (StringUtil.isNotEmpty(vaultName))
                            excluded.with(vaultName);
                    }

                    params.with("VaultsExcluded", excluded);
                }

                List<XElement> keys = code.selectAll("SearchKey");

                if (keys.size() > 0) {
                    ListStruct keylist = ListStruct.list();

                    for (XElement key : keys) {
                        String keyName = Struct.objectToString(StackUtil.refFromElement(stack, key, "Name"));

                        if (StringUtil.isNotEmpty(keyName)) {
                            RecordStruct param = RecordStruct.record()
                                    .with("KeyName", keyName);

                            if (key.hasNotEmptyAttribute("Values")) {
                                String values = Struct.objectToString(StackUtil.refFromElement(stack, key, "Values"));

                                param.with("Values", ListStruct.list(values.split("\\|")));
                            } else if (key.hasNotEmptyAttribute("From")) {
                                String from = Struct.objectToString(StackUtil.refFromElement(stack, key, "From"));
                                param.with("From", from);

                                String to = Struct.objectToString(StackUtil.refFromElement(stack, key, "To"));
                                param.with("To", to);
                            }

                            keylist.with(param);
                        }
                    }

                    params.with("SearchKeys", keylist);
                }
            }

            Unique collector = Unique.unique();

            String term = params.getFieldAsString("Term");

            String locale = params.getFieldAsString("Locale");

            if (StringUtil.isEmpty(locale))
                locale = OperationContext.getOrThrow().getLocale();

            if (StringUtil.isNotEmpty(term)) {
                // TODO use ScoredOrderedUnique

                Term termfilter = new Term();

                termfilter.init(term, locale);

                collector
                        .shiftNested(termfilter);
            }

            // add filters after here

            if (! "Private".equals(params.getFieldAsString("Access", "Public")))
                collector
                        .shiftNested(StandardAccess.standard());

            ListStruct tags = params.getFieldAsList("Tags");

            if ((tags != null) && (tags.size() > 0)) {
                Tags tagfilter = new Tags();
                tagfilter.init(tags);
                collector.shiftNested(tagfilter);
            }

            System.out.println("Search b");

            CustomIndexUtil.searchCustomIndex(params, collector, new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    //System.out.println("found: " + collector.getValues());

                    if (StringUtil.isNotEmpty(result))
                        StackUtil.addVariable(stack, result, IntegerStruct.of(collector.getCount()));

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        return super.operation(stack, code);
    }
}
