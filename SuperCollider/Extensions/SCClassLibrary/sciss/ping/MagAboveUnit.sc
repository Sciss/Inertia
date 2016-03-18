/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: GeneratorUnit, TypeSafe
 *
 *	@version	0.1, 20-Oct-06
 *	@author	Hanns Holger Rutz
 *
 *	@warning	there is a really bad bug i couldn't yet find. dynamically
 *			creating and disposing instances of this class is not
 *			a good idea in a concert!!!! is seems there is a race
 *			condition with allocating / freeing buffers and / or
 *			synths that work with those buffers.
 */
MagAboveUnit : GeneratorUnit {
	var defName = nil, thresh = 1.0e-2;
	var bufs = nil, synth = nil;
	var bufSize;
	
	classvar <allUnits;			// elem = instances of MagAboveUnit
	
	var attributes;

	*initClass {
		allUnits = IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitMagAboveUnit;
	}
	
	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		bufs			= nil;
		
		^result;
	}

	prInitMagAboveUnit {
		bufSize			= 1024;

		attributes		= [
			UnitAttr( \thresh, ControlSpec( 1.0e-3, 1.0e-1, \exp ), \normal, \getThresh, \setThresh, nil, false )
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
	 	var bndl;
	 	
	 	bndl = List.new;
		bufs.do({ arg buf;
//			buf.close;
// ZZZ
//			this.protRemoveGlobalBuffer( buf );
			bndl.add( buf.freeMsg );
		});
		bufs = nil;
		if( bndl.notEmpty, { server.listSendBundle( nil, bndl ); });
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

			if( bufs.isNil, {
				bufs = this.prAllocConsecutive( numChannels, server, bufSize );

// ZZZ
//				this.protAddGlobalBuffer( buf );
				bndl.add( this.prAllocConsecutiveMsg( bufs, synth.newMsg( target, [Ê\aBuf, bufs.first.bufnum, \thresh, thresh, \in, inBus.index, \out,
					outBus.notNil.if({ outBus.index }, 0 ) ], \addToHead )));
			}, {
				bndl.add( synth.newMsg( target, [Ê\aBuf, bufs.first.bufnum, \thresh, thresh, \in, inBus.index, \out,
					outBus.notNil.if({ outBus.index }, 0 ) ], \addToHead ));
			});
//bndl.postln;
			this.protAddNode( synth );
			this.protSetPlaying( true );

		}, {
			TypeSafe.methodWarn( thisMethod, "No input bus has been specified" );
		});
	}
	
	prAllocConsecutive { arg numBufs = 1, server, numFrames, numChannels = 1;
		var	bufBase, newBuf;
		bufBase = server.bufferAllocator.alloc( numBufs );
		^Array.fill( numBufs, { arg i;
			Buffer.new( server, numFrames, numChannels, i + bufBase );
		});
	}
	
	prAllocConsecutiveMsg { arg bufs, completionMsg;
		bufs.do({ arg buf;
			completionMsg = buf.allocMsg( completionMsg );
		});
		^completionMsg;
	}
	
	setInputBus { arg bus;
		var outBus;
		
		outBus = this.getOutputBus;
		
		if( outBus.notNil and: {ÊoutBus.numChannels != bus.numChannels }, {
			TypeSafe.methodError( thisMethod, "Input and output channels cannot be different" );
			^this;
		});

		numChannels	= bus.numChannels;
		defName		= ("magAboveUnit" ++ numChannels).asSymbol;
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
		defName		= ("magAboveUnit" ++ numChannels).asSymbol;
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
		defName		= ("magAboveUnit" ++ numChannels).asSymbol;
		this.prCacheDef( numChannels );
		^super.setOutputBus( bus );
	}

	setThresh { arg value;
		thresh = value;
		synth.set( \thresh, thresh );
	}
	
	getThresh {
		^thresh;
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
		defName	= ("magAboveUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, {
				arg in, out, aBuf, thresh = 1.0e-2;

				var inp, chain, volume, ramp, env;
				
				env			= Env([ 0.0, 0.0, 1.0 ], [ 0.2, 0.2 ], [ \step, \linear ]);
//				ramp			= Line.kr( dur: 0.2 );
				ramp			= EnvGen.kr( env );
				volume		= LinLin.kr( thresh, 1.0e-3, 1.0e-1, 32, 4 );
				inp			= In.ar( in, numChannels );
				chain 		= FFT( aBuf + Array.series( numChannels ), HPZ1.ar( inp ));
				chain 		= PV_MagAbove( chain, thresh ); 
				
				ReplaceOut.ar( out, LPZ1.ar( volume * IFFT( chain )) * ramp );
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
		dup.setThresh( this.getThresh );
	}
}