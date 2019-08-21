package dcraft.example;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.UserContext;
import dcraft.locale.Dictionary;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.schema.SchemaHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class CallNine {
	/*
	 * This expands on CallEight by adding locale support. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * In example 8 we saw so odd error codes in the dcServer log.
		 * _code_440 and _code_447. Why? Well those codes are language
		 * translation codes - so we expect to look up code 447 in a
		 * locale file and find the correct error message.
		 * 
		 * That would work well if using dcServer in full mode, but we
		 * are loading only a few pieces in the example. So we need
		 * load the locale part as well.
		 * 
		 * Pig Latin (x-pig-latin) is easy to translate so we'll use 
		 * that in addition to English (en) in the example. 
		 * 
			<Dictionary>
				<Locale Id="en">
					<Entry Token="_code_100" Value="Chef not found: {$1}" />
					<Entry Token="_code_101" Value="Chef name is invalid: {$1}" />
					<Entry Token="_code_102" Value="Chef rating lookup interrupted: {$1}" />
					<Entry Token="_code_103" Value="Bad ratings data: {$1}" />
					<Entry Token="_code_440" Value="No data type options match.  Could not validate: {$1}" />
					<Entry Token="_code_447" Value="Value does not match pattern: {$1}" />
				</Locale>
				<Locale Id="x-pig-latin">
					<Entry Token="_code_100" Value="ef-Chay ot-nay ound-fay: {$1}" />
					<Entry Token="_code_101" Value="ef-Chay ame-nay is-way invalid-way: {$1}" />
					<Entry Token="_code_102" Value="ef-Chay ating-ray ookup-lay interrupted-way: {$1}" />
					<Entry Token="_code_103" Value="ad-Bay atings-ray ata-day: {$1}" />
					<Entry Token="_code_440" Value="o-Nay ata-day ype-tay options-way atch-may. ould-Cay ot-nay alidate-vay : {$1}" />
					<Entry Token="_code_447" Value="alue-Vay oes-day ot-nay atch-may attern-pay: {$1}" />
				</Locale>
			</Dictionary>
		 * 
		 * So let's create that XML manually
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
		 * And finally set the resource to be the default. Now the logging systems
		 * have those translations available.
		 */
		
		/*
		 * We still want to validate data so continue to use 
		 * the data type schema from example eight.
		 */
		
		XElement schema = XElement.tag("Schema")
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
		 * We need the OperationContext. We'll hard code the sign in.
		 * 
		 * NEW we don't give this user a Locale so they will use
		 * the default (x-pig-latin).
		 */
		
		UserContext currusr = UserContext.rootUser();
		
		OperationContext opctx = OperationContext.context(currusr);
		
		OperationContext.set(opctx);
		
		/*
		 * We are signed in. Next our sequential, nested calls to lookupChefRatings.
		 * 
		 * NEW check in printRatingsResult - we print the OperatingOutcome messages
		 * now to see the locale in effect.
		 */
		
		lookupChefRatings("Harold", new OperationOutcomeList() {
			@Override
			public void callback(ListStruct result) throws OperatingContextException {
				printRatingsResult("Harold", this);

				// call Wanda only after we have results for Harold
				lookupChefRatings("Wanda", new OperationOutcomeList() {
					@Override
					public void callback(ListStruct result) throws OperatingContextException {
						printRatingsResult("Wanda", this);
						
						/*
						 * NEW now we give the user a locale, so OperationContext/Outcome
						 * will be in English now.
						 * 
						 * NEW Note that the system logger is still using Pig Latin.
						 */
						
						System.out.println();
						System.out.println("--------------------------------");
						System.out.println("Switching context to en");
						System.out.println("--------------------------------");
						System.out.println();
						
						opctx.withLocale("en");
						
						// call $tar only after we have results for Wanda
						lookupChefRatings("$tar", new OperationOutcomeList() {
							@Override
							public void callback(ListStruct result) throws OperatingContextException {
								printRatingsResult("$tar", this);
								
								// call Barry only after we have results for $tar
								lookupChefRatings("Barry", new OperationOutcomeList() {
									@Override
									public void callback(ListStruct result) throws OperatingContextException {
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
								});
							}
						});
					}
				});
			}
		});
		
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
	static public void printRatingsResult(String chef, OperationOutcomeList oo) throws OperatingContextException {
		ListStruct ratings = oo.getResult();
		
		/*
		 * NEW print the Outcome messages in whatever locale they use
		 */
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
		else if (oo.hasCode(100)) {
			System.out.println(chef + " - error, chef not in database");
		}
		else if (oo.hasCode(101)) {
			System.out.println(chef + " - error, chef name is not valid");
		}
		/*
		 * data type validation will create 4NN errors so if we requested bad
		 * data we'll get this error message.
		 */
		else if (oo.hasCodeRange(400, 499)) {
			System.out.println(chef + " - error, ratings are invalid, found bad data");
		}
		else {
			System.out.println("Unexpected error for " + chef);
		}
		
		System.out.println();
	}
	
	/*
	 * This function returns a list of ratings for dishes prepared by the chef
	 * name given in the parameter.
	 * 
	 * This function could have loaded data from a database or from a web service,
	 * but to keep it simple and focused we have a hard coded data set. Only two 
	 * chefs are available in that set.
	 */
	static public void lookupChefRatings(String chef, OperationOutcomeList listoutcome) throws OperatingContextException {
		/*
		 *  async data validation is not typical, but it helped us understand function calls
		 *  in earlier examples. Now we toss out that in favor of typical data validation.
		 *  
		 *  SchemaHub provides validation services so we'll use it to validate that the
		 *  requested chef name conforms to the zName data type.
		 */
		SchemaHub.validateType(true, true, Struct.objectToStruct(chef), "zName");
		
		/*
		 * We are effectively inside a ContextMarker now - recall that OperationOutcome
		 * provides the marker feature. Thus if anything went wrong in the validation
		 * about we can now check that with hasErrors or hasCode. If it is invalid return
		 * only an error.
		 */
	
		if (listoutcome.hasErrors()) {
			Logger.errorTr(101, chef);		// NEW use Tr (translation) instead of hard coded English
			listoutcome.returnEmpty();
			return;
		}
		
		/*
		 * Simulating non-blocking (async) data retrieval.
		 */
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
				
				ListStruct data = null;
				
				if (chef.equals("Harold")) {
					data = ListStruct.list( 
							RecordStruct.record()
								.with("Dish", "Mac and Cheese")
						     	.with("Judge", "Janet")
						     	.with("Rating", 7.0),
							RecordStruct.record()
								.with("Dish", "Cheesy Potatoes")
						     	.with("Judge", "Ernie")
						     	.with("Rating", 4.5)
					 );
				}
				
				if (chef.equals("Wanda")) {
					data = ListStruct.list( 
							RecordStruct.record()
								.with("Dish", "Rice Pilaf")
						     	.with("Judge", "Mike")
						     	.with("Rating", 8.5),
							RecordStruct.record()
								.with("Dish", "Ginger Carrot Soup")
						     	.with("Judge", "Gi^^y")
						     	.with("Rating", 9.0)
					 );
				}
				
				if (data != null) {
					/*
					 * Since the data is hard coded this a somewhat artificial 
					 * example, but once the data is loaded we can check the
					 * data to see if it is valid before returning it.
					 * 
					 * The call to validateType will result in 4NN errors if
					 * data is invalid.
					 */
					if (SchemaHub.validateType(true, true, data, "zChefRatings")) {
						listoutcome.returnValue(data);
					}
					else {
						Logger.errorTr(103, chef);		// NEW use Tr (translation) instead of hard coded English
						listoutcome.returnEmpty();
					}
				}
				else {
					Logger.errorTr(100, chef);		// NEW use Tr (translation) instead of hard coded English
					listoutcome.returnEmpty();
				}
			}
		};
		
		Thread t = new Thread(work);
		t.start();
	}
}
