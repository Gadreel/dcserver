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
package dcraft.struct;

import dcraft.hub.time.BigDateTime;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.task.IParentAwareWork;
import io.netty.buffer.ByteBuf;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.schema.DataType;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeOutput;
import dcraft.struct.builder.JsonMemoryBuilder;
import dcraft.struct.builder.JsonStreamBuilder;
import dcraft.struct.serial.CompositeToBufferBuilder;
import dcraft.util.Memory;
import dcraft.util.chars.Special;
import dcraft.xml.XElement;

/**
 * DivConq uses a specialized type system that provides type consistency across services 
 * (including web services), database fields and stored procedures, as well as scripting.
 * 
 * All scalars (including primitives) and composites (collections) are wrapped by some
 * subclass of Struct.  All composites are wrapped by a subclass of this class.  See
 * ListStruct and RecordStruct.
 * 
 *  TODO link to blog entries.
 * 
 * @author Andy
 *
 */
abstract public class CompositeStruct extends BaseStruct implements ICompositeOutput {
	public CompositeStruct() {
	}
	
	public CompositeStruct(DataType type) {
		super(type);
	}
	
	public BigDateTime selectAsBigDateTime(String name) {
		return Struct.objectToBigDateTime(this.select(name));
	}
	
	public BigDecimal selectAsDecimal(String name) {
		return Struct.objectToDecimal(this.select(name));
	}
	
	public BigDecimal selectAsDecimal(String name, BigDecimal defaultval) {
		BigDecimal x = Struct.objectToDecimal(this.select(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	public BigInteger selectAsBigInteger(String name) {
		return Struct.objectToBigInteger(this.select(name));
	}
	
	public BigInteger selectAsBigInteger(String name, BigInteger defaultval) {
		BigInteger x = Struct.objectToBigInteger(this.select(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	public Boolean selectAsBoolean(String name) {
		return Struct.objectToBoolean(this.select(name));
	}
	
	public boolean selectAsBooleanOrFalse(String name) {
		Boolean b = Struct.objectToBoolean(this.select(name));
		
		return (b == null) ? false : b.booleanValue();
	}
	
	public CompositeStruct selectAsComposite(String name) {
		return Struct.objectToComposite(this.select(name));
	}
	
	public ListStruct selectAsList(String name) {
		return Struct.objectToList(this.select(name));
	}
	
	public LocalDate selectAsDate(String name) {
		return Struct.objectToDate(this.select(name));
	}
	
	public LocalTime selectAsTime(String name) {
		return Struct.objectToTime(this.select(name));
	}
	
	public Long selectAsInteger(String name) {
		return Struct.objectToInteger(this.select(name));
	}
	
	public long selectAsInteger(String name, long defaultval) {
		Long x = Struct.objectToInteger(this.select(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	public Memory selectAsBinary(String name) {
		return Struct.objectToBinary(this.select(name));
	}
	
	public RecordStruct selectAsRecord(String name) {
		return Struct.objectToRecord(this.select(name));
	}
	
	public String selectAsString(String name) {
		return Struct.objectToString(this.select(name));
	}
	
	public String selectAsString(String name, String defaultval) {
		String x = Struct.objectToString(this.select(name));
		
		if (x == null)
			return defaultval;
		
		return x;
	}
	
	public ZonedDateTime selectAsDateTime(String name) {
		return Struct.objectToDateTime(this.select(name));
	}
	
	/**
	 * Does this collection have any items or fields
	 * 
	 * @return true if no items or fields
	 */
	abstract public boolean isEmpty();
	
	/**
	 * Remove all child fields or items.
	 */
	abstract public void clear();
	
	@Override
	public String toString() {
		try {
			JsonMemoryBuilder rb = new JsonMemoryBuilder();		
			this.toBuilder(rb);		
			return rb.getMemory().toString();
		}
		catch (Exception x) {
			//
		}
		
		return null;
	}
	
	// TODO there may be more efficient ways to do this, just a quick way for now
	@Override
	public boolean equals(Object obj) {
		if ((obj instanceof CompositeStruct) && this.toString().equals(((CompositeStruct)obj).toString()))
			return true;
			
		return super.equals(obj);
	}
	
	public String toPrettyString() {
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(os);
			JsonStreamBuilder rb = new JsonStreamBuilder(ps, true);		
			this.toBuilder(rb);		
			return os.toString("UTF-8");
		}
		catch (Exception x) {
			//
		}
		
		return null;
	}
	
	@Override
	public boolean isNull() {
		return false;
	}
	
	/**
	 * Convert the structure to Json and return in Memory (think StringBuilder in this usage).
	 *  
	 * @return Memory holding JSON representation of this structure and all children
	 * @throws BuilderStateException if the structure is invalid then this exception arises
	 */
	public Memory toMemory() throws BuilderStateException {		// TODO return funcresult
		JsonMemoryBuilder rb = new JsonMemoryBuilder();		
		this.toBuilder(rb);		
		return rb.getMemory();
	}
	
	public void toSerial(ByteBuf buf) throws BuilderStateException {		
		CompositeToBufferBuilder rb = new CompositeToBufferBuilder(buf);		
		this.toBuilder(rb);		
		rb.write(Special.End);
	}
	
	public void toSerialMemory(Memory res) throws BuilderStateException {
		ByteBuf buf = ApplicationHub.getBufferAllocator().heapBuffer(16 * 1024, 16 * 1024 * 1024);
		
		try {
			CompositeToBufferBuilder rb = new CompositeToBufferBuilder(buf);		
			this.toBuilder(rb);		
			rb.write(Special.End);
			
			res.write(buf.array(), buf.arrayOffset(), buf.readableBytes());
		}
		finally {
			buf.release();
		}
	}
	
	public Memory toSerialMemory() throws BuilderStateException {
		ByteBuf buf = ApplicationHub.getBufferAllocator().heapBuffer(16 * 1024, 16 * 1024 * 1024);
		
		try {
			CompositeToBufferBuilder rb = new CompositeToBufferBuilder(buf);		
			this.toBuilder(rb);		
			rb.write(Special.End);
			
			Memory res = new Memory(buf.readableBytes());
			res.write(buf.array(), buf.arrayOffset(), buf.readableBytes());
			
			return res;
		}
		finally {
			buf.release();
		}
	}
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Clear".equals(code.getName())) {
			this.clear();
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}
}
