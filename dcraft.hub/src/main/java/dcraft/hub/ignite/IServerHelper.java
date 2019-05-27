package dcraft.hub.ignite;

import dcraft.xml.XElement;

public interface IServerHelper {
	boolean init();
	boolean init(String deployment);
	XElement getMatrix();
}
