package dcraft.hub.op;

public class OperationConstants {
	static public final OperationEvent COMPLETED = new OperationEvent();  
	static public final OperationEvent LOG = new OperationEvent();  
	static public final OperationEvent PROGRESS = new OperationEvent();  
	static public final OperationEvent TOUCH = new OperationEvent();  

	static public final Object PROGRESS_AMOUNT = new Object();
	static public final Object PROGRESS_STEP = new Object();
	static public final Object PROGRESS_MESSAGE = new Object();
	
	static public final OperationEvent PREP_TASK = new OperationEvent();  
	static public final OperationEvent START_TASK = new OperationEvent();  
	static public final OperationEvent STOP_TASK = new OperationEvent();
}
