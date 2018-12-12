package dcraft.stream;

import dcraft.struct.RecordStruct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StreamFragment extends RecordStruct {
	static public StreamFragment of(IStream... steps) {
		StreamFragment frag = new StreamFragment();
		
		return frag.withAppend(steps);
	}
	
	protected List<IStream> steps = new ArrayList<>();
	
	public List<IStream> getSteps() {
		return this.steps;
	}
	
	protected StreamFragment() {
	}
	
	public StreamFragment withPrepend(StreamFragment fragment) {
		if (fragment == null)
			return this;
		
		return this.withPrepend(fragment.getSteps());
	}
	
	public StreamFragment withPrepend(Collection<IStream> steps) {
		this.steps.addAll(0, steps);
		
		return this;
	}
	
	public StreamFragment withPrepend(IStream... steps) {
		for (int i = steps.length - 1; i >= 0; i--)
			this.steps.add(0, steps[i]);
		
		return this;
	}
	
	public StreamFragment withAppend(StreamFragment fragment) {
		if (fragment == null)
			return this;
		
		return this.withAppend(fragment.getSteps());
	}
	
	public StreamFragment withAppend(Collection<IStream> steps) {
		this.steps.addAll(steps);
		
		return this;
	}
	
	public StreamFragment withAppend(IStream... steps) {
		for (IStream strm : steps)
			this.steps.add(strm);
		
		return this;
	}
	
	public StreamFragment then(IStream step) {
		if (step == null)
			return this;
		
		this.steps.add(step);
		return this;
	}
}
