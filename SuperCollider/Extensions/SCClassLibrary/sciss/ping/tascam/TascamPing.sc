/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies:	UpdateListener, Collapse, TypeSafe,
 *						AchillesUnit, AutoGateUnit, AutoHilbertUnit,
 *						DisperseUnit, FilterUnit, GendyUnit,
 *						GeneratorUnit, LoopUnit, MagAboveUnit,
 *						MagBelowUnit, MicUnit, OutputUnit, Ping,
 *						PingProc, RectCombUnit, SqrtUnit, TapeUnit,
 *						TrigMachine, UnitFactory, PingSetup,
 *						TascamTwoFour
 *
 *	Scharnier zwischen Ping und TascamTwoFour
 *
 *	@version	0.13, 19-Oct-06
 *	@author	Hanns Holger Rutz
 */
TascamPing {
	var <box;
	var <ping;
	
	var specFader;
	var inputPressed	= false;
	var outputPressed	= false;
	var shiftPressed	= false;
	var nullPressed	= false;
	var fkeyPressed	= false;
	var knobFunc;

	var knobFuncMaster, knobFuncChannel, knobFuncPan;
	
	var knobMode 		= \master;		// either of \chan, \master, \pan

//	var knobGaga;
//	var chanKnobClpse;
	
	var selectedChannel 	= nil;
	
	var soloChannel		= nil;
	var soloProcChain		= nil;
	var soloProcChainRep	= nil;
	var auxChannel		= nil;

//	var knobMasterValues;
//	var knobFuncValues;
//	var knobFuncValues;

	var cues;
//	var mics;
	
	var <>numNormChans		= 20;
	var <>useSync			= false;
	
	var <>chanSyncTime		= 20;
	var <>chanFadeTime		= 21;
	var <>chanAuxVol		= 22;
	var <>chanSoloVol		= 23;
	
	var specFadeTime;
	var specSoloVol;
	var specAuxVol;
	var soloVol			= 1.0;
	var auxVol			= 0.01;
	var fadeTime			= 2.0;
	
//	var masterKnobs;
	
	var selectFunc, selectFuncNormal, selectFuncInput, selectFuncOutput, selectFuncFKey;
	
//	var recordLid		= false;
	
	var updateRout;
	var updateList;
	var updateCond;
	
	var procChainReps;		// Dictionary : channel -> rep
	var procListener;
	
	var createChannel = nil;
	
	var oldLED	= -1;
	
	// --- sync
	
	var	syncTask;
	var	syncTime;
	var	syncType;

	var masterStereoBus;
	
	var <trigs;
	
	var ufs;

	*new { arg ping;
		^super.new.prInitTascamPing( ping );
	}
	
	*run {
		var p, tp;
	
		p		= Ping.new;
		tp 		= TascamPing( p );
		~ping	= p;
		PingSetup.load( p, tp );
		p.start({Êarg p; {
				tp.start;
				~rec = SimpleRecorder( p.server );
//				~rec.channelOffset	= p.server.options.numOutputBusChannels + 4;
				~rec.channelOffset	= 2;
				~rec.numChannels	= 4;
//				~rec.channelOffset	= p.server.options.numOutputBusChannels + 2;
//				~rec.numChannels	= 6;
				~rec.sampleFormat	= "int24";
				~rec.makeWindow;
				
				tp.makeHelperWindow;
			}.defer;
		});
	}
	
	makeHelperWindow {
		var win, flow;
		
		win = GUI.window.new( "Wolkenpumpe II", Rect( 200, 400, 400, 100 ));
		flow	= FlowLayout( win.view.bounds );
		win.view.decorator = flow;
		
		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "Net Test" ]])
			.action_({
				Ping.fritzelSendTest;
			});

		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "ABus Alloc" ]])
			.action_({
				ping.server.audioBusAllocator.debug;
			});

		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "Unit dump" ]])
			.action_({
				GeneratorUnit.subclasses.do({ arg unitClass;
					unitClass.allUnits.do({ arg unit; unit.asString.inform; })
				});
			});

		GUI.button.new( win, Rect( 0, 0, 80, 30 ))
			.states_([[ "Clear Post" ]])
			.action_({
				Document.listener.string="";
			});

