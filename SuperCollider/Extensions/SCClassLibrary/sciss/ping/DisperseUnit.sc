/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: GeneratorUnit, TypeSafe
 *
 *	Changelog:
 *
 *	@version	0.1, 19-Jul-06
 *	@author	Hanns Holger Rutz
 */
DisperseUnit : GeneratorUnit {
	var defName			= nil;
	var synth				= nil;
 	var timeDispersion		= 0.0;
	var pitchDispersion	= 0.0;
	
	classvar <allUnits;				// elem = instances of DisperseUnit

	var attributes;

	*initClass {
		allUnits	= IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitDisperseUnit;
	}
	
	prInitDisperseUnit {
		allUnits.add( this );

		attributes		= [
			UnitAttr( \timeDispersion,  ControlSpec( 0.01, 1, \exp ), \normal, \getTimeDispersion, \setTimeDispersion, nil ),
			UnitAttr( \pitchDispersion, ControlSpec( 0.01, 1, \exp ), \normal, \getPitchDispersion, \setPitchDispersion, nil )
		];
	}
	
	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		^result;
	}

	getAttributes {
		^attributes;
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
				\timeDisp, timeDispersion, \pitchDisp, pitchDispersion ], \addToHead ));
			this.protAddNode( synth );
			this.protSetPlaying( true );
		}, {
			TypeSafe.methodWarn( thisMethod, "No input bus has been specified" );
		});
	}

	setInputBus { arg bus;
		var outBus;
		
		outBus = this.getOutputBus;
		
		if( outBus.notNil and: {ÊoutBus.numChannels != bus.numChannels }, {
			TypeSafe.methodError( thisMethod, "Input and output channels cannot be different" );
			^this;
		});

		numChannels	= bus.numChannels;
		defName		= ("disperseUnit" ++ numChannels).asSymbol;
		this.prCacheDef( numChannels );
		^super.setInputBus( bus );
	}

	setOutputBusToBundle { arg bndl, bus;
		var inBus;
		
		inBus = this.getInputBus;
		
		if( inBus.notNil and: {ÊinBus.numChannels != bus.numChannels }, {
			TypeSafe.methodError( thisMethod, "Input and output channels cannot be different" );
			^this;
		});
		numChannels	= bus.numChannels;
		defName		= ("disperseUnit" ++ numChannels).asSymbol;
		this.prCacheDef( numChannels );
		^super.setOutputBusToBundle( bndl, bus );
	}

	setOutputBus { arg bus;
		var inBus;
		
		inBus = this.getInputBus;
		
		if( inBus.notNil and: {ÊinBus.numChannels != bus.numChannels }, {
			TypeSafe.methodError( thisMethod, "Input and output channels cannot be different" );
			^this;
		});
		numChannels	= bus.numChannels;
		defName		= ("disperseUnit" ++ numChannels).asSymbol;
		this.prCacheDef( numChannels );
		^super.setOutputBus( bus );
	}

	setTimeDispersion { arg amount;
		timeDispersion = amount.clip( 0, 1 );
		synth.set( \timeDisp, timeDispersion );
	}
	
	getTimeDispersion {
		^timeDispersion;
	}

	setPitchDispersion { arg amount;
		pitchDispersion = amount.clip( 0, 1 );
		synth.set( \pitchDisp, pitchDispersion );
	}
	
	getPitchDispersion {
		^pitchDispersion;
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
		defName = ("disperseUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, { arg in, out, timeDisp, pitchDisp;
				var ins, flt, grainSize;

				grainSize	= 0.5;
				ins		= In.ar( in, numChannels );
				flt		= PitchShift.ar(
					ins, 
					grainSize, 		
					1,					// nominal pitch rate = 1
					timeDisp, 			// pitch dispersion
					pitchDisp * grainSize	// time dispersion
				);
				
				ReplaceOut.ar( out, flt );
			}, [ nil, nil, 0.1 ]);

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
		dup.setTimeDispersion( this.getTimeDispersion );
		dup.setPitchDispersion( this.getPitchDispersion );
	}
}