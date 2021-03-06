package dcraft.cms.thread.schema;

import dcraft.db.DbServiceRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class ThreadObject extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		if ("Create".equals(code.getName())) {
			RecordStruct data = RecordStruct.record()
					.with("Title", StackUtil.stringFromElement(state, code, "Title"))
					.with("Type", StackUtil.stringFromElement(state, code, "Type"))
					.with("From", StackUtil.stringFromElement(state, code, "From"))
					.with("SharedAttributes", StackUtil.refFromElement(state, code, "SharedAttributes"));

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "Create")
					.withData(data)
					.withOutcome(
							new OperationOutcomeStruct() {
								@Override
								public void callback(Struct result) throws OperatingContextException {
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
			data.with("Parties", StackUtil.refFromElement(state, code, "Value"));

			ServiceHub.call(ServiceRequest.of("dcmServices", "Thread", "AddParties")
					.withData(data)
					.withOutcome(
							new OperationOutcomeStruct() {
								@Override
								public void callback(Struct result) throws OperatingContextException {
									state.withContinueFlag();

									OperationContext.getAsTaskOrThrow().resume();
								}
							})
			);

			return ReturnOption.AWAIT;
		}

		if ("SetTitle".equals(code.getName())) {
			String title = StackUtil.resolveValueToString(state, code.getValue());

			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmThread")
					.withId(this.getFieldAsString("Id"))
					.withUpdateField("dcmTitle", title)
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							state.withContinueFlag();

							OperationContext.getAsTaskOrThrow().resume();
						}
					})

			);

			return ReturnOption.AWAIT;
		}

		if ("AddContent".equals(code.getName())) {
			String content = StackUtil.resolveValueToString(state, code.getValue());
			String contenttype = StackUtil.stringFromElement(state, code, "Type");
			String originator = StackUtil.stringFromElement(state, code, "Originator");

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
							public void callback(Struct result) throws OperatingContextException {
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
							public void callback(Struct result) throws OperatingContextException {
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
							public void callback(Struct result) throws OperatingContextException {
								//if (StringUtil.isNotEmpty(name))
								//	StackUtil.addVariable(state, name, result);

								state.withContinueFlag();

								OperationContext.getAsTaskOrThrow().resume();
							}
						})
			);

			return ReturnOption.AWAIT;
		}

		return super.operation(state, code);
	}
}
