package dcraft.task;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.util.StringUtil;

public class ContextShimWork extends ChainWork {
	static public ContextShimWork ofScript(CommonPath script) {
		ContextShimWork shimWork = new ContextShimWork();
		shimWork.script = script;
		return shimWork;
	}

	static public ContextShimWork ofClass(String className) {
		ContextShimWork shimWork = new ContextShimWork();
		shimWork.className = className;
		return shimWork;
	}

	protected CommonPath script = null;
	protected String className = null;

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		if (this.script != null) {
			Script s = Script.of(this.script);

			if (s != null) {
				this.then(s.toWork());
			}
		}
		else if (StringUtil.isNotEmpty(this.className)) {
			IWork w = (IWork) taskctx.getResources().getClassLoader().getInstance(this.className);

			if (w != null) {
				this.then(w);
			}
		}

		super.init(taskctx);
	}
}