//		win.onClose	= {
//		};

		win.front;
	}
	
	prInitTascamPing { arg argPing;
		ping			= argPing;
		procChainReps	= IdentityDictionary.new;
		ufs			= Array.newClear( 8 );
	}
	
	setUnitFactory { arg ch, uf;
		ufs[ ch ] = uf;
	}

	start {
		var micOff, micOff2;
	
		masterStereoBus = Bus( \audio, ping.masterIndex, 2, ping.server );

		cues = List.new;
		{
			PathName( Ping.dataFolder ++ "tapes" ).files.do({ arg f;
				cues.add( f.fullPath );
			});
		}.fork( AppClock );
~tapeCues = cues;
		
		// XXX
//		mics = List.new;
////		micOff = ping.server.options.numOutputBusChannels;
//		micOff = ping.server.options.numOutputBusChannels + 8; // + 10; // + 8;
//		micOff2 = ping.server.options.numOutputBusChannels;
//		mics.add( Bus( \audio, micOff, 2 ));			// air mic
//		mics.add( Bus( \audio, micOff + 2, 2 ));		// piezo
//		mics.add( Bus( \audio, micOff + 4, 2 ));		// ludger
//		mics.add( Bus( \audio, micOff + 6, 2 ));		// markowski
//		mics.add( Bus( \audio, micOff2, 2 ));			// johannes
	
		specFadeTime	= ControlSpec( 0.1, 20, \exp );
		specSoloVol	= ControlSpec( 0.1, 10, \exp );
		specAuxVol	= ControlSpec( 0.01, 10, \exp );
	
//		masterKnobs = Array.newClear( 24 );
//		masterKnobs[ chanSoloVol ] 	= specSoloVol.unmap( soloVol );
//		masterKnobs[ chanFadeTime ] = specFadeTime.unmap( fadeTime );

		if( useSync, {
			syncTask = Task({
				var blinking = false;
			
				syncTime.do({ arg i;
					1.0.wait;
					box.channelFader( chanSyncTime, 1 - ((i + 1) / syncTime) );
					if( blinking.not and: {Ê(syncTime - i) <= 7 }, { // 5x blinken entspricht ca. 7 sek.
						if( syncType === \term, {
							box.channelSolo( chanSyncTime, 1 );
						}, {
							box.channelSelect( chanSyncTime, 1 );
						});
					});
				});
				box.channelSelect( chanSyncTime, 0 );
				box.channelSolo( chanSyncTime, 0 );
				box.channelMute( chanSyncTime, 127 );
				1.wait;
				box.channelMute( chanSyncTime, 0 );
			});
		});
		
		ping.addDependant( this );
		box	= TascamTwoFour.new;
		box.addDependant( this );
		
		specFader = ControlSpec( -60.dbamp, 18.dbamp, \exp );
		
		box.masterFader( specFader.unmap( 1.0 ));
		
		knobFuncMaster = {Êarg ch, inc;
			this.prKnobFuncMaster( ch, inc );
		};

		knobFuncChannel = {Êarg ch, inc;
			this.prKnobFuncChannel( ch, inc );
		};

		knobFuncPan = {Êarg ch, inc;
			this.prKnobFuncPan( ch, inc );
		};
		
		selectFuncNormal = { arg ch, pressed;
			this.prChannelSelect( ch, pressed );
		};
		
		selectFuncInput = { arg ch, pressed;
			this.prChannelInput( ch, pressed );
		};
		
		selectFuncOutput = { arg ch, pressed;
			this.prChannelOutput( ch, pressed );
		};
		
		selectFuncFKey = { arg ch, pressed;
			this.prChannelFKey( ch, pressed );
		};
		
		knobFunc	= knobFuncMaster;
//		knobGaga	= Array.newClear( 24 );
//		chanKnobClpse = Array.newClear( 24 );
//		chanKnobClpse = Array.fill( 24, {
//			Collapse({ }, 0.2, SystemClock );
//		});

		selectFunc = selectFuncNormal;

		this.prMasterMode;

		if( useSync, {
			box.channelFader( chanSyncTime, 0 );
		});
		box.channelFader( chanSoloVol, specSoloVol.unmap( soloVol ));
		if( ping.useAux, {
			box.channelFader( chanAuxVol, specAuxVol.unmap( auxVol ));
		});
		box.channelFader( chanFadeTime, specFadeTime.unmap( fadeTime ));
		
		// --------- update listener ---------
		
		updateList	= List.new;
		updateCond	= Condition.new;
		updateRout	= Routine.run({ this.prRoutUpdate; });
		
		// --------- proc listener ---------
		
		procListener	= UpdateListener({ arg obj, what;
			var ch, procChain;
			
			case
			{ what === \paused }
			{
				ch = ping.getProcSlot( obj );
//("ch == "++ch).inform;
				if( ch.notNil, {
					procChain = ping.getProcChain( ch );
					if( procChain.notNil, {
						procChain.do({ arg proc;
							proc.stop( fadeTime );
						});
						box.channelMute( ch, 1 );
					});
				});
			}
			{ what === \disposed }
			{
//				("disposed : "++obj).inform;
				obj.removeDependant( procListener );
			};
		});

		// --------- server listener ---------
		
		UpdateListener.newFor( ping.server, {Êarg obj, what;
			var newLED;
			
			if( what === \counts, {
				newLED = obj.peakCPU.asInteger.div( 10 ).min( box.numAuxes );
				if( newLED != oldLED, {
					box.numAuxes.do({ arg i; box.aux( i, i < newLED ); });
					oldLED = newLED;
				});
			});
		});
		
//		box.numAuxes.do({ arg i; box.aux( i, false ); });

		// --------- trig machine ---------

		trigs		= Array.newClear( numNormChans );
		TrigMachine.load;
	}
	
	*guiLaunch {
		var win, flow, ggLaunch, ggCountDown, ggStopCount, rCount;
		
		win = GUI.window.new( "Wolkenpumpe II", Rect( 200, 400, 400, 100 ));
		flow	= FlowLayout( win.view.bounds );
		win.view.decorator = flow;
		
		ggLaunch = GUI.button.new( win, Rect( 0, 0, 80, 30 ));
		ggLaunch.states = [[ "Launch" ]];
		ggLaunch.action = {
			TascamPing.run;
			win.close;
		};
		
		ggCountDown = GUI.staticText.new( win, Rect( 0, 0, 40, 30 ));
		
		ggStopCount = GUI.button.new( win, Rect( 0, 0, 120, 30 ));
		ggStopCount.states = [[ "Stop Countdown" ]];
		ggStopCount.action = {
			rCount.stop;
			win.close;
		};

//		GUI.button.new( win, Rect( 0, 0, 120, 30 ))
//			.states_([[ "Net Test" ]])
//			.action = {
//				Ping.fritzelSendTest;
//			};
		
		win.onClose	= {
			rCount.stop;
		};
		
		rCount = Routine.run({
			10.do({ arg i;
				ggCountDown.string = (10 - i).asString;
				1.0.wait;
			});
			ggLaunch.doAction;
		}, clock: AppClock );
		
		win.front;
	}

	playSpeechCue { arg name;
		^ping.solo.playSpeechCue( name );
	}
	
	/**
	 *	This method simply adds
	 *	updates to the end of updateList
	 *	and signals updateCond so as
	 *	to wake prRoutUpdate.
	 */
	update {Êarg ... args;
		updateList.add( args );
		updateCond.test = true;
		updateCond.signal;
	}

	/**
	 *	This method is run inside a Routine.
	 *	An infinte loop checks for entries in updateList.
	 *	An entry is removed from the head and processed.
	 *	When updateList is empty, the method waits for
	 *	Condition updateCond, and re-scans updateList.
	 */
	prRoutUpdate {
		var status, obj, ch, pressed, args;
		
		inf.do({
			updateCond.wait;
			updateCond.test = false;
			while({ updateList.notEmpty }, {
				args		= updateList.removeAt( 0 );
				obj		= args[ 0 ];
				status	= args[ 1 ];
		
				try {
					case
					{ obj === box }
					{
						case
						{ status === \channelFader }
						{
							ch =  args[ 2 ];
							case
							{ ch < numNormChans }
							{
								this.prChannelFader( args[ 2 ], args[ 3 ]);
							}
							{ ch === chanSoloVol }
							{
								this.prSoloVolume( args[ 3 ]);
							}
							{ (ch === chanAuxVol) and: { ping.useAux }}
							{
								this.prAuxVolume( args[ 3 ]);
							}
							{ ch === chanFadeTime }
							{
								this.prFadeTime( args[ 3 ]);
							};
//							{
//								TypeSafe.methodError( thisMethod, "Illegal chan num "++ch );
//							};
						}
						{ status === \channelKnob }
						{
							knobFunc.value( args[ 2 ], args[ 3 ]);
						}
						{ status === \channelSelect }
						{
							ch 		= args[ 2 ];
							pressed	= args[ 3 ];
							case
							{ ch < numNormChans }
							{
								selectFunc.value( ch, pressed );
							}
							{ (ch === chanSyncTime) and: { useSync }}
							{
								if( pressed, { ping.fritzelSendSync; });
							};
						}
						{ status === \channelSolo }
						{
							ch 		=  args[ 2 ];
							pressed	= args[ 3 ];
							case
							{ ch < numNormChans }
							{
								this.prChannelSolo( ch, pressed );
							}
							{ (ch === chanSyncTime) and: { useSync }}
							{
								if( pressed, {Êping.fritzelSendTerminate; });
							};
						}
						{ status === \channelMute }
						{
							if( args[ 2 ] < numNormChans, {
								this.prChannelMute( args[ 2 ], args[ 3 ]);
							});
						}
						{ status === \chanMode }
						{
							this.prChanMode( args[ 2 ]);
						}
						{ status === \panMode }
						{
							this.prPanMode( args[ 2 ]);
						}
						{ status === \soloClear }
						{
							this.prSoloClear( args[ 2 ]);
						}
						{ status === \shiftPressed }
						{
							shiftPressed = args[ 2 ];
							box.shift( shiftPressed );
						}
						{ status === \fkeyPressed }
						{
							this.prFKeyMode( args[ 2 ]);
						}
						{ status === \nullMode }
						{
							nullPressed = args[ 2 ];
							box.nullMode( nullPressed );
						}
						{ status === \joystick }
						{
							if( nullPressed.not, { this.prJoystick( args[ 2 ], args[ 3 ]);});
						}
						{ status === \masterFader }
						{
							this.prMasterFader( args[ 2 ]);
						}
						{ status === \masterSelect }
						{
							pressed	= args[ 2 ];
							if( inputPressed, {
								selectFuncInput.value( -1, pressed );
							}, { if( outputPressed, {
								selectFuncOutput.value( -1, pressed );
							})});
						}
						{ status === \transportRwd }
						{
							this.prTransportRwd( args[ 2 ]);
						}
						{ status === \transportFFwd }
						{
							this.prTransportFFwd( args[ 2 ]);
						}
						{ status === \inputPressed }
						{
							this.prInputMode( args[ 2 ]);
						}
						{ status === \outputPressed }
						{
							this.prOutputMode( args[ 2 ]);
						}
						{ status === \transportStop }
						{
							this.prTransportStop( args[ 2 ]);
						}
						{ status === \transportPlay }
						{
							this.prTransportPlay( args[ 2 ]);
						}
						{ status === \transportRec }
						{
							this.prTransportRec( args[ 2 ]);
						}
						;
					}
					{ obj === ping }
					{
						this.prUpdatePing( args );
					}
					{
						TypeSafe.methodError( thisMethod, "Unknown model "++obj );
					};
				}
				{ arg error;
					error.reportError;
				};
			});
		});
	}

	prSoloVolume { arg knobVal;
		soloVol	= specSoloVol.map( knobVal );
//		box.channelKnobFill( ch, knobVal );
//		masterKnobs[ ch ] = knobVal;
		ping.solo.setVolume( soloVol );
	}

	prAuxVolume { arg knobVal;
		auxVol	= specAuxVol.map( knobVal );
		ping.aux.setVolume( auxVol );
	}

	prFadeTime {Êarg knobVal;
//		knobVal	= (specFadeTime.unmap( fadeTime ) + inc).clip( 0, 1 );
		fadeTime	= specFadeTime.map( knobVal );
//		box.channelKnobFill( ch, knobVal );
//		masterKnobs[ ch ] = knobVal;
	}

	prInputMode { arg pressed;
		// XXX check isRecorder
		
		inputPressed = pressed;
		
		if( pressed, {
			selectFunc = selectFuncInput;
		}, {
			selectFunc = selectFuncNormal;
		});
		box.inputMode( pressed );
	}

	prOutputMode { arg pressed;
		outputPressed = pressed;
		
		if( pressed, {
			selectFunc = selectFuncOutput;
		}, {
			selectFunc = selectFuncNormal;
		});
		box.outputMode( pressed );
	}

	prFKeyMode { arg pressed;		
		if( pressed, {
			selectFunc = selectFuncFKey;
		}, {
			selectFunc = selectFuncNormal;
		});
		fkeyPressed = pressed;
		box.fkey( pressed );
	}

	prChannelInput { arg ch, pressed;
		var procChain, proc, sourceProc, unit, sourceChain, bus;
		if( pressed, {
			if( selectedChannel.notNil, {
				procChain		= ping.getProcChain( selectedChannel );
				if( procChain.notNil, {
					proc	= procChain.first;
					unit	= proc.getUnit;
					if( unit.respondsTo( \isRecorder ), {
//"Yes.".inform;
						if( unit.isRecording.not, {
//("shiftPressed == "++shiftPressed).inform;
							if( ch >= 0, {	// ---------- FROM OTHER STRIP
								sourceChain = ping.getProcChain( ch );
								if( sourceChain.notNil, {
									sourceProc = sourceChain[ sourceChain.size - 2 ];
									bus = sourceProc.getOutputBus;
									if( bus.notNil, {
										unit.setInputBus( bus );
									}, {
										this.playSpeechCue( \NoRecordSource );
									});
								}, {
									this.playSpeechCue( \NoRecordSource );
								});
							}, {					// ---------- FROM MASTER SUM
								("setInputBus : "++masterStereoBus).inform;
								unit.setInputBus( masterStereoBus );
							});
						}, {
							this.playSpeechCue( \AlreadyRecording );
						});
					}, {
						this.playSpeechCue( \Failed );
					});
				}, {
					this.playSpeechCue( \NoRecordSource );
				});
			}, {
				this.playSpeechCue( \NoProcess );
			});
		});
	}

	prChannelOutput { arg ch, pressed;
		var proc, procChain, oldSel;
		
		if( pressed and: { ping.useAux }, {
			procChain = ping.getProcChain( ch );
			if( procChain.notNil, {
				proc 	= procChain[ procChain.size - 2 ];
				oldSel	= auxChannel;
				if( oldSel === ch, {
					this.prAuxClear;
				}, {
					this.prAuxClear;
					ping.aux.solo( proc );
					auxChannel = ch;
//					if( selectedChannel !== soloChannel, { this.prChannelSelect( ch, true );});
				});
			});
		});
	}

	prChannelFKey { arg ch, pressed;
		var unit, oUnit, proc, procChain, sourceChain, pred, bus, oProc;

		if( pressed, {
			if( createChannel.notNil and: {ÊselectedChannel === createChannel }, {
//				if( selectedChannel === soloChannel, {
//					this.prSoloClear( force: true );
//				});

				if( shiftPressed.not, {		// --------- create new or add process ---------
					procChain = ping.getProcChain( createChannel );
					if( procChain.isNil, {	// --------- create new process ---------
						if( ufs[ ch ].notNil, {
							unit = ufs[ ch ].makeUnit( ping, selectedChannel );
						});
						if( unit.isNil, {
							this.playSpeechCue( \NoProcess );
							^this;
						});

						if( unit.isKindOf( TrigTapeUnit ), {
							trigs[ createChannel ] = TrigMachine( ping, unit, createChannel );
						});
		
						proc = ping.createProcForUnit( unit );
						oProc = ping.createOutputForProc( proc );
						oProc.getUnit.setVolume( 0 );
						procChain = [ proc, oProc ];
						ping.addProcChain( procChain, createChannel );
						procChainReps.put( createChannel, TascamProcChainRep( box, procChain ));
						ping.server.sync;
						proc.addDependant( procListener );
						proc.play;
		
						box.channelFader( createChannel, 0 );
						box.channelMute( createChannel, 127 );
		
						createChannel = nil;
						box.channelSelect( selectedChannel, 127 );
						this.prEnterChanMode;  // rebuilds knobGaga
					
					}, {					// --------- add process ---------
						case
						{ ch === 8 }
						{
							unit 	= FilterUnit.new;
							unit.setNormFreq( 0 );
						}
						{ ch === 9 }
						{
							unit  = AutoHilbertUnit.new;
							unit.setAmount( 0.5 );
						}
						{ ch === 10 }
						{
							unit  = AutoGateUnit.new;
							unit.setAmount( 0.5 );
						}
						{ ch === 11 }
						{
							unit  = GendyUnit.new;
							unit.setAmount( 0.5 );
						}
						{ ch === 12 }
						{
							unit  = DisperseUnit.new;
							unit.setPitchDispersion( 0.01 );
							unit.setTimeDispersion( 0.5 );
						}
						{ ch === 13 }
						{
							unit  = AchillesUnit.new;
							unit.setSpeed( 0.5 );
						}
						{ ch === 14 }
						{
							unit  = SqrtUnit.new;
							unit.setAmount( 0.5 );
						}
						{ ch === 15 }
						{
							unit  = MagAboveUnit.new;
//							unit.setAmount( 0.5 );
						}
						{ ch === 16 }
						{
							unit  = MagBelowUnit.new;
//							unit.setAmount( 0.5 );
						}
						{ ch === 17 }
						{
							unit  = RectCombUnit.new;
							unit.setSpeed1( 10.0 );
							unit.setSpeed2( 0.1 );
						}
						{
							this.playSpeechCue( \NoProcess );
							^this;
						};
						
//						if( soloChannel !== createChannel, {
							if( soloChannel.notNil, {
								this.prSoloClear( force: true );
							});
//						});
						
						proc = ping.createProcForUnit( unit );
						oProc = procChain.last; // ping.createOutputForProc( proc );
						oProc.getUnit.setVolume( 0 );
						ping.removeProcChain( procChain );
						procChainReps.removeAt( createChannel ).dispose;
						pred = procChain.copyFromStart( procChain.size - 2 );
						proc.getGroup.moveAfter( pred.last.getGroup );
						bus	= pred.last.getOutputBus;
						proc.getUnit.setInputBus( bus );
						proc.setOutputBus( bus );
//						oProc.getUnit.setInputBus( proc.getOutputBus );
						procChain = pred ++ [ proc, oProc ];
~test = procChain;
						ping.addProcChain( procChain, createChannel );
						procChainReps.put( createChannel, TascamProcChainRep( box, procChain ));
						ping.server.sync;
//						proc.addDependant( procListener );
						proc.play;
		
						box.channelFader( createChannel, 0 );
						box.channelMute( createChannel, 127 );
		
						createChannel = nil;
						box.channelSelect( selectedChannel, 127 );
						this.prEnterChanMode;  // rebuilds knobGaga
					
					});

				}, {						// --------- duplicate ---------
				
					sourceChain	= if( ch === selectedChannel and: { soloProcChain.notNil }, {ÊsoloProcChain }, { ping.getProcChain( ch )});
					if( sourceChain.notNil, {
						procChain = Array.newClear( sourceChain.size - 1 );
						pred = nil;
						(sourceChain.size - 1).do({ arg i;
							proc = sourceChain[ i ].duplicate;
							if( pred.isNil, {
								proc.addDependant( procListener );
							}, {
								proc.getGroup.moveAfter( pred.getGroup );
								if( proc.getUnit.respondsTo( \setInputBus ), {
									proc.getUnit.setInputBus( pred.getOutputBus );
								});
							});
							procChain[ i ] = proc;
							pred = proc;
						});
						oProc = ping.createOutputForProc( proc );
						oProc.getUnit.setVolume( 0 );
						procChain = procChain.add( oProc ); // procChain ++ [ oProc ];
						ping.addProcChain( procChain, createChannel );
						procChainReps.put( createChannel, TascamProcChainRep( box, procChain ));
						ping.server.sync;
						(procChain.size - 1).do({ arg i;
							procChain[ i ].play;
						});
//~test = procChain;
						box.channelFader( createChannel, 0 );
						box.channelMute( createChannel, 127 );
	
//sourceChain.do({ arg p; var unit; unit = p.getUnit; ("source "++unit++" ; getOutputBus "++p.getOutputBus).inform; });
//procChain.do({ arg p; var unit; unit = p.getUnit; ("source "++unit++" ; getOutputBus "++p.getOutputBus).inform; });
	
						createChannel = nil;
						box.channelSelect( selectedChannel, 127 );
						this.prEnterChanMode;  // rebuilds knobGaga
					});
				});
			}, {
				this.playSpeechCue( \NotRunning );
			});
		});
	}

	prKnobFuncMaster { arg ch, inc;
//		var knobVal;
//		
//		inc = (if( inc >= 64, {Ê64 - inc }, inc )) / 127;
//		
//		case
//		{ ch === chanSoloVol }	// solo vol
//		{
//			knobVal	= (specSoloVol.unmap( soloVol ) + inc).clip( 0, 1 );
//			soloVol	= specSoloVol.map( knobVal );
//			box.channelKnobFill( ch, knobVal );
//			masterKnobs[ ch ] = knobVal;
//			ping.solo.setVolume( soloVol );
//		}
//		{ ch === chanFadeTime } // fade time
//		{
//			knobVal	= (specFadeTime.unmap( fadeTime ) + inc).clip( 0, 1 );
//			fadeTime	= specFadeTime.map( knobVal );
//			box.channelKnobFill( ch, knobVal );
//			masterKnobs[ ch ] = knobVal;
//		}
//		;
	}
	
	prTransportRec { arg pressed;
		var proc, procChain, unit, recFadeTime, slot;
	
		if( pressed and: { selectedChannel.notNil }, {
			procChain = ping.getProcChain( selectedChannel );
			if( procChain.notNil, {
				proc	= procChain.first;
				unit	= proc.getUnit;
				if( unit.respondsTo( \isRecorder ), {
					if( unit.isRecording, {
						if( shiftPressed, {
							if( unit.cancelRecording.not, {
								this.playSpeechCue( \Failed );
							});
						}, {
							if( unit.stopRecording.not, {
								this.playSpeechCue( \Failed );
							});
						});
					}, {
						recFadeTime = fadeTime;
						if( unit.startRecording( nil, { arg numFrames;
							box.transportRec( false );
							slot = ping.getProcSlot( proc );
							if( slot.notNil, {
								if( slot === selectedChannel, {
									box.transportRec( false );
								});
								proc.getUnit.useRecording;
								proc.crossFade( Dictionary.new, recFadeTime );
							}, {
								unit.trashRecording;
							});
						}), {
							box.transportRec( true );
						},Ê{
							box.transportRec( false );
							this.playSpeechCue( \NoRecordSource );
						});
					});
				});
			});
		});
	}

	prTransportRwd { arg pressed;
		if( pressed, { this.prTransportInc( 65 ); });
	}

	prTransportFFwd { arg pressed;
		if( pressed, { this.prTransportInc( 1 ); });
	}
	
	prTransportInc { arg inc;
		var procChainRep, procRep, attr, spec;
	
//TypeSafe.methodWarn( thisMethod, "N.Y.I." );
		if( selectedChannel.notNil and: { knobMode === \chan }, {
			procChainRep	= if( (selectedChannel === soloChannel) and: {ÊsoloProcChainRep.notNil }, {ÊsoloProcChainRep }, { procChainReps[ selectedChannel ];});
//			procChainRep	= if( selectedChannel === soloChannel, {ÊsoloProcChainRep }, { procChainReps[ selectedChannel ];});
			if( procChainRep.notNil, {
				procRep	= procChainRep.getProcAt( 0 );
				if( procRep.notNil, {
					 procRep.getNumAttr.do({ arg i;
						attr	= procRep.getAttrAt( i );
						if( attr.spec.step == 1, {
							this.prKnobFuncChannel( i + 1, inc );
						});
					});
				});
			});
		});
	}

	prTransportStop { arg pressed;
		if( pressed and: { selectedChannel.notNil and: { trigs[ selectedChannel ].notNil and: {Êtrigs[ selectedChannel ].disposed.not }}}, {
			trigs[ selectedChannel ].freeze;
		});
	}

	prTransportPlay { arg pressed;
		if( pressed and: { selectedChannel.notNil and: { trigs[ selectedChannel ].notNil and: {Êtrigs[ selectedChannel ].disposed.not }}}, {
			trigs[ selectedChannel ].unfreeze;
		});
	}
	
	prKnobFuncChannel { arg ch, inc;
		var str, procRep, soloProcRep, procChain, proc, procChainRep, attr, oldVal, newVal, knobVal, clpse, demChannel; //, map;
		var map2;

		demChannel = selectedChannel;

		if( demChannel.notNil and: { knobMode === \chan }, {
			procChainRep	= if( (demChannel === soloChannel) and: {ÊsoloProcChainRep.notNil }, {ÊsoloProcChainRep }, { procChainReps[ demChannel ];});
//("KIEKA; demChannel == "++demChannel++"; soloChannel == "++soloChannel++"; soloProcChainRep.notNil == "++(soloProcChainRep.notNil)).inform;
			if( procChainRep.notNil, {
				procRep	= procChainRep.getProcAt( ch );
				if( procRep.notNil, {
//("AGA").inform;
					attr	= procChainRep.getAttrAt( ch );
					if( fkeyPressed, {			// ----- info dorfer -----
						if( attr.isNil, {
							this.playSpeechCue( procRep.getUnit.getName );
//							(":::: "++ procRep.getUnit.getName).inform;
						}, {
							str = attr.name.asString;
							str = (str.first.toUpper ++ str.copyToEnd( 1 )).asSymbol;
							this.playSpeechCue( str );
//							(":::::::: "++ attr.name).inform;
						});
					
					}, {						// ----- schimpfo dorfer -----
						if( attr.notNil, {
//("LOLO").inform;
//							oldVal				  = procRep.getValue( attr );
							#knobVal, newVal, oldVal = procRep.calcNewValue( attr, inc );
							if( oldVal != newVal, {
								if( attr.shouldFade, {
//("POPO").inform;
//("AAA procRep = "++procRep.hash++"; proc = "++proc++" (hash "++proc.hash++"); map = "++map++"; fadeTime = "++fadeTime).inform;
									clpse = procRep.getCollapse( attr );
									if( clpse.isNil, {
										// ---------------------------------------------------
										clpse = Collapse({ arg newVal;
											var map;
											fork {
												map = Dictionary[ attr.name -> newVal ];
												if( soloChannel !== demChannel, {
												    proc = procRep.getProc;
	//("BBB procRep = "++procRep.hash++"; proc = "++proc++" (hash "++proc.hash++"); map = "++map++"; fadeTime = "++fadeTime).inform;
												    proc.crossFade( map, fadeTime );
												},Ê{ if( soloProcChain.notNil, {
												    proc = procRep.getProc;
												    proc.crossFade( map, 0.1 );  // no fuckn' fade
												}, {
												    procChain = ping.getProcChain( demChannel );
												    procChainRep = procChainReps[ demChannel ];
												    if( procChain.notNil, {
												        soloProcChain = Array.fill( procChain.size, { arg i;
												            proc = procChain[ i ].duplicate;
												            if( i == 0, {    
												                proc.addDependant( procListener );
												            }, {
												                if( proc.getUnit.respondsTo( \setInputBus ), {
												                    proc.getUnit.setInputBus( procChain[ i - 1 ].getOutputBus );
												                });
												            });
												            proc;
												        });
												        soloProcChainRep = TascamProcChainRep( box, soloProcChain );
	
	// WARNING: DON'T USE procRep AS VARIABLE HERE!!
													// DENN SONST KANN soloChannel !== demChannel
													// BRANCH DEN ALTEN procRep NIT FINDEN
												        soloProcRep = soloProcChainRep.getProcAt( ch );
												        proc    = soloProcRep.getProc;
												        proc.applyAttr( map );
												
												        soloProcChain.do({ arg proc;
												            proc.play;
												        });
													   
												        this.prEnterChanMode;  // rebuilds knobGaga
											             ping.solo.solo( soloProcChain[ soloProcChain.size - 2 ]);
												        box.channelSolo( demChannel, 1 );  // blinkendorfer
											         }, {
												        TypeSafe.methodWarn( thisMethod, "Lost proc chain" );
												    });
												})});
												
												this.prUpdateChanKnobs;
											};
										}, 0.2, SystemClock );
										procRep.setCollapse( attr, clpse );
										// ---------------------------------------------------
									});
									clpse.defer( newVal );
								}, {
									map2 = Dictionary[ attr.name -> newVal ];
									if( soloChannel !== demChannel, {
									    proc = procRep.getProc;
									    proc.applyAttr( map2 );
									},Ê{ if( soloProcChain.notNil, {
									    proc = procRep.getProc;
										proc.applyAttr( map2 );
									}, {
									    procChain = ping.getProcChain( demChannel );
									    procChainRep = procChainReps[ demChannel ];
									    if( procChain.notNil, {
									        soloProcChain = Array.fill( procChain.size, { arg i;
									            proc = procChain[ i ].duplicate;
									            if( i == 0, {    
									                proc.addDependant( procListener );
									            }, {
									                if( proc.getUnit.respondsTo( \setInputBus ), {
									                    proc.getUnit.setInputBus( procChain[ i - 1 ].getOutputBus );
									                });
									            });
									            proc;
									        });
									        soloProcChainRep = TascamProcChainRep( box, soloProcChain );

										// WARNING: DON'T USE procRep AS VARIABLE HERE!!
										// DENN SONST KANN soloChannel !== demChannel
										// BRANCH DEN ALTEN procRep NIT FINDEN
									        soloProcRep = soloProcChainRep.getProcAt( ch );
									        proc    = soloProcRep.getProc;
									        proc.applyAttr( map2 );
									
									        soloProcChain.do({ arg proc;
									            proc.play;
									        });
										   
									        this.prEnterChanMode;  // rebuilds knobGaga
								             ping.solo.solo( soloProcChain[ soloProcChain.size - 2 ]);
									        box.channelSolo( demChannel, 1 );  // blinkendorfer
								         }, {
									        TypeSafe.methodWarn( thisMethod, "Lost proc chain" );
									    });
									})});
								});
								case {Êattr.type === \normal }
								{
									box.channelKnob( ch, knobVal );
								}
								{ attr.type === \pan }
								{
									box.channelKnobPan( ch, knobVal );
								}
								{ attr.type === \fill }
								{
									box.channelKnobFill( ch, knobVal );
								};
							});
						});
					});
				});
			});
		});
	}

