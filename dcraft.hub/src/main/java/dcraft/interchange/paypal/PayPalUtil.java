package dcraft.interchange.paypal;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.URL;

public class PayPalUtil {
	static public final String AUTH_TEST_ENDPOINT = "https://www.sandbox.paypal.com/cgi-bin/webscr";
	static public final String AUTH_LIVE_ENDPOINT = "https://www.paypal.com/cgi-bin/webscr";

	static public void verifyTx(String authalt, String postdata, OperationOutcomeRecord callback) throws OperatingContextException {
		XElement auth = ApplicationHub.getCatalogSettings("CMS-PayPal", authalt);

		if (auth == null) {
			Logger.error("Missing store PayPal settings.");
			callback.returnEmpty();
			return;
		}

		//String lid = auth.getAttribute("BusinessEmail");

		boolean live = auth.getAttributeAsBooleanOrFalse("Live");

		// Auth documentation:
		// http://developer.authorize.net/api/reference/

		try {
			OperationContext.getOrThrow().touch();

			String path = live ? AUTH_LIVE_ENDPOINT : AUTH_TEST_ENDPOINT;
			URL url = new URL(path + "?cmd=_notify-validate");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			String body = postdata.toString();

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(body);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				CharSequence resp = IOUtil.readEntireStream(con.getInputStream());

				// switch resp to String for compare
				if (! "VERIFIED\n".equals(resp.toString()))
					Logger.error("Payment was rejected by gateway");
			}
			else {
				Logger.error("Error processing payment: Unable to connect to payment gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing payment: Unable to connect to payment gateway.");
		}

		callback.returnEmpty();
	}

	static public String authOrder(ICallContext request, TablesAdapter db, String authalt, String refid, RecordStruct order) throws OperatingContextException {
		if (! order.validate("dcmOrderInfo")) {
			return null;
		}

		if (order.isFieldEmpty("PaymentInfo")) {
			Logger.error("Missing payment details.");
			return null;
		}

		RecordStruct paymentinfo = (RecordStruct) order.removeField("PaymentInfo").getValue();

		if (paymentinfo.isFieldEmpty("Uuid") || paymentinfo.isFieldEmpty("PaymentTx")) {
			Logger.error("Missing payment details.");
			return null;
		}

		String id = ThreadUtil.getThreadId(db, paymentinfo.getFieldAsString("Uuid"));

		if (StringUtil.isEmpty(id)) {
			Logger.error("Cannot find thread");
			return null;
		}

		boolean paid = Struct.objectToBooleanOrFalse(db.getScalar("dcmThread", id, "dcmPaymentVerified"));

		if (! paid) {
			Logger.error("Payment not verified");
			return null;
		}

		String paytx = Struct.objectToString(db.getScalar("dcmThread", id, "dcmPaymentTx"));

		if (! paymentinfo.getFieldAsString("PaymentTx").equals(paytx)) {
			Logger.error("Invalid payment tx");
			return null;
		}

		db.setScalar("dcmThread", id, "dcmOrderId", refid);

		return paytx;
	}
}
