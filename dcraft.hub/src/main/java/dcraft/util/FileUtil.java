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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import dcraft.filestore.CommonPath;
import dcraft.log.Logger;
import dcraft.util.io.LineIterator;

// see Hub, it clears the temp files
public class FileUtil {
	
	static public String randomFilename() {
		return RndUtil.nextUUId();
	}
	
	static public String randomFilename(String ext) {
		return RndUtil.nextUUId() + "." + ext;
	}
	
	static public Path allocateTempFile() {
		return FileUtil.allocateTempFile("tmp");
	}
	
	static public Path allocateTempFile(String ext) {
		try {
			Path temps = Paths.get("./temp");

			Files.createDirectories(temps);
			
			String fname = FileUtil.randomFilename(ext);
			
			return temps.resolve(fname);
		} 
		catch (IOException x) {
			Logger.error("Unable to create temp file: " + x);
			return null;
		}
	}
	
	static public Path allocateTempFolder() {
		try {
			Path temps = Paths.get("./temp/" + FileUtil.randomFilename());		

			Files.createDirectories(temps);
			
			return temps;
		} 
		catch (IOException x) {
			Logger.error("Unable to create temp folder: " + x);
			return null;
		}
	}
	
	static public void cleanupTemp() {
		File temps = new File("./temp");
		
		if (!temps.exists())
			return;
		  
		for(File next : temps.listFiles()) {
			if (next.isDirectory())
				continue;
			
			long time = System.currentTimeMillis();
			long modified = next.lastModified();
			  
			if(modified + (60 * 60 * 1000) > time)			// wait an hour 
				continue;
		      
			next.delete();
		}
	}
	
	static public Path replaceFileExtension(Path p, String ext) {
		String fname = p.getFileName().toString();
		
		if (fname == null)
			return null;
		
		int pos = fname.lastIndexOf('.');
		
		if (pos > -1)
			fname = fname.substring(0, pos);
		
		return p.getParent().resolve(fname + "." + ext);
	}
	
	static public String getFileExtension(Path p) {
		String fname = p.getFileName().toString();
		
		if (fname == null)
			return null;
		
		int pos = fname.lastIndexOf('.');
		
		if (pos == -1)
			return null;
		
		return fname.substring(pos + 1);
	}
	
	static public String getFileExtension(CommonPath p) {
		String fname = p.getFileName();
		
		if (fname == null)
			return null;
		
		int pos = fname.lastIndexOf('.');
		
		if (pos == -1)
			return null;
		
		return fname.substring(pos + 1);
	}
	
	static public String getFileExtension(String fname) {
		if (fname == null)
			return null;
		
		int pos = fname.lastIndexOf('.');
		
		if (pos == -1)
			return null;
		
		return fname.substring(pos + 1);
	}

	// path to folder creates a temp file in folder
	// writes 64KB blocks 
	static public Path generateTestFile(Path path, String ext, int minblocks, int maxblocks) {
		if (!Files.isDirectory(path)) 
			return null;
		
		try {
			if (!Files.exists(path))
				Files.createDirectories(path);
		
			String fname = FileUtil.randomFilename(ext);
			path = path.resolve(fname);
	
			int blocks = minblocks + RndUtil.testrnd.nextInt(maxblocks - minblocks);
			
			// 64KB block
			byte[] buffer = new byte[64 * 1024];
			
			for (int i = 0; i < buffer.length / 256; i++)
				for (int j = 0; j < 256; j++)
					buffer[(i * 256) + j] = (byte)j;
			
			ByteBuffer bb = ByteBuffer.wrap(buffer);
			
			try (FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
				while (blocks > 0) { 
					fc.write(bb);
					blocks--;
					bb.position(0);		// so we can write again
				}
			}
			
			return path;
		} 
		catch (IOException x) {
			Logger.error("Unable to generate test file: " + x);
			
			return null; 
		}
	}
	