/*
	prKnobFuncChannelOLD { arg ch, inc;
		var ctrl, ctrl2, spec, proc, type, unit, oldVal, knobVal, newVal, soloProc, soloSlot, map, procChain;
	
		ctrl = knobGaga[ ch ];
		// [ proc, getter, setter, spec, type ]
		if( ctrl.notNil, {
		
			proc		= ctrl[ 0 ];
			unit		= proc.getUnit;
			spec		= ctrl[ 3 ];
			if( chanKnobClpse[ ch ].isNil, {
				oldVal = unit.perform( ctrl[ 1 ]);
			}, {
				oldVal = chanKnobClpse[ ch ].args.first;
			});
			if( spec.step == 0, {
				inc		= (if( inc >= 64, {Ê64 - inc }, inc )) / 127;
				knobVal	= (spec.unmap( oldVal ) + inc).clip( 0, 1 );
			}, {
				inc		= (if( inc >= 64, {Ê64 - inc }, inc )).sign * spec.step;
				knobVal	= (spec.unmap( oldVal + inc )).clip( 0, 1 );
			});
			newVal	= spec.map( knobVal );
			
			if( oldVal != newVal, {
				if( chanKnobClpse[ ch ].isNil, {
					// ---------------------------------------------------
					chanKnobClpse[ ch ] = Collapse({ arg newVal;
						fork {
							map		= Dictionary[ ctrl[ 2 ] -> newVal ];
							if( soloChannel !== selectedChannel, {
								proc.crossFade( map, fadeTime );
							},Ê{ if( soloProcChain.notNil, {
								proc.crossFade( map, 0.1 );  // no fuckn' fade
							}, {
								procChain = ping.getProcChain( selectedChannel );
								soloProcChain = Array.fill( procChain.size, { arg i;
									proc = procChain[ i ].duplicate;
									if( i > 0, {
										if( proc.getUnit.respondsTo( \setInputBus ), {
											proc.getUnit.setInputBus( procChain[ i - 1 ].getOutputBus );
										});
									});
									proc;
								});
								
								this.prEnterChanMode;  // rebuilds knobGaga
								ctrl		= knobGaga[ ch ];
								proc		= ctrl[ 0 ];
								proc.applyAttr( map );
								
								soloProcChain.do({ arg proc;
									proc.play;
								});
								
								ping.solo.solo( soloProcChain[ soloProcChain.size - 2 ]);
								box.channelSolo( selectedChannel, 1 );  // blinkendorfer

								"autonomous solo".inform;
							});});
							
							this.prUpdateChanKnobs;
						};
					}, 0.2, SystemClock );
					// ---------------------------------------------------
				});
				chanKnobClpse[ ch ].defer( newVal );

				type	= ctrl[ 4 ];
				case {Êtype === \normal }
				{
					box.channelKnob( ch, knobVal );
				}
				{ type === \pan }
				{
					box.channelKnobPan( ch, knobVal );
				}
				{ type === \fill }
				{
					box.channelKnobFill( ch, knobVal );
				};
			});
			
//		// add or modify process
//		}, {
//			
		});
	}
*/
	prKnobFuncPan { arg ch, inc;
		// XXX nothing here yet
	}
	
	prChanMode { arg pressed, force = false;
		if( force, {
			this.prEnterChanMode;
		}, {
			if( pressed, {
				if(ÊknobMode === \chan, {
					this.prMasterMode;
				}, {
					if( selectedChannel.notNil, {
						this.prEnterChanMode;
					});
				});
			});
		});
	}

	prSetKnobMode { arg mode;
		knobMode	= mode;
		case { mode === \chan }
		{
			knobFunc	= knobFuncChannel;
			box.panMode( false );
			box.chanMode( true );
		}
		{ mode === \master }
		{
			knobFunc	= knobFuncMaster;
			box.panMode( false );
			box.chanMode( false );
		}
		{ mode === \pan }
		{
			knobFunc	= knobFuncPan;
			box.panMode( true );
			box.chanMode( false );
		};
	}

	prEnterChanMode {
		var procChainRep, unit, idx, name, getter, val, spec, type;
	
		this.prSetKnobMode( \chan );
		
		if( soloChannel === selectedChannel, {
			procChainRep = soloProcChainRep ?? { procChainReps[ selectedChannel ];};
		}, {
			procChainRep = procChainReps[ selectedChannel ];
		});
		if( procChainRep.notNil, {
			procChainRep.display;
		});
	}

