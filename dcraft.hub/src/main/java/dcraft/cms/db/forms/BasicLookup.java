package dcraft.cms.db.forms;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.DbUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.interchange.slack.SlackUtil;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class BasicLookup implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

		SelectFields select = new SelectFields()
				.with("Id")
				.with("dcmTitle", "Title")
				.with("dcmAlias", "Alias")
				.with("dcmEmail", "Email")
				.withReverseSubquery("CustomFields", "dcmProductCustomFields", "dcmBasicCustomForm", new SelectFields()
						.with("Id")
						.with("dcmPosition", "Position")
						.with("dcmFieldType", "Type")
						.with("dcmDataType", "DataType")
						.with("dcmLabel", "Label")
						.with("dcmLongLabel", "LongLabel")
						.with("dcmPlaceholder", "Placeholder")
						.with("dcmPattern", "Pattern")
						.with("dcmRequired", "Required")
						.with("dcmMaxLength", "MaxLength")
						.with("dcmHorizontal", "Horizontal")
						.with("dcmPrice", "Price")
						.withGroup("dcmOptionLabel", "Options", "Id", new SelectFields()
								.with("dcmOptionLabel", "Label")
								.with("dcmOptionValue", "Value")
								.with("dcmOptionPrice", "Price")
								.with("dcmOptionWeight", "Weight")
						)
				);
		
		String id = data.getFieldAsString("Id");
		
		if (StringUtil.isEmpty(id)) {
			String alias = data.getFieldAsString("Alias");
			
			id = Struct.objectToString(db.firstInIndex("dcmBasicCustomForm", "dcmAlias", alias, true));
		}
		
		RecordStruct result = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmBasicCustomForm", id, select);
		
		ListStruct fields = result.getFieldAsList("CustomFields");
		
		if (fields != null) {
			fields.sortRecords("Position", false);
			
			for (int i = 0; i < fields.size(); i++) {
				fields.getItemAsRecord(i).removeField("Position");
			}
		}
		
		RecordStruct custom = RecordStruct.record()
				.with("Controls", fields);
		
		result.with("CustomFields", custom);
		
		callback.returnValue(result);
	}
}
