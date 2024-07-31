package dcraft.hub.resource;

import dcraft.cms.meta.CustomVaultUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.*;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.*;

public class TagResource extends ResourceBase {
    protected Map<String, RecordStruct> trees = new HashMap<>();

    public TagResource() {
        this.setName("Tag");
    }

    public void loadTree(Path file) {
        CharSequence json = IOUtil.readEntireFile(file);

        if (StringUtil.isNotEmpty(json)) {
            RecordStruct tree = Struct.objectToRecord(json);

            if (tree != null) {
                String alias = tree.getFieldAsString("Alias");

                if (StringUtil.isNotEmpty(alias)) {
                    this.trees.put(alias, tree);
                    return;
                }
            }
        }

        Logger.error("Unable to load tag tree: " + file);
    }

    // TODO may be useful later for compiling and merging tag imports, but not correct for it to be during tenant load
    /* premature code - hide for now
    public void loadLang(Path file) {
        CharSequence json = IOUtil.readEntireFile(file);

        if (StringUtil.isNotEmpty(json)) {
            RecordStruct tree = Struct.objectToRecord(json);

            if (tree != null) {
                String alias = tree.getFieldAsString("Alias");

                if (StringUtil.isNotEmpty(alias)) {
                    RecordStruct original = trees.get(alias);

                    this.mergeLang(original, tree);

                    return;
                }
            }
        }

        Logger.error("Unable to load tag lang: " + file);
    }

    public void mergeLang(RecordStruct original, RecordStruct supplement) {
        RecordStruct originallocale = original.getFieldAsRecord("Locale");

        if (originallocale == null) {
            originallocale = RecordStruct.record();
            original.with("Locale", originallocale);
        }

        // supplement the current

        RecordStruct supplocale = supplement.getFieldAsRecord("Locale");

        if (supplocale != null)
            originallocale.copyFields(supplocale);

        // supplement the children

        ListStruct originalchildren = original.getFieldAsList("Children");

        // supplement cannot have children where original does not
        if (originalchildren == null)
            return;

        ListStruct suppchildren = supplement.getFieldAsList("Children");

        // supplement does not have to expend to all children
        if (suppchildren == null)
            return;

        for (int i = 0; i < originalchildren.size(); i++) {
            RecordStruct chorg = originalchildren.getItemAsRecord(i);

            String coalias =  chorg.getFieldAsString("Alias");

            if (StringUtil.isEmpty(coalias))
                continue;

            for (int g = 0; g < suppchildren.size(); g++) {
                RecordStruct chsupp = suppchildren.getItemAsRecord(g);

                String csalias =  chsupp.getFieldAsString("Alias");

                if (StringUtil.isEmpty(csalias))
                    continue;

                if (csalias.equals(coalias))
                    this.mergeLang(chorg, chsupp);
            }
        }
    }

     */

