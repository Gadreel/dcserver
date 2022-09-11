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
import dcraft.util.TimeUtil;
import dcraft.util.cb.TimeoutPlan;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.function.Consumer;

public class AuthUtil {
    static public final DateTimeFormatter incomingFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");


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

            RecordStruct tx = RecordStruct.record()
                    .with("InvoiceId", id)
                    .with("Taxes", taxes)
                    .with("Shipping", shipping)
                    .with("Amount", amount)
                    .with("Email", email)
                    .with("IPAddress", ipaddress)
                    .with("Payment", paymentinfo)
                    .with("BillTo", bill)
                    .with("ShipTo", ship)
                    .with("Items", items);

            JsonBuilder body = authCaptureBody(tx, alt);

            if (body == null) {
                callback.returnEmpty();
                return;
            }

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

    /* NOTE auth does care about the order of the fields, so JSON is not ideal - see AuthUtilXml instead
    // tx = auth payment format
    public static void authCaptureTransaction(RecordStruct txbody, String refid, String alt, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "createTransactionRequest");

            if (StringUtil.isNotEmpty(refid))
                body.field("refId", refid);

            body.field("transactionRequest");
            body.value(txbody);

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

     */

    /*
    Incoming:
    {
       Amount: nnn,
       Shipping: nnn,
       Taxes: nnn,
       Payment: {
        Token1: nnn,
        Token2: nnn

        OR

        Number: nnn,
        Expiration: yyyy-mm,
        Code: nnn
       },
       Items: [
            {
                    ProductId: nnn,
                    Name: nnn,
                    Description: nnn,
                    Quantity: nnn,
                    Price: nnn
            }
       ],
       Email: nnnn,
       IPAddress: nnnn,
       InvoiceId: nnnn,
       BillTo: {
        FirstName: nnn,
        LastName: nnn,
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       },
       ShipTo: {
        FirstName: nnn,
        LastName: nnn,
        Address: nnn,
        City: nnn,
        State: nnn,
        Zip: nnnnn,
        Country: nnn
       }
    }
     */

    public static RecordStruct authCaptureTransactionSync(RecordStruct tx, String alt) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = authCaptureBody(tx, alt);

