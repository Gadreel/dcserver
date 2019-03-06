# Datasets

A dataset may be marked as public or private. Has no real meaning other than as a warning when sharing dataset, or when a friend shares your dataset.

Data entries may be be entities (things, places, people, organizations) or events.

Entries may be marked with properties and with relationships to events.

# Data Management

## Concerns

- Privacy of each property
- Significance of each property
- Authorship of each property
	- author's can work as a team, so content can be easily associated with a group when group is the author
- Evidence of each property
- Tagging on each property
- Timestamp of each property's last edit

## Combined Data Sources

### Source Action

Author (group) defines an dataset token with level of privacy, level of significance and a title. They can allocate multiple access tokens if they believe they may wish to revoke access someday - or provide just one access token for their dataset. If public then no access token is required, just the dataset token. If the author is on a different server than user provide the url of the author's database.

If a dataset is dependent on other datasets then you are shown the dependencies with a notice for any private datasets included in the list.  

### Destination Action Different Server

Add the URL to their server, enter the access or dataset token, define the following optional filters:

- significance (meaningless if lower than source)
- privacy (meaningless if lower than source)
- tags (only accept certain tags)
- evidence (only accept certain evidence)
- geographical region
- min / max date

Suggest not filtering sources unless the source is huge and little of the data is desired. Generally prefer to filter at the View level. Then continue with Destination Action Same Server.

### Destination Action Same Server

No filter is available because the data is already present, filter is only for managing data size.

Admins of the server manage a list of access/dataset tokens and titles. They define what views access the datasets.

## Views

A View is an index of the sources that provides high performance data retrieval. Each view allows user to select one or more sources, listed in order of trust. Each source may be filtered:

- significance (meaningless if lower than source)
- privacy (meaningless if lower than source)
- tags (only accept certain tags)
- evidence (only accept certain evidence)
- geographical region
- min / max date

Filtering is done on the locally cached datasets as the view index is built and updated.

Access to a view is assigned to individual users or to user groups.

Significance adjustment. Each dataset may have a significance adjustment as it comes into the view so that some datasets are less important and others are more important than the baseline. For example a Family History view may add significance to the family member datasets that are linked into the view.

## Users

After login may select and access a view. View will restore the last position accessed, or to view defaults.




# Data Structure

Data may be be entities (things, places, people, organizations) or events.

## Inception and Culmination Properties

All entities have an Inception and Culmination property. An without a Culmination is considered ongoing. A Culmination of best guess can be used - just a year.

If there is concern that the entity ever truly existed then the evidence should go in the inception property.

Inception is special in that if Inception fails the filter test for a View then the entire entity is removed from that view. Other properties are independent and may fail the filter without removing the entity from the view.

## Events

Data should be event oriented when possible, rather than thing oriented.

### Kinds of Events

- Interaction (debate, attack, meet, romance)
- Start of Relationship (friendship, membership or employment [subservient], union or merger or contract/bond [purchase, be purchased - land for example])
- End of Relationship
- Journey / movement (with a path)

Relationships can be inferred from events - duration of the relationship is not inferred from one event nor should an event be entered to reflect duration of a relationship. Rather duration of an event is merely for the period of time that the meeting, purchase, or date lasted.

### Roles

Leads - who all was the leads of the event
Target - who all was the target of the event
Participant - TODO.
Present - who all was present at the event. Present is defined as someone who was able to (or reasonably could have) meaningfully engage the circumstances of the event.
Witness - who had real time information about the event but was not able to meaningfully engage the circumstances of the event.

Each role may have start and end times (or duration). If the event is a journey and if the role is participant then it is assumed typically that the participant is on the same part of the path as defined in the event. However, participant can be marked as remote and then not assumed as at the give path.

### Reactions

A reaction may or may not occur at the same time as the event. Participants can have reactions to event at same time they are participating. So reactions are separate from roles.

### Significance

Significance is relative to world history. However, each reaction also has a significance to the entity having a reaction.

## Examples

TODO

### Scenarios

- how does change of address look
- romantic date
- assault
- job promotion, becoming pope or king
- go to movie
- go to museum
- attend party
- go to art opening


political action

- Marches
	A March is a public gathering to bring attention to a specific issue or issues. The main purpose of a march is public awareness, which can be connected to specific actions such as policy change. A March starting point and end point usually have significance and direct relation to the issues being addressed, such as government buildings, embassies, corporate headquarters, etc. Most Marches either begin, end, or are bookended by a Rally with live speakers.