/*
	prEnterChanModeOLD {
		var procChain, unit, idx, name, getter, val, spec, type;
	
		this.prSetKnobMode( \chan );
		
		if( soloChannel === selectedChannel, {
			procChain = soloProcChain ?? { ping.getProcChain( selectedChannel );};
		}, {
			procChain = ping.getProcChain( selectedChannel );
		});
		if( procChain.notNil, {
			idx = 0;
			procChain.do({ arg proc;
				box.channelKnobDisco( idx, 1, true );
				if( chanKnobClpse[ idx ].notNil, {
					chanKnobClpse[ idx ].cancel;
					chanKnobClpse[ idx ] = nil;
				});
				
				knobGaga[ idx ] = nil;
				idx 			= idx + 1;
				unit			= proc.getUnit;
				unit.getAttributes.do({ arg attr;

					if( chanKnobClpse[ idx ].notNil, {
						chanKnobClpse[ idx ].cancel;
						chanKnobClpse[ idx ] = nil;
					});

					name		= attr[ 0 ].asString;
					name		= name.first.toUpper ++ name.copyToEnd( 1 );
					getter	= ("get" ++ name).asSymbol;
//					setter	= ("set" ++ name).asSymbol;
					spec		= attr[ 1 ];
					val		= spec.unmap( unit.perform( getter ));
					type		= attr[ 2 ];
					case {Êtype === \normal }
					{
						box.channelKnob( idx, val );
					}
					{ type === \pan }
					{
						box.channelKnobPan( idx, val );
					}
					{ type === \fill }
					{
						box.channelKnobFill( idx, val );
					};
					knobGaga[ idx ]		= [ proc, getter, name, spec, type ];
					idx = idx +1;
				});
				
			});
			
			if( procChain.first.getUnit.respondsTo( \isRecorder ), {
				box.transportRec( procChain.first.getUnit.isRecording );
				recordLid = true;
			}, {
				box.transportRec( false );
				recordLid = false;
			});
		}, {
			box.transportRec( false );
			recordLid = false;
		});
		while({ idx < 24 }, {
			box.channelKnobFill( idx, 0, true );
			knobGaga[ idx ] = nil;

			if( chanKnobClpse[ idx ].notNil, {
				chanKnobClpse[ idx ].cancel;
				chanKnobClpse[ idx ] = nil;
			});

			idx = idx + 1;
		});
	}
*/
	// when pan is pressed
	prPanMode { arg pressed, force = false;
//		if( recordLid, {
//			box.transportRec( false );
//			recordLid = false;
//		});
	
		if( force, {
			this.prSetKnobMode( \pan );
			TascamProcChainRep.displayBlank( box );
		}, {
			if( pressed, {
				if(ÊknobMode === \pan, {
					this.prMasterMode;
				}, {
					this.prSetKnobMode( \pan );
					TascamProcChainRep.displayBlank( box );
				});
			});
		});
	}
	
	prMasterMode {
//		if( recordLid, {
//			box.transportRec( false );
//			recordLid = false;
//		});

		this.prSetKnobMode( \master );
		TascamProcChainRep.displayBlank( box );

//		24.do({ arg ch;
//			if( masterKnobs[ ch ].isNil, {
//				box.channelKnobFill( ch, 0, true );
//			}, {
//				box.channelKnobFill( ch, masterKnobs[ ch ], false );
//			});
//		});
	}
	
	// fork
	prSoloClear { arg pressed, force = false;
		var procChain, proc, attrOld, attrNew, attrChanged;
		
		if( force ||Êpressed, {
//("HAKLLO "++soloChannel).inform;
			if( soloChannel.notNil, {
				ping.solo.unsolo;
				box.channelSolo( soloChannel, 0 );
				if( soloProcChain.notNil, {
					if( force ||ÊshiftPressed.not, {
//						"disposing temp solo".inform;
						ping.server.sync;
						soloProcChain.do({ arg proc, i;
//							if( i == 0, { proc.removeDependant( procListener );});
							proc.dispose;
						});
					}, {
						"appyling temp solo".inform;
						ping.server.sync;
						procChain = ping.getProcChain( soloChannel );
						soloProcChain.do({ arg soloProc, i;
							proc			= procChain[ i ];
							attrOld		= proc.getAttr;
							attrNew		= soloProc.getAttr;
							attrChanged	= IdentityDictionary.new;
							attrNew.keysValuesDo({ arg key, value;
								if( attrOld[ key ] != value, {
									attrChanged.put( key, value );
								});
							});
							if( attrChanged.notEmpty, {
								("  yes for dem "++proc).inform;
								proc.crossFade( attrChanged, fadeTime );
							});
//							if( i == 0, { proc.removeDependant( procListener );});
							soloProc.dispose;
						});
					});
					soloProcChain = nil;
					soloProcChainRep.dispose;
					soloProcChainRep = nil;
				});
				soloChannel = nil;
				this.prUpdateChanKnobs;
			});
		});
	}
	
	prAuxClear {
		if( ping.useAux, {
			ping.aux.unsolo;
//			box.channelFader( chanAuxVol, 0 );
			this.prAuxVolume( 0 );
			auxChannel	= nil;
		});
	}
	
	prUpdateChanKnobs {
		if( knobMode === \chan, {
			this.prEnterChanMode;	// updatendorfer
		});
	}
	
	prJoystick { arg h, v;
		var procChain, unit, oldAzi, azi, spread;
		
		if( selectedChannel.notNil, {
			procChain = ping.getProcChain( selectedChannel );
			if( procChain.notNil, {
				h		= h / 63.5 - 1;
				v		= v / 63.5 - 1;
				unit		= procChain.last.getUnit;	// OutputUnit
				azi		= atan2( h, v ) / pi;
				spread	= max( h.abs, v.abs ) / 2;	// ok ?
				unit.setSpread( spread );
				oldAzi	= unit.getAzimuth;
				while({ (oldAzi - azi) > 1 }, { azi = azi + 2; });
				while({ (oldAzi - azi) < -1 }, { azi = azi - 2; });
				unit.setAzimuth( azi );
//				[ azi, spread ].postln;
			});
		});
	}
	
	prChannelSelect { arg ch, pressed;
		var procChain;
		
		if( pressed, {
			if( selectedChannel === ch, {
				box.channelSelect( ch, 0 );
				selectedChannel = nil;
				if( knobMode === \chan, {Êthis.prMasterMode; });
			}, {
				procChain = ping.getProcChain( ch );
				if( procChain.notNil, {
					if( selectedChannel.notNil, { box.channelSelect( selectedChannel, 0 );});
					selectedChannel = ch;
					box.channelSelect( selectedChannel, 127 );
					this.prChanMode( force: true );
				});
			});
		});
	}

	// fork
	prChannelSolo { arg ch, pressed;
		var proc, procChain, current, oldSel, soloSlot;
		
		if( pressed, {
			procChain = ping.getProcChain( ch );
			if( procChain.notNil, {
				proc 	= procChain[ procChain.size - 2 ];
				oldSel	= soloChannel;
				if( oldSel === ch, {
					this.prSoloClear( pressed: true );
				}, {
					this.prSoloClear( force: true );
					box.channelSolo( ch, 127 );
					ping.solo.solo( proc );
					soloChannel = ch;
					if( selectedChannel !== soloChannel, { this.prChannelSelect( ch, true );});
				});
			});
		});
	}
	
	// fork
	prChannelMute { arg ch, pressed;
		var proc, oProc, unit, solo, procChain, current;
		
		if( pressed, {
			procChain = ping.getProcChain( ch );

			// pause, resume or dispose process
			if( procChain.notNil and: { fkeyPressed.not }, {
				if( shiftPressed, {
					if( ch === selectedChannel, {
						box.channelSelect( ch, 0 );
						selectedChannel = nil;
						if( knobMode === \chan, {Êthis.prMasterMode; });
					});
					if( ch === soloChannel, {
						this.prSoloClear( force: true );
					});
					if( ch === auxChannel, {
						this.prAuxClear;
					});
					ping.removeProcChain( procChain );
					procChainReps.removeAt( ch ).dispose;
					procChain.do({ arg proc;
						proc.dispose;
					});
					box.channelMute( ch, 0 );
				}, {
					if( procChain.first.isRunning, {
						procChain.do({ arg proc;
							proc.stop( fadeTime );
						});
						box.channelMute( ch, 1 );
					}, {
						procChain.do({ arg proc;
							proc.play( fadeTime );
						});
						box.channelMute( ch, 127 );
					});
				});
				
			// instantiate new process
			}, {
				createChannel = ch;
				if( procChain.isNil, {
					box.channelFader( ch, 0 );
				});
				if( selectedChannel.notNil, {
					box.channelSelect( selectedChannel, 0 );
					selectedChannel = nil;
				});
				selectedChannel = ch;
				box.channelSelect( ch, 1 );
				this.prChanMode( force: true );
				
////				proc = ping.createProcForUnit( unit );
//				proc = PingProc( ping.server );
//				oProc = ping.createOutputForProc( proc );
//				oProc.getUnit.setVolume( 0 );
//				procChain = [ proc, oProc ];
//				ping.addProcChain( procChain, ch );
//				procChainReps.put( ch, TascamProcChainRep( box, procChain ));
//				ping.server.sync;
////				proc.play;
//				box.channelMute( ch, 127 );
//				box.channelFader( ch, 0 );
//				if( selectedChannel.notNil, {
//					box.channelSelect( selectedChannel, 0 );
//					selectedChannel = nil;
//				});
//				selectedChannel = ch;
//				box.channelSelect( selectedChannel, 127 );
//				this.prChanMode( force: true );
							
//				// XXX
//				if( ch < 8, {
//					unit = TapeUnit.new;
//					cues.do({ arg cue;
//						unit.addCue( cue, nil );
//					});
//					unit.setCueIndex( 0 );
//				}, { if( ch < 16, {
//					unit = MicUnit.new;
//					mics.do({ arg mic;
//						unit.addMic( mic );
//					});
//					unit.setMicIndex( 0 );
//				}, {
//					unit = LoopUnit.new;
//					unit.setNumFrames( (ping.server.sampleRate * 30).asInteger );
//					unit.setNowNumFrames( unit.getNumFrames );
//					unit.setNumChannels( 2 );
//				})});
//				proc = ping.createProcForUnit( unit );
//				oProc = ping.createOutputForProc( proc );
//				oProc.getUnit.setVolume( 0 );
//				ping.addProcChain([ proc, oProc ], ch );
//				ping.server.sync;
//				proc.play;
//				box.channelMute( ch, 127 );
//				box.channelFader( ch, 0 );
			});
		});
	}
	
	prChannelFader { arg ch, val;
		var procChain;
		
		procChain = ping.getProcChain( ch );
		if( procChain.notNil, {
			procChain.last.getUnit.setVolume( if( val === 0,  0, {ÊspecFader.map( val ); }));
		});
	}
	
	prMasterFader { arg val;
		ping.master.setVolume( if( val === 0,  0, {ÊspecFader.map( val ); }));
	}
	
	prUpdatePing { arg args;
		var what;
		
		what = args[ 1 ];
	
		case
		{ what === \fritzelSync }
		{
			if( useSync, {
				box.channelSelect( chanSyncTime, 127 );
				box.channelSolo( chanSyncTime, 0 );
				box.channelFader( chanSyncTime, 1 );
				syncTask.stop;
				syncTask.reset;
				syncType = \sync;
	//			syncTime = SystemClock.seconds + args[ 2 ];
				syncTime = args[ 2 ].asInt.max( 20 ).min( 300 );
				syncTask.start;
			});
		}
		{ what === \fritzelTerminate }
		{
			if( useSync, {
				box.channelSelect( chanSyncTime, 0 );
				box.channelSolo( chanSyncTime, 127 );
				box.channelFader( chanSyncTime, 1 );
				syncTask.stop;
				syncTask.reset;
				syncType = \term;
				syncTime = args[ 2 ].asInt.max( 20 ).min( 300 );
				syncTask.start;
			});
		};		
	}
}

