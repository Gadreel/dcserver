package dcraft.example;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class CallSix {
	/*
	 * This expands on CallFive by finishing the basic OperationOutcomes. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * First we need the OperationContext. As with CallThree we'll hard code 
		 * the sign in:
		 */
		
		UserContext currusr = UserContext.rootUser();
		
		OperationContext opctx = OperationContext.context(currusr);
		
		OperationContext.set(opctx);
		
		/*
		 * OK, we are signed in. 
		 * 
		 * NEW With async calls the (generally most advisable) practice is to only continue
		 * you next step when you are done with your first step. This makes your work
		 * run sequentially rather than in parallel. Don't worry, dcServer runs lots of tasks
		 * in parallel - parallel is efficient and desirable. But most of the time a
		 * specific task is done in sequential steps.
		 * 
		 * NEW To make our work sequential just move the next call to lookupChefRating so it
		 * happens only after we get the result from the last call. The code gets a little
		 * ugly - there are ways to fix that - but it works quite exactly the way it should.
		 * 
		 * NEW These calls are nested, which is fine for many uses - encouraged even - but keep
		 * in mind that nesting too deep can cause troubles. A dozen level of nesting is probably
		 * too much. Three to five is fine...in between depends. We'll address this in more detail
		 * later.
		 */
		
		lookupChefRatings("Harold", new OperationOutcomeList() {
			@Override
			public void callback(ListStruct result) throws OperatingContextException {
				printRatingsResult("Harold", this);

				// NEW call Barry only after we have results for Harold
				lookupChefRatings("Barry", new OperationOutcomeList() {
					@Override
					public void callback(ListStruct result) throws OperatingContextException {
						printRatingsResult("Barry", this);
						
						// NEW call $tar only after we have results for Barry
						lookupChefRatings("$tar", new OperationOutcomeList() {
							@Override
							public void callback(ListStruct result) throws OperatingContextException {
								printRatingsResult("$tar", this);
								
								// NEW there is nothing else to do - no more lookups - so print "done" msg
								System.out.println();
								System.out.println("Example done, proceed to next example.");
								System.out.println();
								
								// NEW only one message for exiting program
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
		
		/*
		 * NEW As we progress into multi-threaded (async) coding we can longer
		 * rely on System.in - the code below causes the program to wait until the 
		 * user presses Enter before exiting. With async code is possible that you could end
		 * before all the function calls have completed.
		 */
		
		System.in.read();
	}
	
	/*
	 * NEW Most of the time when an OperationOutcome is passed as a parameter it is because we want
	 * it to return a result. In this case though - a not so typical case - we are passing it as
	 * a parameter so the function can review and user the result (the outcome) which is the opposite of
	 * the function producing the result.
	 */
	static public void printRatingsResult(String chef, OperationOutcomeList oo) throws OperatingContextException {
		System.out.println("Here are the ratings for " + chef + ":");
		System.out.println();
		
		ListStruct ratings = oo.getResult();
		
		if (ratings != null) {
			for (int n = 0; n < ratings.size(); n++) {
				RecordStruct ratingrec = ratings.getItemAsRecord(n);
				
				System.out.println(ratingrec.getFieldAsString("Rating") + " for " + 
						ratingrec.getFieldAsString("Dish") + " by " + ratingrec.getFieldAsString("Judge"));
			}
		}
		else if (oo.hasCode(100)) {
			System.out.println(chef + " not in database");
		}
		else if (oo.hasCodeRange(200, 209)) {
			System.out.println(chef + " failed name validation");
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
	 * 
	 * NEW we no longer return a list from this function, instead list is returned
	 * via an OperationOutcomeList that will be triggered when the method is done.
	 * 
	 * NEW We'll look at the OperationOutcomeList in a moment but first review the 
	 * code in this method and how the call to validateChefName works.
	 * 
	 */
	static public void lookupChefRatings(String chef, OperationOutcomeList listoutcome) throws OperatingContextException {
		/*
		 * NEW Checking the chef's name is also now an async call (this is only as an example of async
		 * there is no compelling reason for such a validation to be async). This outcome will be "Empty"
		 * as in "OperationOutcomeEmpty" meaning no value is returned. In that sense OperationOutcomeEmpty
		 * is quite like OperationMarker - it's only usefulness in for checking hasErrors or hasCode.
		 * 
		 * NEW Below the outcome uses an anonymous class. In the anonymous class there is a "callback"
		 * method that is called when the async call is completed. This is typical in dcServer,
		 * all outcomes will have a "callback" method that occurs when the function is done (or when
		 * it times out).
		 */
		validateChefName(chef, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				/*
				 * NEW We get here when validateChefName is done. OperationOutcome
				 * are like OperationMarkers because they isolate log codes
				 * (example CallThree explains isolating the log codes).
				 * 
				 * this.hasErrors is providing the same sort of support that 
				 * om.hasErrors would. this.hasCode would work here too.
				 */
				
				if (this.hasErrors()) {
					listoutcome.returnEmpty();
					return;
				}
				
				/*
				 * NEW If there aren't errors then we want return a result to our caller.
				 * Rather than using the "return x;" statement in Java we'll use the
				 * async approach with OperationOutcomeList. The variable "listoutcome"
				 * is our way to send that result back by calling returnValue. Note that
				 * above we used returnEmpty because there were errors and no data 
				 * should be returned.
				 */
				
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
						     	.with("Judge", "Ginny")
						     	.with("Rating", 9.0)
					 ));
					
					return;
				}
				
				/* 
				 * NEW If there is no chef that is an error too, note we use
				 * returnEmpty again. Also note how no changes to Logger
				 * calls are necessary when switching from sync calls to
				 * async calls.
				 */
				Logger.error(100, "Chef not found.");	
				listoutcome.returnEmpty();
			}
		});
	}
	
	/*
	 * Check that the name being requested is a valid format.
	 * Name must not be null, must be at least 3 alphanumeric 
	 * characters. 
	 * 
	 * NEW OperatingContextException has been added to multiple places in this
	 * code. Just ignore it, it is only important for catching callers without an
	 * OperationContext - but we have one.
	 * 
	 * NEW OperationOutcomeEmpty is the new (async) way to return results from
	 * a function call. 
	 */
	static public void validateChefName(String chef, OperationOutcomeEmpty outcome) throws OperatingContextException {
		/*
		 * NEW When using an Empty outcome the only point is to return the log codes / errors. Like with
		 * OperationMarker, below we make error entries in the log if there is a problem. OperationOutcome
		 * then isolates log codes to the call to this method allowing it to detect if errors
		 * occurred during the call.
		 */
		  if (chef == null) {
			  Logger.error(200, "Chef name may not be null");
		  }
		  else {
		      int strLen = chef.length();
		      
		      if (strLen < 3) {
				  Logger.error(201, "Chef name is too short. Invalid name: " + chef);
			  }
		      else {
			      for (int i = 0; i < strLen; i++) {
			          if (! Character.isLetterOrDigit(chef.charAt(i))) {
						  Logger.error(202, "Chef name must alphanumeric. Invalid name: " + chef);
			          }
			      }
		      }
		  }
		  
		  // NEW we are done, return results to caller 
		  outcome.returnEmpty();
	}
}
