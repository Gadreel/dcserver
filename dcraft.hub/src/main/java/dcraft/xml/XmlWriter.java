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
package dcraft.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import dcraft.util.Memory;
import dcraft.util.StringUtil;

/**
 * Utilities to help with Xml output.
 * 
 * @author Andy
 *
 */
public class XmlWriter {
	/**
	 * Write a xml node and all children to a file
	 * 
	 * @param xml node to write
	 * @param filename name of file to create/overwrite
	 */
	static public void writeToFile(XNode xml, String filename) {
		if (StringUtil.isEmpty(filename))
			return;
		
		XmlWriter.writeToFile(xml, Paths.get(filename));
	}
		
	/**
	 * Write a xml node and all children to a file
	 * 
	 * @param xml node to write
	 * @param dest file to create/overwrite
	 */
	static public void writeToFile(XNode xml, Path dest) {
		if ((xml == null) || (dest == null))
			return;

		try {
			// make sure the folder is there
			Path folder = dest.getParent();
			Files.createDirectories(folder);
		}
		catch (IOException x) {
			// ???
		}
		
		// TODO use more efficient approach than copy to memory first		
		Memory content = xml.toMemory(true);
		content.setPosition(0);
		
		try (OutputStream fos = Files.newOutputStream(dest)) {
			content.copyToStream(fos);
		}
		catch (IOException x) {
			// ???
		}
	}
}
