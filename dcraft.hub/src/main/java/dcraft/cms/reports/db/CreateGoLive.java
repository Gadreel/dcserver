package dcraft.cms.reports.db;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class CreateGoLive implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		// TODO assemble this list of steps from settings that may include package level settings

		ServiceHub.call(ServiceRequest.of("dcCoreServices.TaskList.Add")
				.withData(RecordStruct.record()
						.with("Title", "Go Live Report")
						.with("Description", "Go Live Report")
						.with("Params", null)
						.with("LeadTab", RecordStruct.record()
								.with("Alias", "main")
								.with("Title", "Summary")
								.with("Path", "/dcr/go-live/main/$Id")
						)
						.with("Steps", ListStruct.list()
								.with(RecordStruct.record()
										.with("Title", "Config")
										.with("Description", "Review of config.xml and shared.xml")
										.with("Weight", 25)
										.with("ChildTab", RecordStruct.record()
												.with("Alias", "step-0000")
												.with("Title", "Config")
												.with("Path", "/dcr/go-live/review-config/$Id")
										)
								)
								.with(RecordStruct.record()
										.with("Title", "Feeds")
										.with("Description", "Review of Site Feeds")
										.with("Weight", 25)
										.with("ChildTab", RecordStruct.record()
												.with("Alias", "step-0001")
												.with("Title", "Feeds")
												.with("Path", "/dcr/go-live/review-feeds/$Id")
										)
								)
								.with(RecordStruct.record()
										.with("Title", "Galleries")
										.with("Description", "Review of Galleries")
										.with("Weight", 25)
										.with("ChildTab", RecordStruct.record()
												.with("Alias", "step-0002")
												.with("Title", "Galleries")
												.with("Path", "/dcr/go-live/review-galleries/$Id")
										)
								)
								.with(RecordStruct.record()
										.with("Title", "DCA Specific")
										.with("Description", "Additional checks that are DCA specific")
										.with("Weight", 25)
										.with("ChildTab", RecordStruct.record()
												.with("Alias", "step-0003")
												.with("Title", "DCA Specific")
												.with("Path", "/dcr/go-live/review-dca/$Id")
										)
								)
						)
				)
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (this.isNotEmptyResult()) {
							String reportid = ((RecordStruct) result).getFieldAsString("Id");

							db.updateStaticScalar("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmCurrentGoliveReport", reportid);

							callback.returnValue(RecordStruct.record()
									.with("Id", reportid)
							);
						}
						else {
							callback.returnEmpty();
						}
					}
				})
		);
	}
}
