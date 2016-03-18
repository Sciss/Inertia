/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe, Span, GeneratorUnit, UnitAttr, RegionStake
 *
 *	ZZZ BUFFER CRASH FIX VERSION
 *	NOTE (BUG) :	don't add buffers to GeneratorUnit since buffers of other TapeUnits
 *				are freed as well with any unit disposed. This version frees the buffers
 *				itself, next version should go back to (fixed) GeneratorUnit
 *
 *	Changelog:
 *		- 11-Jun-06	subclass of GeneratorUnit; play defaults to position 0 now
 *					(use getCurrentPos to reproduce previous behaviour)
 *		- 14-Sep-06	fixed playToBundle and prUpdateBuffer omitting synth.run( true ) message
 *					in certain circumstances. fixed bug in prUpdateBuffer when looping.
 *					renamed prSetStartPos to setStartPos.
 *
 *	@todo	switching of loop not working as should (should use stop / server.sync / play manually now)
 *	@todo	CmdPeriod not yet working, checking for server re-launch not yet working
 *	@todo	handle NRT mode
 *	@todo	(FIXED?) stop with release time will stop the trig responder from updating the buffer any more
 *			; use a NodeProxy or similar for now
 *	@todo	add event listening, e.g. for detecting release-end
 *	@todo	could use re-use same nodeID, use OSCpathResponder
 *	@todo	caches should be in a per server list
 *	@todo	switching output bus becomes only effective after stop / play
 *	@todo	fails for files with >19 channels (bug in BufRd)
 *
 *	@version	0.19, 16-Dec-06
 *	@author	Hanns Holger Rutz
 */
