package dcraft.web.ui.inst;

import dcraft.cms.util.GalleryUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class ImgCache extends Base {
	static public ImgCache tag() {
		ImgCache el = new ImgCache();
		el.setName("dc.ImgCache");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ImgCache.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		RecordStruct templateimage = Struct.objectToRecord(StackUtil.queryVariable(state, "_Image"));

		if (templateimage != null) {
			this.attr("src", templateimage.getFieldAsString("Path"));

			RecordStruct variant = Struct.objectToRecord(StackUtil.queryVariable(state, "_Variant"));

			if (variant != null) {
				if (variant.isNotFieldEmpty("ExactWidth"))
					this.attr("width", variant.getFieldAsString("ExactWidth"));

				if (variant.isNotFieldEmpty("ExactHeight"))
					this.attr("height", variant.getFieldAsString("ExactHeight"));
			}

			if (! this.hasAttribute("alt")) {
				String desc = Struct.objectToString(templateimage.select("Description"));

				if (StringUtil.isEmpty(desc))
					desc = Struct.objectToString(templateimage.select("Element.attributes.Description"));

				if (StringUtil.isEmpty(desc))
					desc = Struct.objectToString(templateimage.select("Data.Description"));

				if (StringUtil.isEmpty(desc))
					desc = Struct.objectToString(templateimage.select("Element.attributes.Title"));

				if (StringUtil.isEmpty(desc))
					desc = Struct.objectToString(templateimage.select("Data.Title"));

				if (StringUtil.isEmpty(desc))
					desc = Struct.objectToString(templateimage.select("Element.attributes.Name"));

				if (StringUtil.isEmpty(desc))
					desc = Struct.objectToString(templateimage.select("Data.Name"));

				if (StringUtil.isNotEmpty(desc))
					this.attr("alt", desc);
			}

			return;
		}

		String path = StackUtil.stringFromSource(state, "Path");

		if (StringUtil.isEmpty(path))
			return;

		CommonPath commonPath = CommonPath.from(path);

		if ((commonPath == null) || commonPath.isRoot())
			return;

		String section = commonPath.subpath(0, 1).toString().substring(1);
		String secpath = path;

		if (! "galleries".equals(section) && ! "files".equals(section)) {
			section = "www";
		}
		else {
			secpath = commonPath.subpath(1).toString();
		}

		Path imgpath = OperationContext.getOrThrow().getSite().findSectionFile(section, secpath,
				OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));

		if (imgpath == null)
			return;

		try {
			FileTime fileTime = Files.getLastModifiedTime(imgpath);

			this.attr("src", path + "?dc-cache=" + TimeUtil.stampFmt.format(LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.of("UTC"))));
		}
		catch (IOException x) {
			Logger.warn("Problem finding image file: " + imgpath);
		}

		if ("galleries".equals(section)) {
			try {
				RecordStruct meta = GalleryUtil.getMeta(secpath);

				if (meta != null) {
					RecordStruct variant = GalleryUtil.findVariation(meta, imgpath.getFileName().toString());

					if (variant.isNotFieldEmpty("ExactWidth"))
						this.attr("width", variant.getFieldAsString("ExactWidth"));

					if (variant.isNotFieldEmpty("ExactHeight"))
						this.attr("height", variant.getFieldAsString("ExactHeight"));
				}
			}
			catch (IllegalArgumentException x) {
				System.out.println("bad path - try Missing");
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		if (this.hasEmptyAttribute("loading"))
			this.attr("loading", "lazy");

		this
				// add "pure-img-no" or a valid pure img class to avoid the automatic
				.withClass(! this.hasClass("pure-img") ? "pure-img-inline" : "")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("img");
	}
}