	static public boolean confirmOrCreateDir(Path path) {
		if (path == null) {
			Logger.error("Path is null");
			return false;
		}
		
        if (Files.exists(path)) {
            if (! Files.isDirectory(path)) {
            	Logger.error(path + " exists and is not a directory. Unable to create directory.");
            	return false;
            }
            
            return true;
        } 
        
        try {
        	Files.createDirectories(path);
        	
        	return true;
        }
        catch (FileAlreadyExistsException x) {
        	// someone else created a file under our noses
            if (! Files.isDirectory(path)) {
            	Logger.error(path + " exists and is not a directory. Unable to create directory.");
                
                return false;
            }
            
            // created by someone else, but it is there and that is fine
            return true;
        }
        catch (Exception x) {
        	Logger.error("Unable to create directory " + path + ", error: " + x);
            
            return false;
        }
    }
	
    public static void deleteDirectoryContent(Path directory, String... except) {
		if (Files.exists(directory) && Files.isDirectory(directory)) {
			try (Stream<Path> strm = Files.list(directory)) {
				strm.forEach(file -> {
					for (String exception : except) {
						if (!file.getFileName().toString().equals(exception)) {
							if (Files.isDirectory(file))
								deleteDirectory(file);
							else
								try {
									Files.delete(file);
								} 
								catch (Exception x) {
									Logger.error("Unable to delete file: " + x);
								}
						}
					}
				});
			}
			catch (IOException x) {
				Logger.error("Unable to list directory contents: " + x);
			}
		}
    }
    
	// TODO add secure delete option - JNA?
	// TODO add delete followup feature someday
	
    // true if not present or deleted
    public static boolean deleteDirectory(Path directory) {
		if (directory == null) {
			Logger.error("Path is null");
			return false;
		}
		
        if (Files.notExists(directory)) 
            return true;
        
		if (! Files.isDirectory(directory)) {
			Logger.error("Path is not a folder: " + directory);
			return false;
		}

		try {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path sfile, BasicFileAttributes attrs) throws IOException {
					Files.delete(sfile);
					
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult postVisitDirectory(Path sfile, IOException x1) throws IOException {
					if (x1 != null)
						throw x1;
					
					Files.delete(sfile);
					
					return FileVisitResult.CONTINUE;
				}
			});
			
