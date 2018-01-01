package dcraft.task;

import dcraft.hub.op.OperatingContextException;

/*
 */
public class ControlWork {
	static public DieOnError dieOnError(String msg) {
		DieOnError w = new DieOnError();
		w.msg = msg;
		return w;
	}
	
	static public DieOnError dieOnError(long code, String msg) {
		DieOnError w = new DieOnError();
		w.code = code;
		w.msg = msg;
		return w;
	}
	
	
	static public class DieOnError implements IWork {
		protected String msg = null;
		protected long code = -1;
		
		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			if (taskctx.hasExitErrors()) {
				if (this.code == -1)
					taskctx.kill(this.msg);
				else
					taskctx.kill(this.code, this.msg);
			}
			else {
				taskctx.returnResult();	// keep same result
			}
		}
	}
}
