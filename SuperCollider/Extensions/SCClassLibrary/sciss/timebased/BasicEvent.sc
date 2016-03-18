/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	SuperCollider implementation of the java class de.sciss.app.BasicEvent
 *
 *  @version	0.1, 31-Mar-06
 *  @author		Hanns Holger Rutz
 */

BasicEvent {
	var source, id, when;

	*new { arg source, id, when;
		^super.new.prInitBasicEvent( source, id, when );
	}
	
	prInitBasicEvent { arg argSource, argID, argWhen;
		argSource	= source;
		id			= argID;
		when   		= argWhen;
	}
	
	getSource {
		^source;
	}
	
	getID {
		^id;
	}

	getWhen {
		^when;
	}

	// abstract	
	incorporate { arg oldEvent;
		^this.subclassResponsibility( thisMethod );
	}
}
