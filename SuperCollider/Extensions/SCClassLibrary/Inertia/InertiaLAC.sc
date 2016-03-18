/**
 *	(C)opyright 2005-2007 by Hanns Holger Rutz. All rights reserved.
 *
 *	@version	0.4, 09-Mar-07
 *	@author	Hanns Holger Rutz
 */
 InertiaLAC {
	classvar <dataFolder;
	classvar <>hidDebug		= false;

	var <>soundCard 			= "Built-in Audio"; // "Mobile I/O 2882 [2600]"; // "MOTU Traveler";
	var <>numInputBusChannels	= 2;
	var <>numOutputBusChannels	= 8;
	var <>numAudioBusChannels	= 256; 			// 256;   // critical!!
	var <>soloIndex			= 0;
	var <>soloChannels			= 2;
	var <>masterIndex			= 0;
	var <>masterChannels		= 2; // 8;
	var <>tapeChannels			= 2;
	var <>textChannels			= 2;
	var <>gluionIP			= "192.168.5.77";
	var <>gluionPort			= 57120;
	
	var <>tapeBusOffset		= 0; // 4; // 6;		// get them out at ch. 7+8
	var <>textBusOffset		= 0;
	var <>verbBusOffset		= 0; // 4; // 6;

	var <>jitIP				= "127.0.0.1";
	var <>jitPort				= 57111;
	var <jitAddr;

	var <server;
	var <solo;
	var <master;
	var <masterBus;
	var <grpMaster;
	var <verbBus;
	
	var <doc;		// instanceof InertiaSession
//	var <player;	// instanceof InertiaPlayer
	var <jit;		// instanceof InertiaJitterClient

//	var <hidDevice;
	var <controller;
	var nextOSCPath;
	
	var oscPaths;

	// ------- synths & co. -------
	var <lim;
	var <tapeText;
	var <reverb;
	var <tapeAtmo;
	
	var <masterVol	= 0.2;	// linear
	
	var <section		= 0;		// decibels

	var keyMode		= \none;	// \mobile, \filter1 ... \filter3, \tape, \loop, \layer
	
	// ------- volume control -------
	var <taskVolCon;
	var volConInc		= 0;		// master
	var volConIncT	= 0;		// tape
	
	var tapeVolumes;	// linear
	var <tapeMasterVol	= 1.0;
	var tapePositions;
	
	*initClass {
		dataFolder	= "~/scwork/trailers".standardizePath ++ "/";
	}

	*new { arg server;
		^super.new.prInit( server );
	}
	
	prInit { arg argServer;
		server 			= argServer ?? { Server.default; };
		jitAddr			= NetAddr( jitIP, jitPort );
		oscPaths			= Array.fill( InertiaSession.kNumMovies + 1, { arg ch;
			"/inertia/" ++ (65 + ch).asAscii;
		});
		oscPaths[ oscPaths.size - 1 ] = "/inertia/all";
		
		taskVolCon		= Task({
			inf.do({
				0.1.wait;
//				("volConInc.dbamp = "++volConInc.dbamp).postln;
				if( volConInc != 0, {
					this.setMasterVolume( (this.masterVol * volConInc.dbamp).clip( 0.01, 1.0 ));
				});
				if( volConIncT != 0, {
					this.setTapeVolume( volConIncT );
				});
			});
		});
		
		tapeVolumes = [ 0.dbamp, 0.dbamp, -3.dbamp, 0.dbamp, 0.dbamp, -1.0.dbamp, -1.0.dbamp, 0.dbamp ];
		tapePositions = Array.fill( 8, 0 );
	}

	*run {
		var ils;
	
		ils 		= this.new;
		ils.start({ arg ils; {
//				~rec = SimpleRecorder( p.server );
//				~rec.channelOffset	= 2;
//				~rec.numChannels	= 4;
//				~rec.sampleFormat	= "int24";
//				~rec.makeWindow;
				"Initializing Controller ... ".postln;
				ils.initController;
				0.wait;
				"    ... done".postln;
				
				"Loading Session ... ".postln;
				
				0.wait;
				ils.loadSession;
				"    ... done".postln;
				0.wait;
				ils.initPlayers;

				ils.taskVolCon.start;
				
				{ ils.makeHelperWindow; }.defer;	// defer because of a bug in routine

			}.fork( AppClock );
		});
		^ils;
	}
	
	loadSession {
		doc = InertiaSession.load( dataFolder ++ "session/session.xml", dataFolder ++ "audio/" );
	}

	initPlayers {
		var def, bus;
	
		jit = InertiaJitterClient( doc );
//		player = InertiaPlayer( doc );
		doc.createPlayer;

		grpMaster = Group.tail( server );

		verbBus	= Bus.audio( server, tapeChannels );
		
		// ---------- limiter ----------

		def = SynthDef( \masterLim, { arg bus, ceil = 0.98, boost = 0.5;
				ReplaceOut.ar( bus, Limiter.ar( In.ar( bus, masterChannels ) * boost, ceil ));
		}, [ 0, 0.1, 0.1 ]);
		lim = Synth.basicNew( \masterLim, server );
		def.send( server, lim.newMsg( grpMaster,
			[ \bus, masterIndex, \boost, masterVol, \ceil, min( 0.98, masterVol * 2 )]));

		// ---------- global reverb ----------

		def = SynthDef( \reverb, { arg in, out, roomsize, revtime, damping, inputbw, spread = 15,
				drylevel, earlylevel, taillevel;
			var inp, verb;
			
			inp	= Mix.ar( In.ar(Êin, tapeChannels ));
			verb	= GVerb.ar( inp, roomsize, revtime, damping, inputbw, spread,
						   drylevel, earlylevel, taillevel, roomsize );
			Out.ar( out, verb );
		});
		reverb = Synth.basicNew( \reverb, server );
		def.send( server, reverb.newMsg( grpMaster,
			[ \in, verbBus, \out, masterIndex + verbBusOffset, \roomsize, 80, \revtime, 5, \damping, 0.18, \inputbw, 0.2,
			  \drylevel, -15.dbamp, \earlylevel, -9.dbamp, \taillevel, -3.dbamp ]));
			  
		def = SynthDef( \rampSend, {Êarg in, out, level = 0.5, fadeIn = 1, fadeOut = 0.01;
			var inp, ramp, env;
			
			inp	= In.ar( in, tapeChannels );
			env	= Env.new([ 0, level, 0 ],[ fadeIn, fadeOut ], \sine );
			// array[ 4 ] = level; array[ 5 ] = fadeIn, array[ 9 ] = fadeOut
			ramp	= EnvGen.ar( env, doneAction: 2 );
			Out.ar( out, inp * ramp );
		});
		def.send( server );

		// ---------- intro / outro tape ----------

		tapeText = TapeUnit.new;
		tapeText.addCue( dataFolder ++ "introoutro/AlphavilleIlArrive.aif" );
		tapeText.addCue( dataFolder ++ "introoutro/AlphavilleLePresentEstComme.aif" );
		tapeText.setGroup( Group.before( grpMaster ));
		tapeText.setOutputBus( Bus( \audio, masterIndex + textBusOffset, masterChannels, server ));
//		tapeText.setCueIndex( 0 );
		
		// ---------- atmo tape ----------
		tapeAtmo = TapeUnit.new;
		
		{
			PathName( dataFolder ++ "lostshadows" ).files.do({ arg f;
				tapeAtmo.addCue( f.fullPath );
			});
		}.defer;
		tapeAtmo.setGroup( Group.before( grpMaster ));
		bus	= Bus.audio( server, tapeChannels );
		tapeAtmo.setOutputBus( bus );
//		tapeAtmo.setCueIndex( 0 );
		// feed the private tape bus onto master sum
		Monitor.new.play( bus.index, tapeChannels, masterIndex + tapeBusOffset, tapeChannels,
			tapeAtmo.getGroup, false, 1.0, 0.1, \addAfter );


		// ----------- filter (XXX static) -----------
		doc.player.setFilter( 1, nil, "Filter1" );
		doc.player.setFilter( 2, nil, "Filter2" );
		doc.player.setFilter( 3, nil, "Filter3" );

		jit.setJitterAddr( jitAddr );
		jit.setCoupling( InertiaJitterClient.kCoupNone );
		jit.handshake;
	}
	
	setMasterVolume { arg vol;
		masterVol = vol;
		lim.set( \boost, vol, \ceil, min( 0.98, vol * 2 ));
		this.changed( \masterVolume );
	}
	
	setTapeVolume { arg volInc;
		tapeMasterVol = (tapeMasterVol * volConIncT.dbamp).clip( 0.01, 2.0 );
		if( tapeAtmo.isPlaying, {
			tapeAtmo.setVolume( (tapeAtmo.getVolume * volConIncT.dbamp).clip( 0.01, 2.0 ));
		});
		this.changed( \tapeVolume );
	}
	
	initController {
		var oldVal1 = 0, oldVal2 = 0;
		
		controller = OSCresponder( NetAddr( gluionIP, gluionPort ), '/switch', { arg time, resp, msg;
			var val1, val2, path, trig, col, row, mask;
			val1 = msg[ 1 ];
			val2 = msg[ 2 ];
			if( (val1 != oldVal1) or: {Êval2 != oldVal2 }, {
				40.do({ arg schoko;
			   		trig = false;
					if( schoko < 24, {
						mask = (1 << schoko);
						trig = ((oldVal1 & mask) == 0) and: { (val1 & mask) != 0 };
			   		  }, {
			   		  	mask = (1 << (schoko - 24));
			   		  	trig = ((oldVal2 & mask) == 0) and: { (val2 & mask) != 0 };
					});
					if( trig, {
						col	= schoko % 10;
						row	= schoko.div( 10 );
						path	= "/inertia/" ++ (68 - row).asAscii;
						if( col < 8, {
							jitAddr.sendMsg( path, col );
							fork {Ê0.1.wait; jitAddr.sendMsg( path, \start ); };
						}, {
							jitAddr.sendMsg( path, \stop );
						});
					});
			   	});
			   	oldVal1 = val1;
			   	oldVal2 = val2;
			});
		}).add;
	}
	
	setSection { arg x;
		section = x;
		this.changed( \section );
	}
	
	prMobileAction { arg id;
		case
		{ id == 0 }	// stop mobile
		{
			if( doc.player.isMobilePlaying, { doc.player.stopMobile });
		}
		{ id == 1 }	// fast mobile
		{
			InertiaMobile.kMinDelay	= 4000;
			InertiaMobile.kMaxDelay	= 20000;
			InertiaMobile.kMaxStep	= 5000;
			if( doc.player.isMobilePlaying.not, { doc.player.startMobile });
		}
		{ id == 2 }	// medium mobile
		{
			InertiaMobile.kMinDelay	= 5000;
			InertiaMobile.kMaxDelay	= 50000;
			InertiaMobile.kMaxStep	= 10000;
			if( doc.player.isMobilePlaying.not, { doc.player.startMobile });
		}
		{ id == 3 }	// slow mobile
		{
			InertiaMobile.kMinDelay	= 20000;
			InertiaMobile.kMaxDelay	= 90000;
			InertiaMobile.kMaxStep	= 10000;
			if( doc.player.isMobilePlaying.not, { doc.player.startMobile });
		};
	}

	prTapeAction { arg id;
		if( id == 0, {
			if( tapeAtmo.isPlaying, {
				Synth( \rampSend, [ \in, tapeAtmo.getOutputBus.index, \out, verbBus.index,
					\fadeIn, 2.5, \fadeOut, 0.01 ], tapeAtmo.getGroup, \addAfter );
				fork {
					1.0.wait;
					tapePositions[ tapeAtmo.getCueIndex ] = tapeAtmo.getRatioPos;
//("tapePositions[ " ++ tapeAtmo.getCueIndex ++ " ] = "++tapePositions[ tapeAtmo.getCueIndex ]).postln;
					tapeAtmo.stop( 1.51 );
				};
			});
		}, { id = id - 1; if( id < tapeAtmo.getNumCues, {
			if( tapeAtmo.isPlaying.not, {
				tapeAtmo.setCueIndex( id );
				tapeAtmo.setRatioPos( if( tapePositions[ tapeAtmo.getCueIndex ] < 0.9,
					tapePositions[ tapeAtmo.getCueIndex ], 0 ));
//("tapeAtmo.setRatioPos( " ++ tapePositions[ tapeAtmo.getCueIndex ] ++ " )").postln;
				tapeAtmo.setVolume( tapeVolumes[ id ] * tapeMasterVol );
				fork {ÊtapeAtmo.play( atk: 0.01 )};
			});
		})});
	}

	prLayerAction { arg id;
		case
		{ id == 0 }	// alle durchwinken
		{
			InertiaPlayer.kDurchgewunken 		= 12;
			InertiaPlayer.kDurchgewunkenOffset 	= 12;
		}
		{ id == 1 }	// max durchwinken
		{
			InertiaPlayer.kDurchgewunken 		= 6;
			InertiaPlayer.kDurchgewunkenOffset 	= 4;
		}
		{ id == 2 }	// norm durchwinken
		{
			InertiaPlayer.kDurchgewunken 		= 3;
			InertiaPlayer.kDurchgewunkenOffset 	= 0;
		}
		{ id == 3 }	// min durchwinken
		{
			InertiaPlayer.kDurchgewunken 		= 1;
			InertiaPlayer.kDurchgewunkenOffset 	= 0;
		}
		{ id == 4 }	// max filtered
		{
			InertiaPlayer.kShadowCoin = 0.0;
		}
		{ id == 5 }	// more filtered
		{
			InertiaPlayer.kShadowCoin = 0.25;
		}
		{ id == 6 }	// 50:50 filtered
		{
			InertiaPlayer.kShadowCoin = 0.5;
		}
		{ id == 7 }	// less filtered
		{
			InertiaPlayer.kShadowCoin = 0.75;
		}
		{ id == 8 }	// min filtered
		{
			InertiaPlayer.kShadowCoin = 1.0;
		};
		InertiaPlayer.kOverlap = InertiaPlayer.kDurchgewunken / 4;
	}

	prLoopAction { arg id;
	
	}

	prFilterAction { arg id, filterIdx;
	
	}
	
	prVolumeControl { arg val;
		val = (127 - val) / 127;
		volConInc = val.squared * val.sign;
//		("val = "++val++"; volConInc = "++volConInc).postln;
//		if( val == 0, {
//			taskVolCon.stop;
//		}, {
//			("taskVolCon.isPlaying.not = "++taskVolCon.isPlaying.not).postln;
//			if( taskVolCon.isPlaying.not, {
//				taskVolCon.reset;
//				taskVolCon.start;
//			});
//		});
	}

	prVolumeControlT { arg val;
		val = (127 - val) / 127;
		volConIncT = val.squared * val.sign;
	}
	
	intro {
		this.setSection( 1 );	// main
		fork {
			1.0.wait;
			jitAddr.sendMsg( oscPaths[ 0 ], "intro" );
			jitAddr.sendMsg( oscPaths[ 0 ], "start" );
			2.0.wait;
			jitAddr.sendMsg( oscPaths[ 1 ], "intro" );
			jitAddr.sendMsg( oscPaths[ 1 ], "start" );
			2.0.wait;
			jitAddr.sendMsg( oscPaths[ 2 ], "intro" );
			jitAddr.sendMsg( oscPaths[ 2 ], "start" );
			1.0.wait;
			jitAddr.sendMsg( oscPaths[ 3 ], "intro" );
			jitAddr.sendMsg( oscPaths[ 3 ], "start" );
		};
		fork {
			server.sync;
			1.2.wait;
			tapeText.setCueIndex( 0 );
			tapeText.setVolume( -14.dbamp );
			tapeText.play;
			jit.setCoupling( InertiaJitterClient.kCoupSlave );
		};
	}

	outro {
		fork {
			1.0.wait;
			jitAddr.sendMsg( oscPaths[ 0 ], "outro" );
			jitAddr.sendMsg( oscPaths[ 0 ], "start" );
			2.0.wait;
			jitAddr.sendMsg( oscPaths[ 1 ], "outro" );
			jitAddr.sendMsg( oscPaths[ 1 ], "start" );
			2.5.wait;
			jitAddr.sendMsg( oscPaths[ 2 ], "outro" );
			jitAddr.sendMsg( oscPaths[ 2 ], "start" );
			2.7.wait;
			jitAddr.sendMsg( oscPaths[ 3 ], "outro" );
			jitAddr.sendMsg( oscPaths[ 3 ], "start" );
		};
		this.setSection( 2 );	// ˆ la fin
		jit.setCoupling( InertiaJitterClient.kCoupNone );
		doc.transport.stopAllAndWait;
		fork {
			server.sync;
			1.2.wait;
			tapeText.setCueIndex( 1 );
			tapeText.setVolume( -5.dbamp );
			tapeText.play;
		};
	}

	start { arg doWhenBooted;
		var o;
		
		if( server.serverRunning, {
			TypeSafe.methodWarn( thisMethod, "Server already running. Options may be wrong" );
			fork {
				this.prInitAfterBoot;
			}
		}, {
			o					= server.options;
			o.device				= soundCard;
			o.numInputBusChannels	= numInputBusChannels;
			o.numInputBusChannels	= numInputBusChannels;
			o.numAudioBusChannels	= numAudioBusChannels;
			
			server.waitForBoot({
				1.0.wait;
				this.prInitAfterBoot;
				doWhenBooted.value( this );
			});
		});
	}

	prInitAfterBoot {
//		solo 	= SoloManager2( server );
//		solo.setOutputBus( Bus( \audio, soloIndex, soloChannels, server ));
//		solo.setSpeechCuePath( Ping.dataFolder ++ "SpeechCuesPlain.aif" );
//		solo.addSpeechCues( Ping.dataFolder ++ "speech.rgn" );
//		solo.setSpeechVol( 0.2 );
		
//		masterBus	= Bus.audio( server, 3 );
//		master 	= MasterUnit.new( server );
//		master.setInputBus( masterBus );
//		master.setOutputBus( Bus( \audio, masterIndex, masterChannels ));
		
		// necessary to wait for synth defs
		server.sync;
		
//		master.play;
	}

	makeHelperWindow {
		var win, flow, layerListener, movieLabels, ggMobileOnOff, ggMasterVol,
		    ggTapeVol, ggTime, taskTimer;
		
		win = GUI.window.new( "Trailers Lost Shadows", Rect( 200, 400, 400, 300 ), resizable: false );
		flow	= FlowLayout( win.view.bounds );
		win.view.decorator = flow;
		win.view.background = Color.white;
		
//		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
//			.states_([[ "Net Test" ]])
//			.action_({
//				Ping.fritzelSendTest;
//			});
//

		GUI.button.new( win, Rect( 0, 0, 100, 30 ))
			.states_([[ "Jit Handshake" ]])
			.action_({
				jit.handshake;
			});

//		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
//			.states_([[ "ABus Alloc" ]])
//			.action_({
//				ping.server.audioBusAllocator.debug;
//			});
//
//		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
//			.states_([[ "Unit dump" ]])
//			.action_({
//				GeneratorUnit.subclasses.do({ arg unitClass;
//					unitClass.allUnits.do({ arg unit; unit.asString.inform; })
//				});
//			});

		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "Clear Post" ]])
			.action_({
				Document.listener.string = "";
			});
			
		ggMobileOnOff = GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "Mobile" ], [ "Mobile", Color.white, Color.blue ]])
			.action_({
				doc.player.isMobilePlaying.if({ doc.player.stopMobile }, { doc.player.startMobile });
			});

		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "Start Over" ]])
			.action_({
				doc.transport.stopAllAndWait;
				this.setSection( 0 );
				jit.handshake;
//				jit.setCoupling( InertiaJitterClient.kCoupSlave );
				jit.setCoupling( InertiaJitterClient.kCoupNone );
				tapePositions = Array.fill( 8, 0 );
			});

		flow.nextLine;
		ggMasterVol = GUI.ezSlider.new( win, 300 @ 30, "Main Vol.",  ControlSpec( -20, 0, units: " dB" ), {Êarg ez;
			this.setMasterVolume( ez.value.dbamp );
		}, this.masterVol.ampdb, false );
		ggMasterVol.sliderView.background = Color.black;
		ggMasterVol.sliderView.knobColor = Color.white;

		flow.nextLine;
		ggTapeVol = GUI.ezSlider.new( win, 300 @ 30, "Tape Vol.",  ControlSpec( -20, 6, units: " dB" ),  nil, 
			this.tapeMasterVol.ampdb, false );
		ggTapeVol.sliderView.background = Color.black;
		ggTapeVol.sliderView.knobColor = Color.white;
		
		UpdateListener.newFor( this, {Êarg upd, obj, what;
			case
			{ what === \masterVolume }
			{
				{ ggMasterVol.value = masterVol.ampdb.round( 0.01 )}.defer;
			}
			{ what === \tapeVolume }
			{
				{ ggTapeVol.value = tapeMasterVol.ampdb.round( 0.01 )}.defer;
			}
			{ what === \section }
			{
				taskTimer.stop;
				if( this.section == 1, {
					taskTimer.reset;
					taskTimer.start;
				});
			};
		});

		movieLabels = Array.fill( doc.layers.numLayers, {Êarg ch;
			flow.nextLine;
			GUI.staticText.new( win, Rect( 0, 0, 60, 22 ))
				.string_( "Layer " ++ (ch + 1).asString );
			GUI.button.new( win, Rect( 0, 0, 80, 22 ))
				.states_( Array.fill( doc.layers.numLayers, { arg ch;
					[ "Screen " ++ (ch+65).asAscii, Color.black, Color.hsv( ch / doc.layers.numLayers, 0.5, 1.0 )]
				}))
				.value_( ch );
		});

		layerListener = Event[ \layersSwitched -> { arg this, e;
			var movieX, movieY, lbMovieX, lbMovieY;

			lbMovieX	= movieLabels[ e.getFirstLayer ];
			lbMovieY	= movieLabels[ e.getSecondLayer ];
			movieX	= e.getManager.getMovieForLayer( e.getFirstLayer );
			movieY	= e.getManager.getMovieForLayer( e.getSecondLayer );
//			lbMovieX.setSelectedIndex( movieX );
//			lbMovieY.setSelectedIndex( movieY );
			{ lbMovieX.value = movieX;
			  lbMovieY.value = movieY; }.defer;
		}, \layersFiltered -> { arg this, e;
//			((JComboBox) filterCombos.get( e.getFirstLayer() )).setSelectedItem( e.getParam() );
		}];
		doc.layers.addListener( layerListener );

		UpdateListener.newFor( doc.player, { arg upd, obj, what ... args;
			if( what === \mobileOnOff, {
				{ ggMobileOnOff.value = doc.player.isMobilePlaying.if( 1, 0 )}.defer;
			});
		});
		
		flow.nextLine;
		flow.shift( 0, 8 );
		GUI.staticText.new( win, Rect( 0, 0, 60, 22 ))
			.string_( "Elapsed" );
		
		ggTime = GUI.staticText.new( win, Rect( 0, 0, 135, 30 ))
			.font_( GUI.font.new( "LucidaGrande", 24 ));

		taskTimer = Task({ var startTime = thisThread.seconds;
			inf.do({
				1.0.wait;
				ggTime.string = SMPTE( thisThread.seconds - startTime ).toString.copyFromStart( 7 );
			});
		}, AppClock );

//		win.onClose	= {
//		};
win.userCanClose = false;

		win.front;
	}
}
