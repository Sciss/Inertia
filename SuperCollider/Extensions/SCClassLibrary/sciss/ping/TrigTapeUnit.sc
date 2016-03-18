/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies:	TapeUnit
 */
TrigTapeUnit : TapeUnit {

	*new { arg server;
		^super.new( server );
	}

	getAttributes {
		^nil;
	}
}