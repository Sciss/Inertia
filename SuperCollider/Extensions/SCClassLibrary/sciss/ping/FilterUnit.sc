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
FilterUnit : GeneratorUnit {
	var defName	= nil;
	var synth		= nil;
	var freq		= 1000;
	
	classvar <allUnits;				// elem = instances of FilterUnit

	var attributes;

	*initClass {
		allUnits	= IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitFilterUnit;
	}
	
	prInitFilterUnit {
		allUnits.add( this );

		attributes		= [
			UnitAttr( \normFreq, ControlSpec( -1, 1, \lin ), \pan, \getNormFreq, \setNormFreq, nil )
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
				\freq, freq ], \addToHead ));
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
		defName		= ("filterUnit" ++ numChannels).asSymbol;
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
		defName		= ("filterUnit" ++ numChannels).asSymbol;
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
		defName		= ("filterUnit" ++ numChannels).asSymbol;
		this.prCacheDef( numChannels );
		^super.setOutputBus( bus );
	}

	// -1 ... +1
	setNormFreq { arg f;
		if( f < 0, {
//			this.setFreq( f.linexp( -1, 0, -20000, -30 ));
//			this.setFreq( (1 - f).linexp( 0, 1, -20000, -30 ));
//(" f == "+f).inform;
			this.setFreq( f.linexp( -1, 0, 30, 20000 ).neg );
		}, {
			this.setFreq( f.linexp( 0, 1, 30, 20000 ));
		});
	}

	getNormFreq {
		if( freq < 0, {
//			^freq.explin( -20000, -30, -1, 0 );
//			^(1 - freq.explin( -20000, -30, 0, 1 ));
//			^freq.neg.explin( 30, 20000, 0, 1 ).neg;
			^freq.neg.explin( 30, 20000, -1, 0 );
		}, {
			^freq.explin( 30, 20000, 0, 1 );
		});
	}

	// note: negative freqs = lpf, positive = hpf
	setFreq { arg f;
		freq = f;
//("freq = "++freq).inform;
		synth.set( \freq, freq );
	}
	
	getFreq {
		^freq;
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
		defName	= ("filterUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, { arg in, out, freq = 100;
				var inp, dry, dryMix, freqLag, lowFreq, lowMix, highFreq, highMix, lpf, hpf;

				inp			= In.ar( in, numChannels ).asArray;
				freqLag		= Lag.kr( freq );
				lowFreq		= freqLag.neg;
				lowMix		= (lowFreq / 30).clip( 0, 1 );
				highFreq		= freqLag;
				highMix		= (highFreq / 30).clip( 0, 1 );
//				lowFreq		= (20000 - lowFreq).clip( 30, 20000 );
				lowFreq		= lowFreq.clip( 30, 20000 );
				highFreq		= highFreq.clip( 30, 20000 );
				dryMix		= 1 - (lowMix + highMix);
				
				lpf			= LPF.ar( inp, lowFreq ) * lowMix;
				hpf			= HPF.ar( inp, highFreq ) * highMix;
				dry			= inp * dryMix;
				
//				OffsetOut.ar( out, dry + lpf + hpf );
				ReplaceOut.ar( out, dry + lpf + hpf );
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
		dup.setFreq( this.getFreq );
	}
}