package dcraft.web.ui.inst.misc;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class ServerInfoWidget extends Base {
	static public ServerInfoWidget tag() {
		ServerInfoWidget el = new ServerInfoWidget();
		el.setName("dcm.ServerInfo");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ServerInfoWidget.tag();
	}
	
	public ServerInfoWidget() {
		super("dc.ServerInfo");
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// TODO add parameters - only works for Admins

		/* TODO restore
		work.get().incExpand();
		
		Task itask = Task
			.ofSubContext()
			.withTitle("Collecting Server Info")
			.withWork(new IWork() {
				@Override
				public void run(TaskContext trun) {
					Logger.info("Starting Task: " + trun.getTask());
					
					ServerInfo.this.with(new UIElement("div")
							.withText("Value from database.")
						);
					
					ServerInfo.this.getRoot()
						.with(new UIElement("dc.Function").withAttribute("Mode", "Load").withCData("console.log('t1');"))
						.with(new UIElement("dc.Function").withAttribute("Mode", "Load").withCData("console.log('t2');"));
					
					work.get().decExpand();
					
					trun.complete();
				}
			});
		
		TaskHub.scheduleIn(itask, 2);
		*/
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// don't change my identity until after the scripts run
		this.withAttribute("class", "dcw-server-info");
		this.setName("div");
	}
}
