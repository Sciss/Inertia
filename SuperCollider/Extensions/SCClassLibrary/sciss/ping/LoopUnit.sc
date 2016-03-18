/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: GeneratorUnit, UpdateListener, TypeSafe
 *
 *	ZZZ BUFFER CRASH TEST
 *
 *	Changelog:
 *
 *	@version	0.12, 19-Jan-06
 *	@author	Hanns Holger Rutz
 *
 *	@todo	laufende aufnahme ermoeglichen, die "hinter" dem loop laeuft (bei rate == 1)
 *	@todo	cmdPeriod -> clear bufUses, clear recSynth
 */
LoopUnit : GeneratorUnit {
	classvar debug 	= false;

	var defName		= nil;
	var synth			= nil;
	var buf			= nil;
	
	classvar <allUnits;			// elem = instances of LoopUnit

	var attributes;

	var <numFrames;
	var minFrames;
	var speed			= 1.0;
	
	var reuseBuf		= nil;
	var startFrame	= 0;
	var nowNumFrames;
	
	var recDefName;
	var recSynth;
	var recBuf;
	var recDoneAction;
	var useRecBuf		= false;
	var recBufFrames	= 0;
	
	classvar bufUses;
	
	*initClass {
		allUnits				= IdentitySet.new;
		bufUses				= Dictionary.new;	// Buffer -> use count
	}

	*new { arg server;
		^super.new( server ).prInitLoopUnit;
	}
	
	// detector responder
	isRecorder { ^true }
	
	*debugPrintBufUses {
		bufUses.keysValuesDo({ arg key, value; ("buf = "++key++"; use = "++value).inform; });
	}
	
	prInitLoopUnit {
	
// XXX
		attributes		= [
			UnitAttr( \speed, ControlSpec( 0.125, 2.3511, \exp ), \normal, \getSpeed, \setSpeed, nil )
		];

		minFrames		= (server.sampleRate * 2).asInteger;

		allUnits.add( this );
	}

	setDuration { arg dur;
		^this.setNumFrames( (dur * server.sampleRate).asInteger );
	}
	
	setStartFrame { arg frame;
		startFrame = frame;
		if( startFrame + (nowNumFrames ?? 0) > numFrames, {
			TypeSafe.methodWarn( thisMethod, "numFrames too big. adjusting" );
			startFrame	= max( 0, numFrames - minFrames );
			nowNumFrames	= max( minFrames, numFrames - startFrame );
		});
		if( synth.notNil, {
			synth.set( \startFrame, speed, \numFrames, nowNumFrames );
		});
	}
	
	getStartFrame {
		^startFrame;
	}
	
	setNowNumFrames { arg frames;
		nowNumFrames = frames;
		if( nowNumFrames < minFrames, {
			TypeSafe.methodWarn( thisMethod, "numFrames too small. adjusting" );
			nowNumFrames = minFrames;
			startFrame = min( startFrame ?? 0, numFrames - nowNumFrames );
		});
		if( startFrame + (nowNumFrames ?? 0) > numFrames, {
			TypeSafe.methodWarn( thisMethod, "numFrames too big. adjusting" );
			startFrame	= max( 0, numFrames - nowNumFrames );
			nowNumFrames	= min( nowNumFrames, numFrames );
		});
if( debug, { TypeSafe.methodInform( thisMethod, "nowNumFrames = "++nowNumFrames++"; startFrame = "++startFrame );});
		if( synth.notNil, {
if( debug, { TypeSafe.methodInform( thisMethod, "synth.set( \\startFrame, "++startFrame++", \\numFrames, "++nowNumFrames++" );" );});
			synth.set( \startFrame, startFrame, \numFrames, nowNumFrames );
		});
	}
	
	getNowNumFrames {
		^nowNumFrames;
	}

	setNumFrames { arg frames;
		numFrames = max( minFrames, frames );
if( debug, { TypeSafe.methodInform( thisMethod, "numFrames = "++numFrames ); });
		if( numChannels.notNil, {
			this.allocBuffer;
		});
	}
	
	setNumChannels { arg chan;
		numChannels 	= chan;
		this.prCacheDefs( numChannels );
		defName		= ("loopUnit" ++ numChannels).asSymbol;
		recDefName	= ("loopUnit" ++ numChannels ++ "Rec").asSymbol;
if( debug, { TypeSafe.methodInform( thisMethod, "numChannels = "++numChannels++"; defName = '"++defName++"'; recDefName = '"+recDefName+"'" ); });
		if( numFrames.notNil, {
			this.allocBuffer;
		});
	}
	
	getNumFrames {
		^numFrames;
	}
	
	/**
	 *	This method can be called prior to play in order
	 *	to have the buffer creation ready and decrease latency
	 */
	allocBuffer {
		if( buf.isNil, {
			if( numFrames.notNil && numChannels.notNil, {
				buf	= Buffer.new( server, numFrames, numChannels );
// ZZZ
//				this.protAddGlobalBuffer( buf );
				buf.alloc;
				bufUses.put( buf, 1 );
if( debug, { TypeSafe.methodInform( thisMethod, "buf.isNil -> buf = "++buf ); });
			}, {
				TypeSafe.methodWarn( thisMethod, "# of frames and channel have not been specified" );
			});
		}, {
if( debug, { TypeSafe.methodInform( thisMethod, "buf.notNil -> buf = "++buf ); });
		});
	}

	prSetBuffer { arg argBuf;
		buf			= argBuf;
//		numChannels	= buf.numChannels;
//		numFrames		= buf.numFrames;
		bufUses.put( buf, (bufUses[ buf ] ?? 0) + 1 );
//		this.prCacheDefs( numFrames );
if( debug, { TypeSafe.methodInform( thisMethod, "buf = "++buf++"; bufUses = "++bufUses[ buf ] ); });
		this.setNumFrames( buf.numFrames );
		this.setNumChannels( buf.numChannels );
	}
	
	freeBuffer {
		var count;
		
		count	= bufUses.at( buf ) - 1;
		
		if( count == 0, {
			this.protRemoveGlobalBuffer( buf );
			bufUses.removeAt( buf );
if( debug, { TypeSafe.methodInform( thisMethod, "buf = "++buf ); });
			buf.free;
			buf = nil;
		}, {
			bufUses.put( buf, count );
			if( verbose, { ("LoopPlayer: don't free buffer "++buf.object.bufnum++" ; useCount "++count).postln; });
if( debug, { TypeSafe.methodInform( thisMethod, "buf = "++buf++"; count = "++count ); });
		});
	}

	trashRecording {
//		mBuf.put( \valid, false );
//		mBuf.object.free;
//		this.prFreeBuffer( mBuf.object );
//		if( verbose, { ("LoopPlayer.trashRecording : buf "++mBuf.object.bufnum++"; useCount "++
//			mBuf.object.at( \useCount )).postln; });
if( debug, { TypeSafe.methodInform( thisMethod, "recBuf = "++recBuf ); });
		recBuf.free;
		recBuf = nil;
		useRecBuf = false;
	}
	
	useRecording {
		if( recBuf.notNil, {
			useRecBuf = true;
if( debug, { TypeSafe.methodInform( thisMethod, "recBuf = "++recBuf ); });
		}, {
if( debug, { TypeSafe.methodInform( thisMethod, "recBuf.isNil" ); });
		});
	}

	// arg bus, startFrame = 0, numFrames, target, addAction = \addToHead, doneAction;

	startRecording { arg numFrames, doneAction;
		var inBus, bndl, startTime, stopTime;

		numFrames	= numFrames ?? { this.getNumFrames; };

		TypeSafe.checkArgClasses( thisMethod, [ numFrames, doneAction ], [ Integer, AbstractFunction ],
			[ false, false ]);
		
		inBus = this.getInputBus;
		if( inBus.isNil, {
			TypeSafe.methodError( thisMethod, "No record source specified" );
			^false;
		});

		if( inBus.numChannels > numChannels, {
			TypeSafe.methodError( thisMethod, "Incompatible # of channels" );
			^false;
		});

		// Es kann nur EINEN geben (EINE?)
		if( this.isRecording, {
			TypeSafe.methodWarn( thisMethod, "Already recording!" );
			^false;
		});

		bndl		= List.new;
		recBuf	= Buffer.new( server, numFrames, numChannels );
		recSynth	= Synth.basicNew( recDefName, server );
		bndl.add( recBuf.allocMsg( recSynth.newMsg( server.asTarget,
			[ \aOutBuf, recBuf.bufnum, \aInBus, inBus.index, \startFrame, 0,
			  \numFrames, numFrames ], \addToTail );
		));

		nw.register( recSynth );
		UpdateListener.newFor( recSynth, { arg upd, synth, what;
			case { what == \n_end }
			{
				upd.remove;
				stopTime = SystemClock.seconds - server.latency;
				if( verbose, { ("LoopSamplerRec.n_end : " ++ synth.nodeID ++ "; recorded time " ++
						(stopTime - startTime)).postln; });
// useCounts remains at 1 to be consistent with prepareCrossFade !
//				recBuf.put( \useCount, 0 );
				recBufFrames = min( numFrames, (max( 1, stopTime - startTime ) * server.sampleRate).asInteger );
if( debug, { TypeSafe.methodInform( thisMethod, "n_end; stopTime = "++stopTime++"; recBufFrames = "++recBufFrames ); });
				recDoneAction.value( recBufFrames );
				recSynth = nil;
				recBuf = nil;
			}
			{ what == \n_go }
			{
				if( verbose, { ("LoopUnit->startRecording.n_go : " ++ synth.nodeID).postln; });
				startTime = SystemClock.seconds;
if( debug, { TypeSafe.methodInform( thisMethod, "n_go; startTime = "++startTime ); });
			};
		});

		if( verbose, { ("LoopUnit->startRecording : starting to write bus " ++ inBus.index ++
			" to buffer " ++ recBuf.bufnum).postln; });

if( debug, { TypeSafe.methodInform( thisMethod, "recBuf = "++recBuf++"; inBus = "++inBus++"; numFrames = "++numFrames++"; recSynth = "++recSynth ); });

		recDoneAction = doneAction;

		server.listSendBundle( nil, bndl );
		
		useRecBuf = false;
		
		^true;
	}
	
	cancelRecording {
if( debug, { TypeSafe.methodInform( thisMethod, "" ); });
		recDoneAction = { this.trashRecording; };
		^this.stopRecording;
	}

	stopRecording {
		if( recSynth.isNil.not, {
if( debug, { TypeSafe.methodInform( thisMethod, "recSynth = "++recSynth ); });
			recSynth.free;
			^true;
		}, {
if( debug, { TypeSafe.methodInform( thisMethod, "recSynth.isNil" ); });
			^false;
		});
	}

	isRecording {
		^recSynth.isNil.not;
	}

	getAttributes {
		^attributes;
	}

	setSpeed { arg factor;
		speed = factor;
//		if( playing, {
//			this.stop;
//			this.play;
//		});
		if( synth.notNil, {
			synth.set( \rate, speed );
		});
	}
	
	getSpeed {
		^speed;
	}

	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		recSynth		= nil;
		buf			= nil;
		^result;
	}

	play {
		var bndl;

		bndl = List.new;
		this.playToBundle( bndl );
		server.listSendBundle( nil, bndl );
	}
	
	playToBundle { arg bndl;
		var outBus, allocMsg;

		if( playing, {
			this.stop;
			if( synth.notNil, { this.protRemoveNode( synth );});
			synth = nil;
		});

		synth = Synth.basicNew( defName, server );

		if( buf.isNil, {
			buf	= Buffer.new( server, numFrames, numChannels );
// ZZZ
//			this.protAddGlobalBuffer( buf );
			allocMsg = buf.allocMsg( synth.runMsg( true ));
			bufUses.put( buf, 1 );
		});
				
		if( startFrame.isNil, {
			TypeSafe.methodWarn( thisMethod, "Unspecified start frame" );
			startFrame = 0;
		});
		if( nowNumFrames.isNil, {
			TypeSafe.methodWarn( thisMethod, "Unspecified number of frames" );
			nowNumFrames = numFrames - startFrame;
		});
		if( nowNumFrames < minFrames, {
			TypeSafe.methodWarn( thisMethod, "numFrames too small. adjusting" );
			nowNumFrames = minFrames;
			startFrame = min( startFrame, numFrames - nowNumFrames );
		});
		if( startFrame + nowNumFrames > numFrames, {
			TypeSafe.methodWarn( thisMethod, "numFrames too big. adjusting" );
			startFrame	= max( 0, numFrames - nowNumFrames );
			nowNumFrames	= min( nowNumFrames, numFrames );
		});
		
		outBus = this.getOutputBus;
		bndl.add( synth.newMsg( target, [ \aInBuf, buf.bufnum, \rate, speed, \out,
			outBus.notNil.if({ outBus.index }, 0 ), \startFrame, startFrame, \numFrames, nowNumFrames ],
				\addToHead ));
		if( allocMsg.notNil, {
			bndl.add( synth.runMsg( false ));
			bndl.add( allocMsg );
		});

		this.protAddNode( synth );
//		trigResp.add;
		this.protSetPlaying( true );

if( debug, { TypeSafe.methodInform( thisMethod, "startFrame = "++startFrame++"; nowNumFrames = "++nowNumFrames++"; outBus = "++outBus++"; synth = "++synth++"; buf = "++buf++"; allocMsg = "++allocMsg ); });
	}

