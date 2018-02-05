/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.util.net;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.googlecode.ipv6.IPv6Address;

import dcraft.log.Logger;

public class NetUtil {
    static public String urlEncodeUTF8(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } 
        catch (UnsupportedEncodingException e) {
            // NA
        }
        
        return null;
    }
    
    static public String urlEncodeUTF8(Map<String,String> map) {
        StringBuilder sb = new StringBuilder();
        
        for (Map.Entry<String,String> entry : map.entrySet()) {
            if (sb.length() > 0) 
                sb.append("&");
            
            sb.append(urlEncodeUTF8(entry.getKey()) + "=" + urlEncodeUTF8(entry.getValue()));
        }
        
        return sb.toString();       
    }
    
    static public String formatIpAddress(InetSocketAddress addr) {
    	if (addr.getAddress() instanceof Inet4Address)
    		return addr.getHostString();
    	
		if (addr.getAddress() instanceof Inet6Address) {
			IPv6Address got = IPv6Address.fromInetAddress(addr.getAddress());
			
			return got.toString();
		}
		
		return null;
    }
    
	public static boolean download(String address, Path localFile, boolean allowRedirect) {
		OutputStream out = null;
		HttpURLConnection conn = null;
		InputStream  in = null;
		
		try {
			URL url = new URL(address);
			conn = (HttpURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(allowRedirect);
			in = conn.getInputStream();
			
			byte[] buffer = new byte[1024];
			int numRead = in.read(buffer);
			
			// if no first block don't create a dest file
			if (numRead == -1)
				return false;

			Files.createDirectories(localFile.getParent());

			out = new BufferedOutputStream(Files.newOutputStream(localFile));
			
			// first block
			out.write(buffer, 0, numRead);
			
			while ((numRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, numRead);
			}

			return true;
		}
		catch (Exception x) {
			Logger.error("Unable to download file. " + x);
			
			return false;
		} 
		finally {
			try {
				if (in != null) 
					in.close();
				
			} 
			catch (IOException x) {
				Logger.error("Unable to close input stream. " + x);
			}
			
			try {
				if (out != null) {
					out.close();
				}
			} 
			catch (IOException x) {
				Logger.error("Unable to close downloaded file. " + x);
			}
		}
	}
}
