package dcraft.interchange.tuo;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeList;
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

public class TuoUtil {
	
	static public void loadOrderDetail(String orderid, String token, OperationOutcomeList callback) {
		/*
		callback.returnValue(ListStruct.list()
				.with(
						RecordStruct.record()
							.with("line_items", ListStruct.list()
									.with(
											RecordStruct.record()
												.with("product_name", "red singlet")
												.with("product_sku", "1001")
												.with("product_upc", "1")
												.with("size", "L")
												.with("quantity", 5)
													.with("unit_price", 22.14)
												.with("name_text", "HENRY,MICH,BAKER")
									)
									.with(
											RecordStruct.record()
												.with("product_name", "red singlet")
												.with("product_sku", "1001")
												.with("product_upc", "1")
												.with("size", "M")
												.with("quantity", 2)
													.with("unit_price", 23.14)
												.with("name_text", "JIM")
									)
									.with(
											RecordStruct.record()
												.with("product_name", "blue singlet")
												.with("product_sku", "1001")
												.with("product_upc", "2")
												.with("size", "M")
												.with("quantity", 4)
													.with("unit_price", 24.14)
												.with("name_text", "PERRY,DAN")
									)
									.with(
											RecordStruct.record()
												.with("product_name", "blue jacket")
												.with("product_sku", "1002")
												.with("product_upc", "1")
												.with("size", "XL")
												.with("quantity", 1)
												.with("unit_price", 29.14)
												.with("name_text", "BILL")
									)
							)
				)
		);

		return;
		*/

		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL("https://api.tuosystems.com/api/v2/order/" + orderid);
			
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "DivConq/1.0 (Language=Java/8)");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Authorization", "Token token=" + token);
			
			int responseCode = con.getResponseCode();
			
			if (responseCode != 200) {
				Logger.error("Error processing api call: Unable to load order.");
				callback.returnEmpty();
			}
			else {
				// parse and close response stream
				CompositeStruct resp = CompositeParser.parseJson(con.getInputStream());
				
				if (resp == null) {
					Logger.error("Error processing api call: incomplete response.");
					callback.returnEmpty();
				}
				else {
					callback.returnValue((ListStruct) resp);
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error processing api call: Unable to connect to gateway.");
			callback.returnEmpty();
		}
	}
}