TapeUnit : GeneratorUnit {
	classvar defSet;				// elem = channel # for defs sent to the server
	classvar mapPathsToSoundFiles;	// key = (String) path ; value = (SoundFile)
	classvar <allUnits;			// elem = instances of TapeUnit

	classvar	debugBuffer		= false;	// true to see buffers allocated + freed

	classvar pad = 4;				// # sample frames padding for BufRd interpolation
	
	var path = nil, file = nil, defName = nil, startTime, startPos = 0, looping = false, frozen = false, speed = 1.0;
	var buf = nil, synth = nil;
	var bufSize, halfBufSize, halfBufSizeM, trigResp;
	var currentPos, currentPosT;
	var loopSpan = nil;

	var attributes;
	var cues;
	var cueIdx 				= nil;
	var cueAttrDirty			= true;
	
	*initClass {
		defSet				= IdentitySet.new;
		mapPathsToSoundFiles	= Dictionary.new;
		allUnits				= IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitTapeUnit;
	}
	
	addCue { arg path, region;
		var file, name;
		file = this.cachePath( path );
		if( file.notNil, {
			name = path.asSymbol; // path.copyToEnd( path.lastIndexOf( $/ ));
			region = region ?? { RegionStake( Span( 0, file.numFrames ), name ); };
			cues.add([ path, region ]);
			cueAttrDirty = true; // this.prUpdateCueAttr;
		});
	}

	prSetCues { arg cueList;
		cues			= List.newFrom( cueList );
		cueAttrDirty	= true; // this.prUpdateCueAttr;
	}
	
	setCueIndex { arg idx;
		var cue;
		
		if( idx.notNil, {
			cue = cues[ idx ];
			if( cue.notNil, {
				this.setPath( cue[ 0 ]);
				this.setStartPos( cue[ 1 ].getSpan.start );
				cueIdx = idx;
			});
		});
	}
	
	getCueIndex {
		^cueIdx;
	}
	
	getNumCues {
		^cues.size;
	}
	
	setRatioPos { arg p;
		if( file.notNil, {
			this.setStartPos( (p * file.numFrames).asInteger );
		});
	}
	
	getRatioPos {
		^if( file.notNil, {
			this.getCurrentPos / file.numFrames;
		}, 0 );
	}
	
	prInitTapeUnit {
		bufSize			= (server.sampleRate.asInteger + pad) * 2;
		bufSize			= bufSize & 1.bitNot;	    // must be even
		halfBufSize		= bufSize >> 1;
		halfBufSizeM		= halfBufSize - pad;
		cues				= List.new;

		attributes		= [
			UnitAttr( \speed,    ControlSpec( 0.125, 2.3511, \exp ), \normal, \getSpeed,    \setSpeed,    nil ),
			UnitAttr( \ratioPos, ControlSpec( 0, 1, \lin ),          \normal, \getRatioPos, \setRatioPos, nil ),
			nil;	// \cueIndex
		];
//		cueAttrDirty = true; // this.prUpdateCueAttr;

		trigResp	= OSCresponderNode( server.addr, '/tr', { arg time, resp, msg;
			var trigVal, bndl, nodeID;

			nodeID = msg[ 1 ];
			
			if( synth.notNil and: {Êsynth.nodeID === nodeID }, {
				trigVal 	= msg[ 3 ].asInt + 1;
				if( verbose, {
					("TapeUnit: got /tr node=" ++ nodeID ++ " val=" ++ trigVal ++ " ; system " ++
						SystemClock.seconds).postln;
				});
				bndl = List.new;
				this.prUpdateBuffer( trigVal, bndl );
				if( bndl.notEmpty, {
					server.listSendBundle( 0.1, bndl );
				}, {
					if( verbose, {
						("TapeUnit: freeing node=" ++ nodeID).postln;
					});
					server.sendMsg( "/n_free", nodeID );
//					this.stop;
				});
			});
		});
		
//		(" adding "++this).inform;
		allUnits.add( this );
	}
	
	// doesn't return boolean anymore, instead returns first message
	// (can be used as completionMessage e.g. in b_allocRead)
	// ; if bndl is nil, messages are sequenced using completionMsg mechanism
	prUpdateBuffer { arg trigVal, bndl, completionMsg;
		var msg, bufOff, frame, frameStop, frameMax, frameLen;
				
		bufOff		= trigVal.even.if( 0, halfBufSize );
		frame		= (trigVal * halfBufSizeM) + startPos + trigVal.even.if( 0, pad );
		currentPosT	= SystemClock.seconds;
		// NOT LOOPED
		if( loopSpan.isNil, {
			currentPos	= frame - halfBufSize;
			frameStop 	= frame + halfBufSize;
			frameMax  	= file.numFrames;
			case {ÊframeStop <= frameMax }
			{
				msg = [ "/b_read", buf.bufnum, path, frame, halfBufSize, bufOff, 0, completionMsg ];
				if( bndl.notNil, { bndl.add( msg ); });
			}
			{ frame < frameMax }
			{
				msg 		= [ "/b_read", buf.bufnum, path, frame, frameMax - frame, bufOff, 0, completionMsg ];
				bufOff	= bufOff + frameMax - frame;
				if( bndl.notNil, {
					bndl.add( msg );
					bndl.add([ "/b_fill", buf.bufnum, bufOff, halfBufSize - frameMax + frame, 0.0 ]);
// otherwise assume, buffer has been zeroed before!
//				}, {
//					if( emptyFilePath.notNil, {
//						
//					});	// else omit the /b_fill (not possible in async chain)
				});
				if( verbose, {
					("TapeUnit: zeroing off = "++bufOff++
						"; len = "++(halfBufSize - frameMax + frame)).postln;
				});
			}
			{
				if( frame - halfBufSize < frameMax, {
					if( bndl.notNil, {
						msg = [ "/b_zero", buf.bufnum, completionMsg ];
						bndl.add( msg );
					}, {
						// assume buffer has been zeroed
						msg = completionMsg;
					});
					if( verbose, {
						("TapeUnit: zeroing complete buffer").postln;
					});
				}, {
					currentPosT	= nil;
					startPos		= 0;
					msg			= completionMsg;
				});
			};
		},
		// LOOPED
		{
			currentPos	= frame - halfBufSize;
			if( frame > loopSpan.start, { frame = ((frame - loopSpan.start) % loopSpan.getLength) + loopSpan.start; });
			if( currentPos > loopSpan.start, {
				currentPos = ((currentPos - loopSpan.start) % loopSpan.getLength) + loopSpan.start;
			});
			frameMax		= loopSpan.stop;
			frameLen		= halfBufSize;
			if( bndl.notNil, {
				while({ frameLen > 0 }, {
					if( frame + frameLen <= frameMax, {
						msg			= [ "/b_read", buf.bufnum, path, frame, frameLen, bufOff, 0, completionMsg ];
						bndl.add( msg );
						frameLen 		= 0;	
					}, {
						msg			= [ "/b_read", buf.bufnum, path, frame, frameMax - frame, bufOff, 0 ];
						bndl.add( msg );
						bufOff		= bufOff + frameMax - frame;
						frameLen		= frameLen - frameMax + frame;
						frame		= loopSpan.start;
					});
				});
			}, {
				msg = completionMsg;	// since the loop is executed in reverse order on the server, make sure the completionMsg is executed last
				while({ frameLen > 0 }, {
					if( frame + frameLen <= frameMax, {
						msg			= [ "/b_read", buf.bufnum, path, frame, frameLen, bufOff, 0, msg ];
						frameLen		= 0;	
					}, {
						msg			= [ "/b_read", buf.bufnum, path, frame, frameMax - frame, bufOff, 0, msg ];
						bufOff		= bufOff + frameMax - frame;
						frameLen		= frameLen - frameMax + frame;
						frame		= loopSpan.start;
					});
				});
			});
		});
		
//		if( bndl.notNil, {Êthis.changed( \currentPos, currentPos );});
		this.changed( \currentPos, currentPos );
		^msg;
	}

	getAttributes {
		if( cueAttrDirty, {
			attributes[ attributes.size - 1 ] = UnitAttr( \cueIndex, ControlSpec( 0, this.getNumCues - 1, \lin, 1 ), \normal, \getCueIndex, \setCueIndex, nil );
			cueAttrDirty		= false;
		});
		^attributes;
	}

	// XXX : should call class method prFlushDefs only once and not per instance ... but how?
	// ALSO : cache should be in a per server list!!!!
	protServerStarted {
		var result;
		
		result = super.protServerStarted;
		this.class.prFlushDefs;
		if( this.getPath.notNil, {
			this.prCacheDef( this.getNumChannels );
		});
		^result;
	}
	
	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		trigResp.remove;
		synth		= nil;
		buf			= nil;
		startPos		= this.getCurrentPos;
		currentPosT	= nil;
		
		^result;
	}

	setStartPos { arg pos;
		startPos = pos;
	}
	
	getCurrentPos {
		var pos;
		
		if( currentPosT.notNil && file.notNil, {
			pos = (currentPos + ((SystemClock.seconds - currentPosT) * server.sampleRate * speed).asInt)
				.clip( 0, file.numFrames );
			if( loopSpan.notNil and: { currentPos > loopSpan.start }, {
				pos = ((pos - loopSpan.start) % loopSpan.getLength) + loopSpan.start;
			});
		}, {Êpos = startPos });
		
		^pos;
	}
	
	getNumFrames {
		^if( file.notNil, {
			file.numFrames;
		}, -1 );
	}

	getSampleRate {
		^if( file.notNil, {
			file.sampleRate;
		}, -1 );
	}

	/**
	 *	Activates or deactivates looping and adjusts the looping span.
	 *
	 *	@param	span		(Span) the span to loop, or nil to switch off looping.
	 *					Note that minimum loop length is 256 sample frames to
	 *					avoid OSC message overloads. Passing an empty span equals
	 *					setting the loop to nil (no looping).
	 *	@todo	changing the looping span while playing may result in strange position shift
	 *			; safer is to call stop / setLoop / play
	 */
	setLoop {Êarg span;
		if( span.isNil or: {Êspan.getLength >= 256 }, {
			loopSpan = span;
		}, { if( span.isEmpty, {
			loopSpan = nil;
		})});
	}
	
	loopAll {
		this.setLoop( Span( 0, this.getNumFrames ));
	}
	
	getLoop {
		^loopSpan;
	}

	
	play { arg position, atk = 0;
		var bndl;

		bndl = List.new;
		this.playToBundle( bndl, position, atk );
		server.listSendBundle( nil, bndl );
	}
	
	/**
	 *	This method can be called prior to play in order
	 *	to have the buffer creation ready and decrease latency
	 */
	allocBuffer {
		if( buf.isNil, {
			if( file.notNil, {
				buf	= Buffer.new( server, bufSize, file.numChannels );
// ZZZ
//				this.protAddGlobalBuffer( buf );
//				buf.allocRead( path, 0, bufSize );
				buf.alloc;
if( debugBuffer, { (this.hash.asString ++ " : alloc "++buf).postln; });
			}, {
				TypeSafe.methodWarn( thisMethod, "No path has been specified" );
			});
		});
	}

	freeBuffer {
		if( buf.notNil, {
			buf.close;
// ZZZ
//			this.protRemoveGlobalBuffer( buf );
			buf.free;
if( debugBuffer, {Ê(this.hash.asString ++ " : free "++buf).postln; });
			buf = nil;
		});
	}

	playToBundle { arg bndl, position, atk = 0;
		var allocMsg, bus;

		if( playing, {
			this.stop;
			if( synth.notNil, {Êthis.protRemoveNode( synth );});
			synth = nil;
			trigResp.remove;
		});

		if( file.notNil, {
//			startPos	= position; // ?? { this.getCurrentPos; };
			startPos = position ?? { this.getCurrentPos; };
			synth	= Synth.basicNew( defName, server );
			bus		= this.getOutputBus;

			if( buf.isNil, {
				buf	= Buffer.new( server, bufSize, file.numChannels );
if( debugBuffer, {Ê(this.hash.asString ++ " : alloc "++buf).postln; });
// ZZZ
//				this.protAddGlobalBuffer( buf );
				// this is stupid: reading the whole buffer then zeroing it. however
				// there is no way to specify the sample rate if we used /b_alloc instead
				// and the synthdef relies on sample rate (BufRateScale etc.)
				allocMsg = buf.allocMsg( buf.zeroMsg( this.prUpdateBuffer( 0, nil, this.prUpdateBuffer( 1, nil, synth.runMsg( true )))));
//				allocMsg = buf.allocMsg( this.prUpdateBuffer( 0, nil, this.prUpdateBuffer( 1, nil, synth.runMsg( true ))));
			});
			bndl.add( synth.newMsg( target, [Ê\i_inBuf, buf.bufnum, \i_bufRate, file.sampleRate, \rate, speed, \out,
				bus.notNil.if({ bus.index }, 0 ), \i_atk, atk, \volume, volume ], \addToHead ));
			bndl.add( synth.runMsg( false ));
			if( allocMsg.notNil, {
				bndl.add( allocMsg );
			}, {
				this.prUpdateBuffer( 0, bndl );
				this.prUpdateBuffer( 1, bndl, synth.runMsg( true ));
			});

			this.protAddNode( synth );
			trigResp.add;
			this.protSetPlaying( true );

		}, {
			TypeSafe.methodWarn( thisMethod, "No path has been specified" );
		});
	}

