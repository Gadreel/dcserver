package dcraft.interchange.authorize;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.mailchimp.MailChimpUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.JsonBuilder;
import dcraft.struct.builder.JsonMemoryBuilder;
import dcraft.util.StringUtil;
import dcraft.util.cb.TimeoutPlan;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class AuthUtil {

    /*
    payment - { Number: [card number], Expiration: [YYYY-MM], Code: [card code] }
    or payment - { Token1: [description], Token2: [value] }
    items - [
        { ProductId: [id], Name: [name], Description: [desc], Quantity: n, Price: n }
    ]
    bill - { FirstName: [name], LastName: [name], Address: [address], City: [city], State: [state], Zip: [zip], Country: [country] }
    ship - { FirstName: [name], LastName: [name], Address: [address], City: [city], State: [state], Zip: [zip], Country: [country] }

     */

    public static void authCaptureTransaction(String id, BigDecimal taxes, BigDecimal shipping, BigDecimal amount, String email, String ipaddress, RecordStruct paymentinfo,
                                              RecordStruct bill, RecordStruct ship, ListStruct items, String alt, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "createTransactionRequest");

            //body.field(	"refId", id);
            body.field("transactionRequest");

            body.startRecord();
            body.field("transactionType", "authCaptureTransaction");
            body.field("amount", amount);

            if (paymentinfo != null) {
                body.field("payment");

                body.startRecord();

                if (paymentinfo.isNotFieldEmpty("Token1")) {
                    body.field("opaqueData");

                    body.startRecord();
                    body.field("dataDescriptor", paymentinfo.selectAsString("Token1"));
                    body.field("dataValue", paymentinfo.selectAsString("Token2"));
                    body.endRecord();
                }
                else {
                    body.field("creditCard");

                    body.startRecord();
                    body.field("cardNumber", paymentinfo.selectAsString("Number"));
                    body.field("expirationDate", paymentinfo.selectAsString("Expiration"));
                    body.field("cardCode", paymentinfo.selectAsString("Code"));
                    body.endRecord();
                }

                body.endRecord();
            }

            body.field("order");

            body.startRecord();
            body.field("invoiceNumber", id);
            body.endRecord();

            body.field("lineItems");

            body.startRecord();
            body.field("lineItem");
            body.startList();

            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    RecordStruct item = items.getItemAsRecord(i);

                    //         { ProductId: [id], Name: [name], Description: [desc], Quantity: n, Price: n }

                    body.startRecord();
                    body.field("itemId", item.selectAsString("ProductId"));
                    body.field("name", item.selectAsString("Name"));

                    if (item.isNotFieldEmpty("Description"))
                        body.field("description", item.selectAsString("Description"));

                    body.field("quantity", item.selectAsString("Quantity"));
                    body.field("unitPrice", item.selectAsString("Price"));
                    body.endRecord();
                }
            }

            body.endList();
            body.endRecord();

            body.field("tax");

            body.startRecord();
            body.field("amount", taxes);
            body.endRecord();

            body.field("shipping");

            body.startRecord();
            body.field("amount", shipping);
            body.endRecord();

            body.field("customer");

            body.startRecord();
            body.field("type", "individual");
            body.field("email", email);
            body.endRecord();

            if (bill != null) {
                body.field("billTo");

                body.startRecord();
                body.field("firstName", bill.selectAsString("FirstName"));
                body.field("lastName", bill.selectAsString("LastName"));
                body.field("address", bill.selectAsString("Address"));
                body.field("city", bill.selectAsString("City"));
                body.field("state", bill.selectAsString("State"));
                body.field("zip", bill.selectAsString("Zip"));
                body.field("country", bill.selectAsString("Country"));
                body.endRecord();
            }

            if (ship != null) {
                body.field("shipTo");

                body.startRecord();
                body.field("firstName", ship.selectAsString("FirstName"));
                body.field("lastName", ship.selectAsString("LastName"));
                body.field("address", ship.selectAsString("Address"));
                body.field("city", ship.selectAsString("City"));
                body.field("state", ship.selectAsString("State"));
                body.field("zip", ship.selectAsString("Zip"));
                body.field("country", ship.selectAsString("Country"));
                body.endRecord();
            }

            body.field("customerIP", ipaddress);
            body.field("transactionSettings");

            body.startRecord();
            body.field("setting");

            body.startRecord();
            body.field("settingName", "emailCustomer");
            body.field("settingValue", "false");
            body.endRecord();

            body.endRecord();

            body.endRecord();

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(new AuthHttpResponse() {
                        @Override
                        public void callback(RecordStruct result) throws OperatingContextException {
                            if (result == null) {
                                callback.returnEmpty();
                            }
                            else {
                                RecordStruct resp = result.getFieldAsRecord("transactionResponse");

                                long code = resp.getFieldAsInteger("responseCode", 0);

                                if (code == 1) {
                                    callback.returnValue(resp);
                                }
                                else {
                                    Logger.error("Card declined");
                                    callback.returnEmpty();
                                }
                            }
                        }
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
            callback.returnEmpty();
        }
    }

    public static void getTransactionDetail(String txid, String alt, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "getTransactionDetailsRequest");

            body.field(	"transId", txid);

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(new AuthHttpResponse() {
                        @Override
                        public void callback(RecordStruct result) throws OperatingContextException {
                            if (result == null)
                                callback.returnEmpty();
                            else
                                callback.returnValue(result.getFieldAsRecord("transaction"));
                        }
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
            callback.returnEmpty();
        }
    }

    public static void voidTransaction(String refid, String txid, String alt, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "createTransactionRequest");

            body.field(	"refId", refid);
            body.field("transactionRequest");

            body.startRecord();
            body.field("transactionType", "voidTransaction");
            body.field("refTransId", txid);
            body.endRecord();

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(new AuthHttpResponse() {
                        @Override
                        public void callback(RecordStruct result) throws OperatingContextException {
                            if (result == null)
                                callback.returnEmpty();
                            else
                                callback.returnValue(result.getFieldAsRecord("transactionResponse"));
                        }
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
            callback.returnEmpty();
        }
    }

    public static void refundTransaction(String orderid, String txid, BigDecimal amount, RecordStruct txdetail, String alt, OperationOutcomeRecord callback) throws OperatingContextException {
        OperationContext.getOrThrow().touch();

        OperationOutcomeRecord detailcallback = new OperationOutcomeRecord() {
            @Override
            public void callback(RecordStruct result) throws OperatingContextException {

                try {
                JsonBuilder body = startRequestBody(alt, "createTransactionRequest");

                body.field("transactionRequest");

                body.startRecord();
                body.field("transactionType", "refundTransaction");
                body.field("amount", amount);

                RecordStruct payment = txdetail.getFieldAsRecord("payment");

                if (payment != null) {
                    if (payment.isNotFieldEmpty("creditCard")) {
                        body.field("payment");

                        body.startRecord();
                        body.field("creditCard");

                        body.startRecord();
                        body.field("cardNumber", payment.selectAsString("creditCard.cardNumber"));
                        body.field("expirationDate", payment.selectAsString("creditCard.expirationDate"));
                        body.endRecord();

                        body.endRecord();
                    }
                }

                body.field("refTransId", txid);

                body.field("order");
                body.startRecord();
                body.field("invoiceNumber", orderid);
                body.endRecord();

                RecordStruct billTo = txdetail.getFieldAsRecord("billTo");

                if (billTo != null) {
                    body.field("billTo");

                    body.startRecord();
                    body.field("firstName", billTo.selectAsString("firstName"));
                    body.field("lastName", billTo.selectAsString("lastName"));
                    body.field("address", billTo.selectAsString("address"));
                    body.field("city", billTo.selectAsString("city"));
                    body.field("state", billTo.selectAsString("state"));
                    body.field("zip", billTo.selectAsString("zip"));
                    body.field("country", billTo.selectAsString("country"));
                    body.endRecord();
                }

                body.endRecord();

                HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

                // Send post request
                HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenAcceptAsync(new AuthHttpResponse() {
                            @Override
                            public void callback(RecordStruct result) throws OperatingContextException {
                                if (result == null)
                                    callback.returnEmpty();
                                else
                                    callback.returnValue(result.getFieldAsRecord("transactionResponse"));
                            }
                        });
                }
                catch (Exception x) {
                    Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
                    callback.returnEmpty();
                }
            }
        };

        if (txdetail == null) {
            getTransactionDetail(txid, alt, detailcallback);
        }
        else {
            detailcallback.returnValue(txdetail);
        }
    }

    public static void cancelFullTransaction(String refid, String txid, String alt, OperationOutcome<BigDecimal> callback) throws OperatingContextException {
        getTransactionDetail(txid, alt, new OperationOutcomeRecord() {
            @Override
            public void callback(RecordStruct result) throws OperatingContextException {
                if (result == null) {
                    callback.returnEmpty();
                    return;
                }

                if ("capturedPendingSettlement".equals(result.selectAsString("transactionStatus"))) {
                    voidTransaction(refid, txid, alt, new OperationOutcomeRecord() {
                        @Override
                        public void callback(RecordStruct result) throws OperatingContextException {
                            callback.returnValue(result.getFieldAsDecimal("authAmount"));
                        }
                    });
                }
                else {
                    refundTransaction(refid, txid, result.getFieldAsDecimal("settleAmount"), result, alt, new OperationOutcomeRecord() {
                        @Override
                        public void callback(RecordStruct result2) throws OperatingContextException {
                            callback.returnValue(result.getFieldAsDecimal("settleAmount"));
                        }
                    });
                }
            }
        });
    }

    public static void cancelPartialTransaction(String refid, String txid, BigDecimal amt, String alt, OperationOutcomeRecord callback) throws OperatingContextException {
        getTransactionDetail(txid, alt, new OperationOutcomeRecord() {
            @Override
            public void callback(RecordStruct result) throws OperatingContextException {
                if (result == null) {
                    callback.returnEmpty();
                    return;
                }

                if ("capturedPendingSettlement".equals(result.selectAsString("transactionStatus"))) {
                    if (amt == null) {
                        voidTransaction(refid, txid, alt, new OperationOutcomeRecord() {
                            @Override
                            public void callback(RecordStruct result) throws OperatingContextException {
                                result.with("_dcAmount", result.getFieldAsDecimal("settleAmount"));
                                callback.returnValue(result);
                            }
                        });
                    }
                    else {
                        Logger.error("Unable to refund until charge is cleared.");
                        callback.returnEmpty();
                    }
                }
                else {
                    BigDecimal amt2 = (amt != null) ? amt : result.getFieldAsDecimal("settleAmount");

                    refundTransaction(refid, txid, amt2, result, alt, new OperationOutcomeRecord() {
                        @Override
                        public void callback(RecordStruct result2) throws OperatingContextException {
                            result.with("_dcAmount", amt2);
                            callback.returnValue(result);
                        }
                    });
                }
            }
        });
    }

    static public JsonBuilder startRequestBody(String alt, String method) {
        XElement auth = ApplicationHub.getCatalogSettings("CMS-Authorize", alt);

        if (auth == null) {
            Logger.error("Missing store Authorize settings.");
            return null;
        }

        String authid = auth.getAttribute("LoginId");
        String key = auth.getAttribute("TransactionKey");

        String authkey = ApplicationHub.getClock().getObfuscator().decryptHexToString(key);

        return startRequestBody(authid, authkey, method);
    }

    static public JsonBuilder startRequestBody(String authid, String key, String method) {
        try {
            JsonMemoryBuilder builder = new JsonMemoryBuilder();

            builder.startRecord();
            builder.field(method);

            builder.startRecord();
            builder.field("merchantAuthentication");

            builder.startRecord();
            builder.field("name", authid);
            builder.field("transactionKey", key);
            builder.endRecord();

            // still need two endRecords, but first let the body get filled in

            return builder;
        }
        catch (BuilderStateException x) {
            Logger.error("Cannot form authorize request body: " + x);
            return null;
        }
    }

    static public HttpRequest.Builder buildRequest(String alt, JsonBuilder body) {
        XElement auth = ApplicationHub.getCatalogSettings("CMS-Authorize", alt);

        if (auth == null) {
            Logger.error("Missing store Authorize settings.");
            return null;
        }

        boolean istest = ! auth.getAttributeAsBooleanOrFalse("Live");

        return buildRequest(istest, body);
    }

    static public HttpRequest.Builder buildRequest(boolean istest, JsonBuilder body) {
        try {
            String endpoint = istest ? AuthUtilXml.AUTH_TEST_ENDPOINT : AuthUtilXml.AUTH_LIVE_ENDPOINT;

            body.endRecord();   // operation
            body.endRecord();   // request

            String json = body.toMemory().toString();

            System.out.println("in: " + json);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

            return builder;
        }
        catch (BuilderStateException x) {
            Logger.error("Cannot form authorize request body: " + x);
            return null;
        }
    }

    static abstract public class AuthHttpResponse extends OperationOutcomeRecord implements Consumer<HttpResponse<String>> {
        public AuthHttpResponse() throws OperatingContextException {
            super();
        }

        public AuthHttpResponse(TimeoutPlan plan) throws OperatingContextException {
            super(plan);
        }

        @Override
        public void accept(HttpResponse<String> response) {
            this.useContext();

            int responseCode = response.statusCode();
            RecordStruct respBody = null;

            if ((responseCode < 200) || (responseCode > 299)) {
                Logger.error("Error processing request: Auth sent an error response code: " + responseCode);
            }
            else {
                String respraw = response.body();

                if (StringUtil.isNotEmpty(respraw)) {
                    respraw = respraw.substring(1);

                    CompositeStruct resp = CompositeParser.parseJson(respraw);

                    if (resp == null) {
                        Logger.error("Error processing request: Auth sent an incomplete response: " + responseCode);
                    } else {
                        System.out.println("Auth Resp: " + responseCode + "\n" + resp.toPrettyString());

                        respBody = (RecordStruct) resp;

                        if (!"Ok".equals(respBody.selectAsString("messages.resultCode"))) {
                            Logger.error("Error processing auth request: " + responseCode);
                        }
                    }
                }
            }

            this.returnValue(respBody);
        }
    }

}