- Actions

	An Action is a direct and physical act to promote and catalyze specific changes in your community and governance. Actions can be organized through your Huddle, School, Chapter or Community. Here are some examples of Actions:

	- Sit-in
	- Strike
	- Walkout
	- Rally
	- Community Service
	- Meeting with members of your government
	- Guest Speaker
	- Booth

- Salons

	A Salon is an intimate gathering, a “convening” with 100 or fewer people that should occur frequently, monthly or quarterly.

	The purpose of a Women's March Salon is to foster an understanding of Women's March, highlight local innovation and impact stories in your region and keep the community connected between the less frequent larger local events and Women's March Summits.

	Women's March Salons should strive to discover and present great local speakers in the community to speak on key issues. All salons have at least one live speaker.

- Vigils

	A vigil is an informal gathering or meetup in respect of a specific issue, cause or tragedy. A Vigil is usually held in the evening at a public space and are often held at a location relevant to the specifics of the Vigil.

	If appropriate, attendees can bring candles. Phone lights are a good and safe alternative.
	If small enough, it is recommended that speakers not use a megaphone to keep the Vigil intimate.

- Huddles
	A Huddle is a gathering of a small group of friends, family, neighbors and fellow organizers, providing a guide and a space to meet. Your role as a Chapter leader and/or huddle host is a critical part of how we keep the Women's March spirit alive, build the movement beyond those who marched, and set a concrete plan of action.

- Conferences
	A Women’s March Conference is a large, potentially multi-day event focused on a number of issues of interest to the local community. There will be main speakers or discussions as well as smaller, more specific issue-based breakout discussions with speakers. Your Conference can be organized with other Chapters in your country or region and/or with partner organizations as well.

### Questions

- Did [person] [do - any event] anything [interesting - significance] while in [city/country] when visiting in [year/time].
- What did [person] [do - any event] while in [city/country] when visiting in [year/time].
- Did [person] [meet - interact event] anyone while in [city/country] when visiting in [year/time].
- Did [person] [meet - interact event] [person] while in [city/country] when visiting in [year/time].
- Did [person] [visit/see - view event] [place] while in [city/country] when visiting in [year/time].
- Did [person] [attend] [event - attend event] while in [city/country] when visiting in [year/time].


# Family Legacy

How does data ownership and access change from generation to generation? When a family history dataset is no longer accessible by the original party, a local admin (on a different server) can unlink a remote dataset (mark it as no longer the master source) or switch it to another server.

Users cannot (or should not) edit someone else's dataset directly, but rather they should edit in their own dataset as overrides to the "parent" dataset. Then a dataset can be marked as dependent on another data set.

# UI Notes

Search - by subject(s), category or keyword. Ex. Stan [entity in database] Vacation [type/cat of event] John [word in description].

Column - can be individual entity or a grouping of entities

Entity Significance:

1. world/empire
2. Country
3. state/region
4. township/county
5. neighborhood

Event Significance

1. Life changing/major
2. primary Milestones
3. secondary Milestones
4. valued routine
5. minor routine

Icons - based on tags (tags can be suggested from keywords)

Events can be related to events, part of an event, a role and level of significance within the event (another kind of significance chart for that).

# Chronologica Examples

```
<timeline xmlns="http://eTimeline.org/schema2003/timeline" synchronized="2004/9/5" database="634DF854EE034811B8134A60E68AB789">
  <author id="FA9302EE097A42B0A6282D3C2A18D3E8" stamp="2004/9/5" status="altered" />
  <entities>
  <person id="4CB94830B4FB4F3CB2A283D825D3C2DC">
	<properties>
	  <name id="0082BB6F486A44A299A2D9C776C755BB">
		<value>Pope Urban VIII</value>
	  </name>
	  <inception id="8780F90D1C7C4C95978E76897AA0C517">
		<date level="9">1568/04</date>
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </inception>
	  <generic id="0534912ECCD94C1FB08A20923D7F6C5E" name="pob">
		<date level="9">1568/04</date>
		<value>Florence Italy</value>
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </generic>
	  <allegiance id="99ADB249263A4B09AA37740485F53F74">
		<value>813684ED875A4C8A946FDBFCC404A5E6</value>
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </allegiance>
	  <occupation id="9EE9342109714DA6A4E20D25C6125259">
		<date>1623/08/06</date>
		<value>Pope</value>
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </occupation>
	  <generic id="3BA0C3962D164A95B0CB78E32AE3C35D" name="rank">
		<date level="9">1623/08/06</date>
		<value>Pope</value>
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </generic>
	  <culmination id="95C686A2B12E4391863E328527764EE4">
		<date level="9">1644/07/04</date>
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </culmination>
	  <position id="DA17F3E3C3E742608F25896E27842426">
		<date level="9">1623/08/06</date>
		<path xmlns="http://eTimeline.org/schema2003/path" location="12.5,-41.8" level="0" />
		<evidence ref="AE7D19A8D651406BAAB8A07E5D74133E" />
	  </position>
	  <event id="5CF3BE2D564C4A568C02CC7B29E31DC8">
		<date level="6">1624/12/31</date>
		<event xmlns="http://eTimeline.org/schema2003/event">
		  <category id="B2585B5D8DD54172B422967532F4B45C" value="10" los="4" />
		  <media>
			<text>Pope Urban VIII angered by Tobacco from the New World - he threatens to Excommunicate snuff users.</text>
		  </media>
		</event>
		<evidence ref="5F657A88774B4A3390C94D34ECDB18DD">
		  <notes>p54</notes>
		</evidence>
	  </event>
	</properties>
  </person>
```

