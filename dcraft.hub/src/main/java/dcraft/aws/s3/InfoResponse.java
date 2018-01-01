//  This software code is made available "AS IS" without warranties of any
//  kind.  You may copy, display, modify and redistribute the software
//  code either by itself or as incorporated into your code; provided that
//  you do not remove any proprietary notices.  Your use of this software
//  code is at your own risk and you waive any claim against Amazon
//  Digital Services, Inc. or its affiliates with respect to your use of
//  this software code. (c) 2006 Amazon Digital Services, Inc. or its
//  affiliates.

package dcraft.aws.s3;

import dcraft.util.web.DateParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A Response object returned from AWSAuthConnection.get().  Exposes the attribute object, which
 * represents the retrieved object.
 */
public class InfoResponse extends Response {
    public ListEntry object;

    /**
     * Pulls a representation of an S3Object out of the HttpURLConnection response.
     */
    public InfoResponse(HttpURLConnection connection) throws IOException {
        super(connection);
        if (connection.getResponseCode() < 400) {
            Map<String,List<String>> headers = connection.getHeaderFields();

            this.object = new ListEntry();
    
            String lm = headers.get("Last-Modified").get(0);
            long in = new DateParser().convert(lm);
            
            this.object.lastModified = Instant.ofEpochMilli(in);
            
            String etag = headers.get("ETag").get(0);
            
            this.object.eTag = etag.substring(1, etag.length() - 1);
            this.object.size = Long.parseLong(headers.get("Content-Length").get(0));
        }
    }
}
