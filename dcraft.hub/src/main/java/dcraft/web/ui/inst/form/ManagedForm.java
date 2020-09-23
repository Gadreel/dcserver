package dcraft.web.ui.inst.form;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.web.ui.inst.IReviewAware;
import dcraft.xml.XElement;

public class ManagedForm extends Form implements IReviewAware {
	static public ManagedForm tag() {
		ManagedForm el = new ManagedForm();
		el.setName("dcf.ManagedForm");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ManagedForm.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.removeAttribute("RecordOrder");		// only Default record is allowed

		String form = this.getAttribute("Name");

		XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);

		if (mform == null) {
			Logger.warn("Managed form not enabled.");
		}
		else if (mform.hasNotEmptyAttribute("Type")) {
			String rtype = mform.getAttribute("Type");

			this.withAttribute("data-dcf-record-type", rtype);

			// automatically require the form's data types
			this.getRoot(state).with(XElement.tag("Require")
					.withAttribute("Types", rtype));
		}

		this.withAttribute("data-dcf-managed", "true");
		
		// defaults to true so that form prefills will still be saved
		if (! this.hasNotEmptyAttribute("AlwaysNew"))
			this.withAttribute("data-dcf-always-new", "true");

		super.renderAfterChildren(state);
	}

	@Override
	public IWork buildReviewWork(RecordStruct result) throws OperatingContextException {
		return new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				String form = ManagedForm.this.getAttribute("data-dcf-name");

				XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);

				ListStruct forms = result.getFieldAsList("ManagedForms");

				if (forms == null) {
					forms = ListStruct.list();
					result.with("ManagedForms", forms);
				}

				forms.with(RecordStruct.record()
						.with("Name", form)
						.with("Defined", (mform != null))
				);

				taskctx.returnEmpty();
			}
		};
	}
}
