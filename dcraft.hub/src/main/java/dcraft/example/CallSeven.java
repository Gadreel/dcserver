package dcraft.example;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class CallSeven {
	/*
	 * This expands on CallSix by using threading with OperationOutcomes. 
	 */
	public static void main(String... args) throws Exception {
		/*
		 * Although this is a little advanced, it will help to demonstrate a 
		 * more realistic use of OperationOutcomes. So far the use is accurate
		 * but hypothetical. By adding threads to this example the OperationOutcomes
		 * will operate as intended. If the use of threads below doesn't make complete
		 * sense, don't worry about it - this is advanced and in most cases you don't
		 * need to know threads to code dcServer. You just need to know how to use
		 * OperationOutcome and the threading part will typically be invisible.
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
				lookupChefRatings("Barry", new OperationOutcomeList() {
					@Override
					public void callback(ListStruct result) throws OperatingContextException {
						printRatingsResult("Barry", this);
						
						// call $tar only after we have results for Barry
						lookupChefRatings("$tar", new OperationOutcomeList() {
							@Override
							public void callback(ListStruct result) throws OperatingContextException {
								printRatingsResult("$tar", this);
								
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
		
		// NEW wait while threads execute
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
	 */
	static public void lookupChefRatings(String chef, OperationOutcomeList listoutcome) throws OperatingContextException {
		validateChefName(chef, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				/*
				 */
				
				if (this.hasErrors()) {
					listoutcome.returnEmpty();
					return;
				}
				
				/*
				 * NEW With our code we are simulating non-blocking (async) data retrieval,
				 * such as a call to database or a web service. That data may come
				 * back on another thread so to simulate this we deliberately create
				 * a new thread.
				 */
				Runnable work = new Runnable() {
					@Override
					public void run() {
						/*
						 * NEW when writing a function that supports OperatingContext,
						 * the first thing to do in a new thread is to restore that context.
						 * An easy way is to call "useContext" on the OperationOutcome.
						 * This is an advanced idea, if it doesn't make sense don't worry
						 * focus on "how to call functions" not on "how to write functions".
						 */
						
						listoutcome.useContext();
						
						try {
							Thread.sleep(1000);
						} 
						catch (InterruptedException x) {
							  Logger.error(102, "Chef rating lookup interrupted");
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
								     	.with("Judge", "Ginny")
								     	.with("Rating", 9.0)
							 ));
							
							return;
						}
						
						Logger.error(100, "Chef not found.");	
						listoutcome.returnEmpty();
					}
				};
				
				Thread t = new Thread(work);
				t.start();
			}
		});
	}
	
	/*
	 * Check that the name being requested is a valid format.
	 * Name must not be null, must be at least 3 alphanumeric 
	 * characters. 
	 */
	static public void validateChefName(String chef, OperationOutcomeEmpty outcome) throws OperatingContextException {
		/*
		 * NEW With our code we are simulating non-blocking (async) data validation. 
		 * The validation may return on another thread so to simulate this we 
		 * deliberately create a new thread.
		 */
		Runnable work = new Runnable() {
			@Override
			public void run() {
				/*
				 * NEW when writing a function that supports OperatingContext,
				 * the first thing to do in a new thread is to restore that context.
				 * An easy way is to call "useContext" on the OperationOutcome.
				 * This is an advanced idea, if it doesn't make sense don't worry
				 * focus on "how to call functions" not on "how to write functions".
				 */
				
				outcome.useContext();
				
				try {
					Thread.sleep(1000);
				} 
				catch (InterruptedException x) {
					  Logger.error(203, "Chef name validation interrupted");
					  outcome.returnEmpty();
					  return;
				}
				
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
				  
				  outcome.returnEmpty();
			}
		};
		
		Thread t = new Thread(work);
		t.start();
	}
}
