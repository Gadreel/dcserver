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
package dcraft.stream;

import java.nio.file.Path;

import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;

public class StreamUtil {
	static public LocalStoreFile localFile(Path lpath) {
	    LocalStore drv = LocalStore.of(lpath.getParent());
	    return LocalStoreFile.of(drv, new CommonPath("/" + lpath.getFileName().toString()), false);
	}
	
	static public LocalStoreFile localFolder(Path lpath) {
	    LocalStore drv = LocalStore.of(lpath.getParent());
	    return LocalStoreFile.of(drv, new CommonPath("/" + lpath.getFileName().toString()), true);
	}
	
	static public LocalStore localDriver(Path lpath) {
	    return LocalStore.of(lpath.getParent());
	}
	
	static public LocalStoreFile tempFile(String ext) {
        CommonPath path = new CommonPath("/" + (StringUtil.isNotEmpty(ext) ? FileUtil.randomFilename(ext) : FileUtil.randomFilename()));
        
        Path tfpath = FileUtil.allocateTempFolder();

        LocalStore drv = LocalStore.of(tfpath);
        drv.isTemp(true);
        
        return LocalStoreFile.of(drv, path, false);
	}
	
	static public LocalStoreFile tempFolder() {
        Path path = FileUtil.allocateTempFolder();

        LocalStore drv = LocalStore.of(path);
        drv.isTemp(true);
        
        return LocalStoreFile.of(drv, CommonPath.ROOT, true);
	}

}
