package dcraft.interchange.aws;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
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
    static public void listObjects(String bucket, CommonPath path, OperationOutcome<XElement> callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.listObjects(connection, region, bucket, path, callback);
    }

    static public void listObjects(XElement connection, String region, String bucket, CommonPath path, OperationOutcome<XElement> callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Params", RecordStruct.record()
                        .with("list-type", "2")
                        .with("delimiter", "/")
                        .with("prefix", path.isRoot() ? "" : path.toString().substring(1) + "/")
                )
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    static public void listObjectsDeep(XElement connection, String region, String bucket, CommonPath path, OperationOutcome<XElement> callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Params", RecordStruct.record()
                        .with("list-type", "2")
                        .with("prefix", path.isRoot() ? "" : path.toString().substring(1) + "/")
                )
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    static public void getFileInfo(String bucket, CommonPath path, OperationOutcomeRecord callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.getFileInfo(connection, region, bucket, path, callback);
    }

    static public void getFileInfo(XElement connection, String region, String bucket, CommonPath path, OperationOutcomeRecord callback) {
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket, path)
                .with("Method", "HEAD")
            ).method("HEAD", HttpRequest.BodyPublishers.noBody());

        HttpClient.newHttpClient()
                .sendAsync(req.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete(HeadResponseConsumer.of(callback));
    }

    static public void getFileDirect(String bucket, CommonPath path, OperationOutcome<Memory> callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.getFileDirect(connection, region, bucket, path, callback);
    }

    static public void getFileDirect(XElement connection, String region, String bucket, CommonPath path, OperationOutcome<Memory> callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket, path));

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete(BinaryResponseConsumer.of(callback));
    }

    static public FuncResult<String> getFilePresign(String bucket, CommonPath path) throws OperatingContextException {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        return AWSS3.getFilePresign(connection, region, bucket, path);
    }

    static public FuncResult<String> getFilePresign(XElement connection, String region, String bucket, CommonPath path) throws OperatingContextException {
        FuncResult<String> result = new FuncResult<>();

        result.setResult(AWSUtilCore.presignRequest(connection, AWSS3.buildHostOptionS3(region, bucket, path)
                .with("Stamp", "20220217T204610Z")
        ));

        return result;
    }

    static public void putFileDirect(String bucket, CommonPath path, Memory body, OperationOutcomeEmpty callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.putFileDirect(connection, region, bucket, path, body, callback);
    }

    static public void putFileDirect(XElement connection, String region, String bucket, CommonPath path, Memory body, OperationOutcomeEmpty callback) {
        body.setPosition(0);

        String payload_hash = HashUtil.getSha256(body.getInputStream());
        body.setPosition(0);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket, path)
                .with("Method", "PUT")
                .with("PayloadHash", payload_hash)
             )
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toArray()));

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete(EmptyResponseConsumer.of(callback));
    }

    static public FuncResult<RecordStruct> putFilePresign(String bucket, CommonPath path) throws OperatingContextException {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        return AWSS3.putFilePresign(connection, region, bucket, path);
    }

    static public FuncResult<RecordStruct> putFilePresign(XElement connection, String region, String bucket, CommonPath path) throws OperatingContextException {
        FuncResult<RecordStruct> result = new FuncResult<>();

        RecordStruct options = AWSS3.buildHostOptionS3(region, bucket, path)
                .with("Method", "PUT")
                .with("ContentType", ResourceHub.getResources().getMime().getMimeTypeForPath(path).getMimeType());

        String url = AWSUtilCore.presignRequest(connection, options);

        result.setResult(RecordStruct.record()
                .with("Url", url)
                .with("Stamp", options.getFieldAsString("Stamp"))
                .with("ContentType", options.getFieldAsString("ContentType"))
        );

        return result;
    }

    static public void createFolder(String bucket, CommonPath path, OperationOutcomeEmpty callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.createFolder(connection, region, bucket, path, callback);
    }

    static public void createFolder(XElement connection, String region, String bucket, CommonPath path, OperationOutcomeEmpty callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket)
                .with("Method", "PUT")
                .with("Path", path.toString() + "/")
        )
                .PUT(HttpRequest.BodyPublishers.ofString(""));

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete(EmptyResponseConsumer.of(callback));
    }

    static public void removeFile(String bucket, CommonPath path, OperationOutcomeEmpty callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.removeFile(connection, region, bucket, path, callback);
    }

    static public void removeFile(XElement connection, String region, String bucket, CommonPath path, OperationOutcomeEmpty callback) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSS3.buildHostOptionS3(region, bucket, path)
                .with("Method", "DELETE")
        )
                .DELETE();

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete(EmptyResponseConsumer.of(callback));
    }

    static public void removeFolder(String bucket, CommonPath path, OperationOutcomeEmpty callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws");

        String region = connection.getAttribute("StorageRegion", connection.getAttribute("Region"));

        AWSS3.removeFolder(connection, region, bucket, path, callback);
    }

    static public void removeFolder(XElement connection, String region, String bucket, CommonPath path, OperationOutcomeEmpty callback) {
        Logger.error("AWS remove folder not yet implemented");

        // TODO recursive task

        // listObjectsDeep

        callback.returnEmpty();
    }

    static public RecordStruct buildHostOptionS3(String region, String bucket, CommonPath path) {
        RecordStruct options = AWSS3.buildHostOptionS3(region, bucket);

        options
                .with("Path", path.toString());  // .substring(1));

        return options;
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