/**
 *	Interface represenation of a process chain
 */
TascamProcChainRep : Object {
	var box;
	var procReps;
	var procRepIdxOffs;
	var numProcs;
	var <isRecorder;	// (Boolean) re first proc (generator)

	*new {Êarg box, procChain;
		^super.new.prInitProcChainRep( box, procChain );
	}
	
	prInitProcChainRep {Êarg argBox, procChain;
		var idx;
	
		TypeSafe.checkArgClasses( thisMethod, [ argBox, procChain ], [ TascamTwoFour, SequenceableCollection ], [ false, false ]);
	
		box				= argBox;
		numProcs			= procChain.size;
		procReps			= Array.newClear( numProcs );
		procRepIdxOffs	= Array.newClear( numProcs );

		idx = 0;
		procChain.do({ arg proc, i;
			procRepIdxOffs[ i ]	= idx;
			procReps[ i ]			= TascamProcRep( proc );
			idx 					= idx + procReps[ i ].getNumAttr + 1;
		});
		
		if( procChain.notEmpty, {
			isRecorder = procReps.first.isRecorder;
		}, {
			isRecorder = false;
		});
	}
	
	dispose {
		procReps.do({ arg p; p.dispose; });
	}
	
	// display current values in the knobs plane
	display {
		var idx, val, attr;
	
		idx = 0;
		block {Êarg break;
			procReps.do({ arg procRep, i;
				if( idx >= box.numChannels, break );
	
				box.channelKnobDisco( idx, 1, true );
				idx = idx + 1;
				procRep.getNumAttr.do({ arg i;
					if( idx >= box.numChannels, break );
					
					attr		= procRep.getAttrAt( i );
					val		= attr.getNormalizedValue( procRep.getUnit );
					
					case
					{Êattr.type === \normal }
					{
						box.channelKnob( idx, val );
					}
					{ attr.type === \pan }
					{
						box.channelKnobPan( idx, val );
					}
					{ attr.type === \fill }
					{
						box.channelKnobFill( idx, val );
					}
					{
						TypeSafe.methodError( thisMethod, "Illegal display type " ++ attr.type );
					};
					idx = idx +1;
				});
			});
			
			while({ idx < box.numChannels }, {
				box.channelKnobFill( idx, 0, true );
				idx = idx + 1;
			});
		};

		box.transportRec( isRecorder.if({ÊprocReps.first.getUnit.isRecording }, false ));
	}
	
	*displayBlank { arg box;
		var idx = 0;

		while({ idx < box.numChannels }, {
			box.channelKnobFill( idx, 0, true );
			idx = idx + 1;
		});
	}
	
	/**
	 *	@return	(TascamProcRep)
	 */
	getProcAt {Êarg knobIdx;
		procRepIdxOffs.do({ arg off, i;
			if( knobIdx < off, {
				if( i > 0, {
					^procReps[ i - 1 ];
				}, {
					^nil;
				});
			});
		});
		^nil;
	}
	
	/**
	 *	@return	(UnitAttr)
	 */
	getAttrAt { arg knobIdx;
		var procRep, lastOff, attrIdx;
	
		procRepIdxOffs.do({ arg off, i;
			if( (knobIdx < off) and: { lastOff.notNil}, {
				procRep	= procReps[ i - 1 ];
				attrIdx	= knobIdx - lastOff - 1;
				^procRep.getAttrAt( attrIdx );
			});
			lastOff = off;
		});
		^nil;
	}
	
	getNumProcs {
		^numProcs;
	}
}

