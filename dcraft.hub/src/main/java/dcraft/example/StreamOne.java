package dcraft.example;

import dcraft.example.service.ChefDataStreamService;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.locale.Dictionary;
import dcraft.locale.LocaleResource;
import dcraft.schema.SchemaResource;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.service.ServiceResource;
import dcraft.stream.record.SyncRecordStreamConsumer;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.run.WorkHub;
import dcraft.xml.XElement;

public class StreamOne {
	/*
	 * This expands on CallTen by converting the service call to use streaming. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Keep Translations from CallNine
		 * 
		 */
		
		XElement translations = XElement.tag("Dictionary")
			.with(XElement.tag("Locale")
					.withAttribute("Id", "en")
					.with(XElement.tag("Entry")
						.withAttribute("Token", "_code_100")
						.withAttribute("Value", "Chef not found: {$1}")
					)
					.with(XElement.tag("Entry")
						.withAttribute("Token", "_code_101")
						.withAttribute("Value", "Chef name is invalid: {$1}")
					)
					.with(XElement.tag("Entry")
						.withAttribute("Token", "_code_102")
						.withAttribute("Value", "Chef rating lookup interrupted: {$1}")
					)
					.with(XElement.tag("Entry")
						.withAttribute("Token", "_code_103")
						.withAttribute("Value", "Bad ratings data: {$1}")
					)
					.with(XElement.tag("Entry")
						.withAttribute("Token", "_code_440")
						.withAttribute("Value", "No data type options match. Could not validate: {$1}")
					)
					.with(XElement.tag("Entry")
						.withAttribute("Token", "_code_447")
						.withAttribute("Value", "Value does not match pattern: {$1}")
					)
			)
			.with(XElement.tag("Locale")
				.withAttribute("Id", "x-pig-latin")
				.with(XElement.tag("Entry")
					.withAttribute("Token", "_code_100")
					.withAttribute("Value", "ef-Chay ot-nay ound-fay: {$1}")
				)
				.with(XElement.tag("Entry")
					.withAttribute("Token", "_code_101")
					.withAttribute("Value", "ef-Chay ame-nay is-way invalid-way: {$1}")
				)
				.with(XElement.tag("Entry")
					.withAttribute("Token", "_code_102")
					.withAttribute("Value", "ef-Chay ating-ray ookup-lay interrupted-way: {$1}")
				)
				.with(XElement.tag("Entry")
					.withAttribute("Token", "_code_103")
					.withAttribute("Value", "ad-Bay atings-ray ata-day: {$1}")
				)
				.with(XElement.tag("Entry")
					.withAttribute("Token", "_code_440")
					.withAttribute("Value", "o-Nay ata-day ype-tay options-way atch-may. ould-Cay ot-nay alidate-vay : {$1}")
				)
				.with(XElement.tag("Entry")
					.withAttribute("Token", "_code_447")
					.withAttribute("Value", "alue-Vay oes-day ot-nay atch-may attern-pay: {$1}")
				)
			);
		
		/*
		 * Now create a dictionary for those codes
		 */
		
		Dictionary dict = Dictionary.create();
		
		dict.load(translations);
		
		/*
		 * And load it into a ConfigResource along with defaults for locale and timezone.
		 * We are starting with x-pig-latin as the default locale
		 */
		
		LocaleResource lres = new LocaleResource();
		
		lres.setDefaultChronology("America/Chicago");
		lres.setDefaultLocale("x-pig-latin");		// switch this to en and the system default would be English
		lres.setLocalDictionary(dict);
		
		ResourceHub.getTopResources().with(lres);
		
		/*
		 * We still want to validate data so continue to use 
		 * the data type schema from example eight.
		 * 
		 * NEW A service definition must be added to the schema.
		 * Calls to services must conform to the schema. Services operations have a three
		 * part path - Service Name, Feature Name and Operation Name. So a single service
		 * may have multiple features and each feature may have multiple operations.
		 * 
		 * Sections of a service may be secured to use by only certain user roles.
		 * 
			<Services>
				<Service Name="ChefData">
					<Secure Tags="SysAdmin,Admin,User">
						<Feature Name="Ratings">
							<Op Name="Lookup">
								<Request Type="zName" Required="True" />
								<ResponseStream Type="zChefRating" />
							</Op>
						</Feature>
					</Secure>
				</Service>
			</Services>
		 * 
		 */
		