			return true;
		}
		catch (IOException x) {
			Logger.error("Unable to delete directory: " + directory + ", error: " + x);
			return false;
		}
    }
	
    // returns false if there are no visible files or folders in the folder (files starting with "." count as hidden)
    static public boolean isDirEmpty(Path path) {
		try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(path)) {
			Iterator<Path> pathIterator = dirStream.iterator();
			
			if (! pathIterator.hasNext())
				return true;
			
			Path entry = pathIterator.next();
			
			while (entry != null) {
				if (! entry.getFileName().toString().startsWith("."))
					return false;
				
				if (! pathIterator.hasNext())
					break;
				
				entry = pathIterator.next();
			}
			
			return true;
		}
		catch (IOException x) {
			return true;
		}
	}
	
    /**
     * Returns an Iterator for the lines in a <code>File</code>.
     * <p>
     * This method opens an <code>InputStream</code> for the file.
     * When you have finished with the iterator you should close the stream
     * to free internal resources. This can be done by calling the
     * {@link LineIterator#close()} or
     * {@link LineIterator#closeQuietly(LineIterator)} method.
     * <p>
     * The recommended usage pattern is:
     * <pre>
     * LineIterator it = FileUtils.lineIterator(file, "UTF-8");
     * try {
     *   while (it.hasNext()) {
     *     String line = it.nextLine();
     *     /// do something with line
     *   }
     * } finally {
     *   LineIterator.closeQuietly(iterator);
     * }
     * </pre>
     * <p>
     * If an exception occurs during the creation of the iterator, the
     * underlying stream is closed.
     *
     * @param file  the file to open for input, must not be {@code null}
     * @param encoding  the encoding to use, {@code null} means platform default
     * @return an Iterator of the lines in the file, never {@code null}
     * @throws IOException in case of an I/O error (file closed)
     * @since 1.2
     */
    public static LineIterator lineIterator(File file, String encoding) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return IOUtil.lineIterator(in, Charset.forName(encoding));
        } 
        catch (Exception ex) {
            throw ex;
        }
    }

    /**
     * Returns an Iterator for the lines in a <code>File</code> using the default encoding for the VM.
     *
     * @param file  the file to open for input, must not be {@code null}
     * @return an Iterator of the lines in the file, never {@code null}
     * @throws IOException in case of an I/O error (file closed)
     * @since 1.3
     * @see #lineIterator(File, String)
     */
    public static LineIterator lineIterator(final File file) throws IOException {
        return lineIterator(file, "UTF-8");
    }
    
    public static long copyFileTree(Path source, Path target) {
    	return copyFileTree(source, target, null);
    }
    
    // counts files and folders, returns number completed even if errored out
    public static long copyFileTree(Path source, Path target, Predicate<Path> filter) {
    	AtomicLong cnt = new AtomicLong();
    	
        try {
        	if (Files.notExists(target)) 
        		Files.createDirectories(target);

			Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
			        new SimpleFileVisitor<Path>() {
			            @Override
			            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
			                throws IOException
			            {
			                Path targetdir = target.resolve(source.relativize(dir));
			                
			                if (Files.notExists(targetdir)) {
			                	Files.createDirectories(targetdir);
							}
							else if (! Files.isDirectory(targetdir)) {
			                	// move overrides current content
								Files.delete(targetdir);
								Files.createDirectories(targetdir);
							}
			                
			                return FileVisitResult.CONTINUE;
			            }
			            
			            @Override
			            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
			                throws IOException
			            {
			            	if (file.endsWith(".DS_Store"))
				                return FileVisitResult.CONTINUE;
			            		
			            	if ((filter == null) || filter.test(file)) {
			            		Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
			            		cnt.incrementAndGet();
			            	}
			            	
			                return FileVisitResult.CONTINUE;
			            }
			        });
		} 
        catch (IOException x) {
			Logger.error("Error copying file tree: " + x);
		}
        
        return cnt.get();
    }
	
	// counts files and folders, returns number completed even if errored out
	public static long moveFileTree(Path source, Path target, BiPredicate<Path, Path> filter) {
		AtomicLong cnt = new AtomicLong();
		
		try {
			if (Files.notExists(target))
				Files.createDirectories(target);
			
			Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
					new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
								throws IOException
						{
							Path targetdir = target.resolve(source.relativize(dir));
							
							try {
								Files.copy(dir, targetdir, StandardCopyOption.COPY_ATTRIBUTES);
								cnt.incrementAndGet();
							}
							catch (FileAlreadyExistsException x) {
								if (! Files.isDirectory(targetdir))
									throw x;
							}
							
							return FileVisitResult.CONTINUE;
						}
						
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
								throws IOException
						{
							if (file.endsWith(".DS_Store"))
								return FileVisitResult.CONTINUE;
							
							Path dest = target.resolve(source.relativize(file));
							
							if ((filter == null) || filter.test(file, dest)) {
								Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
								cnt.incrementAndGet();
							}
							
							return FileVisitResult.CONTINUE;
						}
					});
		}
		catch (IOException x) {
			Logger.error("Error copying file tree: " + x);
		}
		
		return cnt.get();
	}
 
	public static void moveFile(Path source, Path dest) {
		try {
			if (source.getFileName().toString().equals(".DS_Store"))
				return;

			if (Files.notExists(dest.getParent()))
				Files.createDirectories(dest.getParent());

			Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException x) {
			Logger.error("Error copying file: " + x);
		}
	}

	public static void moveFolder(Path source, Path dest) {
		try {
			if (Files.notExists(dest))
				Files.createDirectories(dest);

			Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException x) {
			Logger.error("Error copying file: " + x);
		}
	}

	static public String formatFileSize(long size) {
	    if(size <= 0) 
	    	return "0";
	    
	    int exp = (int) (Math.log10(size) / Math.log10(1024));
	    
	    String f = new DecimalFormat("#,##0.#").format(size / Math.pow(1024, exp)); 
		
	    return f  + " " + (exp == 0 ? "bytes": "KMGTPEZY".charAt(exp - 1) + "B");
	}
    
    static public long parseFileSize(String size) {
		Long x = StringUtil.parseLeadingInt(size);
		
		if (x == null)
			return 0;
		
		size = size.toLowerCase();
		
		if (size.endsWith("kb"))
			x *= 1024;
		else if (size.endsWith("mb"))
			x *= 1024 * 1024;
		else if (size.endsWith("gb"))
			x *= 1024 * 1024 * 1024;
		
		return x;
    }

    static public Path getAppPath() {
    	return Path.of(System.getProperty("user.dir"));
	}
 }
