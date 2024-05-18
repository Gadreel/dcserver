package dcraft.service;

import dcraft.db.DatabaseException;
import dcraft.db.DbServiceRequest;
import dcraft.db.IConnectionManager;
import dcraft.db.util.DbUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.schema.DbProc;
import dcraft.schema.SchemaHub;
import dcraft.schema.SchemaResource;
import dcraft.schema.ServiceSchema;
import dcraft.script.Script;
import dcraft.struct.BaseStruct;
import dcraft.struct.CompositeParser;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.ChainWork;
import dcraft.task.TaskHub;
import dcraft.util.json3.JsonParser;
import dcraft.xml.XElement;
import dcraft.xml.XmlParser;
import dcraft.xml.XmlReader;

import java.nio.file.Path;

/**
 */
public class StandardService implements IService {
	static protected CommonPath SERVICE_PATH = CommonPath.from("/services");

	/*
	protected String name = null;
	protected ResourceTier tier = null;
	protected XElement config = null;
	 */

	protected boolean enabled = false;

	@Override
	public String getName() {
		return null;  // this.name;
	}
	
	@Override
	public boolean isEnabled() {
		return this.enabled;
	}
	
	@Override
	public void init(String name, XElement config, ResourceTier tier) {
		//this.name = name;
		//this.config = config;
		//this.tier = tier;	// creates a circular reference, but will be found/resolved in config reload
	}

	protected CommonPath toServiceFolder(CommonPath path) {
		if (path == null)
			return null;

		// add .v to end of service op name
		path = path.getParent().resolve(path.getFileName() + ".v");

		return SERVICE_PATH.resolve(path);
	}

	@Override
	public SchemaResource.OpInfo lookupOpInfo(ServiceRequest request) throws OperatingContextException {
		CommonPath folder = this.toServiceFolder(request.getPath());

		if (folder == null)
			return null;

		Path schemaPath = ResourceHub.getResources().getScripts().findScript(folder.resolve("schema.xml"));

		Path securityPath = ResourceHub.getResources().getScripts().findScript(folder.resolve("access.json"));

		// security can be in the parent
		if (securityPath == null)
			securityPath = ResourceHub.getResources().getScripts().findScript(folder.getParent().resolve("access.json"));

		if ((schemaPath != null) && (securityPath != null)) {
			XElement opel = XmlReader.loadFile(schemaPath, false, true);

			RecordStruct access = Struct.objectToRecord(CompositeParser.parseJson(securityPath));

			if ((opel != null) && (access != null))
				return ResourceHub.getResources().getSchema().loadIsloatedInfo(request.getPath(), access, opel);
		}

		return null;
	}

	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		CommonPath folder = this.toServiceFolder(request.getPath());

		if (folder == null)
			return false;

		Path scriptPath = ResourceHub.getResources().getScripts().findScript(folder.resolve("code.dcs.xml"));

		OperationContext ctx = OperationContext.getOrThrow();

		if (scriptPath != null) {
			Script script = Script.of(scriptPath);

			if (script != null) {
				ctx.addVariable("Data", request.getData());

				TaskHub.submit(
						ChainWork.of(taskctx -> {
							//System.out.println("before");

							taskctx.returnEmpty();
						})
								.then(script.toWork())
								.then(taskctx -> {
									//System.out.println("after 1: " + taskctx.getParams());
									//System.out.println("after 2: " + taskctx.getResult());

									callback.returnValue(taskctx.getParams());
									taskctx.returnEmpty();
								})
				);

				return true;
			}
		}

		return false;
	}

	@Override
	public void start() {
		this.enabled = true;
	}
	
	@Override
	public void stop() {
		this.enabled = false;
	}
}
