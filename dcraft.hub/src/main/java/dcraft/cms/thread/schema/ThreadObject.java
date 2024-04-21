package dcraft.cms.thread.schema;

import dcraft.cms.meta.CustomIndexAdapter;
import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DbServiceRequest;
import dcraft.db.IConnectionManager;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

public class ThreadObject extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		if ("Create".equals(code.getName())) {
			RecordStruct data = RecordStruct.record()
					.with("Title", StackUtil.stringFromElementClean(state, code, "Title"))
					.with("Type", StackUtil.stringFromElementClean(state, code, "Type"))
					.with("From", StackUtil.stringFromElementClean(state, code, "From"))
					.with("Deliver", StackUtil.stringFromElementClean(state, code, "Deliver"))
					.with("End", StackUtil.stringFromElementClean(state, code, "End"))
					.with("SharedAttributes", StackUtil.refFromElement(state, code, "SharedAttributes", true));

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "Create")
					.withData(data)
					.withOutcome(
							new OperationOutcomeStruct() {
								@Override
								public void callback(BaseStruct result) throws OperatingContextException {
									if (this.isNotEmptyResult())
										//	StackUtil.addVariable(state, name, result);
										ThreadObject.this.copyFields((RecordStruct) result);

									state.withContinueFlag();

									OperationContext.getAsTaskOrThrow().resume();
								}
							})
			);

			return ReturnOption.AWAIT;
		}

		if ("AddParties".equals(code.getName())) {
			RecordStruct data = RecordStruct.record();

			data.copyFields(this);
			data.with("Parties", StackUtil.refFromElement(state, code, "Value", true));

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "AddParties")
					.withData(data)
					.withOutcome(
							new OperationOutcomeStruct() {
								@Override
								public void callback(BaseStruct result) throws OperatingContextException {
									state.withContinueFlag();

									OperationContext.getAsTaskOrThrow().resume();
								}
							})
			);

			return ReturnOption.AWAIT;
		}

		if ("SetTitle".equals(code.getName())) {
			String title = StackUtil.resolveValueToString(state, code.getValue(), true);

			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmThread")
					.withId(this.getFieldAsString("Id"))
					.withUpdateField("dcmTitle", title)
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(BaseStruct result) throws OperatingContextException {
							state.withContinueFlag();

							OperationContext.getAsTaskOrThrow().resume();
						}
					})

			);

			return ReturnOption.AWAIT;
		}

		if ("AddContent".equals(code.getName())) {
			String content = StackUtil.resolveValueToString(state, code.getValue(), true);
			String contenttype = StackUtil.stringFromElementClean(state, code, "Type");
			String originator = StackUtil.stringFromElementClean(state, code, "Originator");

			//String name = StackUtil.stringFromElement(state, code, "Result");

			RecordStruct data = RecordStruct.record()
					.with("Content", content)
					.with("ContentType", contenttype)
					.with("Originator", originator);

			data.copyFields(this);

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "AddContent")
					.withData(data)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(BaseStruct result) throws OperatingContextException {
								//if (StringUtil.isNotEmpty(name))
								//	StackUtil.addVariable(state, name, result);

								state.withContinueFlag();

								OperationContext.getAsTaskOrThrow().resume();
							}
						})
			);

			return ReturnOption.AWAIT;
		}

		if ("Deliver".equals(code.getName())) {
			//String name = StackUtil.stringFromElement(state, code, "Result");

			RecordStruct data = RecordStruct.record();

			data.copyFields(this);

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "Deliver")
					.withData(data)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(BaseStruct result) throws OperatingContextException {
								//if (StringUtil.isNotEmpty(name))
								//	StackUtil.addVariable(state, name, result);

								state.withContinueFlag();

								OperationContext.getAsTaskOrThrow().resume();
							}
						})
			);

			return ReturnOption.AWAIT;
		}

		if ("BuildDeliver".equals(code.getName())) {
			//String name = StackUtil.stringFromElement(state, code, "Result");

			RecordStruct data = RecordStruct.record();

			data.copyFields(this);

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "BuildDeliver")
					.withData(data)
					.withOutcome(
						new OperationOutcomeStruct() {
							@Override
							public void callback(BaseStruct result) throws OperatingContextException {
								//if (StringUtil.isNotEmpty(name))
								//	StackUtil.addVariable(state, name, result);

								state.withContinueFlag();

								OperationContext.getAsTaskOrThrow().resume();
							}
						})
			);

			return ReturnOption.AWAIT;
		}

		if ("CollectMessageAccess".equals(code.getName())) {
			String result = StackUtil.stringFromElement(state, code, "Result");

			if (StringUtil.isNotEmpty(result)) {
				String tid = this.getFieldAsString("Id");

				IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

				TablesAdapter adapter = TablesAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

				java.util.List<String> access = ThreadUtil.collectMessageAccess(adapter, OperationContext.getOrThrow(), tid);

				StackUtil.addVariable(state, result, ListStruct.list(access));
			}

			return ReturnOption.CONTINUE;
		}

		if ("DeliverReply".equals(code.getName())) {
			String tid = this.getFieldAsString("Id");

			IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

			TablesAdapter adapter = TablesAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

			java.util.List<String> myparties = ThreadUtil.collectMessageAccess(adapter, OperationContext.getOrThrow(), tid);

			java.util.List<String> parties = adapter.getListKeys("dcmThread", tid, "dcmParty");

			for (String party : parties) {
				if (! myparties.contains(party)) {
					ThreadUtil.updateFolder(adapter, tid, party, "/InBox", false);
				}
			}

			ThreadUtil.updateDeliver(adapter, tid, TimeUtil.now());

			return ReturnOption.CONTINUE;
		}

		return super.operation(state, code);
	}
}
