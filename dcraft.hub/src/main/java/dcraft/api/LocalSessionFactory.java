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
package dcraft.api;

import dcraft.xml.XElement;

public class LocalSessionFactory implements IApiSessionFactory {
	protected XElement config = null;

	@Override
	public void init(XElement config) {
		this.config = config;
	}

	@Override
	public ApiSession create() {
		return this.create(this.config);
	}
	
	@Override
	public ApiSession create(XElement config) {
		/* TODO restore
		ApiSession sess = new LocalSession();
		sess.init(config);
		return sess;
		*/
		
		return null;
	}
}
