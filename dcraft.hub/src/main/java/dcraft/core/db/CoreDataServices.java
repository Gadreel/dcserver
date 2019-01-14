package dcraft.core.db;

import dcraft.service.BaseService;
import dcraft.service.IService;
import dcraft.service.ServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.xml.XElement;

public class CoreDataServices extends BaseService {
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		/* TODO review all and delete this class, not in use
		RecordStruct rec = request.getDataAsRecord();
		
		OperationContext tc = OperationContext.getOrThrow();
		UserContext uc = tc.getUserContext();
		
		IDatabaseManager db = DatabaseHub.defaultDb();
		
		if (db == null) {
			Logger.errorTr(443);
			callback.returnResult();
			return false;
		}
		
		// =========================================================
		//  users
		// =========================================================
		
		if ("Users".equals(request.getFeature())) {
			if ("LoadSelf".equals(request.getOp()) || "LoadUser".equals(request.getOp())) {
				LoadRecordRequest req = new LoadRecordRequest()
					.withTable("dcUser")
					.withId("LoadUser".equals(request.getOp()) ? rec.getFieldAsString("Id") : uc.getUserId())
					.withNow()
					.withSelect(new SelectFields()
						.withField("Id")
						.withField("dcUsername", "Username")
						.withField("dcFirstName", "FirstName")
						.withField("dcLastName", "LastName")
						.withForeignField("dcGroup", "Groups", "dcName")
						.withField("dcEmail", "Email")
						.withField("dcBackupEmail", "BackupEmail")
						.withField("dcLocale", "Locale")
						.withField("dcChronology", "Chronology")
						.withField("dcDescription", "Description")
						.withField("dcConfirmed", "Confirmed")
						.withField("dcBadges", "Badges")
					);  
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
						
			if ("UpdateSelf".equals(request.getOp()) || "UpdateUser".equals(request.getOp())) {
				final UpdateUserRequest req = new UpdateUserRequest("UpdateUser".equals(request.getOp()) ? rec.getFieldAsString("Id") : uc.getUserId());

				if (rec.hasField("Username"))
					req.setUsername(rec.getFieldAsString("Username"));

				if (rec.hasField("FirstName"))
					req.setFirstName(rec.getFieldAsString("FirstName"));

				if (rec.hasField("LastName"))
					req.setLastName(rec.getFieldAsString("LastName"));

				if (rec.hasField("Email"))
					req.setEmail(rec.getFieldAsString("Email"));

				if (rec.hasField("BackupEmail"))
					req.setBackupEmail(rec.getFieldAsString("BackupEmail"));

				if (rec.hasField("Locale"))
					req.setLocale(rec.getFieldAsString("Locale"));

				if (rec.hasField("Chronology"))
					req.setChronology(rec.getFieldAsString("Chronology"));
				
				if (rec.hasField("Password")) 
					req.setPassword(rec.getFieldAsString("Password")); 

				// not allowed for Self (see schema)
				if (rec.hasField("Confirmed")) 
					req.setConfirmed(rec.getFieldAsBoolean("Confirmed"));
				
				// not allowed for Self (see schema)
				if (rec.hasField("Description")) 
					req.setDescription(rec.getFieldAsString("Description"));
				
				// not allowed for Self (see schema)
				if (rec.hasField("AuthorizationTags"))
					req.setAuthorizationTags(rec.getFieldAsList("AuthorizationTags"));
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("AddUser".equals(request.getOp())) {
				AddUserRequest req = new AddUserRequest(rec.getFieldAsString("Username"));
			
				if (rec.hasField("FirstName"))
					req.setFirstName(rec.getFieldAsString("FirstName"));
			
				if (rec.hasField("LastName"))
					req.setLastName(rec.getFieldAsString("LastName"));
			
				if (rec.hasField("Email"))
					req.setEmail(rec.getFieldAsString("Email"));

				if (rec.hasField("BackupEmail"))
					req.setBackupEmail(rec.getFieldAsString("BackupEmail"));
			
				if (rec.hasField("Locale"))
					req.setLocale(rec.getFieldAsString("Locale"));
			
				if (rec.hasField("Chronology"))
					req.setChronology(rec.getFieldAsString("Chronology"));
				
				if (rec.hasField("Password")) 
					req.setPassword(rec.getFieldAsString("Password"));
				
				if (rec.hasField("Confirmed")) 
					req.setConfirmed(rec.getFieldAsBoolean("Confirmed"));
				else
					req.setConfirmed(true);
				
				if (rec.hasField("ConfirmCode")) 
					req.setConfirmCode(rec.getFieldAsString("ConfirmCode"));
				
				if (rec.hasField("Description")) 
					req.setDescription(rec.getFieldAsString("Description"));
				
				if (rec.hasField("AuthorizationTags"))
					req.setAuthorizationTags(rec.getFieldAsList("AuthorizationTags"));
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("RetireSelf".equals(request.getOp()) || "RetireUser".equals(request.getOp())) {
				db.submit(new RetireRecordRequest("dcUser", "RetireUser".equals(request.getOp()) ? rec.getFieldAsString("Id") : uc.getUserId()),
						new StructOutcome() {
							@Override
							public void callback(CompositeStruct result) throws OperatingContextException {
								if ("RetireSelf".equals(request.getOp())) {
									// be sure we keep the Tenant id
									* TODO
									UserContext uc = OperationContext.getOrThrow().getUserContext();
									
									OperationContext.switchUser(OperationContext.getOrThrow(), new OperationContextBuilder()
										.withGuestUserTemplate()
										.withTenant(uc.getTenantAlias())
										.toUserContext());
										*
								}
								
								callback.returnResult();
							}
						});
				
				return true;
			}
			
			if ("ReviveUser".equals(request.getOp())) {
				db.submit(new ReviveRecordRequest("dcUser", rec.getFieldAsString("Id")), new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("SetBadges".equals(request.getOp())) {
				ListStruct users = rec.getFieldAsList("Users");
				ListStruct tags = rec.getFieldAsList("AuthorizationTags");
				
				db.submit(RequestFactory.makeSet("dcUser", "dcBadges", users, tags), new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("AddBadges".equals(request.getOp())) {
				ListStruct users = rec.getFieldAsList("Users");
				ListStruct tags = rec.getFieldAsList("AuthorizationTags");
				
				db.submit(RequestFactory.addToSet("dcUser", "dcBadges", users, tags), new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("RemoveBadges".equals(request.getOp())) {
				ListStruct users = rec.getFieldAsList("Users");
				ListStruct tags = rec.getFieldAsList("AuthorizationTags");
				
				db.submit(RequestFactory.removeFromSet("dcUser", "dcBadges", users, tags), new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("UsernameLookup".equals(request.getOp())) {
				db.submit(new UsernameLookupRequest(rec.getFieldAsString("Username")), new StructOutomeFinal(callback));
				
				return true;
			}

			// use with discretion
			if ("ListUsers".equals(request.getOp())) {
				db.submit(
					new SelectDirectRequest()
						.withTable("dcUser")
						.withSelect(new SelectFields()
							.withField("Id")
							.withField("dcUsername", "Username")
							.withField("dcFirstName", "FirstName")
							.withField("dcLastName", "LastName")
							.withField("dcEmail", "Email")), 
					new StructOutomeFinal(callback));
				
				return true;
			}
		}
		
		// =========================================================
		//  Tenants
		// =========================================================
		
		if ("Tenants".equals(request.getFeature())) {
			if ("LoadTenant".equals(request.getOp()) || "MyLoadTenant".equals(request.getOp())) {
				LoadRecordRequest req = new LoadRecordRequest()
					.withTable(DB_GLOBAL_TENANT_DB)
					.withId("MyLoadTenant".equals(request.getOp()) ? uc.getTenant().getId() : rec.getFieldAsString("Id"))
					.withNow()
					.withSelect(new SelectFields()
						.withField("Id")
						.withField("dcTitle", "Title")
						.withField("dcAlias", "Alias")
						.withField("dcDescription", "Description")
						.withField("dcObscureClass", "ObscureClass")
						.withField("dcName", "Names")
					);
				
				req.withTenant("MyLoadTenant".equals(request.getOp()) ? uc.getTenant().getId() : rec.getFieldAsString("Id"));
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
						
			if ("UpdateTenant".equals(request.getOp()) || "MyUpdateTenant".equals(request.getOp())) {
				DataRequest req = new UpdateRecordRequest()
					.withTable(DB_GLOBAL_TENANT_DB)
					.withId("MyUpdateTenant".equals(request.getOp()) ? uc.getTenantAlias() : rec.getFieldAsString("Id"))
					.withConditionallySetFields(rec, "Title", "dcTitle", "Alias", "dcAlias", "Description", "dcDescription", "ObscureClass", "dcObscureClass")
					.withConditionallySetList(rec, "Names", "dcName");
				
				req.withTenant("MyUpdateTenant".equals(request.getOp()) ? uc.getTenant().getId() : rec.getFieldAsString("Id"));
				
				db.submit(req, new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) {
						// TODO more direct
						//ApplicationHub.fireEvent(HubEvents.TenantUpdated, rec.getFieldAsString("Id"));					
						callback.returnValue(result);
					}
				});
				
				return true;
			}
			
			* TODO review
			if ("AddTenant".equals(op)) {
				ReplicatedDataRequest req = new InsertRecordRequest()
					.withTable(DB_GLOBAL_TENANT_DB)
					.withConditionallySetFields(rec, "Title", "dcTitle", "Alias", "dcAlias", "Description", "dcDescription", "ObscureClass", "dcObscureClass")
					.withSetList("dcName", rec.getFieldAsList("Names"));
				
				db.submit(req, new ObjectResult() {
					@Override
					public void process(CompositeStruct result) {
						LocalFileStore fs = Hub.instance.getTenantsFileStore();
						
						Path dspath = fs.getFilePath().resolve(rec.getFieldAsString("Alias") + "/");
													
						try {
							Files.createDirectories(dspath.resolve("files"));
							Files.createDirectories(dspath.resolve("galleries"));
							Files.createDirectories(dspath.resolve("www"));
						} 
						catch (IOException x) {
							request.error("Unable to create directories for new Tenant: " + x);
							request.returnEmpty();
							return;
						}
						
						Path cpath = dspath.resolve("config/settings.xml");

						XElement tenantsettings = new XElement("Settings",
								new XElement("Web", 
										new XAttribute("UI", "Custom"),
										new XAttribute("SiteTitle", rec.getFieldAsString("Title")),
										new XAttribute("SiteAuthor", rec.getFieldAsString("Title")),
										new XAttribute("SiteCopyright", new DateTime().getYear() + ""),
										new XElement("Package", 
												new XAttribute("Name", "dcWeb")
										),
										new XElement("Package", 
												new XAttribute("Name", "dc/dcCms")
										)
								)
						);

						IOUtil.saveEntireFile(cpath, tenantsettings.toString(true));
						
						Hub.instance.fireEvent(HubEvents.TenantAdded, ((RecordStruct)result).getFieldAsString("Id"));
						
						request.returnValue(result);
					}
				});
				
				return;
			}
			*
						
			if ("ImportTenant".equals(request.getOp())) {
				//SiteInfo site = OperationContext.get().getSite();
				String alias = rec.getFieldAsString("Alias");
				
				LocalFileStore fs = TenantHub.getFileStore();
				
				if (fs == null)  {
					Logger.error("Public file store not enabled.");
					callback.returnResult();
					return true;
				}
				
				Path dspath = fs.getFilePath().resolve(alias + "/");
				
				Path cpath = dspath.resolve("config/settings.xml");
				
				if (Files.notExists(cpath)) {
					Logger.error("Settings file not present.");
					callback.returnResult();
					return true;
				}
				
				XElement domainsettings = XmlReader.loadFile(cpath, false);
				
				if (domainsettings == null) {
					callback.returnResult();
					return true;
				}
				
				String title = domainsettings.getAttribute("Title");
				String desc = "";
				XElement del = domainsettings.find("Description");
				
				if (del != null)
					desc = del.getValue();
				
				String fdesc = desc;
				
				String obs = "";
				XElement oel = domainsettings.find("ObscureClass");
				
				if (oel != null)
					obs = oel.getValue();
				
				if (StringUtil.isEmpty(obs))
					obs = "dcraft.util.StandardSettingsObfuscator";
				
				String fobs = obs;
				
				ListStruct dnames = new ListStruct();
				
				// prefer not
				//for (XElement del2 : domainsettings.selectAll("Tenant"))
				//	dnames.addItem(del2.getAttribute("Name"));
				
				DataRequest req = new DataRequest("dcLoadTenants");		// must be in root .withRootTenant();	// use root for this request
				
				db.submit(req, new StructOutcome() {
					@Override
					public void callback(CompositeStruct result) throws OperatingContextException {
						// if this fails the hub cannot start
						if (this.hasErrors()) {
							callback.returnResult();
							return;
						}
						
						ListStruct domains = (ListStruct) result;
						
						for (Struct d : domains.items()) {
							RecordStruct drec = (RecordStruct) d;
							
							String did = drec.getFieldAsString("Id");
							String dalais = drec.getFieldAsString("Alias");
							
							if (!dalais.equals(alias))
								continue;
							
							DataRequest req = new UpdateRecordRequest()
								.withTable(DB_GLOBAL_TENANT_DB)
								.withId(did)
								.withUpdateField("dcTitle", title)
								.withUpdateField("dcAlias", alias)
								.withUpdateField("dcDescription", fdesc)
								.withUpdateField("dcObscureClass", fobs)
								.withSetList("dcName", dnames);
							
							// updates execute on the domain directly
							req.withTenant(did);
							
							db.submit(req, new StructOutcome() {
								@Override
								public void callback(CompositeStruct result) {
									// TODO more direct
									//ApplicationHub.fireEvent(HubEvents.TenantUpdated, did);
									
									callback.returnValue(new RecordStruct().with("Id", did));
								}
							});
							
							return;
						}
						
						DataRequest req = new InsertRecordRequest()
							.withTable(DB_GLOBAL_TENANT_DB)
							.withUpdateField("dcTitle", title)
							.withUpdateField("dcAlias", alias)
							.withUpdateField("dcDescription", fdesc)
							.withUpdateField("dcObscureClass", fobs)
							.withSetList("dcName", dnames);
						
						db.submit(req, new StructOutcome() {
							@Override
							public void callback(CompositeStruct result) {
								// TODO more direct
								//ApplicationHub.fireEvent(HubEvents.TenantAdded, ((RecordStruct)result).getFieldAsString("Id"));
								
								callback.returnValue(result);
							}
						});
					}
				});
				
				return true;
			}
			
			if ("RetireTenant".equals(request.getOp())) {
				db.submit(new RetireRecordRequest(DB_GLOBAL_TENANT_DB, rec.getFieldAsString("Id")).withTenant(rec.getFieldAsString("Id")), new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("ReviveTenant".equals(request.getOp())) {
				db.submit(new ReviveRecordRequest(DB_GLOBAL_TENANT_DB, rec.getFieldAsString("Id")).withTenant(rec.getFieldAsString("Id")), new StructOutomeFinal(callback));
				
				return true;
			}			
		}
		
		// =========================================================
		//  globals
		// =========================================================
		
		if ("Globals".equals(request.getFeature())) {
			if ("DollarO".equals(request.getOp())) {
				DataRequest req = new DataRequest("dcKeyQuery")
					.withParams(rec);
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
			
			if ("Kill".equals(request.getOp())) {
				DataRequest req = new DataRequest("dcKeyKill")
					.withParams(rec);
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
		}	
		
		// =========================================================
		//  database directly
		// =========================================================
		if ("Database".equals(request.getFeature())) {
			if ("ExecuteProc".equals(request.getOp())) {
				String proc = rec.getFieldAsString("Proc");
				
				SchemaResource schema = ResourceHub.getResources().getSchema();
				DbProc pdef = schema.getDbProc(proc);
				
				if (! OperationContext.getOrThrow().getUserContext().isTagged(pdef.securityTags)) {
					Logger.errorTr(434);
					callback.returnResult();
					return true;
				}
				
				DataRequest req = new DataRequest(proc)
					.withParams(rec.getFieldAsComposite("Params"));
				
				db.submit(req, new StructOutomeFinal(callback));
				
				return true;
			}
		}	
		*/
		
		return false;
	}
}
