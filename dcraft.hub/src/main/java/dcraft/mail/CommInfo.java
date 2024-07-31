package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.schema.SchemaResource;
import dcraft.script.Script;
import dcraft.struct.CompositeParser;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Path;

public class CommInfo {
	static public CommInfo of(CommonPath request) throws OperatingContextException {
		CommonPath folder = MailUtil.toCommFolder(request);

		if (folder == null)
			return null;

		Path schemaPath = ResourceHub.getResources().getComm().findCommFile(folder.resolve("schema.xml"));

		Path securityPath = ResourceHub.getResources().getComm().findCommFile(folder.resolve("access.json"));

		// security can be in the parent
		if (securityPath == null)
			securityPath = ResourceHub.getResources().getComm().findCommFile(folder.getParent().resolve("access.json"));

		Path configPath = ResourceHub.getResources().getComm().findCommFile(folder.resolve("config.json"));
		Path propsPath = ResourceHub.getResources().getComm().findCommFile(folder.resolve("props.json"));

		if ((schemaPath != null) && (securityPath != null) && (configPath != null)) {
			XElement opel = XmlReader.loadFile(schemaPath, false, true);

			RecordStruct access = Struct.objectToRecord(CompositeParser.parseJson(securityPath));
			RecordStruct config = Struct.objectToRecord(CompositeParser.parseJson(configPath));

			// merge in properties, if any

			if (propsPath != null) {
				RecordStruct props = Struct.objectToRecord(CompositeParser.parseJson(propsPath));

				config.copyFields(props);
			}

			// TODO if using a skeleton, load and merge properties from there (such that local props override the skel props)

			if ((opel != null) && (access != null) && (config != null)) {
				SchemaResource.OpInfo opInfo = ResourceHub.getResources().getSchema().loadIsloatedInfo(request, access, opel);

				// TODO optionally add local dictionaries and load them isolated

				Script script = ResourceHub.getResources().getComm().loadScript(folder.resolve("initialize.dcs.xml"));

				CommInfo commInfo = new CommInfo();
				commInfo.opInfo = opInfo;
				commInfo.config = config;
				commInfo.initalize = script;
				commInfo.folder = folder;
				return commInfo;
			}
		}

		return null;
	}

	public SchemaResource.OpInfo opInfo = null;
	public RecordStruct config = null;
	public Script initalize = null;
	public CommonPath folder = null;
	public String locale = null;
}