		XElement schema = XElement.tag("Schema")
				.with(XElement.tag("Services")
						.with(XElement.tag("Service")
								.withAttribute("Name", "ChefData")
								.with(XElement.tag("Secure")
										.withAttribute("Tags", "SysAdmin,Admin,User")
										.with(XElement.tag("Feature")
												.withAttribute("Name", "Ratings")
												.with(XElement.tag("Op")
														.withAttribute("Name", "Lookup")
														.with(XElement.tag("Request")
																.withAttribute("Type", "zName")
																.withAttribute("Required", "true")
														)
														.with(XElement.tag("ResponseStream")
																.withAttribute("Inherits", "zChefRating")
														)
												)
										)
								)
						)
				)
				.with(XElement.tag("Shared")
						.with(XElement.tag("StringType")
								.withAttribute("Id", "zName")
								.with(XElement.tag("StringRestriction")
										.withAttribute("Pattern", "\\w{3,}")
								)
						)
						.with(XElement.tag("StringType")
								.withAttribute("Id", "zDish")
								.with(XElement.tag("StringRestriction")
										.withAttribute("MaxLength", "50")
								)
						)
						.with(XElement.tag("NumberType")
								.withAttribute("Id", "zRating")
								.with(XElement.tag("NumberRestriction")
										.withAttribute("Conform", "Decimal")
								)
						)
						.with(XElement.tag("Record")
								.withAttribute("Id", "zChefRating")
								.with(XElement.tag("Field")
										.withAttribute("Name", "Dish")
										.withAttribute("Type", "zDish")
										.withAttribute("Required", "True")
								)
								.with(XElement.tag("Field")
										.withAttribute("Name", "Judge")
										.withAttribute("Type", "zName")
										.withAttribute("Required", "True")
								)
								.with(XElement.tag("Field")
										.withAttribute("Name", "Rating")
										.withAttribute("Type", "zRating")
										.withAttribute("Required", "True")
								)
						)
						.with(XElement.tag("List")
								.withAttribute("Id", "zChefRatings")
								.withAttribute("Type", "zChefRating")
						)
				);
		
		/*
		 * Load the scheme into dcServer
		 */
		
		SchemaResource sg = SchemaResource.fromXml(schema);
		
		ResourceHub.getTopResources().with(sg);
		
		/*
		 * Initialize the WorkHub as before.
		 */
		
		WorkHub.minStart();
		
		/*
		 * NEW Connect code (an IService class) to the service. Services have an advantage
		 * over functions in that they may be called remotely - a web page may call a service
		 * using JavaScript. Functions on the other hand are only available to server side code.
		 */
		
		ServiceResource srv = new ServiceResource();
		
		srv.registerTierService("ChefData", new ChefDataStreamService());
		
		ResourceHub.getTopResources().with(srv);
		
		/*
		 * We need the OperationContext. We'll hard code the sign in.
		 * 
		 * NEW added Verified because this flag needs to be present when 
		 * services are called.
		 */
		
		UserContext currusr = UserContext.rootUser();
		
		OperationContext opctx = OperationContext.context(currusr);
		
		OperationContext.set(opctx);
		
		/*
		 * We are signed in. Next our sequential, nested calls to lookupChefRatings.
		 * 
		 * NEW we call the service instead of the local function.
		 */
		String chef = "Harold";
		
		ServiceRequest request = ServiceRequest.of("ChefData", "Ratings", "Lookup")
				.wrapData(chef)
				.withResponseStream(new SyncRecordStreamConsumer() {
					@Override
					public void start() throws OperatingContextException {
						System.out.println("Here are the ratings for " + chef + ":");
						System.out.println();
					}
					
					@Override
					public void accept(RecordStruct slice) throws OperatingContextException {
						System.out.println(slice.getFieldAsString("Rating") + " for " +
								slice.getFieldAsString("Dish") + " by " + slice.getFieldAsString("Judge"));
					}
					
					@Override
					public void end(OperationOutcomeStruct outcome) throws OperatingContextException {
						System.out.println();
						System.out.println("Here are the messages logged for " + chef);
						System.out.println(outcome.getMessages().toPrettyString());
						System.out.println();
					}
				});
		
		
		ServiceHub.call(request);
		
		// wait while threads execute
		System.out.println("Got to prompt.");
		System.in.read();
	}
	
}