            if (body == null)
                return null;

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null) {
                RecordStruct resp = result.getFieldAsRecord("transactionResponse");

                long code = resp.getFieldAsInteger("responseCode", 0);

                if (code == 1) {
                    return resp;
                }
            }

            Logger.error("Card declined");
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    static protected JsonBuilder authCaptureBody(RecordStruct tx, String alt) {
        try {
            JsonBuilder body = startRequestBody(alt, "createTransactionRequest");

            if (tx.isNotFieldEmpty("RefId"))
                body.field(	"refId", tx.getFieldAsString("RefId"));

            body.field("transactionRequest");

            body.startRecord();
            body.field("transactionType", "authCaptureTransaction");
            body.field("amount", tx.getFieldAsDecimal("Amount"));

            RecordStruct paymentinfo = tx.getFieldAsRecord("Payment");

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

            if (tx.isNotFieldEmpty("InvoiceId")) {
                body.field("order");

                body.startRecord();
                body.field("invoiceNumber", tx.getFieldAsString("InvoiceId"));
                body.endRecord();
            }

            if (tx.isNotFieldEmpty("Items")) {
                body.field("lineItems");

                body.startRecord();
                body.field("lineItem");
                body.startList();

                ListStruct items = tx.getFieldAsList("Items");

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
            }

            if (tx.isNotFieldEmpty("Taxes")) {
                body.field("tax");

                body.startRecord();
                body.field("amount", tx.getFieldAsDecimal("Taxes"));
                body.endRecord();
            }

            if (tx.isNotFieldEmpty("Shipping")) {
                body.field("shipping");

                body.startRecord();
                body.field("amount", tx.getFieldAsDecimal("Shipping"));
                body.endRecord();
            }

            body.field("customer");

            body.startRecord();
            body.field("type", tx.getFieldAsString("CustomerType","individual"));

            if (tx.isNotFieldEmpty("Email"))
                body.field("email", tx.getFieldAsString("Email"));

            body.endRecord();

            RecordStruct bill = tx.getFieldAsRecord("BillTo");

            if (bill != null) {
                body.field("billTo");

                body.startRecord();
                body.field("firstName", bill.selectAsString("FirstName"));
                body.field("lastName", bill.selectAsString("LastName"));

                if (bill.isNotFieldEmpty("Company"))
                    body.field("company", bill.selectAsString("Company"));

                body.field("address", bill.selectAsString("Address"));
                body.field("city", bill.selectAsString("City"));
                body.field("state", bill.selectAsString("State"));
                body.field("zip", bill.selectAsString("Zip"));
                body.field("country", bill.selectAsString("Country", "USA"));

                if (bill.isNotFieldEmpty("Phone"))
                    body.field("phoneNumber", bill.selectAsString("Phone"));

                body.endRecord();
            }

            RecordStruct ship = tx.getFieldAsRecord("ShipTo");

            if (ship != null) {
                body.field("shipTo");

                body.startRecord();
                body.field("firstName", ship.selectAsString("FirstName"));
                body.field("lastName", ship.selectAsString("LastName"));

                if (bill.isNotFieldEmpty("Company"))
                    body.field("company", bill.selectAsString("Company"));

                body.field("address", ship.selectAsString("Address"));
                body.field("city", ship.selectAsString("City"));
                body.field("state", ship.selectAsString("State"));
                body.field("zip", ship.selectAsString("Zip"));
                body.field("country", ship.selectAsString("Country"));
                body.endRecord();
            }

            if (tx.isNotFieldEmpty("IPAddress"))
                body.field("customerIP", tx.getFieldAsString("IPAddress"));

            body.field("retail");

            body.startRecord();
            body.field("marketType", 0);
            body.field("deviceType", 8);
            body.endRecord();

            body.field("transactionSettings");

            body.startRecord();
            body.field("setting");

            body.startRecord();
            body.field("settingName", "emailCustomer");
            body.field("settingValue", tx.getFieldAsBooleanOrFalse("EmailNotice") ? "true" : "false");
            body.endRecord();

            body.endRecord();

            body.endRecord();

            return body;
        }
        catch (BuilderStateException x) {
            Logger.error("Bad auth capture body. Error: " + x);
        }

        return null;
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

    public static RecordStruct getTransactionDetailSync(String txid, String alt) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "getTransactionDetailsRequest");

            body.field(	"transId", txid);

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null) {
                return result.getFieldAsRecord("transaction");
            }
        }
        catch (Exception x) {
            Logger.error("Error collecting details: Unable to connect to authorize. Error: " + x);
        }

        return null;
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
                            if (result == null) {
                                Logger.error("Auth Unable to void tx: " + txid + " no valid response");
                                callback.returnEmpty();
                            }
                            else {
                                callback.returnValue(result.getFieldAsRecord("transactionResponse"));
                            }
                        }
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
            callback.returnEmpty();
        }
    }

    public static RecordStruct voidTransactionSync(String refid, String txid, String alt) {
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

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null) {
                return result.getFieldAsRecord("transactionResponse");
            }
            else {
                Logger.error("Auth Unable to void tx: " + txid + " no valid response");
            }
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    public static void refundTransaction(String orderid, String txid, BigDecimal amount, RecordStruct txdetail, String alt, OperationOutcomeRecord callback) throws OperatingContextException {
        OperationContext.getOrThrow().touch();

        OperationOutcomeRecord detailcallback = new OperationOutcomeRecord() {
            @Override
            public void callback(RecordStruct result) throws OperatingContextException {

                try {
                    JsonBuilder body = refundBody(orderid, txid, amount, txdetail, alt);

                    HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

                    // Send post request
                    HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenAcceptAsync(new AuthHttpResponse() {
                            @Override
                            public void callback(RecordStruct result) throws OperatingContextException {
                                if (result == null) {
                                    Logger.error("Auth Unable to refund tx: " + txid + " no valid response");
                                    callback.returnEmpty();
                                }
                                else {
                                    callback.returnValue(result.getFieldAsRecord("transactionResponse"));
                                }
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

    public static RecordStruct refundTransactionSync(String orderid, String txid, BigDecimal amount, RecordStruct txdetail, String alt) throws OperatingContextException {
        OperationContext.getOrThrow().touch();

        if (txdetail == null)
            txdetail = getTransactionDetailSync(txid, alt);

        try {
            JsonBuilder body = refundBody(orderid, txid, amount, txdetail, alt);

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null) {
                return result.getFieldAsRecord("transactionResponse");
            }
            else {
                Logger.error("Auth Unable to refund tx: " + txid + " no valid response");
            }
        }
        catch (Exception x) {
            Logger.error("Error processing subscription: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    static protected JsonBuilder refundBody(String orderid, String txid, BigDecimal amount, RecordStruct txdetail, String alt) {
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

            return body;
        }
        catch (BuilderStateException x) {
            Logger.error("Bad auth capture body. Error: " + x);
        }

        return null;
    }

    public static void cancelFullTransaction(String refid, String txid, String alt, OperationOutcome<BigDecimal> callback) throws OperatingContextException {
        getTransactionDetail(txid, alt, new OperationOutcomeRecord() {
            @Override
            public void callback(RecordStruct result) throws OperatingContextException {
                if (result == null) {
                    callback.returnEmpty();
                    return;
                }

                //System.out.println("1: " + result.getFieldAsDecimal("settleAmount").toPlainString());

                if ("capturedPendingSettlement".equals(result.selectAsString("transactionStatus"))) {
                    voidTransaction(refid, txid, alt, new OperationOutcomeRecord() {
                        @Override
                        public void callback(RecordStruct result2) throws OperatingContextException {
                            //System.out.println("2: " + result.getFieldAsDecimal("settleAmount").toPlainString());

                            //System.out.println("3: " + result2.getFieldAsDecimal("authAmount").toPlainString());

                            if (result != null)
                                callback.returnValue(result.getFieldAsDecimal("settleAmount"));
                            else
                                callback.returnEmpty();
                        }
                    });
                }
                else {
                    refundTransaction(refid, txid, result.getFieldAsDecimal("settleAmount"), result, alt, new OperationOutcomeRecord() {
                        @Override
                        public void callback(RecordStruct result2) throws OperatingContextException {
                            if (result != null)
                                callback.returnValue(result.getFieldAsDecimal("settleAmount"));
                            else
                                callback.returnEmpty();
                        }
                    });
                }
            }
        });
    }

    public static BigDecimal cancelFullTransactionSync(String refid, String txid, String alt) throws OperatingContextException {
        RecordStruct txdetail = getTransactionDetailSync(txid, alt);

        if (txdetail == null) {
            Logger.error("Auth Transaction not found: " + txid + " - unable to cancel.");
            return null;
        }

        //System.out.println("1: " + txdetail.getFieldAsDecimal("settleAmount").toPlainString());

        if ("capturedPendingSettlement".equals(txdetail.selectAsString("transactionStatus"))) {
            RecordStruct result = voidTransactionSync(refid, txid, alt);

            if (result != null)
                return result.getFieldAsDecimal("settleAmount");
        }
        else {
            RecordStruct result = refundTransactionSync(refid, txid, txdetail.getFieldAsDecimal("settleAmount"), txdetail, alt);

            if (result != null)
                return result.getFieldAsDecimal("settleAmount");
        }

        Logger.error("Auth Unable to refund tx: " + txid + " no valid response");

        return null;
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
                                if (result == null) {
                                    callback.returnEmpty();
                                }
                                else {
                                    result.with("_dcAmount", result.getFieldAsDecimal("settleAmount"));
                                    callback.returnValue(result);
                                }
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
                            if (result == null) {
                                callback.returnEmpty();
                            }
                            else {
                                result.with("_dcAmount", amt2);
                                callback.returnValue(result);
                            }
                        }
                    });
                }
            }
        });
    }

    public static RecordStruct cancelPartialTransactionSync(String refid, String txid, BigDecimal amt, String alt) throws OperatingContextException {
        RecordStruct txdetail = getTransactionDetailSync(txid, alt);

        if (txdetail == null) {
            Logger.error("Auth Transaction not found: " + txid + " - unable to cancel.");
            return null;
        }

        if ("capturedPendingSettlement".equals(txdetail.selectAsString("transactionStatus"))) {
            if (amt == null) {
                RecordStruct result = voidTransactionSync(refid, txid, alt);

                if (result != null) {
                    result.with("_dcAmount", result.getFieldAsDecimal("settleAmount"));
                    return result;
                }
            }
            else {
                Logger.error("Unable to refund until charge is cleared.");
                return null;
            }
        }
        else {
            BigDecimal amt2 = (amt != null) ? amt : txdetail.getFieldAsDecimal("settleAmount");

            RecordStruct result = refundTransactionSync(refid, txid, amt2, txdetail, alt);

            if (result != null) {
                result.with("_dcAmount", amt2);
                return result;
            }
        }

        return null;
    }

    public static void getSettledBatches(ZonedDateTime first, ZonedDateTime last, String alt, OperationOutcomeList callback) {
        getSettledBatches(incomingFormat.format(first.withZoneSameInstant(ZoneOffset.UTC)), incomingFormat.format(last.withZoneSameInstant(ZoneOffset.UTC)), alt, callback);
    }

    // format - yyyy-MM-dd'T'HH:mm:ssZ
    public static void getSettledBatches(String first, String last, String alt, OperationOutcomeList callback) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "getSettledBatchListRequest");

            body.field(	"firstSettlementDate", first);
            body.field(	"lastSettlementDate", last);

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(new AuthHttpResponse() {
                        @Override
                        public void callback(RecordStruct result) throws OperatingContextException {
                            if (result == null)
                                callback.returnEmpty();
                            else
                                callback.returnValue(result.getFieldAsList("batchList"));
                        }
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing getSettledBatchListRequest: Unable to connect to authorize. Error: " + x);
            callback.returnEmpty();
        }
    }

    // format - yyyy-MM-dd'T'HH:mm:ssZ
    public static ListStruct getSettledBatchesSync(String first, String last, String alt) {
        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "getSettledBatchListRequest");

            body.field(	"firstSettlementDate", first);
            body.field(	"lastSettlementDate", last);

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null) {
                return result.getFieldAsList("batchList");
            }
        }
        catch (Exception x) {
            Logger.error("Error collecting details: Unable to connect to authorize. Error: " + x);
        }

        return null;
    }

    // TODO need callback (non-blocking) option for getTransactionListSync

    public static ListStruct getTransactionListAllPagesSync(String batchid, String alt, List<String> filter) throws OperatingContextException {
        ListStruct results = ListStruct.list();

        for (int page = 1; true; page++) {
            FuncResult<ListStruct> funcResult = getTransactionListSync(batchid, 1000, page, alt, filter);

            if (funcResult.isNotEmptyResult())
                results.withCollection(funcResult.getResult());

            if (funcResult.hasBoundary("FinalPage"))
                break;

            try {
                Thread.sleep(100);      // 1/10 sec
            }
            catch (InterruptedException x) {
                Logger.error("Unable to complete tx listing, thread interrupted.");
                break;
            }
        }

        return results;
    }

    // start at page 1
    public static FuncResult<ListStruct> getTransactionListSync(String batchid, int limit, int page, String alt, List<String> filter) throws OperatingContextException {
        FuncResult<ListStruct> funcResult = new FuncResult<>();

        try {
            OperationContext.getOrThrow().touch();

            JsonBuilder body = startRequestBody(alt, "getTransactionListRequest");

            body.field(	"batchId", batchid);

            body.field("sorting");
            body.startRecord();
            body.field("orderBy", "submitTimeUTC");
            body.field("orderDescending", true);
            body.endRecord();

            body.field("paging");
            body.startRecord();
            body.field("limit", limit);
            body.field("offset", 1 + ((page - 1) * limit));
            body.endRecord();

            HttpRequest.Builder builder = AuthUtil.buildRequest(alt, body);

            // Send post request sync
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

            RecordStruct result = processResponse(response);

            if (result != null) {
                ListStruct alltxs = result.selectAsList("transactions");

                if (alltxs != null) {
                    if (alltxs.size() < limit)
                        Logger.boundary("FinalPage");

                    if ((filter == null) || filter.isEmpty()) {
                        funcResult.setResult(alltxs);
                    } else {
                        ListStruct results = ListStruct.list();

                        for (int i = 0; i < alltxs.size(); i++) {
                            RecordStruct tx = alltxs.getItemAsRecord(i);
                            String txstatus = tx.getFieldAsString("transactionStatus");

                            if (filter.contains(txstatus))
                                results.with(tx);
                        }

                        funcResult.setResult(results);
                    }
                }
                else {
                    Logger.boundary("FinalPage");
                }
            }
        }
        catch (Exception x) {
            Logger.error("Error collecting tx list: Unable to connect to authorize. Error: " + x);
        }

        return funcResult;
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

            this.returnValue(processResponse(response));
        }
    }

    static protected RecordStruct processResponse(HttpResponse<String> response) {
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
                        Logger.error("Error processing auth request: " + responseCode + " : " + respBody.selectAsString("messages.message.0.text"));
                    }
                }
            }
        }

        return respBody;
    }
}
