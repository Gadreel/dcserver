package dcraft.example.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.stream.StreamWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

/**
 */
public class ChainInfoWork implements IWork {
	static public ChainInfoWork of(String label, int runs) {
		ChainInfoWork w = new ChainInfoWork();
		w.label = label;
		w.runcnt = runs;
		w.result = Struct.objectToStruct(label);
		return w;
	}
	
	static public ChainInfoWork of(String label, int runs, BaseStruct result) {
		ChainInfoWork w = new ChainInfoWork();
		w.label = label;
		w.runcnt = runs;
		w.result = result;
		return w;
	}
	
	protected int runcnt = 1;
	protected int currcnt = 0;
	protected String label = null;
	protected BaseStruct result = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		this.currcnt++;
		
		System.out.println("Run #" + this.currcnt + " of " + this.label + " from: " + taskctx.getParams());
		
		if (this.currcnt >= this.runcnt)
			taskctx.returnValue(this.result);
		else
			taskctx.resume();
	}
}
