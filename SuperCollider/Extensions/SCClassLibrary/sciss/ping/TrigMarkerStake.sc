/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	@version	0.1,	14-Sep-06
 *	@author	Hanns Holger Rutz
 */
TrigMarkerStake : MarkerStake {
	var <>atk, <>rls, <>freeze, <>skip;

	*new {Êarg pos, name, atk, rls, freeze, skip = 0;
		^super.new( pos, name ).prInitTrigMStake( atk, rls, freeze, skip );
	}

	*newFrom { arg m;
		^this.new( m.pos, m.name, m.atk, m.rls, m.freeze, m.skip );
	}
	
	prInitTrigMStake { arg argAtk, argRls, argFreeze, argSkip;
		atk		= argAtk;
		rls		= argRls;
		freeze	= argFreeze;
		skip		= argSkip;
	}

	replaceStart {Êarg newStart;
		^this.class.new( newStart, name, atk, rls, freeze, skip );
	}
	
	replaceStop {Êarg newStart;
		^this.class.new( newStart, name, atk, rls, freeze, skip );
	}
	
	shiftVirtual { arg delta;
		^this.class.new( pos + delta, name, atk, rls, freeze, skip );
	}
	
	asCompileString {
		^(this.class.name ++ "( "++pos++", "++name.asCompileString++", "++atk++", "++rls++" "++freeze.asCompileString++", "++skip++ " )");
	}
}