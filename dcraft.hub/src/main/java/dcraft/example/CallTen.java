package dcraft.example;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.locale.Dictionary;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.service.*;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.xml.XElement;

public class CallTen {
	/*
	 * This expands on CallNine by migrating to a service call. 
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
								<Response Type="zChefRatings" />
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
														.with(XElement.tag("Response")
																.withAttribute("Type", "zChefRatings")
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
		 * NEW Connect code (an IService class) to the service. Services have an advantage
		 * over functions in that they may be called remotely - a web page may call a service
		 * using JavaScript. Functions on the other hand are only available to server side code.
		 */
		
		ServiceResource srv = new ServiceResource();
		
		srv.registerTierService("ChefData", new ChefDataService());
		
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
		
		ServiceHub.call(ServiceRequest.of("ChefData", "Ratings", "Lookup")
				.wrapData("Harold")
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(BaseStruct result) throws OperatingContextException {
						printRatingsResult("Harold", this);
						
						ServiceHub.call(ServiceRequest.of("ChefData", "Ratings", "Lookup")
								.wrapData("Wanda")
								.withOutcome(new OperationOutcomeStruct() {
									@Override
									public void callback(BaseStruct result) throws OperatingContextException {
										printRatingsResult("Wanda", this);
										
										/*
										 * now we give the user a locale, so OperationContext/Outcome
										 * will be in English now.
										 *
										 * Note that the system logger is still using Pig Latin.
										 */
										
										System.out.println();
										System.out.println("--------------------------------");
										System.out.println("Switching context to en");
										System.out.println("--------------------------------");
										System.out.println();
										
										opctx.withLocale("en");
										
										ServiceHub.call(ServiceRequest.of("ChefData", "Ratings", "Lookup")
												.wrapData("$tar")
												.withOutcome(new OperationOutcomeStruct() {
													@Override
													public void callback(BaseStruct result) throws OperatingContextException {
														printRatingsResult("$tar", this);
														
														ServiceHub.call(ServiceRequest.of("ChefData", "Ratings", "Lookup")
																.wrapData("Barry")
																.withOutcome(new OperationOutcomeStruct() {
																	@Override
																	public void callback(BaseStruct result) throws OperatingContextException {
																		printRatingsResult("Barry", this);
																		
																		// there is nothing else to do - no more lookups - so print "done" msg
																		System.out.println();
																		System.out.println("Example done, proceed to next example.");
																		System.out.println();
																		
																		// only one message for exiting program
																		System.out.println();
																		System.out.println();
																		System.out.println("Press Enter key to exit");
																		System.out.println();
																	}
																})
														);
													}
												})
										);
									}
								})
						);
					}
				})
		);
		
		// wait while threads execute
		System.out.println("Got to prompt.");
		System.in.read();
	}
	
	/*
	 * Most of the time when an OperationOutcome is passed as a parameter it is because we want
	 * it to return a result. In this case though - a not so typical case - we are passing it as
	 * a parameter so the function can review and user the result (the outcome) which is the opposite of
	 * the function producing the result.
	 */
	static public void printRatingsResult(String chef, OperationOutcomeStruct oo) throws OperatingContextException {
		ListStruct ratings = (ListStruct) oo.getResult();
		
		System.out.println("Here are the messages logged for " + chef);
		System.out.println(oo.getMessages().toPrettyString());
		System.out.println();
		System.out.println();
		
		if (! oo.hasErrors() && (ratings != null)) {
			System.out.println("Here are the ratings for " + chef + ":");
			System.out.println();
			
			for (int n = 0; n < ratings.size(); n++) {
				RecordStruct ratingrec = ratings.getItemAsRecord(n);
				
				System.out.println(ratingrec.getFieldAsString("Rating") + " for " + 
						ratingrec.getFieldAsString("Dish") + " by " + ratingrec.getFieldAsString("Judge"));
			}
		}
		else {
			System.out.println("Error for " + chef);
		}
		
		System.out.println();
	}
	
	/*
	 * NEW all service classes must implement IService. If you don't know how Java interfaces work
	 * you may wish to lightly review the topic online. Perhaps here:
	 * 
	 * https://en.wikipedia.org/wiki/Interface_(Java)
	 * 
	 * The value is that the caller of the service does need to know the class name of the service,
	 * just the schema (published) name. All service classes must include the handle method as seen
	 * below. Incoming data will always arrive as "Struct" and outgoing data will always be "Struct".
	 * The schema shows what the real data type is - so this code below can safely assume that "chef"
	 * is a StringStruct - a subclass of Struct.
	 * 
	 */
	static public class ChefDataService extends BaseService {
		/*
		 * This function returns a list of ratings for dishes prepared by the chef
		 * name given in the parameter.
		 * 
		 * This function could have loaded data from a database or from a web service,
		 * but to keep it simple and focused we have a hard coded data set. Only two 
		 * chefs are available in that set.
		 */

		@Override
		public boolean handle(ServiceRequest request, OperationOutcomeStruct listoutcome) throws OperatingContextException {
			/*
			 * NEW no need to validate - all service requests are automatically validated - when we reach
			 * this call we know the data is present and matches the type.
			 * 
			 * <Request Type="zName" Required="True" />
			 * 
			 * no more checks like this, just use the data.
			 * 
			 * SchemaHub.validateType(chef, "zName");
			 */
			
			String chef = request.getData().toString();	// returns the name from the StringStruct
			
			if (chef.equals("Harold")) {
				listoutcome.returnValue(ListStruct.list( 
						RecordStruct.record()
							.with("Dish", "Mac and Cheese")
					     	.with("Judge", "Janet")
					     	.with("Rating", 7.0),
						RecordStruct.record()
							.with("Dish", "Cheesy Potatoes")
					     	.with("Judge", "Ernie")
					     	.with("Rating", 4.5)
				 ));
				
				return true;
			}
			
			if (chef.equals("Wanda")) {
				listoutcome.returnValue(ListStruct.list( 
						RecordStruct.record()
							.with("Dish", "Rice Pilaf")
					     	.with("Judge", "Mike")
					     	.with("Rating", 8.5),
						RecordStruct.record()
							.with("Dish", "Ginger Carrot Soup")
					     	.with("Judge", "Gi^^y")
					     	.with("Rating", 9.0)
				 ));
				
				return true;
			}
			
			Logger.errorTr(100, chef);		
			listoutcome.returnEmpty();

			// true means we are able to process the request (even if the data was not found)
			return true;
		}
	}
	
	/*
	 * In case you are interested, yes the service can be written Async, as we did before, the above 
	 * example is meant to simplify the introduction of services. Here is the Async code:
	 * 
	static public class AsyncChefDataService implements IService {
		@Override
		public boolean init(XElement config) {
			return true;	// nothing to initialize
		}
		
		public boolean handle(String service, String feature, String op, Struct chefdata, OperationOutcomeStruct listoutcome) throws OperatingContextException {
			
			String chef = chefdata.toString();	// returns the name from the StringStruct
			
			 * Simulating non-blocking (async) data retrieval.
			Runnable work = new Runnable() {
				@Override
				public void run() {
					listoutcome.useContext();
					
					try {
						Thread.sleep(1000);
					} 
					catch (InterruptedException x) {
						  Logger.errorTr(102, chef);		// NEW use Tr (translation) instead of hard coded English
						  listoutcome.returnEmpty();
						  return;
					}
					
					if (chef.equals("Harold")) {
						listoutcome.returnValue(ListStruct.list( 
								RecordStruct.record()
									.with("Dish", "Mac and Cheese")
							     	.with("Judge", "Janet")
							     	.with("Rating", 7.0),
								RecordStruct.record()
									.with("Dish", "Cheesy Potatoes")
							     	.with("Judge", "Ernie")
							     	.with("Rating", 4.5)
						 ));
						return;
					}
					
					if (chef.equals("Wanda")) {
						listoutcome.returnValue(ListStruct.list( 
								RecordStruct.record()
									.with("Dish", "Rice Pilaf")
							     	.with("Judge", "Mike")
							     	.with("Rating", 8.5),
								RecordStruct.record()
									.with("Dish", "Ginger Carrot Soup")
							     	.with("Judge", "Gi^^y")
							     	.with("Rating", 9.0)
						 ));
						return;
					}
					
					Logger.errorTr(100, chef);		
					listoutcome.returnEmpty();
				}
			};
			
			Thread t = new Thread(work);
			t.start();
			
			// true means we are able to process the request
			return true;
		}
	}
	*/
}
