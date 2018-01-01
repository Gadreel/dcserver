package dcraft.schema;

import dcraft.hub.ResourceHub;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource.OpInfo;
import dcraft.struct.CompositeStruct;
import dcraft.struct.Struct;

public class SchemaHub {
	static public DataType getTypeOrError(String type) {
		SchemaResource schema = ResourceHub.getResources().getSchema();

		if (schema == null) {
			Logger.error("Missing schema tier");
			return null;
		}
		
		DataType dt = schema.getType(type);
		
		if (dt == null) {
			Logger.errorTr(436);		
			return null;
		}
	
		return dt;
	}

	static public DataType getType(String type) {
		SchemaResource schema = ResourceHub.getResources().getSchema();

		if (schema == null)
			return null;

		DataType dt = schema.getType(type);

		if (dt == null)
			return null;

		return dt;
	}

	static public OpInfo getServiceOpInfo(String service, String feature, String op) {
		SchemaResource schema = ResourceHub.getResources().getSchema();

		if (schema == null) 
			return null;
		
		return schema.getServiceOp(service, feature, op);
	}
	
	/**
	 * For a given structure, validate that it conforms to a given schema type
	 * 
	 * @param data structure to validate
	 * @param type schema name of type
	 * @return log of validation attempt
	 */
	static public boolean validateType(Struct data, String type) {
		SchemaResource schema = ResourceHub.getResources().getSchema();

		if (schema == null) {
			Logger.error("Missing schema level");
			return false;
		}
		
		DataType dt = schema.getType(type);
		
		if (dt == null) {
			Logger.errorTr(436);		
			return false;
		}
	
		return dt.validate(data);
	}
	
	static public Struct normalizeValidateType(Struct data, String type){
		SchemaResource schema = ResourceHub.getResources().getSchema();

		if (schema == null) {
			Logger.error("Missing schema level");
			return null;
		}
		
		DataType dt = schema.getType(type);
		
		if (dt == null) {
			Logger.errorTr(436);		
			return null;
		}
	
		Struct o = dt.normalizeValidate(data);
		
		if (o == null) {
			Logger.error("Unable to validate and normalize data.");
			return null;
		}
		
		return o;
	}
}
