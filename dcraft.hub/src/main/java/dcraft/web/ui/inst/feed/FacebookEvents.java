package dcraft.web.ui.inst.feed;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.If;
import dcraft.script.inst.Var;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.script.work.InstructionWork;
import dcraft.struct.CompositeParser;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.TextWidget;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlUtil;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.ArrayList;
import java.util.List;

public class FacebookEvents extends Base {
	static public FacebookEvents tag() {
		FacebookEvents el = new FacebookEvents();
		el.setName("dcm.FacebookEventsWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return FacebookEvents.tag();
	}
	
	/*

{
  "data": [
    {
      "end_time": "2017-12-09T17:30:00-0600",
      "description": "Join the shops and restaurants on Monroe Street for a special day of holiday events!  Santa will arrive by convertible in the morning, and in the afternoon the Monroe Street carolers will entertain with songs of the season.  The Monroe Street Library will also hold a special book sale.  Details will be available at www.monroestreetmadison.com soon.",
      "cover": {
        "offset_x": 0,
        "offset_y": 0,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/23844610_10154868112117651_8928357494485765186_n.jpg?oh=9158669af2cffa5d1cb3b31d6ada6951&oe=5A9CE883",
        "id": "10154868112117651"
      },
      "start_time": "2017-12-09T10:00:00-0600",
      "name": "Holiday Glow on Monroe",
      "id": "182716722283307"
    },
    {
      "end_time": "2017-12-02T14:00:00-0600",
      "description": "We are pleased to feature one of our food lines on Sampling Saturday every month. In December we'll be featuring caramels by Bequet Confections of Montana. These small-batch delicacies, winners of six national awards, are made with all natural ingredients. Come try one!   https://bequetconfections.com",
      "cover": {
        "offset_x": 0,
        "offset_y": 224,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/23795403_10154871752807651_6300075622269254799_n.jpg?oh=f2d6f8072a94c97cfc31c6183bc9b6fb&oe=5A951D70",
        "id": "10154871752807651"
      },
      "start_time": "2017-12-02T12:00:00-0600",
      "name": "December Sampling Saturday",
      "id": "129494844398489"
    },
    {
      "end_time": "2017-11-27T20:00:00-0600",
      "description": "Decorate a wooden nutcracker ornament - and you could be the winner of a $50 shopping spree, or one of many other prizes!  A $5 deposit is required (which is refunded when you enter it), and you get to keep your decorated ornament when the contest is over. Prizes will be awarded by age category; adults as well as kids 6 & up are eligible.  We have a limited number of ornaments available, so stop in soon! Contest deadline is 8:00 on Monday, November 27.",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/23319151_10154829236332651_6807088201128327355_n.jpg?oh=47822fe24f047124dfef82ca67a52c10&oe=5A97F24C",
        "id": "10154829236332651"
      },
      "start_time": "2017-11-27T19:59:00-0600",
      "name": "Enter Our Nutcracker Ornament Contest",
      "id": "319729278507975"
    },
    {
      "end_time": "2017-11-25T17:30:00-0600",
      "description": "Our community benefits when you support locally owned businesses!  Stop by Orange Tree Imports on Small Business Saturday for a chance to win gift cards to our neighboring shops, restaurants and service businesses on Monroe Street -- plus a fabric Shop Small tote bag!",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-8/s720x720/22829447_10154829202542651_6105437824756938883_o.jpg?oh=8916cade7b9fde176c9c67229e3977b1&oe=5A91E08F",
        "id": "10154829202542651"
      },
      "start_time": "2017-11-25T10:00:00-0600",
      "name": "Small Business Saturday",
      "id": "170126906904048"
    },
    {
      "end_time": "2017-11-25T22:00:00-0600",
      "description": "This November 25th, we want to share Small Business Saturday® with you! It’s a holiday shopping tradition that celebrates small businesses like ours. And it wouldn’t be a celebration without customers like you joining us.

So mark your calendar for November 25th — the Saturday after Thanksgiving — and get ready to Shop Small® with us. Stop in at participating Monroe Street shops, restaurants and service businesses for a chance to win a cloth Shop Small bag with a $10 gift card at a Monroe Street Business!

1600 BLOCK
Maurie's Fine Chocolates of Madison
ReFind Style Madison

1700 BLOCK
Orange Tree Imports
Art Gecko Shop
rupert cornelius inc.

1800 BLOCK
Pizza Brutta
Canvas Club Boxing
Crescendo Espresso Bar + Music Cafe
Wild Child Inc.
Katy's American Indian Arts
Barriques - Monroe St
Bloom Bake Shop
Mystery to Me
Neuhauser Pharmacy

1900 BLOCK
Monroe Street Framing
Chocolate Shoppe Ice Cream Co.
The Wine & Hop Shop
Brasserie V
Dongzhu Pottery Studio
Karner Blue Candle and Supply, LLC

2500 BLOCK
Infusion Chocolates
Colectivo Coffee

2600 BLOCK
Calabash Gifts

2700 BLOCK
MadCat
Miko Poke
Everly

You can help get the word out and celebrate Small Business Saturday by using #monorestreetmadison and #shopsmall on all your social networks.

Thank you for all your support, and see you Saturday, November 25!

Yours,
Monroe Street Madison


More details: www.monroestreetmadison.com",
      "cover": {
        "offset_x": 50,
        "offset_y": 0,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/23471907_1920232817994115_3573965388749789403_n.jpg?oh=0dc06559ac2500e4f564f43c6cc84cfb&oe=5A9544E9",
        "id": "1920232817994115"
      },
      "start_time": "2017-11-25T08:00:00-0600",
      "name": "Small Business Saturday",
      "id": "163152557615954"
    },
    {
      "end_time": "2017-11-18T13:00:00-0600",
      "description": "Please join us for our popular knife sharpening event with Clark Stone of Wusthof. He will sharpen up to four of your kitchen knives (any brand, but no serrated knives, please) for a suggested donation of $4 per item to our local chapter of Second Harvest Foodbank. Note: if you can't make it on Saturday, you are welcome to drop your knives off in advance. Please wrap them carefully.",
      "cover": {
        "offset_x": 0,
        "offset_y": 51,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/23376115_10154842013557651_5936781556355627601_n.jpg?oh=a4abae2bb311d38e0f96abe8100abbcc&oe=5AD5941A",
        "id": "10154842013557651"
      },
      "start_time": "2017-11-18T10:00:00-0600",
      "name": "Knife Sharpening Benefit for Second HarvestFoodbank",
      "id": "294162667745002"
    },
    {
      "end_time": "2017-11-04T14:00:00-0500",
      "description": "We are pleased to feature one of our newest lines during our November Sampling Saturday: nut brittles by Anette's Chocolates by Brent.  These delicious brittles, made in small batches in the Napa Valley of California, include Triple Nut Kentucky Bourbon Brittle, Firey Beer Micro Brew Brittle and Chili Lime Tequila Tortilla Brittle.",
      "cover": {
        "offset_x": 0,
        "offset_y": 0,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/22728654_10154805857932651_4784862780786407481_n.jpg?oh=37a797405e13fe8e091c924a86eb345e&oe=5A9D6D42",
        "id": "10154805857932651"
      },
      "start_time": "2017-11-04T12:00:00-0500",
      "name": "November Sampling Saturday: Nut Brittle",
      "id": "127331061245490"
    },
    {
      "end_time": "2017-10-29T16:00:00-0500",
      "description": "It will be an afternoon of family fun on Monroe Street!

Trick or Treat at many of your favorite Monroe Street locations. Look for the TREAT STOP signs at all participating locations.

1500 BLOCK:
HotelRED

1600 BLOCK:
Maurie's Fine Chocolates

1700 BLOCK:
Orange Tree Imports
Associated Bank

1800 BLOCK:
Pizza Brutta
Wild Child
Barriques
Hair
Brocach on Monroe
Bloom Bake Shop
Mystery to Me

1900 BLOCK:
Monroe Street Framing
Wine and Hop Shop
Chocolate Shoppe Ice Cream
Karner Blue Candle & Supplies
Cat Cafe Mad

2500 BLOCK:
Colectivo Coffee
Infusion Chocolates
Monroe Street Art Center **Photo Booth with props and treats

2600 BLOCK:
Creando Little Language Learners
Roman Candle Pizza
jacs Dining and Taphouse
The Prep Center
MadCat *People and Pets welcome!
Everly
Miko Poke
Aaron Perry State Farm Agency

3500 BLOCK:
Madison Chocolate Company  *Kids costume contest at 3pm

Sunday, October 29th from Noon-4pm (while supplies last)
Check out all the deatils here : www.monroestreetmadison.com",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-8/s720x720/22290002_1888802897803774_4918799197361995001_o.jpg?oh=c94f527ec3bef199b53a4d84ec5b390d&oe=5ACC81D4",
        "id": "1888802897803774"
      },
      "start_time": "2017-10-29T12:00:00-0500",
      "name": "Trick or Treat on Monroe Street",
      "id": "2383808225178718"
    },
    {
      "end_time": "2017-10-07T14:00:00-0500",
      "description": "Our Sampling Saturday for October will feature donuts made the easy way, using Stonewall Kitchen's mix. Perfect for pairing with some of their delicious jams!",
      "cover": {
        "offset_x": 0,
        "offset_y": 24,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/22007338_10154745926372651_6892583722846274607_n.jpg?oh=0221c8cac40186f31cf051b2783d3d52&oe=5AD504FC",
        "id": "10154745926372651"
      },
      "start_time": "2017-10-07T12:00:00-0500",
      "name": "October Sampling Saturday",
      "id": "624163621306379"
    },
    {
      "end_time": "2017-09-16T17:00:00-0500",
      "description": "The Monroe Street Festival is our biggest one-day sale event of the year -- and you won't want to miss the other activities during this street sale and neighborhood celebration. There will be entertainment, children’s activities and a book sale at the Monroe Street Branch Library as well as the new Monroe Street Green Meet 'n Eat. Activities are held rain or shine, and take place in the shops, on the sidewalks, on Harrison St., and in the parking lanes. For more information, see www.MonroeStreetFestival.com.",
      "cover": {
        "offset_x": 0,
        "offset_y": 60,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-8/s720x720/21273094_10154690569272651_2822585740981775982_o.jpg?oh=ef88e69b40699cc9e9c7b4ea0087b3cc&oe=5A9E5ED9",
        "id": "10154690569272651"
      },
      "start_time": "2017-09-16T10:00:00-0500",
      "name": "40th Annual Monroe Street Festival",
      "id": "1631998316833629"
    },
    {
      "end_time": "2017-09-04T20:00:00-0500",
      "description": "We will be closed on Monday, September 4 for Labor Day. We hope you enjoy the holiday!",
      "cover": {
        "offset_x": 1571,
        "offset_y": 0,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/21192492_10154690583572651_2636927502301972837_n.jpg?oh=4373bae3497ab6368f41011bd734da0b&oe=5A963B85",
        "id": "10154690583572651"
      },
      "start_time": "2017-09-04T00:00:00-0500",
      "name": "Closed Labor Day",
      "id": "510378219299560"
    },
    {
      "end_time": "2017-09-02T14:00:00-0500",
      "description": "Orange Tree Imports offers samples of one of our lines of specialty foods on the first Saturday of every month. In September we'll be featuring KARMIRI  extra virgin olive oil from a small, family-owned farm that produces limited quantities of olive oil much better in quality than what you typically find from large-scale farms and suppliers.",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/21032851_10154672213382651_3304654923467233487_n.jpg?oh=1c90a4111e7df07cb2729550476d45d8&oe=5ACAF0DB",
        "id": "10154672213382651"
      },
      "start_time": "2017-09-02T12:00:00-0500",
      "name": "September Sampling Saturday",
      "id": "1963160733973786"
    },
    {
      "end_time": "2017-08-05T14:00:00-0500",
      "description": "Our popular \"Sampling Saturday\" event in August will feature various ice cream toppings served on delicious frozen custard from our Monroe Street neighbor, Michael's Frozen Custard.",
      "cover": {
        "offset_x": 0,
        "offset_y": 3,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/20374724_10154597470747651_3396012594503797817_n.jpg?oh=f4d22c9ace99fa0b0bf13ae82401d6e2&oe=5A949124",
        "id": "10154597470747651"
      },
      "start_time": "2017-08-05T12:00:00-0500",
      "name": "Sundaes on Saturday",
      "id": "260136494487983"
    },
    {
      "end_time": "2017-07-04T20:00:00-0500",
      "description": "We will be closed on Tuesday, July 4th. We hope you enjoy the holiday!",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/19511302_10154517959142651_1970428429745270619_n.jpg?oh=0da6ddbbe5f2065fc82e1406b3c683e7&oe=5A9D2C64",
        "id": "10154517959142651"
      },
      "start_time": "2017-07-04T10:00:00-0500",
      "name": "Closed on July 4th",
      "id": "2355743807984506"
    },
    {
      "end_time": "2017-07-01T14:00:00-0500",
      "description": "Orange Tree Imports offers samples of one of our lines of specialty foods on the first Saturday of every month. In July we'll be featuring the delicious Monona Bar, a gooey caramel, peanut butter, rice krispies and milk chocolate treat made by Madison's own Chocolaterian.",
      "cover": {
        "offset_x": 0,
        "offset_y": 14,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-0/p180x540/19402179_10154503397942651_4441007680123016037_o.jpg?oh=5cb38dcebdd0afeedeac1e6f03fb0483&oe=5A916B73",
        "id": "10154503397942651"
      },
      "start_time": "2017-07-01T12:00:00-0500",
      "name": "July Sampling Saturday",
      "id": "275869349551828"
    },
    {
      "end_time": "2017-06-10T14:00:00-0500",
      "description": "Christine Pieper, award winning cook, will be at Orange Tree Imports this Saturday to offer samples of the Pecatonica Grapevine's original and tomato whole seed mustard. Chrstine developed these popular mustards for the sandwiches in her coffee shop in beautiful Blanchardville, Wisconsin.",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/l/t31.0-0/p180x540/18922654_10154459497372651_4920788481986150760_o.jpg?oh=c9710eff48e2a95a9ccb2448c563af76&oe=5A9C5AF4",
        "id": "10154459497372651"
      },
      "start_time": "2017-06-10T12:00:00-0500",
      "name": "Sampling Saturday - Local Mustard",
      "id": "1315417241910598"
    },
    {
      "end_time": "2017-06-03T14:00:00-0500",
      "description": "Orange Tree Imports offers samples of one of our lines of specialty foods on the first Saturday of every month. Stop in to see what we have in store for June!",
      "cover": {
        "offset_x": 67,
        "offset_y": 0,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/18582444_10154418511022651_1051852291764305145_n.jpg?oh=f3048264a093621c7088fc8dd7008af7&oe=5AD1E674",
        "id": "10154418511022651"
      },
      "start_time": "2017-06-03T12:00:00-0500",
      "name": "June Sampling Saturday",
      "id": "133695620521393"
    },
    {
      "end_time": "2017-05-06T14:00:00-0500",
      "description": "Our Sampling Saturday usually focuses on our food lines, but this year in honor of Mother's Day we've invited our representatives for Crabtree & Evelyn and The Thymes to come demonstrate their lines of bath and body products. Enter a prize drawing for a deluxe gift basket for Mother's Day!",
      "cover": {
        "offset_x": 0,
        "offset_y": 0,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-8/q90/s720x720/18156667_10154340618677651_3788949207307073867_o.jpg?oh=e96f821ef761a5b3319004cdf0b6ccd3&oe=5A9015C5",
        "id": "10154340618677651"
      },
      "start_time": "2017-05-06T12:00:00-0500",
      "name": "May Sampling Saturday - Hand Care",
      "id": "1218664678232181"
    },
    {
      "end_time": "2017-05-02T20:00:00-0500",
      "description": "Engaged couples are invited to come see all that Monroe Street has to offer! Sponsored by Orange Tree Imports, Madison's Buy Local Gift Registry, this special event will feature demonstrations, tastings and drawing for many prizes including an overnight stay at HotelRED. Please RSVP by calling 255-8211.",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/18157630_10154340677122651_7168018245806034472_n.jpg?oh=eb755afdde54483080f029141c1f0be8&oe=5A8BD287",
        "id": "10154340677122651"
      },
      "start_time": "2017-05-02T18:00:00-0500",
      "name": "Monroe Street Wedding Showcase",
      "id": "642029962663467"
    },
    {
      "end_time": "2017-04-08T17:30:00-0500",
      "description": "The deadline for our 39th Annual Egg Art Contest is Saturday, April 8 at 5:30.  Come see all the entries in our window the week before Easter!

The rules: Eggs may be decorated in any style, but must be real chicken eggs -- no hard-boiled. One entry per person, please.  Adults and children are encouraged to enter, and there will be prizes in many categories.   http://orangetreeimports.com/calendar.jsp",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/s720x720/17264647_10154239005352651_8222410908782804790_n.jpg?oh=f97a0017587a54c6be7cc48f137c18a2&oe=5AD3FB5A",
        "id": "10154239005352651"
      },
      "start_time": "2017-04-08T17:29:00-0500",
      "name": "39th Annual Egg Art Contest",
      "id": "365557897171023"
    },
    {
      "end_time": "2017-04-08T15:00:00-0500",
      "description": "Join us for a free drop-in demonstration of Ukrainian egg decorating with artist Pat Hall, upstairs in our Cooking School area. Orange Tree Imports carries a full line of Ukrainian egg decorating supplies!",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/p720x720/17361828_10154238994547651_5671236043608220834_n.jpg?oh=dd76df81dd12e6663371e9727f3c11ab&oe=5A9C4F47",
        "id": "10154238994547651"
      },
      "start_time": "2017-04-08T13:00:00-0500",
      "name": "Ukrainian Egg Art Demonstration",
      "id": "196212057539834"
    },
    {
      "end_time": "2017-04-01T14:00:00-0500",
      "description": "Orange Tree Imports offers samples of one of our lines of specialty foods on the first Saturday of every month. In April we'll feature a new local line, Addicting Pretzels! These delicious pretzels are made in Wind Lake, Wisconsin. Check out their story at http://www.addictingpretzels.com .",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/17553462_10154258498297651_8065930068464171347_n.jpg?oh=3e2627e5dd1712f7496b879d588d69a1&oe=5A906A88",
        "id": "10154258498297651"
      },
      "start_time": "2017-04-01T12:00:00-0500",
      "name": "April Sampling Saturday",
      "id": "1873696446201600"
    },
    {
      "end_time": "2017-03-04T14:00:00-0600",
      "description": "Orange Tree Imports offers samples of one of our lines of specialty foods on the first Saturday of every month. In March we are pleased to feature Hogan's Brown Irish Soda Bread mix. Get ready for St. Patrick's Day with this easy to use authentic mix from Ireland!",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-8/s720x720/16804205_10154184116062651_4552241416002061370_o.jpg?oh=aa6f5af60c5f074a7a7a975216ea4ac0&oe=5AD20DD5",
        "id": "10154184116062651"
      },
      "start_time": "2017-03-04T12:00:00-0600",
      "name": "March Sampling Saturday: Irish Soda Bread",
      "id": "2240388979518777"
    },
    {
      "end_time": "2017-02-26T14:00:00-0600",
      "description": "Join us for our popular mid-winter clearance sale! There will be bargains from throughout the store.",
      "cover": {
        "offset_x": 0,
        "offset_y": 50,
        "source": "https://scontent.xx.fbcdn.net/v/t1.0-9/p720x720/16711667_10154160747567651_7454247341395521742_n.jpg?oh=1ac0b5290920bd89b658986740a2719b&oe=5A8FD521",
        "id": "10154160747567651"
      },
      "start_time": "2017-02-17T11:00:00-0600",
      "name": "Orange's Lemon Sale",
      "id": "1743563752625202"
    },
    {
      "end_time": "2017-02-04T14:00:00-0600",
      "description": "Orange Tree Imports offers samples of one of our lines of specialty foods on the first Saturday of every month. We are pleased to feature Wisconsin's own Smokin' Ts hand crafted smoked tomato sauce on February 4.  Originally created as a vinaigrette, this unique sauce can be used for everything from a salad dressing to a marinade. Check out their web site at  www.smokin-ts.com.",
      "cover": {
        "offset_x": 0,
        "offset_y": 51,
        "source": "https://scontent.xx.fbcdn.net/v/t31.0-8/s720x720/16178578_10154114941957651_1283322881686100847_o.jpg?oh=4bb91fdee355ded52d4686ee5c489893&oe=5AD776BF",
        "id": "10154114941957651"
      },
      "start_time": "2017-02-04T12:00:00-0600",
      "name": "February Sampling Saturday: Smokin' Ts",
      "id": "384460288578977"
    }
  ],
  "paging": {
    "cursors": {
      "before": "QVFIUjRUaUw3U18yMXNtUVhBcTg3eEYxNUdDX0ZAJN3BPQ3NVdnUxUm03cVZAjd1BwU3ZAUV0hLckNITE9Gc1RSSmNkbGd6dVJMTHhxSTBHbW95NkVPS09NdkdB",
      "after": "QVFIUmJ6eHRJdjhmcE14REZAZAeUY0bUU4THN0S3FWRU5TbFFWaDNLSEN2ZAlhQa3EwVWxxM3dlMEx1WERzaHJubTA1LTV5aFg2OFVvME84d3pET0kyaDNQRWZAn"
    },
    "next": "https://graph.facebook.com/v2.11/41474727650/events?access_token=nnn&pretty=0&fields=event_times%2Cend_time%2Cdescription%2Ccover%2Cstart_time%2Cname%2Cid&limit=25&after=QVFIUmJ6eHRJdjhmcE14REZAZAeUY0bUU4THN0S3FWRU5TbFFWaDNLSEN2ZAlhQa3EwVWxxM3dlMEx1WERzaHJubTA1LTV5aFg2OFVvME84d3pET0kyaDNQRWZAn"
  }
}
	 */
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dcm-fb-listing");
		
		List<XNode> children = this.children;
		
		this.clearChildren();
		
		String alternate = StackUtil.stringFromSource(state, "Alternate");
		
		long count = StackUtil.intFromSource(state, "Max", 8);
		
		XElement fbsettings = ApplicationHub.getCatalogSettings("Social-Facebook", alternate);
		
		if (fbsettings != null) {
			String cname = StringUtil.isNotEmpty(alternate) ? "FacebookEvents-" + alternate + "-" + count : "FacebookEvents-" + count;
			
			Site site = OperationContext.getOrThrow().getSite();;
			
			ListStruct list = (ListStruct) site.getCacheEntry(cname);
			
			if (list == null) {
				list = new ListStruct();
				
				try {
					// TODO may need to load a lot more than Max so we can get upcoming events - unless FB has a way to reverse?
					URL url = new URL("https://graph.facebook.com/" + fbsettings.getAttribute("NodeId") +
							"/events" + "?access_token=" + fbsettings.getAttribute("AccessToken") +
							"&fields=event_times,end_time,description,cover,start_time,name,id,place" +
							"&limit=" + (count + 20));
					
					RecordStruct res = (RecordStruct) CompositeParser.parseJson(url);
					
					if (res == null) {
						Logger.error("Empty Facebook response");
						return;
					}
					
					ListStruct fblist = res.getFieldAsList("data");
					
					DateTimeFormatter incomingFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
					DateTimeFormatter outgoingFormat = DateTimeFormatter.ofPattern("MMM dd yyyy, hh:mm a", java.util.Locale.getDefault());
					long now = TimeUtil.now().getLong(ChronoField.INSTANT_SECONDS);
					int cnt = 0;
					
					// TODO after filtering the past events, reverse the order
					
					for (int n = fblist.size() - 1; n >= 0; n--) {
						if (cnt >= count)
							break;
						
						RecordStruct entry = fblist.getItemAsRecord(n);
						
						// TODO only filter if using default fields
						if (entry.isFieldEmpty("description"))
							continue;
						
						TemporalAccessor start = incomingFormat.parse(entry.getFieldAsString("start_time"));
						TemporalAccessor end = entry.isNotFieldEmpty("end_time") ? incomingFormat.parse(entry.getFieldAsString("end_time")) : start;
						
						if (now > end.getLong(ChronoField.INSTANT_SECONDS))
							continue;
						
						cnt++;
						
						entry
								.with("Start", start)
								.with("End", end)
								.with("StartFmt", outgoingFormat.format(start))
								.with("EndFmt", outgoingFormat.format(end))
								.with("DescriptionMD", UIUtil.regularTextToMarkdown(entry.getFieldAsString("description")));
						
						list.with(entry);
					}
					
					// TODO switch to cache by id/page path rather than the more global cname - FB Feed too
					Logger.info("Facebook events cache set for: " + site.getTenant().getAlias() + " - " + site.getAlias());
					
					site.withCacheEntry(cname, list, StringUtil.parseInt(fbsettings.getAttribute("CachePeriod"), 900));
				}
				catch (Exception x) {
					Logger.error("Unable to load Facebook events.");
					Logger.error("Detail: " + x);
				}
			}
			
			boolean hastemplate = false;
			
			if (children != null) {
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i) instanceof XElement) {
						hastemplate = true;
						break;
					}
				}
			}
			
			/*
			stamp.append('<i>' + dc.util.Date.zToMoment(item.Posted).format('MMM D\\, h:mm a') + '</i>');
			
			 */

			// TODO cleanup some, make into <ul>
			if (! hastemplate) {
				children = new ArrayList<>();
				
				children.add(W3.tag("div")
						.withClass("dcm-fb-entry")
						.with(W3.tag("div")
							.withClass("dcm-fb-header")
							.with(W3.tag("div")
										.withClass("dcm-fb-stamp")
										.with(W3.tag("div")
												.with(W3.tag("b")
														.withText("{$Entry.name}"),
													W3.tag("i")
														.withText("{$Entry.StartFmt}")
												)
										)
							),
							W3.tag("div")
									.withClass("dcm-fb-body")
									.with(
											If.tag()
													.attr("Target", "$Entry.cover.source")
													.attr("IsEmpty", "false")
													.with(
															Out.tag().with(
																	W3.tag("img")
																			.withClass("dcm-fb-picture")
																			.attr("src","{$Entry.cover.source}")
															)
													),
											If.tag()
												.attr("Target", "$Entry.description")
												.attr("IsEmpty", "false")
												.with(
														Out.tag().with(
																TextWidget.tag()
																	.withClass("dcm-fb-message")
																	.with(XElement.tag("Tr")
																		.withText("{$Entry.DescriptionMD}")
																	)
														)
												),
											W3.tag("div")
													.withClass("dcm-fb-link")
													.with(W3.tag("a")
															.attr("target", "_blank")
															.attr("href","https://www.facebook.com/events/{$Entry.id}/")
															.withText("View on Facebook - Share")
													)
									)
					)
				);
			}
			
			List<XNode> fchildren = children;
			int cidx = 0;

			for (Struct ent : list.items()) {
				RecordStruct entry = (RecordStruct) ent;
				
				// setup image for expand
				StackUtil.addVariable(state, "entry-" + cidx, entry);
				
				// switch images during expand
				XElement setvar = Var.tag()
						.withAttribute("Name", "Entry")
						.withAttribute("SetTo", "$entry-" + cidx);
				
				this.with(setvar);
				
				//this.with(Console.tag().withText("{$Entry}"));
				//this.with(Console.tag().withText("------"));
				
				// add nodes using the new variable
				List<XNode> template = XmlUtil.deepCopyChildren(fchildren);
				
				this.withAll(template);
				
				cidx++;
			}
		}
		else {
			this.withText("Missing FB configuration");
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("ul");
    }
}
