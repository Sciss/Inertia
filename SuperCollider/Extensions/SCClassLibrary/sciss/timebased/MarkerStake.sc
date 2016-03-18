/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	SuperCollider implementation of the java class de.sciss.io.MarkerStake
 *
 *	Class dependancies: Stake
 *
 *	@version	0.1, 31-Mar-06
 *	@author	Hanns Holger Rutz
 */
MarkerStake : Stake {
	var <pos, <name;

	*new {Êarg pos, name;
		^super.new( Span( pos, pos )).prInitMarkerStake( pos, name );
	}

	*newFrom { arg anotherMarker;
		^this.new( anotherMarker.pos, anotherMarker.name );
	}
	
	prInitMarkerStake { arg argPos, argName;
		pos	= argPos;
		name	= argName;
	}
	
	duplicate {
		^this.class.newFrom( this );
	}

	replaceStart {Êarg newStart;
		^this.class.new( newStart, name );
	}
	
	replaceStop {Êarg newStart;
		^this.class.new( newStart, name );
	}
	
	shiftVirtual { arg delta;
		^this.class.new( pos + delta, name );
	}
	
	asString {
		^this.asCompileString;
	}
	
	asCompileString {
		^(this.class.name ++ "( "++pos++", "++name.asCompileString++" )");
	}
}