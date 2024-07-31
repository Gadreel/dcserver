package dcraft.mail;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;

public class CommAdapter extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("EnsureTracker".equals(code.getName())) {
			String channel = Struct.objectToString(StackUtil.refFromElement(stack, code, "Channel", true));
			String address = Struct.objectToString(StackUtil.refFromElement(stack, code, "Address", true));
			String userId = Struct.objectToString(StackUtil.refFromElement(stack, code, "UserId", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String trackid = CommUtil.ensureCommTrack(channel, address, userId);

			if (StringUtil.isEmpty(trackid))
				Logger.error("Missing comm track address");

			if (StringUtil.isNotEmpty(handle)) {
				if (StringUtil.isEmpty(trackid))
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				else
					StackUtil.addVariable(stack, handle, StringStruct.of(trackid));
			}

			return ReturnOption.CONTINUE;
		}

		if ("PrepareAddresses".equals(code.getName())) {
			String channel = Struct.objectToString(StackUtil.refFromElement(stack, code, "Channel", true));
			BaseStruct addresses = StackUtil.refFromElement(stack, code, "Address", true);
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));
			ListStruct results = ListStruct.list();

			if (StringUtil.isEmpty(channel)) {
				Logger.error("Missing comm track channel");
			}
			else if ((addresses == null) || (! (addresses instanceof ListStruct) && ! (addresses instanceof StringStruct))) {
				Logger.error("Missing comm track addresses");
			}
			else if (addresses instanceof StringStruct) {
				CharSequence addrs = ((StringStruct) addresses).getValue();

				if (StringUtil.isNotEmpty(addrs)) {
					results = CommUtil.prepareAddresses(channel, addrs.toString());
				}
			}
			else if (addresses instanceof ListStruct) {
				results = CommUtil.prepareAddresses(channel, (ListStruct) addresses);
			}

			if (StringUtil.isNotEmpty(handle))
				StackUtil.addVariable(stack, handle, results);

			return ReturnOption.CONTINUE;
		}

		if ("PrepareSend".equals(code.getName())) {
			String channel = Struct.objectToString(StackUtil.refFromElement(stack, code, "Channel", true));
			BaseStruct addresses = StackUtil.refFromElement(stack, code, "Address", true);
			BaseStruct args = StackUtil.refFromElement(stack, code, "Args", true);
			BaseStruct tags = StackUtil.refFromElement(stack, code, "Tags", true);
			String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path"));
			String handle = StackUtil.stringFromElement(stack, code, "Result");
			ListStruct results = ListStruct.list();

			if (StringUtil.isEmpty(channel)) {
				Logger.error("Missing comm track send channel");
			}
			else if ((addresses == null) || (! (addresses instanceof ListStruct))) {
				Logger.error("Missing comm track send addresses");
			}
			else if (StringUtil.isEmpty(path)) {
				Logger.error("Missing comm path");
			}
			else if (addresses instanceof ListStruct) {
				results = CommUtil.prepareSend(channel, path, (ListStruct) addresses, args, Struct.objectToList(tags));
			}

			if (StringUtil.isNotEmpty(handle))
				StackUtil.addVariable(stack, handle, results);

			return ReturnOption.CONTINUE;
		}

		if ("BuildContent".equals(code.getName())) {
			String channel = Struct.objectToString(StackUtil.refFromElement(stack, code, "Channel", true));
			RecordStruct request = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Request", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			if (StringUtil.isEmpty(channel)) {
				Logger.error("Missing comm track send channel");
			}
			else if (request == null) {
				Logger.error("Missing comm request");
			}
			else if (request.isFieldEmpty("Path")) {
				Logger.error("Missing comm path");
			}
			else if (request.isFieldEmpty("SendId")) {
				Logger.error("Missing comm send id");
			}
			else {
				CommUtil.buildContent(channel, request, new OperationOutcomeRecord() {
					@Override
					public void callback(RecordStruct result) throws OperatingContextException {
						stack.setState(ExecuteState.DONE);

						if (StringUtil.isNotEmpty(handle))
							StackUtil.addVariable(stack, handle, result);

						OperationContext.getAsTaskOrThrow().resume();
					}
				});

				return ReturnOption.AWAIT;
			}

			return ReturnOption.CONTINUE;
		}

		if ("Deliver".equals(code.getName())) {
			String channel = Struct.objectToString(StackUtil.refFromElement(stack, code, "Channel", true));
			RecordStruct request = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Request", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			if (StringUtil.isEmpty(channel)) {
				Logger.error("Missing comm track deliver channel");
			}
			else if (request == null) {
				Logger.error("Missing comm deliver request");
			}
			else if (request.isFieldEmpty("Path")) {
				Logger.error("Missing comm path");
			}
			else if (request.isFieldEmpty("SendId")) {
				Logger.error("Missing comm deliver send id");
			}
			else {
				CommUtil.deliver(channel, request, new OperationOutcomeRecord() {
					@Override
					public void callback(RecordStruct result) throws OperatingContextException {
						stack.setState(ExecuteState.DONE);

						if (StringUtil.isNotEmpty(handle))
							StackUtil.addVariable(stack, handle, result);

						OperationContext.getAsTaskOrThrow().resume();
					}
				});

				return ReturnOption.AWAIT;
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
}
