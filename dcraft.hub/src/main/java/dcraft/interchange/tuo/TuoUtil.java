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
	
	static public void loadStoreOrdersDetail(String storeid, String token, OperationOutcomeList callback) {
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
			
			URL url = new URL("https://api.tuosystems.com/api/v2/order/search?store_id=" + storeid);
			
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

	static public void loadStoreProducts(String storeid, String token, OperationOutcomeList callback) {
		/*
		   {
				"id": 1021573,
				"name": "Uniform 2p - Fight Shorts (Bottom)",
				"storefront_name": "Uniform 2p - Fight Shorts (Bottom)",
				"brand": "GO EARN IT",
				"type": "Compression",
				"description": "",
				"sku": "1000031",
				"upc": null,
				"active": true,
				"catalog_id": 74474,
				"store_id": 1159841,
				"images": [
					{
						"url": "https://s3.amazonaws.com/tuo-p-public/product_images/756a4ecef52975020b320aee3ca25db7365443bd.png?1548013622",
						"position": 1,
						"filesize": 57396
					}
				],
				"colors": {
					"No color": [
						"NA",
						"NA"
					]
				},
				"sizes": [
					{
						"category_name": "Standard Youth",
						"code": "YXS",
						"price": "99.95"
					},
					{
						"category_name": "Standard Youth",
						"code": "YS",
						"price": "99.95"
					},
					...
				]
			},
		*/

		try {
			OperationContext.getOrThrow().touch();
			
			URL url = new URL("https://api.tuosystems.com/api/v2/product/search?store_id=" + storeid);
			
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

				if (resp instanceof RecordStruct)
					resp = ListStruct.list(resp);		// turn it into a list
				
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
