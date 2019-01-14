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
package dcraft.hub.ignite;

import dcraft.api.ApiSession;
import dcraft.custom.release.ServerHelper;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.Scanner;

public interface IInitializeDeploymentCli {
	void run(Scanner scan, Path template, XElement settings, ServerHelper server, XElement deployment, ApiSession client) throws Exception;
}
