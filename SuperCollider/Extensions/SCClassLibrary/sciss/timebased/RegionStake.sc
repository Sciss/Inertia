/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: Stake, Span
 *
 *	SuperCollider implementation of the java class de.sciss.io.RegionStake
 *
 *  @version	0.11, 08-Jun-06
 *  @author	Hanns Holger Rutz
 */
RegionStake : Stake {
	var <name;

	*new { arg span, name;
		^super.new( span ).prInitRegionStake( name );
	}

	prInitRegionStake { arg argName;
		name	= argName;
	}
	
	*newFrom { arg anotherRegion;
		^this.new( anotherRegion.getSpan, anotherRegion.name );
	}

	duplicate {
		^this.class.new( this.getSpan, name );
	}

	replaceStart { arg newStart;
		^this.class.new( Span( newStart, this.getSpan.stop ), name );
	}
	
	replaceStop { arg newStop;
		^this.class.new( Span( this.getSpan.start, newStop ), name );
	}

	shiftVirtual { arg delta;
		^this.class.new( this.getSpan.shift( delta ), name );
	}

	asString {
		^this.asCompileString;
	}
	
	asCompileString {
		^(this.class.name ++ "( "++span.asCompileString++", "++name.asCompileString++" )");
	}
}