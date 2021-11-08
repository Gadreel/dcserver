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
package dcraft.struct.scalar;

import dcraft.hub.time.BigDateTime;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.schema.SchemaHub;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.Arrays;

public class AnyStruct extends ScalarStruct {
	static public AnyStruct of(Object v) {
		AnyStruct struct = new AnyStruct();
		struct.value = v;
		return struct;
	}

	protected Object value = null;

	@Override
	public DataType getType() {
		if (this.explicitType != null)
			return super.getType();
		
		return SchemaHub.getTypeOrError("Any");
	}

	public AnyStruct() { }

	@Override
	public Object getGenericValue() {
		return this.value;
	}
	
	@Override
	public void adaptValue(Object v) {
		this.value = v;
	}

	public Object getValue() {
		return this.value; 
	}
	
	public void setValue(Object v) { 
		this.value = v; 
	}

    @Override
    protected void doCopy(BaseStruct n) {
    	super.doCopy(n);
    	
    	AnyStruct nn = (AnyStruct)n;
    	nn.value = this.value;		// TODO clone?
    }
    
	@Override
	public BaseStruct deepCopy() {
		AnyStruct cp = new AnyStruct();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean equals(Object obj) {
		return (AnyStruct.comparison(this, obj) == 0);
	}

	@Override
	public int compareTo(Object y) {
		return AnyStruct.comparison(this, y);
	}
	
	@Override
	public boolean isEmpty() {
		return (this.value == null);
	}
	
	@Override
	public boolean isNull() {
		return (this.value == null);
	}

	@Override
	public int hashCode() {
		return (this.value == null) ? 0 : this.value.hashCode();
	}
	
	@Override
	public String toString() {
		return (this.value == null) ? "null" : this.value.toString();
	}

	@Override
	public Object toInternalValue(RootType t) {
		return this.value;
	}
	
	public static int comparison(Object x, Object y) {
		// TODO convert to "inner value"
		//x = Struct.objectToStruct(x);
		//y = Struct.objectToStruct(y);
		
		if ((y == null) && (x == null))
			return 0;

		if (y == null)
			return 1;

		if (x == null)
			return -1;

		return 0;   // TODO compare...
	}
	
	/**
	 * A way to select a child or sub child structure similar to XPath but lightweight.
	 * Can select composites and scalars.  Use a . or / delimiter.
	 *
	 * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
	 * 4th toy in this person's Toys list.
	 *
	 * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
	 *
	 * @param path string holding the path to select
	 * @return selected structure if any, otherwise null
	 */
	@Override
	public BaseStruct select(String path) {
		return this.select(PathPart.parse(path));
	}
	
	/** _Tr
	 * A way to select a child or sub child structure similar to XPath but lightweight.
	 * Can select composites and scalars.  Use a . or / delimiter.
	 *
	 * For example: "Toys.3.Name" called on "Person" Record means return the (Struct) name of the
	 * 4th toy in this person's Toys list.
	 *
	 * Cannot go up levels, or back to root.  Do not start with a dot or slash as in ".People".
	 *
	 * @param path parts of the path holding a list index or a field name
	 * @return selected structure if any, otherwise null
	 */
	@Override
	public BaseStruct select(PathPart... path) {
		if (this.value instanceof IPartSelector)
			return ((IPartSelector)this.value).select(path);
		
		return null;
	}
}
