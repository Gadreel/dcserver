# Doc Goals

## Files

All files get turned into JSON output like dynamic page loading does, through rpc calls to /dc/core/docs/view services. They are always considered to be MJS mode, no need to name them _mjs.

--First looks for .html files and uses a modified DynamicOutputAdapter.java and then looks for .[locale].md files and modified MarkdownOutputAdapter.java--

## New Link Protocols

- dc-docs://main/  for /docs related
- dc-docs://lib/:  for /script/lib related
- dc-docs://service/:  for /script/service related
- dc-docs://work/:  for /script/work related
- dc-docs://class/:  for /script/class related
- dc-docs://comm/:  for /communicate related

## Flow

### //service   [path to service .v folder]

Sources:

- look for a [path].v/summary.[locale].md file, go deep but pick first
- [optional] look for the detail MD as well, go deep but pick first
- [optional] look for the addendum MD as well, go deep, take all - top most first
- look for schema.xml
- look for access.json

Assembly:

- service name w/ link to parent and callable path
- access
- summary
- each addendum, topmost first
- schema
- detail

### //service   [path to service parent]

Sources:

- [optional] look for a [path]/summary.[locale].md file, go deep but pick first
- each sub folder
  	- look for a [path]/[subfolder].v/summary.[locale].md file, go deep but pick first
  	- look for a [path]/[subfolder]/summary.[locale].md file, go deep but pick first
- [optional] look for the detail MD as well, go deep but pick first
- [optional] look for the addendum MD as well, go deep, take all - top most first
- look for access.json

Assembly:

- folder name w/ link to parent
- access
- summary
- each addendum, topmost first
- detail
- each service (.v) subfolder name and summary w/ link
- each general (not .v) subfolder name and summary w/ link

### //comm   [path to comm .v folder]

Sources:

- look for a [path].v/summary.[locale].md file, go deep but pick first
- [optional] look for the detail MD as well, go deep but pick first
- [optional] look for the addendum MD as well, go deep, take all - top most first
- look for config.json
- look for schema.xml
- look for access.json

Assembly:

- template name w/ link to parent and callable path
- access and config info
- summary
- each addendum, topmost first
- schema
- detail

### //comm   [path to comm parent]

Sources:

- [optional] look for a [path]/summary.[locale].md file, go deep but pick first
- each sub folder
  	- look for a [path]/[subfolder].v/summary.[locale].md file, go deep but pick first
  	- look for a [path]/[subfolder]/summary.[locale].md file, go deep but pick first
- [optional] look for the detail MD as well, go deep but pick first
- [optional] look for the addendum MD as well, go deep, take all - top most first
- look for access.json

Assembly:

- folder name w/ link to parent
- access
- summary
- each addendum, topmost first
- detail
- each comm (.v) subfolder name and summary w/ link
- each general (not .v) subfolder name and summary w/ link

### //lib   [path to lib file .dcs.xml]

Sources:

- look for a [path].[locale].md file, same folder and same level

Assembly:

- script file name w/ link to parent and copyable path
- detail from MD file

### //lib   [path to lib parent]

Sources:

- [optional] look for a [path]/summary.[locale].md file, go deep but pick first
- each script (dcs) file
- each sub folder
  	- look for a [path]/[subfolder]/summary.[locale].md file, go deep but pick first
- [optional] look for the detail MD as well, go deep but pick first
- [optional] look for the addendum MD as well, go deep, take all - top most first

Assembly:

- folder name w/ link to parent
- summary
- each addendum, topmost first
- detail
- each script (.dcs) name
- each general (not .v) subfolder name and summary

### //work   [path to work file .dcs.xml]

Sources:

- look for a [path].[locale].md file, same folder and same level

Assembly:

- script file name w/ link to parent and copyable path
- detail from MD file

### //work   [path to work parent]

Sources:

- [optional] look for a [path]/summary.[locale].md file, go deep but pick first
- each script (dcs) file
- each sub folder
  	- look for a [path]/[subfolder]/summary.[locale].md file, go deep but pick first
- [optional] look for the detail MD as well, go deep but pick first
- [optional] look for the addendum MD as well, go deep, take all - top most first

Assembly:

- folder name w/ link to parent
- summary
- each addendum, topmost first
- detail
- each script (.dcs) name
- each general (not .v) subfolder name and summary


### //class   [path to class .v folder]

Sources:

TODO

Assembly:

TODO

### //class   [path to class parent]

Sources:

TODO

Assembly:

TODO

### //main   [path to doc file]

Sources:

- [optional] look for a [path].[locale].md file
- [optional] look for a [path].html file

Assembly:

- doc file name w/ link to parent and copyable path
- detail from MD file
- if there is a .html file, that will wrap the content using {$Documentation}

### //main   [path to doc folder]

Sources:

- [optional] look for a [path]/summary.[locale].md file
- [optional] look for a [path]/addendum.[locale].md file, for all levels
- [optional] look for a [path]/detail.[locale].md file

Plus optional / special
- [optional] look for a [path]/index.html file

Assembly:

- doc file name w/ link to parent and copyable path
- each addendum, top first
- detail from MD file
- if there is a index.html file, that will wrap the content using {$Documentation}

## Organization

every folder may have a `summary` and a `detail` file which helps explain the folder. Predefined folders are:

- sites\[site alias or `root`]: notable code or dependencies, links to main features  
- cms: doc related to the CMS, in general or even in specific
- communicate: highlights of the communicate/[tenant] system
- lib: highlights of the script/lib/[tenant]
- services: highlights of the script/services/[tenant]
- work: highlights of the script/work/[tenant]

Access to docs may originate from:

### CMS help / plugins
- needs to be able to include client side functions
- needs Widget like ability to reload / drill into links

Gallery Example
- customize a folder to have an extra button and function, Widget is per folder and embedded in the Browser / Chooser

### Sites

Example:

/docs/sites/portal/details.eng.md

high level description of the site's purpose and main features - links to those

### Features

Put into the most appropriate `sites` folder, but cross link as the features cross sites.

Put database description in, be able to embed database schema (by Group name, Field names, all).  Link to services, libraries, communicate.

Example:

/docs/sites/portal/features/applications.eng.md
/docs/sites/portal/features/farm-link.eng.md
/docs/sites/portal/features/farm-training.eng.md

### Threads

/communicate is self documenting, so much of the Threads related content will be in there. However, for an overview of thread type and thread pools use this:

/docs/communicate/threads.eng.md

Cross link with Features above
