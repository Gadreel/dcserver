package dcraft.filevault.work;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class IndexAllFilesWork extends IndexFilesCoreWork {
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Prep Sites List", this::prepSites))
				.withStep(indexSite = StateWorkStep.of("Index Site", this::doSite))
				.withStep(indexVault = StateWorkStep.of("Index Vault", this::doVault))
				.withStep(indexFolder = StateWorkStep.of("Index Folder", this::doFolder))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
}
