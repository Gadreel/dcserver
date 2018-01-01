package dcraft.task;

import java.util.function.Function;

import dcraft.util.FunctionWithExceptions;

public class StateWorkStep {
	// special case of WorkStep - do not add it to your step list, it just means go to the next step in my list
	// and can be used as a return value
	static public final StateWorkStep NEXT = new StateWorkStep();
	// repeat this step, same as .resume
	static public final StateWorkStep REPEAT = new StateWorkStep();
	// special case of WorkStep - do not add it to your step list
	// means wait, I'll tell you when to transition
	static public final StateWorkStep WAIT = new StateWorkStep();
	// means stop, do nothing more
	static public final StateWorkStep STOP = new StateWorkStep();

    /**
     * .map(rethrowFunction(name -> Class.forName(name))) or .map(rethrowFunction(Class::forName))
     */
    public static <E extends Exception> StateWorkStep of(String title, FunctionWithExceptions<TaskContext, StateWorkStep, E> function) throws E  {
		StateWorkStep step = new StateWorkStep();
		
		step.title = title;
		step.method = t -> {
            try {
                return function.apply(t);
            }
            catch (Exception x) {
                throwActualException(x);
                return StateWorkStep.STOP;
            }
        };
        
		return step;
    }
    
    public static <E extends Exception> StateWorkStep of(String title, StateWork parent, IWork work) throws E {
		StateWorkStep step = new StateWorkStep();
	
		step.title = title;
		step.method = t -> {
			try {
				return parent.chainThenNext(t, work);
			}
			catch (Exception x) {
				throwActualException(x);
				return StateWorkStep.STOP;
			}
		};
	
		return step;
	}

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void throwActualException(Exception exception) throws E {
        throw (E) exception;
    }
	
	protected String title = null;
	protected Function<TaskContext, StateWorkStep> method = null;
	
	public String getTitle() {
		return this.title;
	}
	
	public StateWorkStep runStep(TaskContext run) {
		return this.method.apply(run);
	}
}