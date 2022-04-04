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
package dcraft.filestore.aws2;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.schema.SchemaHub;
import dcraft.struct.BaseStruct;

import java.util.List;

/**
 * Eventually we may shift to a "yield" approach, for now collects all files
 * 
 * @author andy
 *
 */
public class AwsStoreScanner extends FileCollection {
	static public AwsStoreScanner of(AwsStore driver, CommonPath storepath) {
		AwsStoreScanner scanner = new AwsStoreScanner();
		
		scanner.driver = driver;
		scanner.basePath = storepath;
		
		return scanner;
	}
	
	protected AwsStore driver = null;
	protected CommonPath basePath = null;
	
	public AwsStore getDriver() {
		return this.driver;
	}
	
	protected AwsStoreScanner() {
		this.withType(SchemaHub.getType("dcAwsStoreScanner"));
	}
	
	protected void collectAll(OperationOutcomeEmpty callback) throws OperatingContextException {
		// don't collect more than once
		if (this.collection != null) {
			callback.returnEmpty();
			return;
		}
		
		driver.getFolderListing(this.basePath, new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				AwsStoreScanner.this.collection = result;
				
				callback.returnEmpty();
			}
		});
	}
	
	@Override
	public void next(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		if (this.collection == null) {
			this.collectAll(new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					AwsStoreScanner.super.next(callback);
				}
			});
			
			return;
		}
		
		super.next(callback);		
	}
	
	@Override
	public void forEach(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		if (this.collection == null) {
			this.collectAll(new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					AwsStoreScanner.super.forEach(callback);
				}
			});
			
			return;
		}
		
		super.forEach(callback);
	}
		
    @Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	AwsStoreScanner nn = (AwsStoreScanner)n;
		nn.driver = this.driver;
    }
    
	@Override
	public AwsStoreScanner deepCopy() {
		AwsStoreScanner cp = new AwsStoreScanner();
		this.doCopy(cp);
		return cp;
	}
}
