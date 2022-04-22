package dcraft.hub.resource;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.*;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.json3.JsonParser;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TagResouce extends ResourceBase {
    protected Map<String, RecordStruct> trees = new HashMap<>();
    protected Map<String, Path> treefiles = new HashMap<>();

    public TagResouce() {
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
                    this.treefiles.put(alias, file);
                    return;
                }
            }
        }

        Logger.error("Unable to load tag tree: " + file);
    }

    // TODO may be useful later for compiling and merging tag imports, but not correct for it to be during tenant load

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

            if (StringUtil.isNotEmpty(result) && StringUtil.isNotEmpty(alias)) {
                RecordStruct tree = this.trees.get(alias);

                if (tree != null)
                    StackUtil.addVariable(stack, result, tree);
            }

            return ReturnOption.CONTINUE;
        }

        if ("SaveTree".equals(code.getName())) {
            RecordStruct tree = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Value"));

            if (tree != null) {
                String alias = tree.getFieldAsString("Alias");

                if (StringUtil.isNotEmpty(alias)) {
                    this.trees.put(alias, tree);

                    // TODO save file via vault
                }
            }

            return ReturnOption.CONTINUE;
        }

        if ("DeleteTree".equals(code.getName())) {
            String alias = Struct.objectToString(StackUtil.refFromElement(stack, code, "Alias"));

            if (StringUtil.isNotEmpty(alias)) {
                this.trees.remove(alias);
            }

            return ReturnOption.CONTINUE;
        }

        return super.operation(stack, code);
    }
}
