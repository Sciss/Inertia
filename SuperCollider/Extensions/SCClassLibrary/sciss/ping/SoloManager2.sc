/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe, Ping, SynthDefCache
 *
 *	Changelog
 *	- 08-Mar-06 	bus argument is MappedObject, not Bus directly
 *	- 13-Jun-06	stripped down for ping usage
 *
 *	@author	Hanns Holger Rutz
 *	@version	13-Jun-06
 *
 *	@todo	verschiedene synthdef namen quatsch, weil speech cues nur auf
 *			erstem kanal gespielt werden (sollen)
 */
SoloManager2 : Object
{
	var <>verbose		= false;

	var <server;
	var bus;
	var group;
	var currentSolo	= nil;	// PlayableToBus
	var mapSpeechCues;			// IdentityDictionary: key = (Symbol) cue name, value = (Cue) c
	var soloVolume	= 1.0;
	var speechCueBuf, speechCueSynth, speechCuePath;
	var speechVol		= 0.5;

	var defCache;
	var defName;
	
	var maxCueLength = 1;

	*new { arg server;
		^super.new.prInitSoloManager( server );
	}
	
	prInitSoloManager { arg argServer;
		var def, bndl;
	
		server		= argServer ?? { Server.default };
		defCache		= SynthDefCache.newFrom( server );

//		TypeSafe.checkArgClasses( thisMethod, [ argServer ], [ Server ], [ false ]);

		mapSpeechCues	= IdentityDictionary.new;
		group		= Group( server );
	}
	
	setSpeechVol {Êarg vol;
		speechVol = vol;
	}

	setOutputBus { arg argBus;
		var bndl, oldSolo, oldDefName, oldChans, def;
		
		oldSolo = this.getSolo;
		this.unsolo;
		oldChans = if( bus.isNil, -1, {Êbus.numChannels; });
		
		bus			= argBus;
		oldDefName	= defName;
		defName		= ("SpeechCue" ++ bus.numChannels).asSymbol;
		if( oldChans != bus.numChannels, {
			speechCueSynth.free;
			speechCueSynth = nil;
			if( oldDefName.notNil, {ÊdefCache.remove( oldDefName );Ê});
			def = SynthDef( defName, { arg aInBuf, aBus, t_trig = 0, dur = 1, vol = 1;
				var in, cue, pause;
				
				// doneAction: 1 = pause self
				pause	= EnvGen.kr( Env.new([ 0, 0 ], [ 1 ], \step ), t_trig,
								   timeScale: dur, doneAction: 1 );
				in		= In.ar( aBus, 1 ) * pause;
				cue		= PlayBuf.ar( 1, aInBuf, 1.0, t_trig, 0, 0 );
				ReplaceOut.ar( aBus, (in * 0.02) + (cue * vol) );
			});
			defCache.add( def );
		});
		this.solo( oldSolo );
	}

	getOutputBus {
		^bus;
	}
	
	setSpeechCuePath { arg path;
		var file;

		try {
			file = SoundFile.openRead( path );
		};
		if( file.isNil, {
			TypeSafe.methodError( thisMethod, "Soundfile '" ++ path ++ "' couldn't be opened" );
			^this;
		});
		file.close;
		if( file.numChannels != 1, {
			TypeSafe.methodError( thisMethod, "Soundfile '" ++ path ++ "' must be mono" );
			^this;
		});
		speechCuePath = path;
	}
	
	addSpeechCues {Êarg regionFile;
		var stakes;
		
		stakes = Ping.loadRegions( regionFile );
		stakes.do({ arg stake;
			this.addSpeechCue( stake );
		});
	}
	
	addSpeechCue { arg region;
		TypeSafe.checkArgClasses( thisMethod, [ region ], [ RegionStake ], [ false ]);
		
		if( region.getSpan.getLength > maxCueLength, {	
			if(ÊspeechCueBuf.notNil, {
				speechCueSynth.free;
				speechCueSynth = nil;
				speechCueBuf.free;
				speechCueBuf = nil;
			});
			maxCueLength = region.getSpan.getLength;
		});

		mapSpeechCues.put( region.name.asSymbol, region );
	}

	removeSpeechCue { arg name;
		TypeSafe.checkArgClasses( thisMethod, [ name ], [ Symbol ], [ false ]);
		mapSpeechCues.removeAt( name );
	}
	
	playSpeechCue { arg name;
		var bndl, cue;
		
		if( speechCuePath.isNil, {
			TypeSafe.methodError( thisMethod, "No soundfile has been specified" );
			^this;
		});
		
		cue	= mapSpeechCues.at( name.asSymbol );
		if( cue.isNil.not, {
			bndl = List.new;
			if( speechCueSynth.isNil, {
				if( defName.isNil or: {Êbus.isNil }, {
					TypeSafe.methodError( thisMethod, "Output bus has not been specified" );
					^this;
				});
				if( speechCueBuf.isNil, {
					speechCueBuf = Buffer( server, maxCueLength, 1 );
					if( speechCueBuf.bufnum.isNil, {
						TypeSafe.methodError( thisMethod, "Could not allocate buffer" );
						speechCueBuf		= nil;
						^this;
					});
					speechCueSynth = Synth.basicNew( defName, server );
					bndl.add( speechCueSynth.newMsg( group, [ \aBus, bus.index, \aInBuf, speechCueBuf.bufnum,
						\vol, speechVol ], \addToTail ));
					bndl.add( speechCueSynth.runMsg( false ));
					bndl.add( speechCueSynth.setMsg( \t_trig, 1, \dur, (cue.getSpan.getLength) / server.sampleRate ));
					bndl.add( speechCueBuf.allocMsg(
						speechCueBuf.readMsg( speechCuePath, cue.getSpan.start, cue.getSpan.getLength, 0, false,
							 speechCueSynth.runMsg( true ))));					server.listSendBundle( nil, bndl );
					^this;
				}, {
					speechCueSynth = Synth.basicNew( defName, server );					bndl.add( speechCueSynth.newMsg( group, [ \aBus, bus.index, \aInBuf, speechCueBuf.bufnum ], \addToTail ));
				});
			});
			bndl.add( speechCueSynth.runMsg( false ));
			bndl.add( speechCueSynth.setMsg( \t_trig, 1, \dur, (cue.getSpan.getLength) / server.sampleRate ));
			bndl.add( speechCueBuf.readMsg( speechCuePath, cue.getSpan.start, cue.getSpan.getLength, 0, false, speechCueSynth.runMsg( true )));
			server.listSendBundle( nil, bndl );
		}, {
			TypeSafe.methodError( thisMethod, "Cue not found : " ++ name );
		});
	}

	commitSolo {
		if( currentSolo.isNil.not, {
			currentSolo.changed( \soloCommit );
		});
		if( verbose, { ("SoloManager: commit solo " ++ currentSolo).postln; });
	}
	
	getSolo {
		^currentSolo;
	}
	
	// has to be called from inside routine
	solo { arg source;
		var oldSolo = currentSolo;

		if( oldSolo.notNil, {
//			server.sync;
			oldSolo.removePlayBus( bus );
			if( verbose, { ("SoloManager: unsolo'ed " ++ oldSolo).postln; });
		});
		currentSolo = source;
		// call .changed here because the listener might want
		// to query the new solo source
		if( oldSolo.notNil, {ÊoldSolo.changed( \soloLost );});
		if( currentSolo.isNil.not, {
//			server.sync;
			currentSolo.addPlayBus( bus, soloVolume );
			currentSolo.changed( \soloGained );
		});
		if( verbose, { ("SoloManager: solo'ed " ++ currentSolo).postln; });
	}
	
	// has to be called from inside routine
	unsolo {
		if( currentSolo.isNil.not, {
//			server.sync;
			currentSolo.removePlayBus( bus );
			currentSolo.changed( \soloLost );
			if( verbose, { ("SoloManager: unsolo'ed " ++ currentSolo).postln; });
			currentSolo = nil;
		});
	}

	getGroup {
		^group;
	}
	
//	pause {
//		group.run( false );
//	}
//
//	resume {
//		group.run( true );
//	}
//
//	start {
//		if( group.isRunning.not, { this.resume; });
//	}
//	
//	stop {
//		if( group.isRunning, { this.pause; });
//	}

	setVolume { arg vol;
		soloVolume = vol;
		if( currentSolo.isNil.not, {
			currentSolo.setPlayBusVolume( bus, soloVolume );
		});
	}

	dispose {
		group.free;
		speechCueBuf.free;
	}
}