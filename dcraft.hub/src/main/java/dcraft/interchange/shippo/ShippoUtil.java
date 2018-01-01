package dcraft.interchange.shippo;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.URL;

public class ShippoUtil {
	static public final String ENDPOINT = "https://api.goshippo.com/";

	static public void createShipment(String alt, RecordStruct order, OperationOutcomeRecord callback) {
		XElement auth = ApplicationHub.getCatalogSettings("CMS-Shipping-Shippo", alt);

		if (auth == null) {
			Logger.error("Missing Shippo settings.");
			callback.returnEmpty();
			return;
		}

		String etoken = auth.getAttribute("Token");

		if (StringUtil.isEmpty(etoken)) {
			Logger.error("Missing Shippo token.");
			callback.returnEmpty();
			return;
		}

		String token = ApplicationHub.getClock().getObfuscator().decryptHexToString(etoken);

		if (StringUtil.isEmpty(token)) {
			Logger.error("Invalid Shippo token setting.");
			callback.returnEmpty();
			return;
		}

		XElement store = ApplicationHub.getCatalogSettings("CMS-Store", alt);		// alt applies across storefront

		if (store == null) {
			Logger.error("Missing Store settings.");
			callback.returnEmpty();
			return;
		}

		XElement shipfrom = store.selectFirst("Shipping/From");

		if (shipfrom == null) {
			Logger.error("Missing Store shipping settings.");
			callback.returnEmpty();
			return;
		}

		RecordStruct shipto = order.getFieldAsRecord("ShippingInfo");
		RecordStruct cinfo = order.getFieldAsRecord("CustomerInfo");

		RecordStruct shipment = RecordStruct.record()
				.with("address_from",
						RecordStruct.record()
							.with("name", shipfrom.getAttribute("Name"))
							.with("street1", shipfrom.getAttribute("Street1"))
							.with("city", shipfrom.getAttribute("City"))
							.with("state", shipfrom.getAttribute("State"))
							.with("zip", shipfrom.getAttribute("Zip"))
							.with("country", shipfrom.getAttribute("Country"))
							.with("phone", shipfrom.getAttribute("Phone"))
							.with("email", shipfrom.getAttribute("Email"))
				)
				.with("address_to",
						RecordStruct.record()
								.with("name", shipto.selectAsString("FirstName") + " "
									+ shipto.selectAsString("LastName"))
								.with("street1", shipto.selectAsString("Address"))
								.with("city", shipto.selectAsString("City"))
								.with("state", shipto.selectAsString("State"))
								.with("zip", shipto.selectAsString("Zip"))
								.with("country", shipto.selectAsString("Country", "US"))		// TODO review
								.with("phone", cinfo.selectAsString("Phone"))
								.with("email", cinfo.selectAsString("Email"))
				)
				.with("parcels", ListStruct.list(
						RecordStruct.record()
							.with("length","5")
							.with("width", "5")
							.with("height", "5")
							.with("distance_unit", "in")
							.with("weight", "2")
							.with("mass_unit", "lb")
					)
				)
				.with("async", false);

		// TODO
		/*
{
       "address_from":{
          "name":"Mr. Hippo",
          "street1":"215 Clayton St.",
          "city":"San Francisco",
          "state":"CA",
          "zip":"94117",
          "country":"US",
          "phone":"+1 555 341 9393",
          "email":"support@goshippo.com"
       },
       "address_to":{
          "name":"Mrs. Hippo",
          "street1":"965 Mission St.",
          "city":"San Francisco",
          "state":"CA",
          "zip":"94105",
          "country":"US",
          "phone":"+1 555 341 9393",
          "email":"support@goshippo.com"
       },
       "parcels":[{
          "length":"5",
          "width":"5",
          "height":"5",
          "distance_unit":"in",
          "weight":"2",
          "mass_unit":"lb"
       }],
       "async": false
    }
		 */

		try {
			OperationContext.getOrThrow().touch();

			URL url = new URL(ShippoUtil.ENDPOINT + "shipments/");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("Authorization", "ShippoToken " + token);
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");

			String body = shipment.toString();

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(body);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());

				if (resp == null) {
					Logger.error("Error processing shipment: Shippo sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

				System.out.println("shippo:\n" + resp.toPrettyString());

				callback.returnValue((RecordStruct) resp);

				/* TODO should we process the response?
				XElement tr = resp.find("transactionResponse");

				if (tr == null) {
					Logger.error("Error processing payment: Gateway sent an incomplete response.");

					callback.returnValue(
							new RecordStruct()
									.with("Message", resp)
					);

					return;
				}

				XElement trc = tr.find("responseCode");
				XElement trid = tr.find("transId");

				if ((trc == null) || (trid == null)) {
					Logger.error("Error processing payment: Gateway sent an incomplete transaction response.");

					callback.returnValue(
							new RecordStruct()
									.with("Message", resp)
					);

					return;
				}

				String rcodeout = trc.getText();
				String txidout = trid.getText();

				if (! "1".equals(rcodeout))
					Logger.error("Payment was rejected by gateway");

				callback.returnValue(
						new RecordStruct()
								.with("Code", rcodeout)
								.with("TxId", txidout)
								.with("Message", resp)
				);
				*/

				return;
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
}
