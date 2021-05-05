package dcraft.interchange.facebook;

import dcraft.db.Constants;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.interchange.mailchimp.MailChimpUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tool.backup.BackupUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;

public class InstagramUtil {
    static public void checkGetToken(TablesAdapter db, String alt, OperationOutcomeString callback) throws OperatingContextException {
        XElement isettings = ApplicationHub.getCatalogSettings("Social-Instagram", alt);

        if (isettings == null) {
            Logger.warn("Missing Instagram settings.");
            callback.returnEmpty();
            return;
        }

        // try new Basic Display first
        String basictoken = isettings.attr("BasicToken");

        String sub = StringUtil.isNotEmpty(alt) ? alt : "default";

        boolean disabled = Struct.objectToBooleanOrFalse(db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessDisabled", sub));

        if (disabled) {
            callback.returnEmpty();
            return;
        }

        ZonedDateTime expire = Struct.objectToDateTime(db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessExpire", sub));

        if (expire != null) {
            basictoken = Struct.objectToString(db.getStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessToken", sub));

            ZonedDateTime checkdate = TimeUtil.now().plusDays(7);

            // if expire in more than 7 days from now then continue
            if (expire.compareTo(checkdate) >= 0) {
                callback.returnValue(basictoken);
                return;
            }
        }

        if (StringUtil.isNotEmpty(basictoken)) {
            InstagramUtil.refresh(basictoken, new OperationOutcomeRecord() {
                @Override
                public void callback(RecordStruct result) throws OperatingContextException {
                    if (this.isNotEmptyResult()) {
                        String token = result.getFieldAsString("access_token");
                        long secs = result.getFieldAsInteger("expires_in", 0L);

                        if (StringUtil.isNotEmpty(token)) {
                            ZonedDateTime expire = TimeUtil.now().plusSeconds(secs);

                            db.updateStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessToken", sub, token);
                            db.updateStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessExpire", sub, expire);

                            BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : " + OperationContext.getOrThrow().getSite().getTenant().getAlias() + " : successfully renewed IG token: " + token);
                        }
                        else {
                            db.updateStaticList("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD, "dcmInstagramAccessDisabled", sub, true);

                            BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : " + OperationContext.getOrThrow().getSite().getTenant().getAlias() + " : unable to renew IG token");
                        }

                        callback.returnValue(token);
                    }
                    else {
                        callback.returnEmpty();
                    }
                }
            });
        }
        else {
            callback.returnEmpty();
        }
    }

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

    static public void refresh(String token, OperationOutcomeRecord callback) throws OperatingContextException {
        try {
            OperationContext.getOrThrow().touch();

            HttpRequest.Builder builder = InstagramUtil.buildRequest( "refresh_access_token?grant_type=ig_refresh_token&access_token=" + token);

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

                            try {
                                BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : " + OperationContext.getOrThrow().getSite().getTenant().getAlias() + " : Error processing request: Instagram sent an incomplete response: " + responseCode);
                            }
                            catch (OperatingContextException e) {
                                Logger.error("Unable to notify admin: Instagram sent an incomplete response");
                            }

                            callback.returnEmpty();
                            return;
                        }

                        System.out.println("Instagram Resp: " + responseCode + "\n" + resp.toPrettyString());

                        callback.returnValue((RecordStruct) resp);
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing token refresh: Unable to connect to Instagram. Error: " + x);
            BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : " + OperationContext.getOrThrow().getSite().getTenant().getAlias() + " : Error processing token refresh: Unable to connect to Instagram. Error: " + x);
            callback.returnEmpty();
        }
    }

    static public HttpRequest.Builder buildRequest(String method) {
        String endpoint = "https://graph.instagram.com/" + method;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
                .GET();

        return builder;
    }
}
