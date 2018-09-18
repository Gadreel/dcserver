package dcraft.hub.resource;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.CompositeParser;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.FileUtil;
import dcraft.util.MimeInfo;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigResource extends ResourceBase {
	/*
	 * Config
	 */
	protected List<XElement> configlist = new ArrayList<>();
	
	public ConfigResource() {
		this.setName("Config");
	}
	
	public ConfigResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getConfig();
		
		return null;
	}

	public void add(XElement v) {
		if (v != null)
			this.configlist.add(v);
	}
	
	public void addTop(XElement v) {
		if (v != null)
			this.configlist.add(0, v);
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("GetTag".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				XElement el = this.getTag(StackUtil.stringFromElement(stack, code, "Path"));
				StackUtil.addVariable(stack, result, el);
			}
			
			return ReturnOption.CONTINUE;
		}
		
		if ("GetTagDeep".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				ListStruct list = ListStruct.list();
				List<XElement> el = this.getTagListDeep(StackUtil.stringFromElement(stack, code, "Path"));
				list.withCollection(el);
				StackUtil.addVariable(stack, result, list);
			}
			
			return ReturnOption.CONTINUE;
		}

		if ("GetCatalog".equals(code.getName())) {
			String name = StackUtil.stringFromElement(stack, code, "Name");
			String result = StackUtil.stringFromElement(stack, code, "Result");

			if (StringUtil.isNotEmpty(result) && StringUtil.isNotEmpty(name)) {
				String alternate = StackUtil.stringFromElement(stack, code, "Alternate");

				XElement el = code.hasNotEmptyAttribute("Mode")
					? this.getCatalog(name, alternate, StackUtil.stringFromElement(stack, code, "Mode"))
					: this.getCatalog(name, alternate);

				StackUtil.addVariable(stack, result, el);
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
	
	public String getAttribute(String name) {
		return this.getAttribute(name, null);
	}

	public String getAttribute(String name, String defaultval) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);

			if (el.hasAttribute(name))
				return el.getAttribute(name, defaultval);
		}

		ConfigResource parent = this.getParentResource();

		if (parent != null)
			return parent.getAttribute(name, defaultval);

		return defaultval;
	}

	public XElement getTag(String path) {
		XElement fnd = this.selectTag(path);

		if (fnd != null)
			return this.expandTag(fnd.getName(), fnd);

		return null;
	}
	
	public XElement getTagLocal(String path) {
		XElement fnd = this.selectTagLocal(path);
		
		if (fnd != null)
			return this.expandTag(fnd.getName(), fnd);
		
		return null;
	}

	public List<XElement> getTagList(String path) {
		List<XElement> ret = new ArrayList<>();
		List<XElement> fnd = this.selectTagList(path);

		this.expandTags(fnd, ret);

		return ret;
	}
	
	public List<XElement> getTagListLocal(String path) {
		List<XElement> ret = new ArrayList<>();
		List<XElement> fnd = this.selectTagListLocal(path);
		
		this.expandTags(fnd, ret);
		
		return ret;
	}

	public List<XElement> getTagListDeep(String path) {
		List<XElement> ret = new ArrayList<>();
		List<XElement> fnd = this.selectTagListDeep(path);

		this.expandTags(fnd, ret);

		return ret;
	}
	
	public List<XElement> getTagListDeepFirst(String path) {
		List<XElement> ret = new ArrayList<>();
		List<XElement> fnd = this.selectTagListDeepFirst(path);
		
		this.expandTags(fnd, ret);
		
		return ret;
	}

	protected XElement selectTag(String path) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);

			XElement fnd = el.selectFirst(path);

			if (fnd != null)
				return fnd;
		}

		ConfigResource parent = this.getParentResource();

		if (parent != null)
			return parent.selectTag(path);

		return null;
	}

	protected List<XElement> selectTagList(String path) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);

			List<XElement> fnd = el.selectAll(path);

			if (fnd.size() > 0)
				return fnd;
		}

		ConfigResource parent = this.getParentResource();

		if (parent != null)
			return parent.selectTagList(path);

		return new ArrayList<>();
	}
	
	protected XElement selectTagLocal(String path) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);
			
			XElement fnd = el.selectFirst(path);
			
			if (fnd != null)
				return fnd;
		}
		
		return null;
	}
	
	protected List<XElement> selectTagListLocal(String path) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);
			
			List<XElement> fnd = el.selectAll(path);
			
			if (fnd.size() > 0)
				return fnd;
		}
		
		return new ArrayList<>();
	}

	protected List<XElement> selectTagListDeep(String path) {
		List<XElement> ret = new ArrayList<>();
		this.selectTagListDeep(path, ret);
		return ret;
	}

	protected void selectTagListDeep(String path, List<XElement> ret) {
		// get all ours
		for (int i = this.configlist.size() - 1; i >= 0; i--)
			this.configlist.get(i).selectAll(path, ret);

		// and then get parents
		ConfigResource parent = this.getParentResource();

		if (parent != null)
			parent.selectTagListDeep(path, ret);
	}
	
	protected List<XElement> selectTagListDeepFirst(String path) {
		List<XElement> ret = new ArrayList<>();
		this.selectTagListDeepFirst(path, ret);
		return ret;
	}
	
	protected void selectTagListDeepFirst(String path, List<XElement> ret) {
		// get parents first
		ConfigResource parent = this.getParentResource();
		
		if (parent != null)
			parent.selectTagListDeepFirst(path, ret);
		
		// then get all ours
		for (int i = 0; i < this.configlist.size(); i++)
			this.configlist.get(i).selectAll(path, ret);
	}

	protected void expandTags(List<XElement> fnd, List<XElement> ret) {
		for (int i = 0; i < fnd.size(); i++) {
			XElement el = fnd.get(i);

			ret.add(this.expandTag(el.getName(), el));
		}
	}

	protected XElement expandTag(String tag, XElement src) {
		// start from operation config
		ConfigResource config = ResourceHub.getResources().getConfig();
		
		if (src.hasAttribute("Use")) {
			XElement def = config.findId(tag + ".Template", src.getAttribute("Use"));

			if (def != null)
				return this.expandTag(tag, def);	// template can have Inherit so expand that if so
		}
		else if (src.hasAttribute("Inherit")) {
			XElement def = config.findId(tag + ".Definition", src.getAttribute("Inherit"));

			if (def != null) {
				def = (XElement) def.deepCopy();		// so we can alter it

				def.mergeDeep(src, true);

				return def;
			}
		}

		return src;
	}

	public XElement findId(String name, String id) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);

			List<XElement> fnd = el.selectAll(name);

			for (XElement xn : fnd) {
				if (id.equals(xn.getAttribute("Id")))
					return xn;
			}
		}

		ConfigResource parent = this.getParentResource();

		if (parent != null)
			return parent.findId(name, id);

		return null;
	}
	
	public XElement findIdLocal(String name, String id) {
		for (int i = this.configlist.size() - 1; i >= 0; i--) {
			XElement el = this.configlist.get(i);
			
			List<XElement> fnd = el.selectAll(name);
			
			for (XElement xn : fnd) {
				if (id.equals(xn.getAttribute("Id")))
					return xn;
			}
		}
		
		return null;
	}
	
	public XElement getCatalog(String name, String alternate) {
		if (StringUtil.isEmpty(name))
			return null;
		
		String lname = StringUtil.isNotEmpty(alternate) ? name + "-" + alternate : name;
		
		// try Both first so we can override layers of config - otherwise lower layers of Prod or Test always win over Both
		XElement cat = this.findIdLocal("Catalog", lname + "-Both");
		
		if (cat != null)
			return cat;
		
		String mname = lname + "-" + (ApplicationHub.isProduction() ? "Production" : "Test");
		
		cat = this.findIdLocal("Catalog", mname);
		
		if (cat != null)
			return cat;
		
		ConfigResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.getCatalog(name, alternate);
		
		return null;
	}

	public XElement getCatalog(String name, String alternate, String mode) {
		if (StringUtil.isEmpty(name))
			return null;

		String lname = StringUtil.isNotEmpty(alternate) ? name + "-" + alternate : name;

		String mname = lname + "-" + mode;

		XElement cat = this.findIdLocal("Catalog", mname);

		if (cat != null)
			return cat;

		ConfigResource parent = this.getParentResource();

		if (parent != null)
			return parent.getCatalog(name, alternate, mode);

		return null;
	}
}