    @Override
    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        if ("ListAll".equals(code.getName())) {
            String result = StackUtil.stringFromElement(stack, code, "Result");

            if (StringUtil.isNotEmpty(result)) {
                String currlocale = OperationContext.getOrThrow().getLocale();
                String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

                ListStruct res = ListStruct.list();

                for (String key : this.trees.keySet()) {
                    RecordStruct tree = this.trees.get(key);

                    if (tree != null) {
                        RecordStruct locale = tree.getFieldAsRecord("Locale");

                        if (locale != null) {
                            String title = locale.selectAsString(currlocale + ".Title");

                            if (StringUtil.isEmpty(title))
                                title = locale.selectAsString(deflocale + ".Title");

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

        if ("LoadTree".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));
            String result = StackUtil.stringFromElement(stack, code, "Result");

            if (StringUtil.isNotEmpty(result)) {
                RecordStruct tree = StringUtil.isNotEmpty(alias) ? this.trees.get(alias) : null;

                StackUtil.addVariable(stack, result, tree != null ? tree : NullStruct.instance);
            }

            return ReturnOption.CONTINUE;
        }

        if ("LoadTreeNode".equals(code.getName())) {
            String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path"));
            String result = StackUtil.stringFromElement(stack, code, "Result");
            String localize = Struct.objectToString(StackUtil.refFromElement(stack, code, "Localize"));

            if (StringUtil.isEmpty(localize) || "*".equals(localize))
                localize = "WithDefault";           // or CurrentOnly or None

            if (StringUtil.isNotEmpty(result)) {
                RecordStruct node = StringUtil.isNotEmpty(path) ? this.selectNode(path, localize) : null;

                StackUtil.addVariable(stack, result, node != null ? node : NullStruct.instance);
            }

            return ReturnOption.CONTINUE;
        }

        if ("SaveTree".equals(code.getName())) {
            RecordStruct tree = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Value"));

            this.saveTree(tree, new OperationOutcomeStruct() {
                @Override
                public void callback(BaseStruct result) throws OperatingContextException {
                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.AWAIT;
        }

        if ("DeleteTree".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));

            this.deleteTree(alias, new OperationOutcomeStruct() {
                @Override
                public void callback(BaseStruct result) throws OperatingContextException {
                    stack.withContinueFlag();

                    OperationContext.getAsTaskOrThrow().resume();
                }
            });

            return ReturnOption.CONTINUE;
        }

        return super.operation(stack, code);
    }

    public void saveTree(RecordStruct tree, OperationOutcomeStruct callback) throws OperatingContextException {
        if (tree == null) {
            Logger.error("No tag tree to save");
            callback.returnEmpty();
            return;
        }

        String alias = tree.getFieldAsString("Alias");

        if (StringUtil.isEmpty(alias)) {
            Logger.error("Tag tree missing alias, could not save");
            callback.returnEmpty();
            return;
        }

        this.trees.put(alias, tree);

        Vault metavault = OperationContext.getOrThrow().getTenant().getVault("Meta");

        if (metavault == null) {
            Logger.error("Meta vault missing.");
            callback.returnEmpty();
            return;
        }

        CommonPath path = CommonPath.from("/tags/" + alias + ".tree.json");

        MemoryStoreFile msource = MemoryStoreFile.of(path).with(tree.toPrettyString());

        VaultUtil.transfer("Meta", msource, path, null, callback);
    }

    public void deleteTree(String alias, OperationOutcomeStruct callback) throws OperatingContextException {
        if (StringUtil.isEmpty(alias)) {
            Logger.error("Tag tree missing alias, could not save");
            callback.returnEmpty();
            return;
        }

        if (this.trees.containsKey(alias))
            this.trees.remove(alias);

        Vault metavault = OperationContext.getOrThrow().getTenant().getVault("Meta");

        if (metavault == null) {
            Logger.error("Meta vault missing.");
            callback.returnEmpty();
            return;
        }

        String path = "/tags/" + alias + ".tree.json";

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
                                callback.returnEmpty();
                            }
                        });
                    }
                });
    }

    public RecordStruct selectNode(String path) throws OperatingContextException {
        return this.selectNode(path, null);
    }

    public RecordStruct selectNode(String path, String localize) throws OperatingContextException {
        String[] pathparts = path.split(":");

        RecordStruct node = this.trees.get(pathparts[0]);

        if (node != null) {
            if ("WithDefault".equals(localize))
                node = TagResource.localizeTree(node);

            return TagResource.selectNode(node, Arrays.copyOfRange(pathparts, 1, pathparts.length));
        }

        return null;
    }

    static public RecordStruct selectNode(RecordStruct tree, String... pathparts) throws OperatingContextException {
        if (pathparts.length == 0)
            return tree;

        ListStruct children = tree.getFieldAsList("Children");

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                RecordStruct child = children.getItemAsRecord(i);

                String alias = child.getFieldAsString("Alias");

                if (alias.equals(pathparts[0]))
                    return TagResource.selectNode(child, Arrays.copyOfRange(pathparts, 1, pathparts.length));
            }
        }

        return null;
    }

    static public RecordStruct localizeTree(RecordStruct tree) throws OperatingContextException {
        String currlocale = OperationContext.getOrThrow().getLocale();
        String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

        return TagResource.localizeTree(tree, currlocale, deflocale);
    }

    static public RecordStruct localizeTree(RecordStruct tree, String currlocale, String deflocale) throws OperatingContextException {
        if (tree == null) {
            Logger.error("Tag Tree not found");
            return null;
        }

        RecordStruct output = RecordStruct.record();

        output.copyFields(tree, "Locale", "Children");

        RecordStruct locale = tree.selectAsRecord("Locale." + currlocale);

        if (locale == null)
            locale = tree.selectAsRecord("Locale." + deflocale);

        if (locale != null) {
            for (FieldStruct field : locale.getFields())
                output.with(field);
        }

        ListStruct children = tree.getFieldAsList("Children");

        if (children != null) {
            ListStruct destchildren = ListStruct.list();

            for (int i = 0; i < children.size(); i++) {
                RecordStruct srcchild = children.getItemAsRecord(i);

                RecordStruct destchild = TagResource.localizeTree(srcchild, currlocale, deflocale);

                if (destchild != null)
                    destchildren.with(destchild);
            }

            output.with("Children", destchildren);
        }

        return output;
    }
}