//	dispose {
//// ZZZ
//this.freeBuffer;
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
	
	setPath { arg pathName;
		var wasPlaying;
	
		wasPlaying = playing;
		if( wasPlaying, { this.stop });
		file	= this.cachePath( pathName );
		if( file.notNil, {
			path	= pathName;
			if( buf.notNil, {
				if(Êfile.numChannels === buf.numChannels, {
					buf.close;
				}, {
					this.freeBuffer;
				});
			});
			numChannels	= file.numChannels;
			defName		= "tapeUnit" ++ numChannels;
			if( wasPlaying, { this.play( this.getCurrentPos )});
		});
	}
	
	getPath {
		^path;
	}
	
	setSpeed { arg factor;
		speed = factor;
		if( playing, {
			this.stop;
			this.play( this.getCurrentPos );
		});
	}
	
	getSpeed {
		^speed;
	}

	*flushCache {
		mapPathsToSoundFiles.clear;
		this.prFlushDefs;
	}
	
	*prFlushDefs {
		defSet.clear;
	}
	
	cachePath {Êarg pathName;
		var file;
		file	= mapPathsToSoundFiles[ pathName ];
		if( file.isNil, {
			try {
				file = SoundFile.openRead( pathName );
			};
			if( file.isNil, {
				TypeSafe.methodError( thisMethod, "Soundfile '" ++ pathName ++ "' couldn't be opened" );
				^nil;
			});
			file.close;
			mapPathsToSoundFiles.put( pathName, file );
		});
		this.prCacheDef( file.numChannels );
		^file;
	}

	prCacheDef { arg numChannels;
		var defName, def;
		if( defSet.includes( numChannels ).not, {
			defName	= ("tapeUnit" ++ numChannels).asSymbol;
			def		= SynthDef( defName, {
				arg out = 0, i_inBuf, rate = 1.0, i_trigID = 10, i_atk = 0, volume = 1, gate = 1, i_bufRate;

				var clockTrig, numFrames, halfPeriod, phasorRate;
				var phasorTrig, phasor, bufReader, interp;
				var env, envGen;

				env 			= Env.asr( 0.1, 1.0, 0.1, \sine ).asArray;
				env[ 5 ]		= i_atk;
				envGen 		= EnvGen.ar( env, gate, doneAction: 2 );

//				numFrames		= BufFrames.ir( i_inBuf );
// FUCKING CRUCIAL TO BE KR!!
				numFrames		= BufFrames.kr( i_inBuf );
//				phasorRate 	= BufRateScale.kr( i_inBuf ) * rate;
				phasorRate 	= (i_bufRate / SampleRate.ir) * rate;
//				halfPeriod	= BufDur.kr( i_inBuf ) / (2 * rate);
				halfPeriod	= numFrames / (i_bufRate * 2 * rate);
				phasor		= Phasor.ar( 0, phasorRate, 0, numFrames - pad - pad ) + pad;

				// BufRd interpolation switches between 1 (none) and 4 (cubic)
				// depending on the rate being 1.0 or not
				interp		= (rate - 1.0).sign.abs * 3 + 1;
				bufReader 	= BufRd.ar( numChannels, i_inBuf, phasor, 0, interp );
				phasorTrig	= Trig1.kr( A2K.kr( phasor ) - (numFrames / 2), 0.01 );
				clockTrig		= phasorTrig + TDelay.kr( phasorTrig, halfPeriod );

				SendTrig.kr( clockTrig, i_trigID, PulseCount.kr( clockTrig ));
				OffsetOut.ar( out, bufReader * envGen * volume );
			}, [ 0, 0, 0, 0, 0, 0.01, 0 ]);
			defSet.add( numChannels );
			def.send( server );
		});
	}

	n_go {Êarg node;
		if( node === synth, {
			currentPosT = SystemClock.seconds;
			currentPos = startPos;
		});
		^super.n_go( node );
	}
	
	n_end { arg node;
		if( node === synth, {
			trigResp.remove;
			synth		= nil;
			startPos		= this.getCurrentPos;
			currentPosT	= nil;
			this.protSetPlaying( false );
		});
		^super.n_end( node );
	}

	protDuplicate { arg dup;
		dup.setSpeed( this.getSpeed );
		dup.setPath( this.getPath );
		dup.setLoop( this.getLoop );
		dup.prSetCues( cues );
		dup.setCueIndex( this.getCueIndex );
		dup.setStartPos( this.getCurrentPos );
	}
}