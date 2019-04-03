package dcraft.filevault;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.interchange.slack.SlackUtil;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.file.FilterStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class ManagedFormVault extends EncryptedVault {
	@Override
	public void afterDeposit(RecordStruct manifest) throws OperatingContextException {
		super.afterDeposit(manifest);
		
		System.out.println("run mf post script");
		
		String datapath = manifest.selectAsString("Write.0");
		
		if (StringUtil.isNotEmpty(datapath)) {
			CommonPath dp = CommonPath.from(datapath);
			String id = dp.getName(0);		//first part of path will be the id
			
			BasicRequestContext brc = BasicRequestContext.ofDefaultDatabase();
			TablesAdapter db = TablesAdapter.ofNow(brc);
			
			String form = Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmManagedFormName"));
			
			String scriptpath = null;
			
			XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);
			
			if (mform == null) {
				String fid = Struct.objectToString(db.firstInIndex("dcmBasicCustomForm", "dcmAlias", form, true));
				
				if (StringUtil.isEmpty(fid)) {
					Logger.error("Managed form not enabled.");
					return;
				}
				
				scriptpath = "/dcm/forms/event-basic-form-submitted.dcs.xml";
			}
			else {
				scriptpath = mform.getAttribute("Script", "/dcm/forms/event-form-submitted.dcs.xml");
			}
			
			// TODO use task queue instead
			TaskHub.submit(Task.ofSubtask("ManagedForm submitted", "CMS")
					.withTopic("Batch")
					.withMaxTries(5)
					.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
					.withParams(RecordStruct.record()
							.with("Id", id)
					)
					.withScript(CommonPath.from(scriptpath)));
			
			Site site = OperationContext.getOrThrow().getSite();
			String event = site.getAlias() + " - form submission completed: " + form;
			SlackUtil.serverEvent(null, event, null);
		}
	}
}
