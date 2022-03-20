package dcraft.interchange.taxjar;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TaxJarUtil {
    static public final String TAXJAR_LIVE_ENDPOINT = "https://api.taxjar.com/v2/";
    static public final String TAXJAR_TEST_ENDPOINT = "https://api.sandbox.taxjar.com/v2/";


    /*
    Incoming:
    {
       Shipping: nnn,
       Items: [
            {
                    ProductId: nnn,
                    Name: nnn,
                    Quantity: nnn,
                    Amount: nnn,
                    Discount: nnn,
                    TaxCode: nnn
            }
       ],
       ShipTo: {
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       },
       ShipFrom: {
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       }
    }

    Outgoing (trimmed):
    {
      "tax": {
        "order_total_amount": 74,
        "shipping": 15,
        "taxable_amount": 74,
        "amount_to_collect": 4.07,
        "rate": 0.055,
        "freight_taxable": true,
        "tax_source": "destination",
        "breakdown": {
          "taxable_amount": 74,
          "tax_collectable": 4.07,
          "combined_tax_rate": 0.055
          "shipping": {
            "taxable_amount": 15,
            "tax_collectable": 0.83,
            "combined_tax_rate": 0.055
          },
          "line_items": [
            {
              "id": "1",
              "taxable_amount": 59,
              "tax_collectable": 3.25,
              "combined_tax_rate": 0.055
            }
          ]
        }
      }
    }
     */

    public static RecordStruct lookupTaxSync(RecordStruct tx, String alt) {
        try {
            OperationContext.getOrThrow().touch();

            RecordStruct body = buildTaxBody(tx);

            if (body == null)
                return null;

            HttpRequest.Builder builder = TaxJarUtil.buildRequest("taxes", alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null)
                return result.getFieldAsRecord("tax");
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    /*
    Incoming:
    {
       OrderId: nnn,
       OrderDate: yyyy-mm-dd,
       Amount: nnn,
       Shipping: nnn,
       Taxes: nnn,
       Items: [
            {
                    ProductId: nnn,
                    Name: nnn,
                    Quantity: nnn,
                    Amount: nnn,
                    Taxes: nnn,
                    Discount: nnn,
                    TaxCode: nnn
            }
       ],
       ShipTo: {
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       },
       ShipFrom: {
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       }
    }
     */

    public static RecordStruct createTxSync(RecordStruct tx, String alt) {
        try {
            OperationContext.getOrThrow().touch();

            RecordStruct body = buildTaxBody(tx);

            if (body == null)
                return null;

            HttpRequest.Builder builder = TaxJarUtil.buildRequest("transactions/orders", alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null)
                return result.getFieldAsRecord("order");
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    /*
    Incoming:
    {
       RefundId: nnn,
       RefundDate: yyyy-mm-dd,
       OrderId: nnn,
       Amount: nnn,
       Shipping: nnn,
       Taxes: nnn,
       Items: [
            {
                    ProductId: nnn,
                    Name: nnn,
                    Quantity: nnn,
                    Amount: nnn,
                    Taxes: nnn,
                    Discount: nnn,
                    TaxCode: nnn
            }
       ],
       ShipTo: {
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       },
       ShipFrom: {
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       }
    }
     */

    public static RecordStruct refundTxSync(RecordStruct tx, String alt) {
        try {
            OperationContext.getOrThrow().touch();

            RecordStruct body = buildTaxBody(tx);

            if (body == null)
                return null;

            HttpRequest.Builder builder = TaxJarUtil.buildRequest("transactions/refunds", alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null)
                return result.getFieldAsRecord("refund");
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    static protected RecordStruct buildTaxBody(RecordStruct tx) {
        ListStruct items = ListStruct.list();

        RecordStruct request = RecordStruct.record()
                .with("shipping", tx.select("Shipping"))
                .withConditional("amount", tx.select("Amount"))
                .withConditional("sales_tax", tx.select("Taxes"))
                .with("to_street", tx.select("ShipTo.Address"))
                .with("to_city", tx.select("ShipTo.City"))
                .with("to_state", tx.select("ShipTo.State"))
                .with("to_zip", tx.select("ShipTo.Zip"))
                .with("to_country", tx.selectAsString("ShipTo.Country", "US"))
                .with("from_street", tx.select("ShipFrom.Address"))
                .with("from_city", tx.select("ShipFrom.City"))
                .with("from_state", tx.select("ShipFrom.State"))
                .with("from_zip", tx.select("ShipFrom.Zip"))
                .with("from_country", tx.selectAsString("ShipFrom.Country", "US"));

        if (tx.isNotFieldEmpty("RefundId")) {
            request
                    .with("transaction_id", tx.select("RefundId"))
                    .with("transaction_reference_id", tx.select("OrderId"))
                    .with("transaction_date", tx.select("RefundDate"));
        }
        else if (tx.isNotFieldEmpty("OrderId")) {
            request
                    .with("transaction_id", tx.select("OrderId"))
                    .with("transaction_date", tx.select("OrderDate"));
        }

        if (tx.isNotFieldEmpty("Items")) {
            ListStruct itemsin = tx.getFieldAsList("Items");

            for (int i = 0; i < itemsin.size(); i++) {
                RecordStruct itemin = itemsin.getItemAsRecord(i);

                RecordStruct itemout = RecordStruct.record()
                        .withConditional("id", itemin.select("Id"))
                        .with("quantity", itemin.select("Quantity"))
                        .with("unit_price", itemin.select("Amount"))
                        .with("product_tax_code", itemin.select("TaxCode"))
                        .withConditional("discount", itemin.select("Discount"))
                        .withConditional("sales_tax", itemin.select("Taxes"));

                items.with(itemout);
            }

            request.with("line_items", items);
        }

        return request;
    }


    static public HttpRequest.Builder buildRequest(String method, String alt, CompositeStruct body) {
        XElement auth = ApplicationHub.getCatalogSettings("CMS-TaxJar", alt);

        if (auth == null) {
            Logger.error("Missing store TaxJar settings.");
            return null;
        }

        String token = auth.getAttribute("TokenPlain");
        boolean istest = ! auth.getAttributeAsBooleanOrFalse("Live");

        return buildRequest(token, istest, method, body);
    }

    static public HttpRequest.Builder buildRequest(String token, boolean istest, String method, CompositeStruct body) {
        String endpoint = istest ? TaxJarUtil.TAXJAR_TEST_ENDPOINT : TaxJarUtil.TAXJAR_LIVE_ENDPOINT;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + method))
                .header("User-Agent", "dcServer/2021.2 (Language=Java/11)")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token);

        System.out.println("tax jar method: " + method);
        System.out.println("tax jar request: " + body);

        if (body != null)
            builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        else
            builder.GET();

        return builder;
    }

    /*
    400	Bad Request – Your request format is bad.
    401	Unauthorized – Your API key is wrong.
    403	Forbidden – The resource requested is not authorized for use.
    404	Not Found – The specified resource could not be found.
    405	Method Not Allowed – You tried to access a resource with an invalid method.
    406	Not Acceptable – Your request is not acceptable.
    410	Gone – The resource requested has been removed from our servers.
    422	Unprocessable Entity – Your request could not be processed.
    429	Too Many Requests – You’re requesting too many resources! Slow down!
    500	Internal Server Error – We had a problem with our server. Try again later.
    503	Service Unavailable – We’re temporarily offline for maintenance. Try again later.
     */

    static protected RecordStruct processResponse(HttpResponse<String> response) {
        int responseCode = response.statusCode();
        RecordStruct respBody = null;

        if ((responseCode < 200) || (responseCode > 299)) {
            Logger.error("Error processing request: TaxJar sent an error response code: " + responseCode);

            switch (responseCode) {
                case 400:
                    Logger.error("Bad Request – Your request format is bad.");
                    break;
                case 401:
                    Logger.error("Unauthorized – Your API key is wrong.");
                    break;
                case 403:
                    Logger.error("Forbidden – The resource requested is not authorized for use.");
                    break;
                case 404:
                    Logger.error("Not Found – The specified resource could not be found.");
                    break;
                case 405:
                    Logger.error("Method Not Allowed – You tried to access a resource with an invalid method.");
                    break;
                case 406:
                    Logger.error("Not Acceptable – Your request is not acceptable.");
                    break;
                case 410:
                    Logger.error("Gone – The resource requested has been removed from our servers.");
                    break;
                case 422:
                    Logger.error("Unprocessable Entity – Your request could not be processed.");
                    break;
                case 429:
                    Logger.error("Too Many Requests – You’re requesting too many resources! Slow down!");
                    break;
                case 500:
                    Logger.error("Internal Server Error – We had a problem with our server. Try again later.");
                    break;
                case 503:
                    Logger.error("Service Unavailable – We’re temporarily offline for maintenance. Try again later.");
                    break;
            }

            String respraw = response.body();

            if (StringUtil.isNotEmpty(respraw)) {
                RecordStruct resp = Struct.objectToRecord(CompositeParser.parseJson(respraw));

                System.out.println("got error: " + resp);

                if (resp != null) {
                    Logger.error("TaxJar error detail: " + resp.getFieldAsString("detail"));
                }
            }
        }
        else {
            String respraw = response.body();

            if (StringUtil.isNotEmpty(respraw)) {
                CompositeStruct resp = CompositeParser.parseJson(respraw);

                System.out.println("taxjar resp: " + resp);

                if (resp == null) {
                    Logger.error("Error processing request: TaxJar sent an incomplete response: " + responseCode);
                } else {
                    System.out.println("TaxJar Resp: " + responseCode + "\n" + resp.toPrettyString());

                    respBody = Struct.objectToRecord(resp);
                }
            }
        }

        return respBody;
    }

}
