package dcraft.interchange.aws;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.FuncResult;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.HashUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;
import jxl.biff.ByteArray;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class AWSS3 {

    static public void listObjects(String bucket, String path, OperationOutcome<XElement> callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.listObjects(connection, region, bucket, path, callback);
    }

    static public void listObjects(XElement connection, String region, String bucket, String path, OperationOutcome<XElement> callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Params", RecordStruct.record()
                        .with("list-type", "2")
                        .with("delimiter", "/")
                        .with("prefix", "/".equals(path) ? "" : path.substring(1) + "/")
                )
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    static public void getFileDirect(String bucket, String path, OperationOutcome<Memory> callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.getFileDirect(connection, region, bucket, path, callback);
    }

    static public void getFileDirect(XElement connection, String region, String bucket, String path, OperationOutcome<Memory> callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Path", path)
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete(BinaryResponseConsumer.of(callback));
    }

    static public FuncResult<String> getFilePresign(String bucket, String path) throws OperatingContextException {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        return AWSS3.getFilePresign(connection, region, bucket, path);
    }

    static public FuncResult<String> getFilePresign(XElement connection, String region, String bucket, String path) throws OperatingContextException {
        FuncResult<String> result = new FuncResult<>();

        result.setResult(AWSUtilCore.presignRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Path", path)
                .with("Stamp", "20220217T204610Z")
        ));

        return result;
    }

    static public void putFileDirect(String bucket, String path, Memory body, OperationOutcomeEmpty callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.putFileDirect(connection, region, bucket, path, body, callback);
    }

    static public void putFileDirect(XElement connection, String region, String bucket, String path, Memory body, OperationOutcomeEmpty callback) {
        body.setPosition(0);

        String payload_hash = HashUtil.getSha256(body.getInputStream());
        body.setPosition(0);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Method", "PUT")
                .with("Path", path)
                .with("PayloadHash", payload_hash)
             )
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toArray()));
        ;

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete(EmptyResponseConsumer.of(callback));
    }

    static public FuncResult<String> putFilePresign(String bucket, String path) throws OperatingContextException {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        return AWSS3.putFilePresign(connection, region, bucket, path);
    }

    static public FuncResult<String> putFilePresign(XElement connection, String region, String bucket, String path) throws OperatingContextException {
        FuncResult<String> result = new FuncResult<>();

        result.setResult(AWSUtilCore.presignRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Method", "PUT")
                .with("Path", path)
        ));

        return result;
    }

    static public RecordStruct buildHostOptionS3(String region, String bucket) {
        String host = "s3.amazonaws.com";

        if (StringUtil.isNotEmpty(bucket))
            host = bucket + ".s3." + region + ".amazonaws.com";

        return RecordStruct.record()
                .with("Region", region)
                .with("Service", "s3")
                .with("Host", host);
    }
}
