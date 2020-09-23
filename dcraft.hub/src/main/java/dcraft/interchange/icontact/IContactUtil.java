package dcraft.interchange.icontact;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.mailchimp.MailChimpUtil;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class IContactUtil {
    // useful info
    // https://www.icontact.com/developerportal/documentation/authenticate-requests

    // how to a get the available lists
    // https://app.icontact.com/icp/a/[account id]/c/[folder id]/lists/


    static public void getLists(String alt, OperationOutcomeRecord callback) {
        XElement mc = ApplicationHub.getCatalogSettings("Integration-iContact", alt);

        if (mc == null) {
            Logger.error("Missing iContact settings.");

            if (callback != null)
                callback.returnEmpty();

            return;
        }

        IContactUtil.getLists(mc.attr("AppId"), mc.attr("AppUser"), mc.attr("AppPasswordPlain"),
                mc.attr("AccountId"), mc.attr("FolderId"), callback);
    }

    static public void getLists(String appid, String appuser, String aapppass, String account, String folder, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            HttpRequest.Builder builder = IContactUtil.buildRequest(appid, appuser, aapppass, account + "/c/" + folder + "/lists/", null);

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        callback.useContext();		// restore our operation context

                        int responseCode = response.statusCode();

                        if ((responseCode < 200) || (responseCode > 299))
                            Logger.error("Error processing request: iContact sent an unexpected response code: " + responseCode);

                        CompositeStruct resp = CompositeParser.parseJson(response.body());

                        if (resp == null) {
                            Logger.error("Error processing request: iContact sent an incomplete response: " + responseCode);
                            callback.returnEmpty();
                            return;
                        }

                        System.out.println("iContact Resp: " + responseCode + "\n" + resp.toPrettyString());

                        callback.returnValue((RecordStruct) resp);
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing listing: Unable to connect to mailchimp. Error: " + x);
            callback.returnEmpty();
        }
    }

    /*
    {
        "email":"lightofgadrel@gmail.com",
        "firstName":"Andy",
        "lastName":"White"
    }
     */
    static public void addContact(String alt, RecordStruct data, OperationOutcomeRecord callback) {
        XElement mc = ApplicationHub.getCatalogSettings("Integration-iContact", alt);

        if (mc == null) {
            Logger.error("Missing iContact settings.");

            if (callback != null)
                callback.returnEmpty();

            return;
        }

        IContactUtil.addContact(mc.attr("AppId"), mc.attr("AppUser"), mc.attr("AppPasswordPlain"),
                mc.attr("AccountId"), mc.attr("FolderId"), data, callback);
    }

    static public void addContact(String appid, String appuser, String aapppass, String account, String folder, RecordStruct data, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            HttpRequest.Builder builder = IContactUtil.buildRequest(appid, appuser, aapppass, account + "/c/" + folder + "/contacts/", ListStruct.list(data));

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        callback.useContext();		// restore our operation context

                        int responseCode = response.statusCode();

                        if ((responseCode < 200) || (responseCode > 299))
                            Logger.error("Error processing request: iContact sent an unexpected response code: " + responseCode);

                        CompositeStruct resp = CompositeParser.parseJson(response.body());

                        if (resp == null) {
                            Logger.error("Error processing request: iContact sent an incomplete response: " + responseCode);
                            callback.returnEmpty();
                            return;
                        }

                        System.out.println("iContact Resp: " + responseCode + "\n" + resp.toPrettyString());

                        callback.returnValue((RecordStruct) resp);
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing listing: Unable to connect to mailchimp. Error: " + x);
            callback.returnEmpty();
        }
    }

    // leave listid = null for default
    static public void subscribe(String alt, String contactid, String listid, OperationOutcomeRecord callback) {
        XElement mc = ApplicationHub.getCatalogSettings("Integration-iContact", alt);

        if (mc == null) {
            Logger.error("Missing iContact settings.");

            if (callback != null)
                callback.returnEmpty();

            return;
        }

        if (listid == null)
            listid = mc.attr("ListId");

        IContactUtil.subscribe(mc.attr("AppId"), mc.attr("AppUser"), mc.attr("AppPasswordPlain"),
                mc.attr("AccountId"), mc.attr("FolderId"), contactid, listid, callback);
    }

    static public void subscribe(String appid, String appuser, String aapppass, String account, String folder, String contactid, String listid, OperationOutcomeRecord callback) {
        try {
            OperationContext.getOrThrow().touch();

            HttpRequest.Builder builder = IContactUtil.buildRequest(appid, appuser, aapppass, account + "/c/" + folder + "/subscriptions/",
                    ListStruct.list(RecordStruct.record()
                            .with("contactId", contactid)
                            .with("listId", listid)
                            .with("status", "normal")
                    )
            );

            // Send post request
            HttpClient.newHttpClient().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        callback.useContext();		// restore our operation context

                        int responseCode = response.statusCode();

                        if ((responseCode < 200) || (responseCode > 299))
                            Logger.error("Error processing request: iContact sent an unexpected response code: " + responseCode);

                        CompositeStruct resp = CompositeParser.parseJson(response.body());

                        if (resp == null) {
                            Logger.error("Error processing request: iContact sent an incomplete response: " + responseCode);
                            callback.returnEmpty();
                            return;
                        }

                        System.out.println("iContact Resp: " + responseCode + "\n" + resp.toPrettyString());

                        callback.returnValue((RecordStruct) resp);
                    });
        }
        catch (Exception x) {
            Logger.error("Error processing listing: Unable to connect to mailchimp. Error: " + x);
            callback.returnEmpty();
        }
    }

    // returns the single subscription record
    static public void subscribe(String alt, RecordStruct contact, String listid, OperationOutcomeRecord callback) throws OperatingContextException {
        IContactUtil.addContact(alt, contact, new OperationOutcomeRecord() {
            @Override
            public void callback(RecordStruct result) throws OperatingContextException {
                if (! this.hasErrors() && this.isNotEmptyResult() && result.isNotFieldEmpty("contacts")) {
                    RecordStruct contactresult = result.getFieldAsList("contacts").getItemAsRecord(0);

                    IContactUtil.subscribe(alt, contactresult.getFieldAsString("contactId"), listid, new OperationOutcomeRecord() {
                        @Override
                        public void callback(RecordStruct result) throws OperatingContextException {
                            if (! this.hasErrors() && this.isNotEmptyResult() && result.isNotFieldEmpty("subscriptions")) {
                                RecordStruct subresult = result.getFieldAsList("subscriptions").getItemAsRecord(0);

                                callback.returnValue(subresult);
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
        });
    }

    static public HttpRequest.Builder buildRequest(String appid, String appuser, String apppass, String method, CompositeStruct post) {
        String endpoint = "https://app.icontact.com/icp/a/" + method;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", "dcServer/2019.1 (Language=Java/11)")
                .header("Accept", "application/json")
                .header("API-Version", "2.2 ")
                .header("API-AppId", appid)
                .header("API-Username", appuser)
                .header("API-Password", apppass);

        if (post != null)
            builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(post.toString()));
        else
            builder.GET();

        return builder;
    }
}
