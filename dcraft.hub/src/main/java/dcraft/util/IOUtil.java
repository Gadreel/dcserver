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
package dcraft.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import dcraft.log.Logger;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.io.LineIterator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

public class IOUtil {
    /**
     * Read entire text file into a string.
     * 
     * @param file to read
     * @return file content if readable, otherwise null
     */
    public static CharSequence readEntireFile(Path file) {
        try (BufferedReader br = Files.newBufferedReader(file, Charset.forName("UTF-8"))) {
            StringBuilder sb = new StringBuilder();
        	
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            
            return sb;
        } 
        catch (IOException x) {
        	Logger.error("Unabled to read file " + file + ", error: " + x);
            
            return null;
		} 
    }
    
    /**
     * Read entire stream into a string.
     * 
     * @param stream to read
     * @return stream content if readable, otherwise null
     */
    public static CharSequence readEntireStream(InputStream stream) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder sb = new StringBuilder();
        	
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            
            return sb;
        } 
        catch (IOException x) {
        	Logger.error("Unabled to read stream, error: " + x);
            
            return null;
		} 
    }
    
	public static boolean saveEntireFile(Path dest, String content) {
		try {
			Files.createDirectories(dest.getParent());
			Files.write(dest, Utf8Encoder.encode(content), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
			
			return true;
		} 
		catch (Exception x) {
        	Logger.error("Unabled to write file " + dest + ", error: " + x);
			
			return false;
		}
	}
    
	public static boolean saveEntireFile(Path dest, Memory content) {
		try {
			Files.createDirectories(dest.getParent());
			Files.write(dest, content.toArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
			
			return true;
		} 
		catch (Exception x) {
        	Logger.error("Unabled to write file " + dest + ", error: " + x);
			
			return false;
		}
	}

	public static boolean saveBuffer(Path path, ByteBuf b) {
		return IOUtil.saveEntireStream(path, new ByteBufInputStream(b));
	}

	public static boolean saveEntireStream(Path path, InputStream in) {
		try {
			Files.createDirectories(path.getParent());
			
			try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
				byte[] b = new byte[16 * 1024];
				
				int len = in.read(b);
				
				while (len != -1) {
					os.write(b, 0, len);
					len = in.read(b);
				}
				
				os.flush();
				
				return true;
			}
			finally {
				IOUtil.closeQuietly(in);
			}
		}
		catch (Exception x) {
        	Logger.error("Unabled to write file " + path + ", error: " + x);
			
			return false;
		}
	}
	
	public static byte[] charsEncodeAndCompress(CharSequence v) {	
		try {
			byte[] buffer = Utf8Encoder.encode(v);
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			GzipCompressorOutputStream cos = new GzipCompressorOutputStream(bos);
			cos.write(buffer);
			cos.close();
			bos.close();
			
			return bos.toByteArray();
		}
		catch (Exception x) {
        	Logger.error("Unabled to encode and compress, error: " + x);
    		
    		return null;
		}
	}

	public static Memory readEntireFileToMemory(Path file) {
		try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            Memory mem = new Memory();		// TODO improve mem to read right from channel...
            
            ByteBuffer bb = ByteBuffer.allocate(4096);

            int amt = ch.read(bb);
            
            while (amt != -1) {
            	bb.flip();
            	mem.write(bb);
            	bb.clear();
            	amt = ch.read(bb);
            }            
            
            mem.setPosition(0);
            
            return mem;
        } 
        catch (IOException x) {
        	Logger.error("Unabled to read file " + file + ", error: " + x);
			
			return null;
		} 
	}
	
	static public boolean isLegalFilename(String name) {
		if (StringUtil.isEmpty(name))
			return false;
		
		if (name.equals(".") || name.contains("..") || name.contains("*") || name.contains("\"") || name.contains("/") || name.contains("\\")
				 || name.contains("<") || name.contains(">") || name.contains(":") || name.contains("?") || name.contains("|"))
			return false;
		
		return true;
	}
	
	static public String toLegalFilename(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		name = name.replace("..", "_").replace("*", "_").replace("\"", "_").replace("/", "_");
		name = name.replace("\\", "_").replace("<", "_").replace(">", "_").replace(":", "_").replace("?", "_").replace("|", "_");
		
		return name;
	}
	
    public static void closeQuietly(Closeable... closeables) {
		if (closeables == null) 
			return;
		
		for (Closeable closeable : closeables) { 
			try {
				if (closeable != null) 
					closeable.close();
			} 
			catch (IOException x) {
				// ignore
			}
		}
    }
	
    public static void closeQuietly(AutoCloseable... closeables) {
		if (closeables == null) 
			return;
		
		for (AutoCloseable closeable : closeables) { 
			try {
				if (closeable != null) 
					closeable.close();
			} 
			catch (Exception x) {
				// ignore
			}
		}
    }

    /**
     * Returns an Iterator for the lines in an <code>InputStream</code>, using
     * the character encoding specified (or default encoding if null).
     * <p>
     * <code>LineIterator</code> holds a reference to the open
     * <code>InputStream</code> specified here. When you have finished with
     * the iterator you should close the stream to free internal resources.
     * This can be done by closing the stream directly, or by calling
     * {@link LineIterator#close()} or {@link LineIterator#closeQuietly(LineIterator)}.
     * <p>
     * The recommended usage pattern is:
     * <pre>
     * try {
     *   LineIterator it = IOUtils.lineIterator(stream, charset);
     *   while (it.hasNext()) {
     *     String line = it.nextLine();
     *     /// do something with line
     *   }
     * } finally {
     *   IOUtils.closeQuietly(stream);
     * }
     * </pre>
     *
     * @param input  the <code>InputStream</code> to read from, not null
     * @param encoding  the encoding to use, null means platform default
     * @return an Iterator of the lines in the reader, never null
     * @throws IllegalArgumentException if the input is null
     * @throws IOException if an I/O error occurs, such as if the encoding is invalid
     * @since 2.3
     */
    public static LineIterator lineIterator(InputStream input, Charset encoding) throws IOException {
        return new LineIterator(new InputStreamReader(input, encoding));
    }
    
    public static long byteArrayToLong(byte[] b) {
        return (long) (b[0] & 0xff) << 56
        		| (long) (b[1] & 0xff) << 48 
        		| (long) (b[2] & 0xff) << 40 
        		| (long) (b[3] & 0xff) << 32 
        		| (long) (b[4] & 0xff) << 24 
        		| (long) (b[5] & 0xff) << 16 
        		| (long) (b[6] & 0xff) << 8 
        		| (long) (b[7] & 0xff);
    }

    public static byte[] longToByteArray(long a) {
        byte[] ret = new byte[8];
        
        ret[0] = (byte) ((a >> 56) & 0xff);
        ret[1] = (byte) ((a >> 48) & 0xff);
        ret[2] = (byte) ((a >> 40) & 0xff);
        ret[3] = (byte) ((a >> 32) & 0xff);
        ret[4] = (byte) ((a >> 24) & 0xff);
        ret[5] = (byte) ((a >> 16) & 0xff);   
        ret[6] = (byte) ((a >> 8) & 0xff);   
        ret[7] = (byte) (a & 0xff);
        
        return ret;
    }    
    
    public static int byteArrayToInt(byte[] b) {
        return  (b[0] & 0xff) << 24
        		| (b[1] & 0xff) << 16 
        		| (b[2] & 0xff) << 8 
        		| (b[3] & 0xff);
    }

    public static byte[] intToByteArray(int a) {
        byte[] ret = new byte[4];
        
        ret[0] = (byte) ((a >> 24) & 0xff);
        ret[1] = (byte) ((a >> 16) & 0xff);   
        ret[2] = (byte) ((a >> 8) & 0xff);   
        ret[3] = (byte) (a & 0xff);
        
        return ret;
    }    
 }
