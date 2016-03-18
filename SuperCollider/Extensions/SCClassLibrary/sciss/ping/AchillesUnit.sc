/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: GeneratorUnit, TypeSafe
 *
 *	@version	0.11, 19-Jul-06
 *	@author	Hanns Holger Rutz
 */
AchillesUnit : GeneratorUnit {
	var defName = nil, speed = 0.5;
	var buf = nil, synth = nil;
	var bufSize;
	
	classvar <allUnits;			// elem = instances of AchillesUnit
	
	var attributes;

	*initClass {
		allUnits = IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitAchillesUnit;
	}
	
	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		buf			= nil;
		
		^result;
	}

	prInitAchillesUnit {
		bufSize			= server.sampleRate.asInteger;

		attributes		= [
			UnitAttr( \speed, ControlSpec( 0.125, 2.3511, \exp ), \normal, \getSpeed, \setSpeed, nil )
		];

		allUnits.add( this );
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
	
//	/**
//	 *	This method can be called prior to play in order
//	 *	to have the buffer creation ready and decrease latency
//	 */
//	allocBuffer {
//		if( buf.isNil, {
//			if( file.notNil, {
//				buf	= Buffer.new( server, bufSize, file.numChannels );
//				this.protAddGlobalBuffer( buf );
//				buf.allocRead( path, 0, bufSize );
//			}, {
//				TypeSafe.methodWarn( thisMethod, "No path has been specified" );
//			});
//		});
//	}

	freeBuffer {
		if( buf.notNil, {
//			buf.close;
// ZZZ
//			this.protRemoveGlobalBuffer( buf );
			buf.free;
			buf = nil;
		});
	}

	playToBundle { arg bndl, position, atk = 0;
		var allocMsg, newMsg, inBus, outBus;

		if( playing, {
			this.stop;
			if( synth.notNil, {Êthis.protRemoveNode( synth );});
			synth = nil;
		});

		inBus = this.getInputBus;
		if( inBus.notNil, {
			synth	= Synth.basicNew( defName, server );
			outBus	= this.getOutputBus;

			if( buf.isNil, {
				buf	= Buffer.new( server, bufSize, numChannels );
// ZZZ
//				this.protAddGlobalBuffer( buf );
				bndl.add( buf.allocMsg( synth.newMsg( target, [Ê\aBuf, buf.bufnum, \rate, speed, \in, inBus.index, \out,
					outBus.notNil.if({ outBus.index }, 0 ) ], \addToHead )));
			}, {
				bndl.add( synth.newMsg( target, [Ê\aBuf, buf.bufnum, \rate, speed, \in, inBus.index, \out,
					outBus.notNil.if({ outBus.index }, 0 ) ], \addToHead ));
			});

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
		defName		= ("achillesUnit" ++ numChannels).asSymbol;
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
		defName		= ("achillesUnit" ++ numChannels).asSymbol;
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
		defName		= ("achillesUnit" ++ numChannels).asSymbol;
		this.prCacheDef( numChannels );
		^super.setOutputBus( bus );
	}

	setSpeed { arg factor;
		speed = factor;
		synth.set( \rate, speed );
	}
	
	getSpeed {
		^speed;
	}

//	dispose {
//// ZZZ
//		this.freeBuffer;
//		allUnits.remove( this );
//		^super.dispose;	// handles free buf
//	}

	dispose {
		var result;
		allUnits.remove( this );
		result = super.dispose;
		this.freeBuffer;
		^result;
	}
	
	*disposeAll {
		var all;
		
		all = List.newFrom( allUnits );
		all.do({ arg unit; unit.dispose; });
	}

	prCacheDef { arg numChannels;
		var defName, def;
		defName	= ("achillesUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, {
				arg in, out, aBuf, rate = 1.0;

				var inp, writeRate, readRate, readPhasor, read;
				var numFrames, writePhasor, old, wet, dry;

				inp			= In.ar( in, numChannels );
				numFrames		= BufFrames.kr( aBuf );
				writeRate 	= BufRateScale.kr( aBuf );
				readRate	 	= writeRate * rate;
				readPhasor	= Phasor.ar( 0, readRate, 0, numFrames );
				read			= BufRd.ar( numChannels, aBuf, readPhasor, 0, 4 );
				writePhasor	= Phasor.ar( 0, writeRate, 0, numFrames );
				old			= BufRd.ar( numChannels, aBuf, writePhasor, 0, 1 );
				wet			= SinOsc.ar( 0, ((readPhasor - writePhasor).abs / numFrames * pi) );
				dry			= 1 - wet.squared;
				wet			= 1 - (1 - wet).squared;
//				wet			= 1 - dry.squared;
//				BufWr.ar( (old * (1 - wet)) + (inp * wet), aBuf, writePhasor );
//				BufWr.ar( SinOsc.ar( [ 441, 441 ], mul: 0.1 ), aBuf, writePhasor );
				BufWr.ar( (old * dry) + (inp * wet), aBuf, writePhasor );
				
				ReplaceOut.ar( out, read );
			}, [ nil, nil, nil, 0.1 ]);
			defCache.add( def );
		});
	}

//	n_go {Êarg node;
//		if( node === synth, {
//			currentPosT = SystemClock.seconds;
//			currentPos = startPos;
//		});
//		^super.n_go( node );
//	}
	
	n_end { arg node;
		if( node === synth, {
			synth		= nil;
			this.protSetPlaying( false );
		});
		^super.n_end( node );
	}

	protDuplicate { arg dup;
		dup.setSpeed( this.getSpeed );
	}
}