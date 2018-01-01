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
package dcraft.filestore.local;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.function.BiPredicate;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.struct.Struct;

/**
 * Eventually we may shift to a "yield" approach, for now collects all files
 * 
 * @author andy
 *
 */
public class LocalStoreScanner extends FileCollection {
	static public LocalStoreScanner of(LocalStore driver, CommonPath storepath) {
		LocalStoreScanner scanner = new LocalStoreScanner();
		
		scanner.driver = driver;
		scanner.basePath = storepath;
		
		return scanner;
	}
	
	protected LocalStore driver = null;
	protected CommonPath basePath = null;
	
	public LocalStore getDriver() {
		return this.driver;
	}
	
	protected LocalStoreScanner() {
		this.withType(SchemaHub.getType("dcLocalStoreScanner"));
	}
	
	public void collectAll() {
		// don't collect more than once
		if (this.collection != null)
			return;
		
		this.collection = new ArrayList<>();
		
		// TODO support this.filter - more effecient
		// TODO support filters/sorting/etc
		
		Path folder = this.driver.resolvePath(this.basePath);
		
		// collect all for now...may be more efficient later, perhaps load 10k at a time:
		/*
			Path baseDir = Paths.get("path/to/dir");
			
			List<Path> tenFirstEntries;
			
			BiPredicate<Path, BasicFileAttributes> predicate = (path, attrs)
				-> attrs.isRegularFile() && path.getFileName().endsWith(".processed");
			
			try (
				Stream<Path> stream = Files.find(baseDir, 1, predicate);
			) {
				tenFirstEntries = stream.limit(10L).collect(Collectors.toList());
			}
		 */

		try {
			Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path sfolder, BasicFileAttributes attrs) throws IOException {
					if (!sfolder.equals(folder))
						LocalStoreScanner.this.collection.add(LocalStoreFile.of(LocalStoreScanner.this.driver, sfolder));
					
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path sfile, BasicFileAttributes attrs) throws IOException {
					LocalStoreScanner.this.collection.add(LocalStoreFile.of(LocalStoreScanner.this.driver, sfile));
					
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException x) {
			Logger.error("Unable to walk directory: " + folder + ", error: " + x);
		}
	}
	
	public void collect(BiPredicate<Path,BasicFileAttributes> filter) {
		// don't collect more than once
		if (this.collection != null)
			return;
		
		this.collection = new ArrayList<>();
		
		Path folder = this.driver.resolvePath(this.basePath);
		
		// collect all for now...may be more efficient later

		try {
			Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path sfolder, BasicFileAttributes attrs) throws IOException {
					if ((filter != null) && !filter.test(sfolder, attrs))
						return FileVisitResult.CONTINUE;
					
					if (!sfolder.equals(folder))
						LocalStoreScanner.this.collection.add(LocalStoreFile.of(LocalStoreScanner.this.driver, sfolder));
					
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path sfile, BasicFileAttributes attrs) throws IOException {
					if ((filter != null) && !filter.test(sfile, attrs))
						return FileVisitResult.CONTINUE;
					
					LocalStoreScanner.this.collection.add(LocalStoreFile.of(LocalStoreScanner.this.driver, sfile));
					
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException x) {
			Logger.error("Unable to walk directory: " + folder + ", error: " + x);
		}
	}
	
	@Override
	public void next(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		if (this.collection == null)
			this.collectAll();
		
		super.next(callback);		
	}
	
	@Override
	public void forEach(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		if (this.collection == null)
			this.collectAll();
		
		super.forEach(callback);
	}
		
    @Override
    protected void doCopy(Struct n) {
    	super.doCopy(n);
    	
    	LocalStoreScanner nn = (LocalStoreScanner)n;
		nn.driver = this.driver;
    }
    
	@Override
	public LocalStoreScanner deepCopy() {
		LocalStoreScanner cp = new LocalStoreScanner();
		this.doCopy(cp);
		return cp;
	}
}
