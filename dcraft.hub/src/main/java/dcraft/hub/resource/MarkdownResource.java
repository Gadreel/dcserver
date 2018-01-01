package dcraft.hub.resource;

import dcraft.web.md.Configuration;
import dcraft.web.md.Plugin;
import dcraft.web.md.plugin.GallerySection;
import dcraft.web.md.plugin.HtmlSection;
import dcraft.web.md.plugin.MediaSection;
import dcraft.web.md.plugin.PairedMediaSection;
import dcraft.web.md.plugin.SlidesSection;
import dcraft.web.md.plugin.StandardSection;

// TODO config should be per domain / website
public class MarkdownResource extends ResourceBase {
	protected Configuration unsafeconfig = null;
	protected Configuration safeconfig = null;
	
	public MarkdownResource() {
		this.setName("Markdown");
	}

	public MarkdownResource getParentResource() {
		if (this.tier == null)
			return null;

		ResourceTier pt = this.tier.getParent();

		if (pt != null)
			return pt.getMarkdown();

		return null;
	}

	public Configuration getUnsafeConfig() {
		if (this.unsafeconfig != null)
			return this.unsafeconfig;

		MarkdownResource parent = this.getParentResource();

		if (parent != null)
			return parent.getUnsafeConfig();

		// set the server wide defaults

		this.unsafeconfig = new Configuration()
				.setSafeMode(false)
				.registerPlugins(new PairedMediaSection(), new StandardSection(),
						new GallerySection(), new HtmlSection(), new MediaSection(),
						new SlidesSection()
				);

		// TODO
		//.registerPlugins(new YumlPlugin(), new WebSequencePlugin(), new IncludePlugin());

		return this.unsafeconfig;
	}
	
	public Configuration getSafeConfig() {
		if (this.safeconfig != null)
			return this.safeconfig;

		MarkdownResource parent = this.getParentResource();

		if (parent != null)
			return parent.getSafeConfig();

		// set the server wide defaults
		this.safeconfig = new Configuration();

		return this.safeconfig;
	}
	
	public MarkdownResource withPlugins(Plugin... plugins) {
		this.unsafeconfig.registerPlugins(plugins);
		return this;
	}
}
