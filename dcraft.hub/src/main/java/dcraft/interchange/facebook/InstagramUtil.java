package dcraft.interchange.facebook;

import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.mailchimp.MailChimpUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class InstagramUtil {
    /*
        Refresh a long-lived Instagram User Access Token that is at least 24 hours old but has not expired. Refreshed tokens are valid for 60 days from the date at which they are refreshed.

        Request Syntax

        GET https://graph.instagram.com/refresh_access_token
          ?grant_type=ig_refresh_token
          &access_token={long-lived-access-token}

        Response

        A JSON-formatted object containing the following properties and values.

        {
          "access_token": "{access-token}",
          "token_type": "{token-type}",
          "expires_in": {expires-in}
        }

        Response Contents

        {access-token}         Numeric string             A long-lived Instagram User Access Token.
        {token-type}           String                     bearer
        {expires-in}           Integer                    The number of seconds until the long-lived token expires.
     */

    static public void refresh(String token, String apikey, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            HttpRequest.Builder builder = InstagramUtil.buildRequest(apikey, "refresh_access_token?grant_type=ig_refresh_token&access_token=" + token);

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        callback.useContext();		// restore our operation context

                        int responseCode = response.statusCode();

                        if ((responseCode < 200) || (responseCode > 299))
                            Logger.error("Error processing request: Instagram sent an unexpected response code: " + responseCode);

                        CompositeStruct resp = CompositeParser.parseJson(response.body());

                        if (resp == null) {
                            Logger.error("Error processing request: Instagram sent an incomplete response: " + responseCode);
                            callback.returnEmpty();
                            return;
                        }

                        System.out.println("Instagram Resp: " + responseCode + "\n" + resp.toPrettyString());

                        callback.returnValue((RecordStruct) resp);
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing token refresh: Unable to connect to Instagram. Error: " + x);
            callback.returnEmpty();
        }
    }

    static public HttpRequest.Builder buildRequest(String apikey, String method) {
        String endpoint = "https://graph.instagram.com/" + method;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
                .GET();

        return builder;
    }
}