/**
 *	Interface represenation of a process
 */
TascamProcRep {
	var	<proc;
	var unitAttrs;
	var unitAttrNum;
	var collapses;
	var <isRecorder;

	*new {Êarg proc;
		^super.new.prInitProcRep( proc );
	}
	
	prInitProcRep {Êarg argProc;
		var unit, attrs;
	
		TypeSafe.checkArgClasses( thisMethod, [ argProc ], [ PingProc ], [ false ]);
	
		proc = argProc;

		unit			= proc.getUnit;
		unitAttrs		= unit.getAttributes;
		unitAttrNum	= unitAttrs.size;
		isRecorder	= proc.getUnit.respondsTo( \isRecorder );
		collapses		= Array.newClear( unitAttrNum );
	}
	
	getProc {
		^proc;
	}
	
	getUnit {
		^proc.getUnit;
	}
	
	getNumAttr {
		^unitAttrNum;
	}
	
	getAttrAt {Êarg attrIdx;
		^unitAttrs[ attrIdx ];
	}
	
	getCollapse { arg attr;
		var idx, result;
		
		idx		= unitAttrs.indexOf( attr );
		if( idx.notNil, {
			^collapses[ idx ];
		}, {
			^nil;
		});
	}

	setCollapse { arg attr, clpse;
		var idx, result;

		TypeSafe.checkArgClasses( thisMethod, [ attr, clpse ], [ UnitAttr, Collapse ], [ false, false ]);
		
		idx		= unitAttrs.indexOf( attr );
		if( idx.notNil, {
			if( collapses[ idx ].isNil, {
				collapses[ idx ] = clpse;
			}, {
				TypeSafe.methodError( thisMethod, "Collapse already exists for "++attr );
			});
		}, {
			TypeSafe.methodError( thisMethod, "Illegal attribute "++attr );
		});
	}

	getValue {Êarg attr;
		var idx;
		
		idx = unitAttrs.indexOf( attr );
		if( idx.notNil, {
			if( collapses[ idx ].isNil, {
				^this.getUnit.perform( attr.getter );
			}, {
				^collapses[ idx ].args.first;
			});
		}, {
			TypeSafe.methodError( thisMethod, "Illegal attribute "++attr );
		});
	}
	
	calcNewValue { arg attr, inc;
		var idx, oldVal, knobVal, newVal;
		
		idx = unitAttrs.indexOf( attr );
		if( idx.notNil, {
			if( collapses[ idx ].isNil, {
				oldVal = this.getUnit.perform( attr.getter );
			}, {
				oldVal = collapses[ idx ].args.first;
			});
			if( attr.spec.step == 0, {
				inc		= (if( inc >= 64, {Ê64 - inc }, inc )) / 127;
				knobVal	= (attr.spec.unmap( oldVal ) + inc).clip( 0, 1 );
			}, {
				inc		= (if( inc >= 64, {Ê64 - inc }, inc )).sign * attr.spec.step;
				knobVal	= (attr.spec.unmap( oldVal + inc )).clip( 0, 1 );
			});
			newVal = attr.spec.map( knobVal );
			^[ knobVal, newVal, oldVal ];
		}, {
			TypeSafe.methodError( thisMethod, "Illegal attribute "++attr );
		});
	}
		
	dispose {
		collapses.do({ arg c; if( c.notNil, { c.cancel; });});
		collapses	 = Array.newClear( unitAttrNum );
	}
}

/*
TascamUnitAttrRep {
	var <unit;
	var <name;
	var <getter;
	var <spec;
	var <type;

	*new {Êarg unit, attr;
		^super.new.prInitUnitAttrRep( unit, attr );
	}
	
	prInitUnitAttrRep { arg argUnit, attr;
		name		= attr[ 0 ].asString;
		name		= name.first.toUpper ++ name.copyToEnd( 1 );
		getter	= ("get" ++ name).asSymbol;
//		setter	= ("set" ++ name).asSymbol;
		spec		= attr[ 1 ];
		val		= spec.unmap( unit.perform( getter ));
		type		= attr[ 2 ];
		case {Êtype === \normal }
		{
			box.channelKnob( idx, val );
		}
		{ type === \pan }
		{
			box.channelKnobPan( idx, val );
		}
		{ type === \fill }
		{
			box.channelKnobFill( idx, val );
		};
		knobGaga[ idx ]		= [ proc, getter, name, spec, type ];
		idx = idx +1;
	}
}
*/