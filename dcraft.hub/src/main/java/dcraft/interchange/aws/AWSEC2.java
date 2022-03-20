package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AWSEC2 {

    // TODO rework to modernize
    static public void regions(XElement connection, OperationOutcome<XElement> callback) {
        String request_parameters = "Action=DescribeRegions&Version=2016-11-15";

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(null)
                .with("Method", "GET")
                .with("Params", request_parameters)
        );

        httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    callback.useContext();		// restore context

                    System.out.println("code: " + response.statusCode());
                    //System.out.println("got: " + response.body());

                    callback.returnValue(Struct.objectToXml(response.body()));
                });
    }

    // TODO rework to modernize
    static public void describeVolumes(XElement connection, String region, OperationOutcome<XElement> callback) {
        String request_parameters = "Action=DescribeVolumes&Version=2016-11-15";

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region)
                .with("Method", "GET")
                .with("Params", request_parameters)
        );

        httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    callback.useContext();		// restore context

                    System.out.println("code: " + response.statusCode());
                    //System.out.println("got: " + response.body());

                    callback.returnValue(Struct.objectToXml(response.body()));
                });
    }

    // TODO rework to modernize
    static public void createSecurityGroup(XElement connection, String region, String alias, String desc, OperationOutcome<XElement> callback) {
        try {
            String request_parameters = "Action=CreateSecurityGroup&Version=2016-11-15&GroupName=" +
                    URLEncoder.encode(alias, "UTF-8") + "&GroupDescription=" + URLEncoder.encode(desc, "UTF-8");

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region)
                    .with("Method", "GET")
                    .with("Params", request_parameters)
            );

            httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        callback.useContext();        // restore context

                        System.out.println("code: " + response.statusCode());
                        //System.out.println("got: " + response.body());

                        callback.returnValue(Struct.objectToXml(response.body()));
                    });
        }
        catch (UnsupportedEncodingException x) {
            Logger.error("encoding error: " + x);
            callback.returnEmpty();
        }
    }

    // TODO rework to modernize
    static public void describeSecurityGroup(XElement connection, String region, String id, OperationOutcome<XElement> callback) {
        try {
            String groupid = URLEncoder.encode(id, "UTF-8");

            //String request_parameters = "Action=DescribeSecurityGroups&Filter.1.Name=group-id&Filter.1.Value.1=" +
            String request_parameters = "Action=DescribeSecurityGroups&GroupId.1=" +
                    groupid + "&Version=2016-11-15";
            //String request_parameters = "Action=DescribeSecurityGroups&MaxResults=10&Version=2016-11-15";

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region)
                    .with("Method", "GET")
                    .with("Params", request_parameters)
            );

            httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        callback.useContext();        // restore context

                        System.out.println("code: " + response.statusCode());
                        //System.out.println("got: " + response.body());

                        callback.returnValue(Struct.objectToXml(response.body()));
                    });
        }
        catch (UnsupportedEncodingException x) {
            Logger.error("encoding error: " + x);
            callback.returnEmpty();
        }
    }

    static public void describeSecurityGroupRules(XElement connection, String region, String groupid, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "DescribeSecurityGroupRules")
                .with("GroupId", groupid)
                .with("Filter", ListStruct.list()
                        .with(RecordStruct.record()
                                .with("Name", "group-id")
                                .with("Value", groupid)
                        )
                );

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region, params));

        httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    static public void describeSecurityGroupRule(XElement connection, String region, String groupid, String ruleid, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "DescribeSecurityGroupRules")
                .with("Filter", ListStruct.list()
                        .with(RecordStruct.record()
                                .with("Name", "group-id")
                                .with("Value", groupid)
                        )
                        .with(RecordStruct.record()
                                .with("Name", "security-group-rule-id")
                                .with("Value", ruleid)
                        )
                );

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region, params));

        httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    /*
        Rules: [
            {
                SecurityGroupRuleId: id,
                SecurityGroupRule: {
                    CidrIpv4: cidr
                }
            }
       ]
     */
    static public void updateSecurityGroupRuleIngress(XElement connection, String region, String groupid, ListStruct rules, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "ModifySecurityGroupRules")
                .with("GroupId", groupid)
                .with("SecurityGroupRule", rules);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region, params));

        httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    // TODO rework to modernize
    static public void allocateAddress(XElement connection, String region, OperationOutcome<XElement> callback) {
        String request_parameters = "Action=AllocateAddress&Version=2016-11-15";

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSEC2.buildHostOptionEC2(region)
                .with("Method", "GET")
                .with("Params", request_parameters)
        );

        httpClient.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync(response -> {
                    callback.useContext();        // restore context

                    System.out.println("code: " + response.statusCode());
                    //System.out.println("got: " + response.body());

                    callback.returnValue(Struct.objectToXml(response.body()));
                });
    }



    static public RecordStruct buildHostOptionEC2(String region, RecordStruct params) {
        RecordStruct options = AWSEC2.buildHostOptionEC2(region);

        if (params.isFieldEmpty("Version"))
            params.with("Version", "2016-11-15");

        options.with("Params", params);

        return options;
    }


    // https://docs.aws.amazon.com/AWSEC2/latest/APIReference/Using_Endpoints.html
    static public RecordStruct buildHostOptionEC2(String region) {
        String host = "ec2.amazonaws.com";   //		-- needs improvement TODO

        if (StringUtil.isNotEmpty(region)) {
            host = "ec2." + region + ".amazonaws.com";

            if (AWSUtilCore.isHostDualStack(region))
                host = "api.ec2." + region + ".aws";
        }

        return RecordStruct.record()
                .with("Region", region)
                .with("Service", "ec2")
                .with("Host", host);
    }
}
