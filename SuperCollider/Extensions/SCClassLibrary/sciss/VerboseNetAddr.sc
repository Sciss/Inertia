/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: none
 *
 *	@version	0.11, 14-Sep-06
 *	@author	Hanns Holger Rutz
 */
VerboseNetAddr : NetAddr {
	var <>dumpOutgoing	= true;
	var <>notThese;
	var <>backTrace = false;
	
	*new { arg ... args;
		var result;
		
		result = super.new( *args );
		result.notThese = IdentitySet[ '/status' ];
		^result;
	}
	
	sendMsg { arg ... args;
		if( dumpOutgoing and: {ÊnotThese.includes( args.first.asSymbol ).not }, {
			this.prDump( "s: ", args );
		});
		^super.sendMsg( *args );
	}
	
	sendBundle { arg time ... msgs;
		if( dumpOutgoing, {
			("s: [ #bundle, " ++ time ++ ",").postln;
			msgs.do({ arg msg;
				this.prDump( "     ", msg );
			});
			"   ]".postln;
		});
		^super.sendBundle( time, *msgs );
	}
	
	prDump { arg prefix, argList;
		argList = argList.collect({Êarg item; if( item.class === Int8Array, "DATA", item ); });
		(prefix ++ argList).postln;
		if( backTrace, {Êthis.dumpBackTrace });
	}
}