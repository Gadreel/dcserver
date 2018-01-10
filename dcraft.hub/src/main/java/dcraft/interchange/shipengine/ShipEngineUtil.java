package dcraft.interchange.shipengine;

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

public class ShipEngineUtil {
	static public final String ENDPOINT = "https://api.shipengine.com/";

	static public void createShipment(String alt, RecordStruct order, OperationOutcomeRecord callback) {
		XElement auth = ApplicationHub.getCatalogSettings("CMS-Shipping-ShipEngine", alt);

		if (auth == null) {
			Logger.error("Missing ShipEngine settings.");
			callback.returnEmpty();
			return;
		}

		String etoken = auth.getAttribute("Token");

		if (StringUtil.isEmpty(etoken)) {
			Logger.error("Missing ShipEngine token.");
			callback.returnEmpty();
			return;
		}

		String token = ApplicationHub.getClock().getObfuscator().decryptHexToString(etoken);

		if (StringUtil.isEmpty(token)) {
			Logger.error("Invalid ShipEngine token setting.");
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
		RecordStruct sminfo = order.getFieldAsRecord("ShipmentInfo");

		RecordStruct shipment = RecordStruct.record()
				.with("shipment", RecordStruct.record()
					.with("validate_address", "validate_and_clean")
					.with("ship_from",
							RecordStruct.record()
								.with("name", shipfrom.getAttribute("Name"))
								.with("company_name", shipfrom.getAttribute("Company"))
								.with("address_line1", shipfrom.getAttribute("Street1"))
								.with("address_line2", shipfrom.getAttribute("Street2"))
								.with("city_locality", shipfrom.getAttribute("City"))
								.with("state_province", shipfrom.getAttribute("State"))
								.with("postal_code", shipfrom.getAttribute("Zip"))
								.with("country_code", shipfrom.getAttribute("Country"))
								.with("phone", shipfrom.getAttribute("Phone"))
					)
					.with("ship_to",
							RecordStruct.record()
									.with("name", shipto.selectAsString("FirstName") + " "
										+ shipto.selectAsString("LastName"))
									.with("address_line1", shipto.selectAsString("Address"))
									.with("address_line2", shipto.selectAsString("Address2"))
									.with("city_locality", shipto.selectAsString("City"))
									.with("state_province", shipto.selectAsString("State"))
									.with("postal_code", shipto.selectAsString("Zip"))
									.with("country_code", shipto.selectAsString("Country", "US"))		// TODO review
									.with("phone", cinfo.selectAsString("Phone"))
					)
					.with("packages", ListStruct.list(
							RecordStruct.record()
								.with("weight", RecordStruct.record()
									.with("value", sminfo.getFieldAsString("Weight"))
									.with("unit", "ounce")
								)
						)
					)
				)
				.with("rate_options", RecordStruct.record()
						.with("carrier_ids",ListStruct.list(
								auth.getAttribute("Carriers")		// TODO split or something
						))
				);

		try {
			OperationContext.getOrThrow().touch();

			URL url = new URL(ShipEngineUtil.ENDPOINT + "v1/rates");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("api-key", token);
			con.setRequestProperty("User-Agent", "dcServer/1.0 (Language=Java/8)");
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
					Logger.error("Error processing shipment: ShipEngine sent an incomplete response.");
					callback.returnEmpty();
					return;
				}

				//System.out.println("ShipEngine:\n" + resp.toPrettyString());

				callback.returnValue((RecordStruct) resp);

				return;
			}
			else {
				Logger.error("Error processing shipping: Unable to connect to shipping gateway.");
			}
		}
		catch (Exception x) {
			Logger.error("Error processing shipping: Unable to connect to shipping gateway.");
		}

		callback.returnEmpty();
	}
}
