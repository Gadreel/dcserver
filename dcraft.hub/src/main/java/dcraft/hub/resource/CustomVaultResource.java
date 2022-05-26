package dcraft.hub.resource;

import dcraft.cms.meta.CustomVaultUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.*;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class CustomVaultResource extends ResourceBase {
    protected Map<String, RecordStruct> vaults = new HashMap<>();
    protected XElement configlayer = XElement.tag("Config");

    public CustomVaultResource() {
        this.setName("CustomVault");
    }

    public void loadInfo(Path file) {
        CharSequence json = IOUtil.readEntireFile(file);

        if (StringUtil.isNotEmpty(json)) {
            RecordStruct tree = Struct.objectToRecord(json);

            if (tree != null) {
                String alias = tree.selectAsString("Vault/Id");

                if (StringUtil.isNotEmpty(alias)) {
                    this.vaults.put(alias, tree);
                    return;
                }
            }
        }

        Logger.error("Unable to load custom vault: " + file);
    }

    public XElement getCustomVaultConfigLayer() {
        this.buildConfigLayer();

        return this.configlayer;
    }

    public void rebuildConfigLayer() throws OperatingContextException {
        this.configlayer.clearChildren();

        OperationContext.getOrThrow().getSite().flushVaults();

        this.buildConfigLayer();
    }

    public void buildConfigLayer() {
        if (this.configlayer.hasChildren())
            return;

        XElement vaults = XElement.tag("Vaults");

        for (String key : this.vaults.keySet()) {
            RecordStruct custvault = this.vaults.get(key);

            if (custvault != null) {
                RecordStruct vinfo = custvault.getFieldAsRecord("Vault");

                if (vinfo != null) {
                    String alias = vinfo.selectAsString("Id");
                    String vclass = vinfo.selectAsString("Class", "dcraft.filevault.CustomLocalVault");

                    if (StringUtil.isNotEmpty(alias)) {
                        XElement vtag = XElement.tag("Tenant")
                                .attr("Id", alias)
                                .attr("VaultClass", vclass);

                        if (vinfo.getFieldAsBooleanOrFalse("CmsSync"))
                            vtag.attr("CmsSync",  "true");

                        if (vinfo.isNotFieldEmpty("ReadBadges"))
                            vtag.attr("ReadBadges",  vinfo.getFieldAsList("ReadBadges").join(","));

                        if (vinfo.isNotFieldEmpty("WriteBadges"))
                            vtag.attr("WriteBadges",  vinfo.getFieldAsList("WriteBadges").join(","));

                        vaults.with(vtag);
                    }
                }
            }
        }

        this.configlayer.with(vaults);
    }

    public RecordStruct getVaultInfo(String alias) {
        return this.vaults.get(alias);
    }

    @Override
    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        if ("ListAll".equals(code.getName())) {
            String result = StackUtil.stringFromElement(stack, code, "Result");

            if (StringUtil.isNotEmpty(result)) {
                String currlocale = OperationContext.getOrThrow().getLocale();
                String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

                ListStruct res = ListStruct.list();

                for (String key : this.vaults.keySet()) {
                    RecordStruct vault = this.vaults.get(key);

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

                        CustomVaultResource.this.vaults.put(alias, vaultinfo);

                        CustomVaultResource.this.rebuildConfigLayer();
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
                        if (CustomVaultResource.this.vaults.containsKey(alias))
                            CustomVaultResource.this.vaults.remove(alias);

                        CustomVaultResource.this.rebuildConfigLayer();
                    }

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("Reindex".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));

            CustomVaultUtil.updateFileIndexAll(alias, new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("Search".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));
            String term = Struct.objectToString(StackUtil.refFromElement(stack, code, "Term"));
            String locale = Struct.objectToString(StackUtil.refFromElement(stack, code, "Locale"));
            String taglist = Struct.objectToString(StackUtil.refFromElement(stack, code, "Tags"));
            String result = StackUtil.stringFromElement(stack, code, "Result");

            if (StringUtil.isEmpty(locale))
                locale = OperationContext.getOrThrow().getLocale();

            ListStruct tags = ListStruct.list();

            if (StringUtil.isNotEmpty(taglist))
                tags.with(taglist.split(","));

            CustomVaultUtil.search(alias, term, locale, tags, new OperationOutcomeStruct() {
                @Override
                public void callback(BaseStruct found) throws OperatingContextException {
                    if (found != null)
                        StackUtil.addVariable(stack, result, found);

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("LoadDataFile".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));
            String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path"));
            String result = StackUtil.stringFromElement(stack, code, "Result");
            String localize = Struct.objectToString(StackUtil.refFromElement(stack, code, "Localize"));

            if (StringUtil.isEmpty(localize) || "*".equals(localize))
                localize = "WithDefault";           // or CurrentOnly or None

            String flocalize = localize;

            CustomVaultUtil.loadDataFile(alias, CommonPath.from(path), new OperationOutcomeComposite() {
                @Override
                public void callback(CompositeStruct found) throws OperatingContextException {
                    if (found != null) {
                        if ("WithDefault".equals(flocalize))
                            found = CustomVaultUtil.localizeDataFile(alias, found);

                        StackUtil.addVariable(stack, result, found);
                    }

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("UpdateDataFile".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));
            String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path"));

            // TODO check access to vault / path

            String locale = Struct.objectToString(StackUtil.refFromElement(stack, code, "Locale"));
            RecordStruct datafile = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Form"));

            if ("*".equals(locale))
                locale = OperationContext.getOrThrow().getLocale();

            String flocale = locale;

            CustomVaultUtil.loadDataFile(alias, CommonPath.from(path), new OperationOutcomeComposite() {
                @Override
                public void callback(CompositeStruct found) throws OperatingContextException {
                    try (OperationMarker om = OperationMarker.create()) {
                        if (found != null) {
                            if (StringUtil.isNotEmpty(flocale))
                                found = CustomVaultUtil.mergeLocaleDataFile(alias, found, datafile, flocale);
                            else
                                found = CustomVaultUtil.mergeDataFile(alias, found, datafile);

                            if (! om.hasErrors()) {
                                found = CustomVaultUtil.validateNormalize(alias, found);

                                if (! om.hasErrors()) {
                                    CustomVaultUtil.saveDataFile(alias, CommonPath.from(path), found, new OperationOutcomeEmpty() {
                                        @Override
                                        public void callback() throws OperatingContextException {
                                            stack.withContinueFlag();

                                            OperationContext.getAsTaskOrThrow().resume();
                                        }
                                    });

                                    return;
                                }
                            }
                        }
                    }

                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("AddDataFile".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));
            String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path"));

            // TODO check access to vault / path

            String locale = Struct.objectToString(StackUtil.refFromElement(stack, code, "Locale"));
            RecordStruct datafile = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Form"));

            if ("*".equals(locale))
                locale = OperationContext.getOrThrow().getLocale();

            String flocale = locale;

            CompositeStruct initialfile = RecordStruct.record();        // TODO add in defaults from the vault info file, also don't assume Record for non-basic types

            if (StringUtil.isNotEmpty(flocale))
                initialfile = CustomVaultUtil.mergeLocaleDataFile(alias, initialfile, datafile, flocale);
            else
                initialfile = CustomVaultUtil.mergeDataFile(alias, initialfile, datafile);

            // TODO validate the data

            CustomVaultUtil.saveDataFile(alias, CommonPath.from(path), initialfile, new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        return super.operation(stack, code);
    }
}
