package dcraft.web.ui.inst.cms;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class VideoWidget extends Base implements ICMSAware {
	static public VideoWidget tag() {
		VideoWidget el = new VideoWidget();
		el.setName("dcm.VideoWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return VideoWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String url = StackUtil.stringFromSource(state,"Url");
		String preload = StackUtil.stringFromSource(state,"Preload", "auto");
		
		this.with(W3.tag("div")
				.withClass("dc-media-box", "dc-media-video dc-media-video-native")
				.with(W3.tag("video")
						.withAttribute("src", url)
						.withAttribute("preload", preload)
						.withAttribute("controls", "controls")
				)
		);
		
		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget dc-widget-video")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
	
	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		String cmd = command.getFieldAsString("Command");
		
		if ("UpdatePart".equals(cmd)) {
			// TODO check that the changes made are allowed
			RecordStruct params = command.getFieldAsRecord("Params");
			String area = params.selectAsString("Area");
			
			if ("Props".equals(area)) {
				RecordStruct props = params.getFieldAsRecord("Properties");
				
				if (props != null) {
					for (FieldStruct fld : props.getFields()) {
						this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
					}
				}

				return true;
			}
		}
		
		return false;
	}
}