```
<person id="CA1C49F65FA1463583EB04ABF55F5FBC">
   <properties>
   <displayname id="915871F38E4E420D99FE0DC1EA3D9788">
      <value>Mara</value>
   </displayname>
   <name id="88EFF522821847939FD3BD7419CB65AA">
      <value>Marena</value>
   </name>
   <inception id="EA3B31B0D5274862BDEE20B6027B4F6A">
      <date>1893</date>
   </inception>
   <culmination id="9AA7884093D84371BBE4010BD3F33CDC">
      <date>1977</date>
   </culmination>
   <parents id="2178CC4384AE4823B8C0F8A1F98B8D21">
      <mother name="Laura" level="0"/>
      <father level="0"/>
   </parents>
   <spouse id="41685E824668454FB86EC891CA3FFA0E">
      <spouse refid="4FF263060BAB48F6B99D1ACD99EB4610" level="0"/>
   </spouse>
   </properties>
</person>
```

```
<landscape id="77B59F2DD6F34832AA626963C21A91B0">
   <properties>
   <name id="E2BF254D9E614AD794148AD4C7E52973">
      <value>gm crops</value>
   </name>
   <inception id="5F6D8199DFDF4CB5AD398E0F4FAC263F">
      <date>1994/01/01</date>
   </inception>
   <landscape id="E434A395B5874A22AC3FEA730EAF06F2">
      <date>1994/01/01</date>
      <sketch xmlns="http://eTimeline.org/schema2003/sketch" level="0">
         <polygon color="limergreen" labelcolor="black">-87.5,-41.6265678405762 -87.8125,-42.001148223877 -88.125,-42.789852142334 -87.8125,-43.4319229125977 -87.4375,-44.3823890686035 -88.125,-44.561710357666 -89.8125,-44.8296546936035 -92.3125,-44.6511611938477 -95,-44.6064529418945 -96.875,-44.337474822998 -99.0625,-43.9316787719727 -98.8125,-42.3735389709473 -98.3125,-41.3441925048828 -98.125,-40.106388092041 -98.4375,-38.7477149963379 -98.375,-37.1126098632812 -99.0625,-35.4925880432129 -100.25,-33.9956321716309 -100.4375,-32.2064361572266 -97.625,-31.1371269226074 -96.5625,-29.6742649078369 -95.125,-29.7288341522217 -92.875,-30.1099853515625 -90.75,-30.2728881835937 -88.625,-30.6519451141357 -86,-30.3813419342041 -84.875,-33.5255737304687 -86.5625,-35.2877311706543 -84.0625,-38.5514602661133 -83.625,-40.823314666748 -83.6875,-41.7672920227051 -86.1875,-42.1876182556152 -86.5625,-41.57958984375</polygon>
      </sketch>
   </landscape>
   <landscape id="4FF195AAECDF4A24B3AD0B98CF3E1807">
      <date>1995/01/01</date>
      <sketch xmlns="http://eTimeline.org/schema2003/sketch" level="0">
         <polygon color="limergreen" labelcolor="black">-87.5,-41.6265678405762 -87.8125,-42.001148223877 -88.125,-42.789852142334 -87.8125,-43.4319229125977 -87.4375,-44.3823890686035 -88.125,-44.561710357666 -89.8125,-44.8296546936035 -92.3125,-44.6511611938477 -95,-44.6064529418945 -96.875,-44.337474822998 -99.0625,-43.9316787719727 -98.8125,-42.3735389709473 -98.3125,-41.3441925048828 -98.125,-40.106388092041 -98.4375,-38.7477149963379 -98.375,-37.1126098632812 -99.0625,-35.4925880432129 -100.25,-33.9956321716309 -100.4375,-32.2064361572266 -97.625,-31.1371269226074 -96.5625,-29.6742649078369 -95.125,-29.7288341522217 -92.875,-30.1099853515625 -90.75,-30.2728881835937 -88.625,-30.6519451141357 -86,-30.3813419342041 -84.875,-33.5255737304687 -86.5625,-35.2877311706543 -84.0625,-38.5514602661133 -83.625,-40.823314666748 -83.6875,-41.7672920227051 -86.1875,-42.1876182556152 -86.5625,-41.57958984375</polygon>
         <polygon color="darkredishbrown" labelcolor="black">-95.0625,-35.5948257446289 -95.375,-34.9279747009277 -95.1875,-33.3683166503906 -94.25,-32.7364463806152 -93.4375,-33.5779304504395 -93.6875,-34.8248977661133</polygon>
         <polygon color="darkredishbrown" labelcolor="black">-94.6875,-42.8819885253906 -95.0625,-41.2025451660156 -94.0625,-40.2503852844238 -93.125,-41.0605888366699 -92.9375,-42.1410522460937</polygon>
         <polygon color="darkredishbrown" labelcolor="black">-88.4375,-40.2503852844238 -88.375,-39.4788703918457 -88.3125,-38.2067260742188 -87,-38.6005744934082 -86.375,-39.6241912841797 -87.125,-40.2024192810059</polygon>
      </sketch>
   </landscape>
   ...
   <landscape id="AE29FC81846F4A35B72D92325C17CC98">
      <date>2000/01/01</date>
      <sketch xmlns="http://eTimeline.org/schema2003/sketch" level="0">
         <polygon color="limergreen" labelcolor="black">-87.5,-41.6265678405762 -87.8125,-42.001148223877 -88.125,-42.789852142334 -87.8125,-43.4319229125977 -87.4375,-44.3823890686035 -88.125,-44.561710357666 -89.8125,-44.8296546936035 -92.3125,-44.6511611938477 -95,-44.6064529418945 -96.875,-44.337474822998 -99.0625,-43.9316787719727 -98.8125,-42.3735389709473 -98.3125,-41.3441925048828 -98.125,-40.106388092041 -98.4375,-38.7477149963379 -98.375,-37.1126098632812 -99.0625,-35.4925880432129 -100.25,-33.9956321716309 -100.4375,-32.2064361572266 -97.625,-31.1371269226074 -96.5625,-29.6742649078369 -95.125,-29.7288341522217 -92.875,-30.1099853515625 -90.75,-30.2728881835937 -88.625,-30.6519451141357 -86,-30.3813419342041 -84.875,-33.5255737304687 -86.5625,-35.2877311706543 -84.0625,-38.5514602661133 -83.625,-40.823314666748 -83.6875,-41.7672920227051 -86.1875,-42.1876182556152 -86.5625,-41.57958984375</polygon>
         <polygon color="darkredishbrown" labelcolor="black">-95.4231948852539,-37.8601112365723 -97.6332321166992,-37.659610748291 -98.3699035644531,-37.1332931518555 -99.0673980712891,-35.4791603088379 -100.227272033691,-34.0192604064941 -100.431037902832,-32.2084846496582 -97.6410675048828,-31.1433200836182 -96.5595626831055,-29.6771545410156 -95.1253890991211,-29.7210140228271 -92.8526611328125,-30.1094856262207 -90.6974945068359,-30.278657913208 -88.6598739624023,-30.6608638763428 -86.0031356811523,-30.3789100646973 -85.4780578613281,-32.9039726257324 -85.6191253662109,-33.7623672485352 -86.7633209228516,-33.843822479248 -87.8291549682617,-34.081916809082 -89.1875,-34.2037200927734 -91.4375,-36.1040382385254 -92.3824462890625,-37.640811920166 -92.8683395385742,-38.5681304931641 -94.4357376098633,-37.9478302001953</polygon>
         <polygon color="darkredishbrown" labelcolor="black">-97.1316604614258,-44.3012161254883 -99.0438842773437,-43.9503364562988 -98.8087768554687,-42.3839225769043 -98.322883605957,-41.3312911987305 -98.1347961425781,-40.140811920166 -98.4326019287109,-38.7623672485352 -98.3699035644531,-37.1332931518555 -97.625,-37.6616821289063 -94.4375,-37.9594802856445 -92.6875,-38.6496543884277 -91.6614379882813,-39.1257743835449 -90.9717864990234,-40.7297859191895 -89.9059524536133,-43.0919418334961 -89.8040771484375,-44.8275299072266 -91.0501556396484,-44.7398109436035 -92.2257080078125,-44.664623260498 -93.1818161010742,-44.6520919799805 -95,-44.5894355773926</polygon>
         <polygon color="darkredishbrown" labelcolor="black">-89.9529800415039,-42.9728927612305 -90.5642623901367,-41.6571044921875 -91.4375,-39.6241912841797 -91.6457672119141,-39.1257743835449 -92.8683395385742,-38.5743980407715 -92.3824462890625,-37.6470794677734 -91.7633209228516,-36.6320419311523 -91.442008972168,-36.1119918823242 -89.2006301879883,-34.2072296142578 -87.8291549682617,-34.0944480895996 -85.6896514892578,-34.3638725280762 -86.5673980712891,-35.3037223815918 -85.3605041503906,-36.8951988220215 -84.0438842773437,-38.5493354797363 -83.7931060791016,-39.9528427124023 -83.6206893920898,-40.9052238464355 -83.6520385742188,-41.7573547363281 -86.1755447387695,-42.1583557128906 -86.5830688476563,-41.5944480895996 -87.5391845703125,-41.6571044921875 -87.7899703979492,-41.9829177856445 -88.1191253662109,-42.7723922729492 -87.4373016357422,-44.3638725280762 -88.1269607543945,-44.576904296875 -89.7883987426758,-44.8275299072266</polygon>
      </sketch>
   </landscape>
   </properties>
</landscape>
```

