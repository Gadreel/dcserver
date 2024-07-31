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
		[
		  {
			"store_id": 2104824,
			"fulfillment_status": "Payment Received",
			"shipping_cost": "0.0",
			"discount_amount": "0.0",
			"payment_option": "credit_card",
			"country_currency": "USD",
			"line_items": [
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"size_category": "Standard Youth",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "YS",
				"product_id": 2470893,
				"pp_group": 0,
				"id": 23356847
			  },
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"size_category": "Standard Youth",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "YL",
				"product_id": 2470893,
				"pp_group": 0,
				"id": 23356854
			  },
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"name_text": "SMITH",
				"size_category": "Standard Adult",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "AXL",
				"product_id": 2470893,
				"pp_group": 0,
				"id": 23356857
			  },
			  {
				"quantity": 2,
				"product_upc": "",
				"color": "No color,NA,NA",
				"size_category": "Standard Adult",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "AS",
				"product_id": 2470894,
				"pp_group": 0,
				"id": 23356859
			  },
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"name_text": "STEVENS",
				"size_category": "Standard Adult",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "AL",
				"product_id": 2470894,
				"pp_group": 0,
				"id": 23356871
			  },
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"size_category": "Standard Youth",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "YS",
				"product_id": 2470896,
				"pp_group": 0,
				"id": 23356873
			  },
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"size_category": "Standard Adult",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "AM",
				"product_id": 2470896,
				"pp_group": 0,
				"id": 23356875
			  },
			  {
				"quantity": 1,
				"product_upc": "",
				"color": "No color,NA,NA",
				"name_text": "REDD",
				"size_category": "Standard Adult",
				"unit_price": "0.01",
				"product_name": "Shirts SS - Printed (Cotton)",
				"free_quantity": 0,
				"tags": [],
				"product_sku": "1000015",
				"size": "AXL",
				"product_id": 2470896,
				"pp_group": 0,
				"id": 23356888
			  }
			],
			"club_name": "",
			"placed_at": "2024-05-28T11:10:52.000-04:00",
			"team_name": "",
			"total": 0.09,
			"sales_tax": 0,
			"updated_at": "2024-05-28T11:10:52.000-04:00",
			"phone": "(815) 914-3220",
			"delivery_method": "bulk",
			"paid_amount": "0.09",
			"name": "Ben Lampe",
			"store_name": "Rockford Stars - 05-2024",
			"corporate_name": "",
			"id": 38672187,
			"handling_fee": "0.0",
			"email": "ben@goearnit.com"
		  }
		]
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
			[
			  {
				"storefront_name": "Stars - Cotton Shirt - Gray",
				"store_id": 2104824,
				"images": [
				  {
					"position": 1,
					"filesize": 64518,
					"url": "https://s3.amazonaws.com/tuo-p-public/product_images/48048a5ec87ca93ba062377346bd82d81d617ae4.jpg?1716908268"
				  },
				  ...
				],
				"description": "",
				"upc": null,
				"active": true,
				"type": "Shirt",
				"colors": {
				  "No color": [
					"NA",
					"NA"
				  ]
				},
				"catalog_id": 139675,
				"sizes": [
				  {
					"category_name": "Standard Youth",
					"code": "YS",
					"price": "0.01"
				  },
				  {
					"category_name": "Standard Youth",
					"code": "YM",
					"price": "0.01"
				  },
				  {
					"category_name": "Standard Youth",
					"code": "YL",
					"price": "0.01"
				  },
				  ...
				],
				"name": "Shirts SS - Printed (Cotton)",
				"id": 2470893,
				"sku": "1000015",
				"brand": "GO EARN IT"
			  },
			  {
				"storefront_name": "Stars - Cotton Shirt - Navy",
				"store_id": 2104824,
				"images": [
					...
				],
				"description": "",
				"upc": null,
				"active": true,
				"type": "Shirt",
				"colors": {
				  "No color": [
					"NA",
					"NA"
				  ]
				},
				"catalog_id": 139675,
				"sizes": [
					...
				],
				"name": "Shirts SS - Printed (Cotton)",
				"id": 2470894,
				"sku": "1000015",
				"brand": "GO EARN IT"
			  },
			  {
				"storefront_name": "Stars - Cotton Shirt - Red",
				"store_id": 2104824,
				"images": [
					...
				],
				"description": "",
				"upc": null,
				"active": true,
				"type": "Shirt",
				"colors": {
				  "No color": [
					"NA",
					"NA"
				  ]
				},
				"catalog_id": 139675,
				"sizes": [
					...
				],
				"name": "Shirts SS - Printed (Cotton)",
				"id": 2470896,
				"sku": "1000015",
				"brand": "GO EARN IT"
			  }
			]
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
