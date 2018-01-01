package dcraft.example.util;

import dcraft.hub.config.LocalConfigLoader;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.locale.Dictionary;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Path;

public class ExampleConfigLoader extends LocalConfigLoader {
	public static ExampleConfigLoader local(Class<?> exclass) {
		ExampleConfigLoader res = new ExampleConfigLoader();
		res.exclass = exclass;
		return res;
	}
	
	protected Class<?> exclass = null;
	
	@Override
	public Path resolvePath(Path p) {
		return null;
	}
	
	@Override
	public Path resolveRolePath(Path p) {
		return null;
	}
	
	@Override
	public Path resolveNodePath(Path p) {
		return null;
	}
	
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		// ------------------------------------------
		//   load example config from resource file
		// ------------------------------------------
		
		ConfigResource configres = tier.getOrCreateTierConfig();
		
		CharSequence configstr = this.getResourceAsChars("config.xml");
		
		if (StringUtil.isEmpty(configstr)) {
			Logger.errorTr(101, "Unable to find config.xml file");
			taskctx.returnEmpty();
			return;
		}
		
		XElement config = XmlReader.parse(configstr, false, true);
		
		if (config == null) {
			Logger.errorTr(102, "Unable to parse config.xml file");
			taskctx.returnEmpty();
			return;
		}
		
		configres.add(config);
		
		this.initResources(taskctx, tier);
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
	
	@Override
	public Dictionary getDictionary(ResourceTier tier) {
		Dictionary dictionary = super.getDictionary(tier);
		
		XElement exdict = tier.getConfig().getTag("Dictionary");
		
		if (exdict != null)
			dictionary.load(exdict);
		
		return dictionary;
	}
	
	@Override
	public SchemaResource getSchema(ResourceTier tier) {
		SchemaResource resource = super.getSchema(tier);
		
		XElement exschema = tier.getConfig().getTag("Schema");
		
		if (exschema != null)
			resource.loadSchema("example", exschema);
		
		resource.compile();
		
		return resource;
	}
	
	public CharSequence getResourceAsChars(String type) {
		String fileName = this.exclass.getCanonicalName().replace('.', '/') + "-" + type;
		
		return IOUtil.readEntireStream(this.exclass.getClassLoader().getResourceAsStream(fileName));
	}
}