```
<event id="5D2057D374DC4D6286136E69DAF18F43">
   <date>1763/02/10</date>
   <event location="W75:00,N40:00" xmlns="http://eTimeline.org/schema2003/event">
	  <title>Peace of Paris 1763</title>
	  <type level="0">proclamation</type>
	  <category id="B2585B5D8DD54172B422967532F4B45C" value="10" los="10"/>
	  <media>
		 <text lines="1" align="top">Peace of Paris gives Canada and all French territory East of the Missippi River to England. Flordia is exchanged for Havana Cuba and gives Spain all of Louisiana Territory</text>
		 <present file="present\presentations\EventPresLib.cpml" scene="003A7725E3F74FC0A7B937E7D4E20144"/>
	  </media>
   </event>
</event>
<event id="D708F6479623481A80618CDD84D9F2DC">
  <date level="7">1763/10/07</date>
  <event location="W00:02:48:75,N51:30:34:73" xmlns="http://eTimeline.org/schema2003/event">
	<title>Proclamation of 1763</title>
	<type level="5">proclamation</type>
	<category id="B2585B5D8DD54172B422967532F4B45C" value="30" los="30" />
	<media>
	  <text>Proclamation of 1763 disallows English settlement west of the Appalachian Mountains (the demarcation line). This land is to be Indian Territory.</text>
	  <present file="present\presentations\EventPresLib.cpml" scene="F15EE61BE5E5490B8DCA7764B20D78CB"/>
	</media>
  </event>
  <evidence ref="67E32C2963F44EFBAE82249C660607F4">
	<notes>p. 98</notes>
  </evidence>
</event>
<event id="37B3A37C9A874345940E601600A5FACD">
  <date level="7">1765/03/22</date>
  <event location="W00:02:48:75,N51:30:34:73" xmlns="http://eTimeline.org/schema2003/event">
	<title>Stamp Act</title>
	<type level="7">proclamation</type>
	<category id="B2585B5D8DD54172B422967532F4B45C" value="30" los="30" />
	<media>
	  <text lines="1" align="top">English Parliament passes the Stamp Act to tax the American Colonies to help pay for the War. Widespread opposition to "Taxation without Representation" results in organized resistance (Sons of Liberty).</text>
	  <present file="present\presentations\EventPresLib.cpml" scene="BC16125E2E0C43C6856E31E1E38F4D4B"/>
	</media>
  </event>
  <evidence ref="67E32C2963F44EFBAE82249C660607F4">
	<notes>p. 100</notes>
  </evidence>
</event>
```