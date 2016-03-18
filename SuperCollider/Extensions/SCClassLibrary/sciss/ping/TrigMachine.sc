/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies:	Ping,  TrigTapeUnit, UpdateListener, Span, TrigMarkerStake, Trail
 *
 *	@version	0.11, 20-Oct-06
 *	@author	Hanns Holger Rutz
 */
TrigMachine {
	classvar <>pathNorm;
	classvar <>pathFreeze;
	classvar <>fileName;
	classvar <trail;

	var <ping, <slot, <upd, updFunc, <sr, <nextFreeze, <freezeStake, <okToUnfreeze =false;
	var <disposed = false;

	*initClass {
		pathNorm	 	= "~/scwork/ping/Merged.aif".standardizePath;
		pathFreeze 	= "~/scwork/ping/Freeze.aif".standardizePath;
		fileName		= "~/scwork/ping/trigtape.mrk".standardizePath;
	}
		
	*new { arg ping, unit, slot;
		^super.new.prInitTrig( ping, unit, slot );
	}
	
	prInitTrig { arg argPing, argUnit, argSlot;

		ping 	= argPing;
//		unit		= argUnit;
		slot		= argSlot;
		
//		TapeUnit.cachePath( pathNorm );
//		TapeUnit.cachePath( pathFreeze );
		argUnit.cachePath( pathNorm );
		argUnit.cachePath( pathFreeze );

		updFunc = { arg obj, what, value;
			var newUnit, proc;
			case
			{ what === \unitPlaying }
			{
				// XXX stop routines
			}
			{ what === \unitDisposed }
			{
				upd.removeFromAll;
				proc	= ping.getProcChain( slot ).first;
				upd = nil;
				if( proc.notNil, {
					newUnit = proc.getUnit;
					if( newUnit.isKindOf( TrigTapeUnit ), {
//						unit = newUnit;
						upd	= UpdateListener.newFor( newUnit, updFunc );
					});
				});
				if( upd.isNil, {
					disposed = true;
					if( nextFreeze.notNil, {
						nextFreeze.stop;
					});
				});
			};
		};
		
		upd = UpdateListener.newFor( argUnit, updFunc );
		argUnit.setPath( pathNorm );
		sr = argUnit.getSampleRate;
	}

	*load {
		var f, stakes, pos, name, atk, rls, freeze, skip;
		trail = Trail.new;
		"Loading...".inform;
		try {
			stakes = List.new;
			f = File( fileName, "rb" );
			while({ f.pos < f.length }, {			
				pos		= f.getInt32;
				name		= f.getPascalString;
				atk		= f.getFloat;
				rls		= f.getFloat;
				freeze	= Span( f.getInt32, f.getInt32 );
				skip		= f.getFloat;
				stakes.add( TrigMarkerStake( pos, name, atk, rls, freeze, skip ));
			});
			f.close;
//			trail.clear;
			trail.addAll( nil, stakes );
			"Done.".inform;
		}
		{ arg error;
			error.reportError;
		};
	}
	
	freeze {
		var pos, idx, delay, newAttr, proc, unit;

		proc	= ping.getProcChain( slot ).first;
		if( proc.notNil, {
			unit = proc.getUnit;
			if( unit.isKindOf( TrigTapeUnit ), {
				if( nextFreeze.isNil, {
					pos 		= unit.getCurrentPos + 8820;  // min. 200ms headroom
					idx		= trail.indexOfPos( pos );
					if( idx < 0, { idx = (idx + 1).neg; });
					freezeStake	= trail.get( idx );
					if( freezeStake.notNil, {
						delay	= (freezeStake.pos - pos) / sr;
						newAttr	= IdentityDictionary.new;
						newAttr.add( \path -> pathFreeze );
						newAttr.add( \startPos -> freezeStake.freeze.start );
						newAttr.add( \loop -> freezeStake.freeze );
						okToUnfreeze = false;
						nextFreeze = Task({
							("Delay : "++delay.round( 0.1 )).inform;
							delay.wait;
							"Dang!".inform;
							proc.crossFade( newAttr, freezeStake.atk );
							freezeStake.atk.wait;
							okToUnfreeze = true;
							this.changed( \frozen );
						});
						nextFreeze.start;
					}, {
						"No more stakes to come!".warn;
					});
				}, {
					"Freeze already pending!".warn;
				});
			}, {
				"No freeza any more".warn;
			});
		}, {
			"No proc any more".warn;
		});
	}
	
	unfreeze {
		var newAttr, proc, unit;
		
		proc	= ping.getProcChain( slot ).first;
		if( proc.notNil, {
			unit = proc.getUnit;
			if( unit.isKindOf( TrigTapeUnit ), {
				if( nextFreeze.notNil, {
					if( okToUnfreeze, {
						fork {
							newAttr	= IdentityDictionary.new;
							newAttr.add( \path -> pathNorm );
							newAttr.add( \startPos -> (freezeStake.pos + (freezeStake.skip * sr).asInteger) );
							newAttr.add( \loop -> Span.new );
							proc.crossFade( newAttr, freezeStake.rls );
							freezeStake.rls.wait;
							nextFreeze = nil;
							this.changed( \unfrozen );
						}
					}, {
						"Freeze still busy!".warn;
					});
				}, {
					"No freeze pending!".warn;
				});
			}, {
				"No freeza any more".warn;
			});
		}, {
			"No proc any more".warn;
		});
	}
}