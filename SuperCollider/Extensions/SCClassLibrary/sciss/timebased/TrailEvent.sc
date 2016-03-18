/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependacies: BasicEvent, Span, ???
 *
 *	@version	0.1, 31-Mar-06
 *	@author	Hanns Holger Rutz
 */
TrailEvent : BasicEvent {
	classvar <kModified	= 0;
	
	var t, span;

	*new { arg t, source, affectedSpan;
		^super.new( source, kModified, Main.elapsedTime ).prInitTrailEvent( t, affectedSpan );
	}
	
	prInitTrailEvent { arg trail, affectedSpan;
		t			= trail;
		span		= affectedSpan;
	}
	
	getTrail {
		^t;
	}

	getAffectedSpan {
		^span;
	}
	
	incorporate { arg oldEvent;
		if( oldEvent.isKindOf( TrailEvent ) and:
			{ this.getSource == oldEvent.getSource and:
			{Êthis.getID == oldEvent.getID and:
			{Êthis.getTrail == oldEvent.getTrail }}}, {
			
			span	= Span.union( this.getAffectedSpan, oldEvent.getAffectedSpan );
			^true;
		});
		^false;
	}
}