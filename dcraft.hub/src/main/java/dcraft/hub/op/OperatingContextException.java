package dcraft.hub.op;

import dcraft.log.Logger;

public class OperatingContextException extends Exception {
	private static final long serialVersionUID = 833604971652609824L;

	public OperatingContextException(String msg) {
		super(msg);
		
		Logger.error(msg);
	}
}
