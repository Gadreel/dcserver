package dcraft.interchange.authorize;

import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.DecimalStruct;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.math.BigDecimal;

public class AuthInterchange extends RecordStruct {

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		// TODO future support Auth and capture separately, support getting settlement batches

		if ("PaymentTransaction".equals(code.getName())) {
			String oid = StackUtil.stringFromElement(state, code, "RefId");
			BaseStruct tx = StackUtil.refFromElement(state, code, "Tx", true);
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			//System.out.println(tx);

			AuthUtilXml.paymentTransaction(state, (XElement) tx, oid, altConfig, new OperationOutcome<>() {
				@Override
				public void callback(XElement result) throws OperatingContextException {
					if (StringUtil.isNotEmpty(name) && (result != null)) {
						StackUtil.addVariable(state, name, result);
					}

					state.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("RefundTransaction".equals(code.getName())) {
			String oid = StackUtil.stringFromElement(state, code, "RefId");
			String tx = StackUtil.stringFromElement(state, code, "TransId");
			BigDecimal refundAmount = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Amount"));
			RecordStruct refundDetails = Struct.objectToRecord(StackUtil.refFromElement(state, code, "Details"));
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			AuthUtil.refundTransaction(oid, tx, refundAmount,  refundDetails, altConfig, new OperationOutcomeRecord() {
				@Override
				public void callback(RecordStruct result) throws OperatingContextException {
					if (StringUtil.isNotEmpty(name) && (result != null)) {
						StackUtil.addVariable(state, name, result);
					}

					state.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("VoidTransaction".equals(code.getName())) {
			String oid = StackUtil.stringFromElement(state, code, "RefId");
			String tx = StackUtil.stringFromElement(state, code, "TransId");
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			AuthUtil.voidTransaction(oid, tx, altConfig, new OperationOutcomeRecord() {
				@Override
				public void callback(RecordStruct result) throws OperatingContextException {
					if (StringUtil.isNotEmpty(name) && (result != null)) {
						StackUtil.addVariable(state, name, result);
					}

					state.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("TransactionDetail".equals(code.getName())) {
			String tx = StackUtil.stringFromElement(state, code, "TransId");
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			AuthUtil.getTransactionDetail(tx, altConfig, new OperationOutcomeRecord() {
				@Override
				public void callback(RecordStruct result) throws OperatingContextException {
					if (StringUtil.isNotEmpty(name) && (result != null)) {
						StackUtil.addVariable(state, name, result);
					}

					state.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("CancelFullTransaction".equals(code.getName())) {
			String oid = StackUtil.stringFromElement(state, code, "RefId");
			String tx = StackUtil.stringFromElement(state, code, "TransId");
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			AuthUtil.cancelFullTransaction(oid, tx, altConfig, new OperationOutcome<BigDecimal>() {
				@Override
				public void callback(BigDecimal amt) throws OperatingContextException {
					if (StringUtil.isNotEmpty(name) && (amt != null)) {
						StackUtil.addVariable(state, name, RecordStruct.record()
								.with("RefundAmount", amt)
						);
					}

					state.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("CancelPartialTransaction".equals(code.getName())) {
			String oid = StackUtil.stringFromElement(state, code, "RefId");
			String tx = StackUtil.stringFromElement(state, code, "TransId");
			BigDecimal refundAmount = Struct.objectToDecimal(StackUtil.refFromElement(state, code, "Amount"));
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			AuthUtil.cancelPartialTransaction(oid, tx, refundAmount, altConfig, new OperationOutcomeRecord() {
				@Override
				public void callback(RecordStruct result) throws OperatingContextException {
					if (StringUtil.isNotEmpty(name) && (result != null)) {
						StackUtil.addVariable(state, name, RecordStruct.record()
								.with("RefundAmount", result.getFieldAsDecimal("_dcAmount"))
								.with("TransId", result.getFieldAsDecimal("transId"))
						);
					}

					state.setState(ExecuteState.DONE);
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		return super.operation(state, code);
	}
}
