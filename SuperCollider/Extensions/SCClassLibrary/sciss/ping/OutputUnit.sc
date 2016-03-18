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
OutputUnit : GeneratorUnit {
	var defName	= nil;
	var synth		= nil;
	var azi		= 0.001;
	var spread	= 0.25;
	
	classvar <allUnits;				// elem = instances of OutputUnit

	*initClass {
		allUnits	= IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitOutputUnit;
	}
	
	prInitOutputUnit {
		allUnits.add( this );
		numChannels = 3;
	}
	
	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		^result;
	}

	getAttributes {
		^nil;	// XXX
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
				\azi, azi, \spread, spread, \volume, volume ], \addToHead ));
			this.protAddNode( synth );
			this.protSetPlaying( true );
		}, {
			TypeSafe.methodWarn( thisMethod, "No input bus has been specified" );
		});
	}

	setInputBus { arg bus;
		defName	= ("outputUnit" ++ bus.numChannels).asSymbol;
		this.prCacheDef( bus.numChannels );
		^super.setInputBus( bus );
	}

	setOutputBusToBundle { arg bndl, bus;
		if( bus.numChannels != 3, {
			TypeSafe.methodError( thisMethod, "Three channels required for Ambisonics" );
			^this;
		});
		^super.setOutputBusToBundle( bndl, bus );
	}

	setOutputBus { arg bus;
		if( bus.numChannels != 3, {
			TypeSafe.methodError( thisMethod, "Three channels required for Ambisonics" );
			^this;
		});
		^super.setOutputBus( bus );
	}

	setAzimuth { arg az;
		azi = az;
		synth.set( \azi, az );
	}
	
	getAzimuth {
		^azi;
	}

//	setAziSpeed { arg speed;
//		azi = speed;
//		synth.set( \azi, speed );
//	}
//	
//	getAziSpeed {
//		^azi;
//	}

	setSpread { arg argSpread;
		spread = argSpread;
		synth.set( \spread, spread );
	}
	
	getSpread {
		^spread;
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
		defName	= ("outputUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, { arg in, out, azi = 0.001, spread = 0.25, volume = 1;
				var aziGen, sig, w, x, y, w1, x1, y1, spreadOff;

//				aziGen		= LFDNoise1.kr( azi );
aziGen = azi;
				sig			= In.ar( in, numChannels ).asArray;
				w			= 0;
				x			= 0;
				y			= 0;
				numChannels.do({ arg ch;
					spreadOff = spread * ch / max( 1, (numChannels - 1));
					#w1, x1, y1 = PanB2.ar( sig[ ch ], (aziGen + spreadOff).wrap( -1, 1 ), volume );
					w		= w + w1;
					x		= x + x1;
					y		= y + y1;
				});
				OffsetOut.ar( out, [w, x, y ]);
			}, [ nil, nil, 0.5, 0.5, 0.05 ]);
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

	protDuplicate {Êarg dup;
		dup.setAzimuth( this.getAzimuth );
		dup.setSpread( this.getSpread );
	}
}