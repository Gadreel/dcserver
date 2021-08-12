package dcraft.cms.store.db.orders;

import dcraft.cms.store.OrderUtil;
import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.paypal.PayPalUtil;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PayPalStandard implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		String postdata = data.getFieldAsString("Data");

		RecordStruct form = RecordStruct.record();

		// decode
		QueryStringDecoder decoderQuery = new QueryStringDecoder(postdata, false);
		Map<String, List<String>> params = decoderQuery.parameters();        // TODO decode

		for (Map.Entry<String, List<String>> entry : params.entrySet()) {
			form.with(entry.getKey(), ListStruct.list().withCollection(entry.getValue()));
		}

		String uuid = form.selectAsString("custom.0");

		String id = Struct.objectToString(db.firstInIndex("dcmThread", "dcmUuid", uuid, true));

		if (StringUtil.isEmpty(id) || ! db.isCurrent("dcmThread", id)) {
			Logger.error("Invalid thread id");
			callback.returnEmpty();
			return;
		}

		if (! "dcmOrderPayment".equals(db.getScalar("dcmThread", id, "dcmMessageType"))) {
			Logger.error("Invalid thread type");
			callback.returnEmpty();
			return;
		}

		RecordStruct order = Struct.objectToRecord(db.getScalar("dcmThread", id, "dcmOrderData"));

		if (order == null) {
			Logger.error("Missing order record.");
			callback.returnEmpty();
			return;
		}

		RecordStruct pinfo = order.getFieldAsRecord("PaymentInfo");

		if (pinfo == null) {
			Logger.error("Missing order payment record.");
			callback.returnEmpty();
			return;
		}

		/*
		payer_email=lightofgadrel%40gmail.com
		payer_id=5H3UZE5BAQAEQ
		payer_status=VERIFIED
		first_name=Andy
		last_name=White
		address_name=Andrew+White
		address_street=345+Lark+Ave
		address_city=San+Jose
		address_state=CA
		address_country_code=US
		address_zip=95121
		residence_country=US
		txn_id=7TU625337N367241A
		mc_currency=USD
		mc_gross=34.65
		protection_eligibility=INELIGIBLE
		payment_fee=1.30
		payment_gross=34.65
		payment_status=Pending
		pending_reason=unilateral
		payment_type=instant
		item_name1=Curve+Talking+Clock
		item_number1=TT220
		quantity1=2
		mc_gross_1=31.90
		item_name2=Shipping+and+Handling
		quantity2=1
		mc_gross_2=2.75
		num_cart_items=2
		txn_type=cart
		payment_date=2018-03-20T20%3A48%3A30Z
		business=andy%40andywhitewebworks.com
		notify_version=UNVERSIONED
		custom=order-765
		verify_sign=AWtbsfOh.AspQBD5MxJnEc1oMg1HAzwlIxqu-MXn2n1WU0ijWcv-ezqD
		*/

		// for our records
		ThreadUtil.addContent(db, id, postdata, "SafeMD");

		String status = form.selectAsString("payment_status.0");

		// Pending just means money has not transferred, but indicators are that it will go through
		if (! "Completed".equals(status) && ! "Pending".equals(status)) {
			Logger.error("Payment rejected");
			callback.returnEmpty();
			return;
		}

		String txid = form.selectAsString("txn_id.0");

		// Pending just means money has not transferred, but indicators are that it will go through
		if (StringUtil.isEmpty(txid)) {
			Logger.error("Payment missing transaction id");
			callback.returnEmpty();
			return;
		}

		// TODO to really be careful - don't allow same tx to be used twice
		db.setScalar("dcmThread", id, "dcmPaymentTx", txid);

		BigDecimal amount = Struct.objectToDecimal(db.getScalar("dcmThread", id, "dcmPaymentAmount"));
		BigDecimal payment = form.selectAsDecimal("payment_gross.0");

		if ((payment == null) || (payment.stripTrailingZeros().compareTo(amount.stripTrailingZeros()) != 0)) {
			Logger.error("Payment missing or bad amount");
			callback.returnEmpty();
			return;
		}

		PayPalUtil.verifyTx(null, postdata, new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct result) throws OperatingContextException {
				if (! this.hasErrors()) {
					boolean paid = Struct.objectToBooleanOrFalse(db.getScalar("dcmThread", id, "dcmPaymentVerified"));

					if (paid) {
						String oid = Struct.objectToString(db.getScalar("dcmThread", id, "dcmOrderId"));

						if (StringUtil.isNotEmpty(oid)) {
							Logger.info("Order " + oid + " was already verified, probably by PayPal IPN.");

							callback.returnValue(RecordStruct.record()
									.with("Id", oid)
									.with("ViewCode", db.getScalar("dcmOrder", oid, "dcmViewCode"))
							);
							return;
						}
						else {
							Logger.error("Missing order id.");
							callback.returnEmpty();
							return;
						}
					}
					else {
						db.setScalar("dcmThread", id, "dcmPaymentVerified", true);

						pinfo
								.with("Uuid", uuid)
								.with("PaymentTx", txid);

						OrderUtil.processAuthOrder2(request, db, order, callback);
						return;
					}
				}

				callback.returnEmpty();
			}
		});
	}
}
