package dcraft.web;

public enum HtmlMode {
	Static,
	Ssi,		// static + Server Side Includes
	Dynamic,	// Pui, fallback on Ssi
	Strict 		// Pui only
}
