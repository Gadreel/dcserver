package dcraft.session;

import dcraft.hub.op.UserContext;

public interface ISessionAdapter {
	  void kill();
	  void userChanged(UserContext user);
	  boolean isAlive();
}
