package dcraft.interchange.authorize;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.mailchimp.MailChimpUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AuthUtil {

    static public HttpRequest.Builder buildRequest(String authid, String key, boolean istest, String method, RecordStruct post) {
        String endpoint = istest ? AuthUtilXml.AUTH_TEST_ENDPOINT : AuthUtilXml.AUTH_LIVE_ENDPOINT;

        RecordStruct request = RecordStruct.record()
                .with(method, post
                        .with("merchantAuthentication", RecordStruct.record()
                                .with("name", authid)
                                .with("transactionKey", key)
                        )
                );

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()));

        return builder;
    }

    static public HttpRequest.Builder buildRequest(String alt, String method, RecordStruct post) {
        XElement auth = ApplicationHub.getCatalogSettings("CMS-Authorize", alt);

        if (auth == null) {
            Logger.error("Missing store Authorize settings.");
            return null;
        }

        String authid = auth.getAttribute("LoginId");
        String key = auth.getAttribute("TransactionKey");

        String authkey = ApplicationHub.getClock().getObfuscator().decryptHexToString(key);

        boolean live = auth.getAttributeAsBooleanOrFalse("Live");

        return buildRequest(authid, authkey, ! live, method, post);
    }


/*

    function authCreateTransactionFromCard($id,$card,$expire,$cardcode,$tax,$ship,$amt,$email,$ipaddress,$billinfo,$shipinfo,$items) {
        $carddata = new stdClass();
        $carddata->cardNumber = $card;
        $carddata->expirationDate = $expire;
        $carddata->cardCode = $cardcode;

        $paydata = new stdClass();
        $paydata->creditCard = $carddata;

        $paymentinfo = json_encode($paydata, JSON_PRETTY_PRINT);

        return authCreateTransaction($id,$paymentinfo,$tax,$ship,$amt,$email,$ipaddress,$billinfo,$shipinfo,$items);
    }

*
{
	"creditCard": {
			"cardNumber": "5424000000000015",
			"expirationDate": "2020-12",
			"cardCode": "999"
	}
}

{
		"itemId": "1",
		"name": "vase",
		"description": "Cannes logo",
		"quantity": "1",
		"unitPrice": "25.00"
}

{
		"firstName": "Ellen",
		"lastName": "Johnson",
		"address": "14 Main Street",
		"city": "Pecan Springs",
		"state": "TX",
		"zip": "44628",
		"country": "USA"
}*

    function authCreateTransaction($id,$paymentinfo,$tax,$ship,$amt,$email,$ipaddress,$billinfo,$shipinfo,$items) {
        global $authendpoint;

        $billinfo = json_encode($billinfo, JSON_PRETTY_PRINT);
        $shipinfo = json_encode($shipinfo, JSON_PRETTY_PRINT);
        $items = json_encode($items, JSON_PRETTY_PRINT);

        $mauth = authGetAuthentication();

        $response = wp_remote_post($authendpoint, array(
                'headers' => array(
                'Accept' => 'application/json',
                'Content-Type' => 'application/json'
		),
        'data_format' => 'body',
                'body' => "{
		    \"createTransactionRequest\": {
        {$mauth},
		        \"refId\": \"{$id}\",
		        \"transactionRequest\": {
		            \"transactionType\": \"authCaptureTransaction\",
								\"amount\": \"{$amt}\",
		            \"payment\": {$paymentinfo},
		            \"lineItems\": {
		                \"lineItem\": {$items}
    },
            \"tax\": {
            \"amount\": \"{$tax}\"
},
        \"shipping\": {
        \"amount\": \"{$ship}\"
        },
        \"customer\": {
        \"type\": \"individual\",
        \"email\": \"{$email}\"
        },
        \"billTo\": {$billinfo},
        \"shipTo\": {$shipinfo},
        \"customerIP\": \"{$ipaddress}\",
        \"transactionSettings\": {
        \"setting\": {
        \"settingName\": \"emailCustomer\",
        \"settingValue\": \"false\"
        }
        }
        }
        }
        }"
        ));

        if (is_wp_error($response))
        return NULL; //$response->get_error_message();

        $body = CleanJson(wp_remote_retrieve_body($response));

        //echo "body: " . $body . "<br/><br/><br/>";

        $data = json_decode($body);

        if ($data->messages->resultCode != "Ok")
        return NULL;

        return $data->transactionResponse;
        }
*/

        public static void authGetTransactionDetail(String txid, String alt, OperationOutcomeRecord callback) {
            try {
                OperationContext.getOrThrow().touch();

                RecordStruct data = RecordStruct.record()
                        .with(	"transId", txid);

                HttpRequest.Builder builder = AuthUtil.buildRequest(alt, "getTransactionDetailsRequest", data);

                // Send post request
                HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                        .thenAcceptAsync(response -> {
                            callback.useContext();		// restore our operation context

                            int responseCode = response.statusCode();

                            if ((responseCode < 200) || (responseCode > 299))
                                Logger.error("Error processing request: Auth sent an error response code: " + responseCode);

                            CompositeStruct resp = CompositeParser.parseJson(response.body());

                            if (resp == null) {
                                Logger.error("Error processing request: Auth sent an incomplete response: " + responseCode);
                                callback.returnEmpty();
                                return;
                            }

                            System.out.println("Auth Resp: " + responseCode + "\n" + resp.toPrettyString());

                            RecordStruct body = (RecordStruct) resp;

                            if (! "Ok".equals(body.selectAsString("messages.resultCode"))) {
                                Logger.error("Error processing request: Auth could not load transaction: " + responseCode);
                                callback.returnEmpty();
                                return;
                            }

                            callback.returnValue(body.getFieldAsRecord("transaction"));
                        });
            }
            catch (Exception x) {
                Logger.error("Error processing subscription: Unable to connect to MailChimp. Error: " + x);
                callback.returnEmpty();
            }
        }

        /*
// void if (detail) ->transactionStatus == "capturedPendingSettlement"
        function authVoidTransaction($id,$txid) {
        global $authendpoint;

        $mauth = authGetAuthentication();

        $response = wp_remote_post($authendpoint, array(
        'headers' => array(
        'Accept' => 'application/json',
        'Content-Type' => 'application/json'
        ),
        'data_format' => 'body',
        'body' => "{
        \"createTransactionRequest\": {
        {$mauth},
        \"refId\": \"{$id}\",
        \"transactionRequest\": {
        \"transactionType\": \"voidTransaction\",
        \"refTransId\": \"{$txid}\"
        }
        }
        }"
        ));

        if (is_wp_error($response))
        return NULL; //$response->get_error_message();

        $body = CleanJson(wp_remote_retrieve_body($response));

        $data = json_decode($body);

        if ($data->messages->resultCode != "Ok")
        return NULL;

        return $data->transactionResponse;
        }

// $id - ws_order_refund id
// $txid - original tx id, from order
        function authRefundTransaction($id,$txid,$amt,$txdetail) {
        global $authendpoint;

        if ($txdetail == NULL)
        $txdetail = authGetTransactionDetail($txid);

        if ($txdetail->payment->creditCard && $txdetail->payment->creditCard->cardType)
        unset($txdetail->payment->creditCard->cardType);

        if ($txdetail->billTo->phoneNumber)
        unset($txdetail->billTo->phoneNumber);

        //echo json_encode($txdetail, JSON_PRETTY_PRINT) . "<br><br>";

        $paymentinfo = json_encode($txdetail->payment, JSON_PRETTY_PRINT);
        $billinfo = json_encode($txdetail->billTo, JSON_PRETTY_PRINT);

        $mauth = authGetAuthentication();

        $response = wp_remote_post($authendpoint, array(
        'headers' => array(
        'Accept' => 'application/json',
        'Content-Type' => 'application/json'
        ),
        'data_format' => 'body',
        'body' => "{
        \"createTransactionRequest\": {
        {$mauth},
        \"refId\": \"{$id}\",
        \"transactionRequest\": {
        \"transactionType\": \"refundTransaction\",
        \"amount\": \"{$amt}\",
        \"payment\": {$paymentinfo},
        \"refTransId\": \"{$txid}\",
        \"billTo\": {$billinfo}
        }
        }
        }"
        ));

        if (is_wp_error($response))
        return NULL; //$response->get_error_message();

        $body = CleanJson(wp_remote_retrieve_body($response));

        //echo "body: " . $body . "<br/><br/><br/>";

        $data = json_decode($body);

        if ($data->messages->resultCode != "Ok")
        return NULL;

        return $data->transactionResponse;
        }
    */
}
