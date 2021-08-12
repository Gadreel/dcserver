package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;

public class LoadSelf implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		SelectFields selectFields = SelectFields.select()
				.with("dcUsername", "Username")
				.with("dcFirstName", "FirstName")
				.with("dcLastName", "LastName")
				.with("dcEmail", "Email")
				.with("dcBackupEmail", "BackupEmail")
				.with("dcPhone", "Phone")
				.with("dcAddress", "Address")
				.with("dcAddress2", "Address2")
				.with("dcCity", "City")
				.with("dcState", "State")
				.with("dcZip", "Zip")
				.with("dcNotices", "Notices")
				.withAs("DOB", "dcDOB")
				.withAs("DisplayName", "dcDisplayName")
				.withAs("Intro", "dcIntro")
				.withAs("ImageName", "dcImageName")
				.withAs("AltAddresses", "dcAltAddresses")
				.withAs("AltPhones", "dcAltPhones")
				.withAs("PrimaryLanguage", "dcPrimaryLanguage")
				.withAs("OtherLanguages", "dcOtherLanguages")
				.withAs("Gender", "dcGender")
				.withAs("Pronouns", "dcPronouns")
				.withAs("EducationMax", "dcEducationMax")
				.withAs("Ethnicity", "dcEthnicity")
				.withAs("EthnicityNote", "dcEthnicityNote");

		// TODO add chrono, locale

		callback.returnValue(
				TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcUser", OperationContext.getOrThrow().getUserContext().getUserId(), selectFields)
		);
	}
}
