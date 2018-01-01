/* ************************************************************************
#
#  DivConq
#
#  http://divconq.com/
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
package dcraft.hub.resource.lib;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;

public class JarLibLoader extends LibLoader {
	public JarLibLoader(Path path) {
		super(path.getFileName().toString());
		
		try (InputStream theFile = Files.newInputStream(path, StandardOpenOption.READ)) {
	        try (JarArchiveInputStream stream = new JarArchiveInputStream(theFile)) {
				JarArchiveEntry entry = stream.getNextJarEntry();
		
				while (entry != null) {
					if (!entry.isDirectory()) {
						//if (entry.getName().endsWith("Container.class"))
						//	System.out.println("at cont");
						
						int esize = (int) entry.getSize();
						
						if (esize > 0) {
							int eleft = esize;
							byte[] buff = new byte[esize];
							int offset = 0;
							
							while (offset < esize) {
								int d = stream.read(buff, offset, eleft);
								offset += d;
								eleft -= d;
							}
							
							this.entries.put("/" + entry.getName(), buff);
						}
					}
			
					entry = stream.getNextJarEntry();
				}
			}
		}
        catch (Exception x) {
        	// TODO logging
        	System.out.println(x);
        }
	}
}