//	dispose {
//// ZZZ
//		this.freeBuffer;
//		if( recSynth.notNil, {
//			nw.unregister( recSynth );
//if( debug, { TypeSafe.methodInform( thisMethod, "recSynth = "++recSynth ); });
//			recSynth.free;
//			recBuf.free;
//		}, {
//if( debug, { TypeSafe.methodInform( thisMethod, "recSynth.isNil" ); });
//		});
//		allUnits.remove( this );
//		^super.dispose;
//	}

	dispose {
		var result;
		allUnits.remove( this );
		result = super.dispose;
		if( recSynth.notNil, {
			nw.unregister( recSynth );
if( debug, { TypeSafe.methodInform( thisMethod, "recSynth = "++recSynth ); });
			recSynth.free;
			recBuf.free;
		}, {
if( debug, { TypeSafe.methodInform( thisMethod, "recSynth.isNil" ); });
		});
		this.freeBuffer;
		^result;
	}
	
	*disposeAll {
		var all;
		
		all = List.newFrom( allUnits );
		all.do({ arg unit; unit.dispose; });
	}
	
	prCacheDefs { arg numChannels;
		var defName, def;

		defName = ("loopUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
//			def = SynthDef.new( defName, { arg out, aInBuf, rate = 1.0, startFrame = 0, numFrames;
//				var	lOffset, gate1, gate2, lLength, play1, play2, trig1, trig2, duration,
//					dlyDur, env, amp1, amp2, output, gateTrig1, gateTrig2;
//				
////				trig1	= Impulse.kr( freq: LocalIn.kr( 1 ));
////				trig1	= Impulse.kr( freq: LocalIn.kr( 1 ).max( 0.01 ));  // TEST XXX
//				gateTrig1	= PulseDivider.kr( trig: trig1, div: 2, start: 1 );
//				gateTrig2	= PulseDivider.kr( trig: trig1, div: 2, start: 0 );
//				lOffset	= Latch.kr( in: startFrame, trig: trig1 );
//SendTrig.kr( gateTrig1, 666, lOffset );
//				lLength	= Latch.kr( in: numFrames, trig: trig1 );
//SendTrig.kr( gateTrig2, 667, lLength );
//				duration	= lLength / (rate * SampleRate.ir);
//SendTrig.kr( trig1, 668, duration );
//				gate1	= Trig1.kr( in: gateTrig1, dur: duration );
//				env		= Env.adsr( 1, 1, 1, 1, 1, 0 );
//				
//				play1	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
//									trigger: gateTrig1, startPos: lOffset );
//				play2	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
//									trigger: gateTrig2, startPos: lOffset );
//				amp1		= EnvGen.kr( envelope: env, gate: gate1 );
//				amp2		= 1.0 - amp1.squared;
//				amp1		= 1.0 - amp1;
//				amp1		= 1.0 - amp1.squared;
//				output	= (play1 * amp1) + (play2 * amp2);
//
//				OffsetOut.ar( bus: out, channelsArray: output );
//			
//				LocalOut.kr( 1.0 / duration );		
//			});

//			def = SynthDef.new( defName, { arg out, aInBuf, rate = 1.0, startFrame = 0, numFrames;
//				var	lOffset, gate1, gate2, lLength, play1, play2, trig1, trig2, duration,
//					dlyDur, env, amp1, amp2, output, gateTrig1, gateTrig2;
//				
////				trig1	= Impulse.ar( freq: LocalIn.ar( 1 ));
//				trig1	= Impulse.ar( freq: LocalIn.ar( 1 )) + Impulse.ar( 0 );
//				gateTrig1	= PulseDivider.ar( trig: trig1, div: 2, start: 1 );
//				gateTrig2	= PulseDivider.ar( trig: trig1, div: 2, start: 0 );
//				lOffset	= Latch.ar( in: K2A.ar( startFrame ), trig: trig1 );
//SendTrig.ar( gateTrig1, 666, lOffset );
//				lLength	= Latch.ar( in: K2A.ar( numFrames ), trig: trig1 );
//SendTrig.ar( gateTrig2, 667, lLength );
//				duration	= lLength / (rate * SampleRate.ir);
//SendTrig.ar( trig1, 668, duration );
//				gate1	= Trig1.ar( in: gateTrig1, dur: duration );
//				env		= Env.adsr( 1, 1, 1, 1, 1, 0 );
//				
//				play1	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
//									trigger: gateTrig1, startPos: lOffset );
//				play2	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
//									trigger: gateTrig2, startPos: lOffset );
//				amp1		= EnvGen.ar( envelope: env, gate: gate1 );
//				amp2		= 1.0 - amp1.squared;
//				amp1		= 1.0 - amp1;
//				amp1		= 1.0 - amp1.squared;
//				output	= (play1 * amp1) + (play2 * amp2);
//
//				OffsetOut.ar( bus: out, channelsArray: output );
//			
//				LocalOut.ar( 1.0 / duration );		
//			});

//			def = SynthDef.new( defName, { arg out, aInBuf, rate = 1.0, startFrame = 0, numFrames;
//				var	lOffset, gate1, gate2, lLength, play1, play2, trig1, trig2, duration,
//					dlyDur, env, amp1, amp2, output, gateTrig1, gateTrig2;
//				
////				trig1	= Impulse.kr( freq: LocalIn.kr( 1 ));
////				trig1	= A2K.kr( RunningMax.ar( Impulse.ar( freq: LocalIn.kr( 1 ).max( 0.1 )), Impulse.ar( SampleRate.ir/128 )));
////				trig1	= A2K.kr( RunningMax.ar( LFPulse.ar( freq: LocalIn.kr( 1 ).max( 0.1 )), Impulse.ar( SampleRate.ir/128 )));
//				trig1	= LFPulse.kr( freq: LocalIn.kr( 1 ).max( 0.1 ));
//				gateTrig1	= PulseDivider.kr( trig: trig1, div: 2, start: 1 );
//				gateTrig2	= PulseDivider.kr( trig: trig1, div: 2, start: 0 );
//				lOffset	= Latch.kr( in: startFrame, trig: trig1 );
//SendTrig.kr( gateTrig1, 666, lOffset );
//				lLength	= Latch.kr( in: numFrames, trig: trig1 );
//SendTrig.kr( gateTrig2, 667, lLength );
//				duration	= lLength / (rate * SampleRate.ir);
//SendTrig.kr( trig1, 668, duration );
//				gate1	= Trig1.kr( in: gateTrig1, dur: duration );
//				env		= Env.adsr( 1, 1, 1, 1, 1, 0 );
//				
//				play1	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
//									trigger: gateTrig1, startPos: lOffset );
//				play2	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
//									trigger: gateTrig2, startPos: lOffset );
//				amp1		= EnvGen.kr( envelope: env, gate: gate1 );
//				amp2		= 1.0 - amp1.squared;
//				amp1		= 1.0 - amp1;
//				amp1		= 1.0 - amp1.squared;
//				output	= (play1 * amp1) + (play2 * amp2);
//
//				OffsetOut.ar( bus: out, channelsArray: output );
//			
////				LocalOut.kr( 1.0 / duration );
//				LocalOut.kr( 1.0 / duration.max( 0.1 ));
//			});

/*
			def = SynthDef.new( defName, { arg out, aInBuf, rate = 1.0, startFrame = 0, numFrames;
				var	lOffset, gate1, gate2, lLength, play1, play2, trig1, trig2, duration,
					dlyDur, env, amp1, amp2, output, gateTrig1, gateTrig2;
				
//				trig1	= Impulse.kr( freq: LocalIn.kr( 1 ));
//				trig1	= A2K.kr( RunningMax.ar( Impulse.ar( freq: LocalIn.kr( 1 ).max( 0.1 )), Impulse.ar( SampleRate.ir/128 )));
//				trig1	= A2K.kr( RunningMax.ar( LFPulse.ar( freq: LocalIn.kr( 1 ).max( 0.1 )), Impulse.ar( SampleRate.ir/128 )));
				trig1	= LocalIn.kr( 1 ); // LFPulse.kr( freq: LocalIn.kr( 1 ).max( 0.1 ));
				gateTrig1	= PulseDivider.kr( trig: trig1, div: 2, start: 1 );
				gateTrig2	= PulseDivider.kr( trig: trig1, div: 2, start: 0 );
				lOffset	= Latch.kr( in: startFrame, trig: trig1 );
//SendTrig.kr( gateTrig1, 666, lOffset );
				lLength	= Latch.kr( in: numFrames, trig: trig1 );
//SendTrig.kr( gateTrig2, 667, lLength );
				duration	= lLength / (rate * SampleRate.ir);
//SendTrig.kr( trig1, 668, duration );
				gate1	= Trig1.kr( in: gateTrig1, dur: duration );
				env		= Env.adsr( 1, 1, 1, 1, 1, 0 );
				
				play1	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
									trigger: gateTrig1, startPos: lOffset );
				play2	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
									trigger: gateTrig2, startPos: lOffset );
				amp1		= EnvGen.kr( envelope: env, gate: gate1 );
				amp2		= 1.0 - amp1.squared;
				amp1		= 1.0 - amp1;
				amp1		= 1.0 - amp1.squared;
				output	= (play1 * amp1) + (play2 * amp2);

				OffsetOut.ar( bus: out, channelsArray: output );
			
//				LocalOut.kr( 1.0 / duration );
//				LocalOut.kr( LFPulse.kr( 1.0 / duration.max( 0.1 )));
				LocalOut.kr( Impulse.kr( 1.0 / duration.max( 0.1 )));
Poll.kr( gateTrig1, amp1, "amp1" );
Poll.kr( gateTrig2, amp2, "amp2" );
			});
*/

			def = SynthDef.new( defName, { arg out, aInBuf, rate = 1.0, startFrame = 0, numFrames;
				var	lOffset, gate1, gate2, lLength, play1, play2, trig1, trig2, duration,
					dlyDur, env, amp1, amp2, output, gateTrig1, gateTrig2;
				
//				trig1	= Impulse.kr( freq: LocalIn.kr( 1 ));
//				trig1	= A2K.kr( RunningMax.ar( Impulse.ar( freq: LocalIn.kr( 1 ).max( 0.1 )), Impulse.ar( SampleRate.ir/128 )));
//				trig1	= A2K.kr( RunningMax.ar( LFPulse.ar( freq: LocalIn.kr( 1 ).max( 0.1 )), Impulse.ar( SampleRate.ir/128 )));
				trig1	= LocalIn.kr( 1 ); // LFPulse.kr( freq: LocalIn.kr( 1 ).max( 0.1 ));
				gateTrig1	= PulseDivider.kr( trig: trig1, div: 2, start: 1 );
				gateTrig2	= PulseDivider.kr( trig: trig1, div: 2, start: 0 );
				lOffset	= Latch.kr( in: startFrame, trig: trig1 );
//SendTrig.kr( gateTrig1, 666, lOffset );
				lLength	= Latch.kr( in: numFrames, trig: trig1 );
//SendTrig.kr( gateTrig2, 667, lLength );
				duration	= lLength / (rate * SampleRate.ir);
//SendTrig.kr( trig1, 668, duration );
				gate1	= Trig1.kr( in: gateTrig1, dur: duration );
				env		= Env.adsr( 1, 1, 1, 1, 1, 0 );
				
				play1	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
									trigger: gateTrig1, startPos: lOffset );
				play2	= PlayBuf.ar( numChannels: numChannels, bufnum: aInBuf, rate: rate, loop: 0,
									trigger: gateTrig2, startPos: lOffset );
				amp1		= EnvGen.kr( envelope: env, gate: gate1 );
				amp2		= 1.0 - amp1.squared;
				amp1		= 1.0 - amp1;
				amp1		= 1.0 - amp1.squared;
				output	= (play1 * amp1) + (play2 * amp2);

				OffsetOut.ar( bus: out, channelsArray: output );
			
//				LocalOut.kr( 1.0 / duration );
//				LocalOut.kr( LFPulse.kr( 1.0 / duration.max( 0.1 )));
				LocalOut.kr( Impulse.kr( 1.0 / duration.max( 0.1 )));
			});
			defCache.add( def );
		});

		defName = ("loopUnit" ++ numChannels ++ "Rec").asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef.new( defName, {
				arg aOutBuf, aInBus, startFrame = 0, numFrames;
				
				var rec, line, input;
				
				input	= In.ar( bus: aInBus, numChannels: numChannels );
				rec		= RecordBuf.ar( inputArray: input, bufnum: aOutBuf,
									  offset: startFrame, loop: 0 );
				line		= Line.kr( 0.0, 0.0, numFrames / BufSampleRate.kr( aOutBuf ), doneAction: 2 );
			});
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

	protDuplicate { arg dup;
		if( useRecBuf and: { recBuf.notNil }, {
if( debug, { TypeSafe.methodInform( thisMethod, "recBuf = "++recBuf++"; recBufFrames = "++recBufFrames++"; startFrame = "++this.getStartFrame  );});
			dup.prSetBuffer( recBuf );
			recBuf		= nil;
			useRecBuf		= false;
			dup.setNowNumFrames( recBufFrames );
		}, {
if( debug, { TypeSafe.methodInform( thisMethod, "buf = "++buf++"; nowNumFrames = "++this.getNowNumFrames++"; startFrame = "++this.getStartFrame );});
			if( buf.notNil, { dup.prSetBuffer( buf );});
			dup.setNowNumFrames( this.getNowNumFrames );
		});
		dup.setStartFrame( this.getStartFrame );
		dup.setSpeed( this.getSpeed );
	}
}