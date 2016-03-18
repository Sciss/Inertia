/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: GeneratorUnit, TypeSafe
 *
 *	Changelog:
 *
 *	@version	0.1, 14-Jun-06
 *	@author	Hanns Holger Rutz
 */
MasterUnit : GeneratorUnit {
	var defName	= nil;
	var synth		= nil;
	
	classvar <allUnits;				// elem = instances of MasterUnit

	*initClass {
		allUnits	= IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitMasterUnit;
	}
	
	prInitMasterUnit {
		allUnits.add( this );
	}
	
	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		^result;
	}

	play {
		var bndl;

		bndl = List.new;
		this.playToBundle( bndl );
		server.listSendBundle( nil, bndl );
	}
	
	playToBundle { arg bndl;
		var inBus, outBus;

		if( playing, {
			this.stop;
			if( synth.notNil, {Êthis.protRemoveNode( synth );});
			synth = nil;
		});

		inBus = this.getInputBus;
		if( inBus.notNil, {
			synth	= Synth.basicNew( defName, server );
			outBus	= this.getOutputBus;
			bndl.add( synth.newMsg( target, [Ê\in, inBus.index, \out, outBus.notNil.if({ outBus.index }, 0 ),
				\volume, volume ], \addToHead ));
			this.protAddNode( synth );
			this.protSetPlaying( true );
		}, {
			TypeSafe.methodWarn( thisMethod, "No input bus has been specified" );
		});
	}

	setInputBus { arg bus;
		if( bus.numChannels != 3, {
			TypeSafe.methodError( thisMethod, "Three channels required for Ambisonics" );
			^this;
		});
		^super.setInputBus( bus );
	}

	setOutputBus { arg bus;
		defName	= ("masterUnit" ++ bus.numChannels).asSymbol;
		this.prCacheDef( bus.numChannels );
		numChannels = bus.numChannels;
		^super.setOutputBus( bus );
	}

	setOutputBusToBundle { arg bndl, bus;
		defName	= ("masterUnit" ++ bus.numChannels).asSymbol;
		this.prCacheDef( bus.numChannels );
		numChannels = bus.numChannels;
		^super.setOutputBusToBundle( bndl, bus );
	}

	dispose {
		allUnits.remove( this );
		^super.dispose;
	}
	
	*disposeAll {
		var all;
		
		all = List.newFrom( allUnits );
		all.do({ arg unit; unit.dispose; });
	}

	prCacheDef { arg numChannels;
		var defName, def;
		defName	= ("masterUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, { arg in, out, volume = 1;
				var w, x, y, dec, lim;
				
				#w, x, y		= In.ar( in, 3 );
				dec			= DecodeB2.ar( numChannels, w, x, y );
				lim			= Limiter.ar( dec * volume, 0.9, 0.005 );
				
				OffsetOut.ar( out, lim );
			}, [ 0, 0, 0.05 ]);
			defCache.add( def );
		});
	}

	n_end { arg node;
		if( node === synth, {
			synth		= nil;
			this.protSetPlaying( false );
		});
		^super.n_end( node );
	}
}