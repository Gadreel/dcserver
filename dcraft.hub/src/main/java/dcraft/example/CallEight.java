package dcraft.example;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.schema.SchemaResource;
import dcraft.schema.SchemaHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

public class CallEight {
	/*
	 * This expands on CallSeven by adding data validation. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * JSON did not originally have a validating system - a schema definition - to 
		 * further indicate the content of data. As long as you stuck to the accepted
		 * data types, any sort of content was valid.
		 * 
		 * JSON is an excellent way to move data around - including over the wire. But it 
		 * is not easy to read. Thus the JSON schema used by dcServer - a home grown schema -
		 * is written in XML. It is much easier to read.
		 * 
		 * When using dcServer is full mode it comes with a lot of data types predefined.
		 * In our examples we are only using select pieces of dcServer so we'll need to
		 * build the entire schema from scratch and manually load it. Then we can test
		 * the data validation feature of dcServer.
		 * 
		 * Data validation will be a significant part of service calls. We are reaching the
		 * end of the function call examples and will next explore the service calls - but
		 * only after learning a little about data validation and about locales.
		 * 
		 * Here is the schema for this example as it would appear in XML:
		 * 
			<Schema>
				<Shared>
					<StringType Id="zName">
						<StringRestriction Pattern="\w{3,}" />
					</StringType>
					<StringType Id="zDish">
						<StringRestriction MaxLength="50" />
					</StringType>
					<NumberType Id="zRating">
						<NumberRestriction Conform="Decimal" />
					</NumberType>
					<Record Id="zChefRating">
						<Field Name="Dish" Type="zDish" Required="True" />
						<Field Name="Judge" Type="zName" Required="True" />
						<Field Name="Rating" Type="zRating" Required="True" />
					</Record>
					<List Id="zChefRatings" Type="zChefRating" />
				</Shared>
			</Schema>
		 * 
		 * There are three base (scalar) types defined: zName, zDish and zRating. The
		 * first uses a Regular Expression to match a pattern of 3 or more alphanumeric
		 * characters. The second limits the value to 50 characters. The third 
		 * indicates that any decimal value is allowed.
		 * 
		 * Then there is a definition for a record that contains required 3 fields -
		 * each field has a name and a type. Finally there is a list than may contain
		 * only those rating records. Just to compare, if defined as such:
		 * 
					<List Id="zChefNames" Type="zName" />
		 * 
		 * Then we'd expect to validate a list of names like these:
		 * 
		 * [ "Harold", "Wanda", "$tar", "Berry" ]
		 * 
		 * With this data type the type indicated is a record not a scalar so with this:
		 * 
					<List Id="zChefRatings" Type="zChefRating" />
		 * 
		 * We expect to validate a list like this:
		 * 
		 * [ { "Dish": "Mac and Cheese", "Judge": "Janet", "Rating": 7.0 }, ... ]
		 * 
		 * OK, so lets create that schema (in code):
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
		 * NEW Now load the scheme into dcServer:
		 */
		
		SchemaResource sg = SchemaResource.fromXml(schema);
		
		ResourceHub.getTopResources().with(sg);
		
		/*
		 * NEW now we are ready to do data validation. Check out "lookupChefRatings"
		 * and "printRatingsResult" to see the validation changes.
		 * 
		 * First we need the OperationContext. As with CallThree we'll hard code 
		 * the sign in:
		 */
		
		UserContext currusr = UserContext.rootUser();
		
		OperationContext opctx = OperationContext.context(currusr);
		
		OperationContext.set(opctx);
		
		/*
		 * OK, we are signed in. 
		 * 
		 * Next our sequential, nested calls to lookupChefRatings.
		 */
		
		lookupChefRatings("Harold", new OperationOutcomeList() {
			@Override
			public void callback(ListStruct result) throws OperatingContextException {
				printRatingsResult("Harold", this);

				// call Barry only after we have results for Harold
				lookupChefRatings("Wanda", new OperationOutcomeList() {
					@Override
					public void callback(ListStruct result) throws OperatingContextException {
						printRatingsResult("Wanda", this);
						
						// call $tar only after we have results for Barry
						lookupChefRatings("$tar", new OperationOutcomeList() {
							@Override
							public void callback(ListStruct result) throws OperatingContextException {
								printRatingsResult("$tar", this);
								
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
		 * NEW data type validation will create 4NN errors so if we requested bad
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
		 *  NEW async data validation is not typical, but it helped us understand function calls
		 *  in earlier examples. Now we toss out that in favor of typical data validation.
		 *  
		 *  NEW SchemaHub provides validation services so we'll use it to validate that the
		 *  requested chef name conforms to the zName data type.
		 */
		SchemaHub.validateType(Struct.objectToStruct(chef), "zName");
		
		/*
		 * NEW We are effectively inside a ContextMarker now - recall that OperationOutcome
		 * provides the marker feature. Thus if anything went wrong in the validation
		 * about we can now check that with hasErrors or hasCode. If it is invalid return
		 * only an error.
		 */
	
		if (listoutcome.hasErrors()) {
			Logger.error(101, "Chef name is invalid");
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
					  Logger.error(102, "Chef rating lookup interrupted");
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
					 * NEW Since the data is hard coded this a somewhat artificial 
					 * example, but once the data is loaded we can check the
					 * data to see if it is valid before returning it.
					 * 
					 * The call to validateType will result in 4NN errors if
					 * data is invalid.
					 */
					if (SchemaHub.validateType(data, "zChefRatings")) {
						listoutcome.returnValue(data);
					}
					else {
						Logger.error(103, "Bad ratings data.");	
						listoutcome.returnEmpty();
					}
				}
				else {
					Logger.error(100, "Chef not found.");	
					listoutcome.returnEmpty();
				}
			}
		};
		
		Thread t = new Thread(work);
		t.start();
	}
}
