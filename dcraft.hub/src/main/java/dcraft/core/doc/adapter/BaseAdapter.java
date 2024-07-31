package dcraft.core.doc.adapter;

import dcraft.core.doc.DocUtil;
import dcraft.core.doc.IDocOutputWork;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.IFileResolvingResource;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.log.count.CountHub;
import dcraft.script.Script;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract public class BaseAdapter extends ChainWork implements IDocOutputWork, IFileResolvingResource {
    protected RecordStruct request = null;
    protected IFileResolvingResource resolvingResource = null;
    protected CommonPath basePath = CommonPath.ROOT;

    @Override
    public void init(RecordStruct request) throws OperatingContextException {
        this.request = request;
    }

    @Override
    protected void init(TaskContext taskContext) throws OperatingContextException {
        super.init(taskContext);

        CountHub.countObjects("dcDocRunCount-" + taskContext.getTenant().getAlias(), this);
    }

    protected void textToScript(RecordStruct proc, StringBuilder doc, ResourceFileInfo fileInfo) {
        if (fileInfo != null)
            this.textToScript(proc, doc, IOUtil.readEntireFile(fileInfo.getActualPath()));
    }

    protected void textToScript(RecordStruct proc, StringBuilder doc, CharSequence text) {
        if (StringUtil.isEmpty(text))
            return;

        String code = "<dcs.Script><text Name=\"text\">\n" + text + "\n</text><dcs.Exit Result=\"$text\" /></dcs.Script>";

        Script script = Script.of(code);

        if (script != null) {
            this.then(script);

            this.then(taskContext -> {
                doc.append(DocUtil.formatText(proc, (XElement) taskContext.getResult()));

                taskContext.returnEmpty();
            });
        }
    }

    // go deep first, then up (and deep)
    public ResourceFileInfo findClosestFile(CommonPath path, String filename) {
        ResourceFileInfo found = this.findFile(path.resolve(filename));

        if (found != null)
            return found;

        if (! path.isRoot())
            return this.findClosestFile(path.getParent(), filename);

        return null;
    }

    public ResourceFileInfo findMarkdownFile(CommonPath path, String filename, ListStruct locales) {
        if ((path == null) || StringUtil.isEmpty(filename) || (locales == null) || locales.isEmpty())
            return null;

        for (int i = 0; i < locales.size(); i++) {
            String loc = locales.getItemAsString(i);

            ResourceFileInfo found = this.findFile(path.resolve(filename + "." + loc + ".md"));

            if (found != null)
                return found;
        }

        return null;
    }

    public List<ResourceFileInfo> findAllMarkdownFiles(CommonPath path, String filename, ListStruct locales) {
        List<String> filenames = new ArrayList<>();  // in order of preference

        for (int i = 0; i < locales.size(); i++) {
            String loc = locales.getItemAsString(i);

            filenames.add(filename + "." + loc + ".md");
        }

        return this.findFiles(path, filenames);
    }

    // get all possible child folders, but in Common format, for later use in file lookups
    // note the list does not repeat the same path name, even if found at different levels
    public Set<CommonPath> findSubFolderSet(CommonPath path) {
        Set<CommonPath> results = new HashSet<>();

        List<ResourceFileInfo> subFolders = this.findSubFolders(path);

        for (ResourceFileInfo info : subFolders) {
            results.add(info.getLogicalPath());
        }

        return results;
    }

    // get all possible child files, but in Common format, for later use in file lookups
    // note the list does not repeat the same path name, even if found at different levels
    public Set<CommonPath> findSubFileSet(CommonPath path) {
        Set<CommonPath> results = new HashSet<>();

        List<ResourceFileInfo> subFolders = this.listFiles(path);

        for (ResourceFileInfo info : subFolders) {
            results.add(info.getLogicalPath());
        }

        return results;
    }

    public boolean hasFolder(CommonPath path) {
        ResourceFileInfo info = this.findFile(path);

        if (info != null)
            return info.isFolder();

        return false;
    }

    // implement IFileResolvingResource

    @Override
    public boolean hasPath(CommonPath path) {
        return this.resolvingResource.hasPath(this.basePath.resolve(path));
    }

    @Override
    public ResourceFileInfo findFile(CommonPath path) {
        ResourceFileInfo fileInfo = this.resolvingResource.findFile(this.basePath.resolve(path));

        if (fileInfo != null)
            fileInfo = ResourceFileInfo.of(this.adjustPath(fileInfo.getLogicalPath()), fileInfo.getActualPath());

        return fileInfo;
    }

    @Override
    public List<ResourceFileInfo> findFiles(CommonPath path, List<String> filenames) {
        List<ResourceFileInfo> files = this.resolvingResource.findFiles(this.basePath.resolve(path), filenames);

        if (files != null) {
            List<ResourceFileInfo> correctedList = new ArrayList<>();

            for (ResourceFileInfo fileInfo : files)
                correctedList.add(ResourceFileInfo.of(this.adjustPath(fileInfo.getLogicalPath()), fileInfo.getActualPath()));

            files = correctedList;
        }

        return files;
    }

    @Override
    public List<ResourceFileInfo> findSubFolders(CommonPath path) {
        List<ResourceFileInfo> files = this.resolvingResource.findSubFolders(this.basePath.resolve(path));

        if (files != null) {
            List<ResourceFileInfo> correctedList = new ArrayList<>();

            for (ResourceFileInfo fileInfo : files)
                correctedList.add(ResourceFileInfo.of(this.adjustPath(fileInfo.getLogicalPath()), fileInfo.getActualPath()));

            files = correctedList;
        }

        return files;
    }

    @Override
    public List<ResourceFileInfo> listFiles(CommonPath path) {
        List<ResourceFileInfo> files = this.resolvingResource.listFiles(this.basePath.resolve(path));

        if (files != null) {
            List<ResourceFileInfo> correctedList = new ArrayList<>();

            for (ResourceFileInfo fileInfo : files)
                correctedList.add(ResourceFileInfo.of(this.adjustPath(fileInfo.getLogicalPath()), fileInfo.getActualPath()));

            files = correctedList;
        }

        return files;
    }

    protected CommonPath adjustPath(CommonPath path) {
        return path.subpath(this.basePath.getNameCount());
    }
}
