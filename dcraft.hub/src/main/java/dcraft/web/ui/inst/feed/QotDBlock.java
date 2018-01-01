package dcraft.web.ui.inst.feed;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class QotDBlock extends Base {
	static public QotDBlock tag() {
		QotDBlock el = new QotDBlock();
		el.setName("dcm.QotDBlock");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return QotDBlock.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// TODO add parameters and backend data files to this
		
		// test the Build step ability to convert XElement to UIElement 
		
		this
			.with(new XElement("h3").withText("Quote of the Day!"))
			.with(new XElement("blockquote")
				.with(new XElement("p")
					.withText("My soul can find no staircase to Heaven unless it be through Earth's loveliness.")
				)
				.with(new XElement("p")
					.withAttribute("class", "quoteby")
					.withText("- Michelangelo")
				)
			);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// don't change my identity until after the scripts run
		this.withAttribute("class", "dcw-qotd");
		
		this.setName("div");
	}
}
