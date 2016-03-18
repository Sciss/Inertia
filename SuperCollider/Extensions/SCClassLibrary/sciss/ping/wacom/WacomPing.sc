/**
 *	(C)opyright 2006-2007 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies:	UpdateListener, Collapse, TypeSafe,
 *						AchillesUnit, AutoGateUnit, AutoHilbertUnit,
 *						DisperseUnit, FilterUnit, GendyUnit,
 *						GeneratorUnit, LoopUnit, MagAboveUnit,
 *						MagBelowUnit, MicUnit, OutputUnit, Ping,
 *						PingProc, RectCombUnit, SqrtUnit, TapeUnit,
 *						TrigMachine, UnitFactory, PingSetup,
 *						WacomTwoFour
 *
 *	Scharnier zwischen Ping und WacomTwoFour
 *
 *	@version	0.14, 19-Jan-07
 *	@author	Hanns Holger Rutz
 */
WacomPing {
	var <box;
	var <ping;
	
	var specFader;
	var inputPressed	= false;
	var outputPressed	= false;
	var shiftPressed	= false;
//	var nullPressed	= false;
	var fkeyPressed	= false;
	var knobFunc;

	var knobFuncMaster, knobFuncChannel, knobFuncPan;
	
	var knobMode 		= \master;		// either of \chan, \master, \pan

//	var knobGaga;
//	var chanKnobClpse;
	
	var <selectedChannel 	= nil;
	
	var soloChannel		= nil;
	var soloProcChain		= nil;
	var soloProcChainRep	= nil;
	var auxChannel		= nil;

//	var knobMasterValues;
//	var knobFuncValues;
//	var knobFuncValues;

	var cues;
//	var mics;
	
	var <>numNormChans		= 8;
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
		^super.new.prInitWacomPing( ping );
	}
	
	*run {
		var p, wp;
	
		p		= Ping.new;
		wp 		= WacomPing( p );
~wp = wp;
		~ping	= p;
		PingSetup.load( p, wp );
		p.start({Êarg p; {
				wp.start;
				~rec = SimpleRecorder( p.server );
				~rec.channelOffset	= p.server.options.numOutputBusChannels; // + 4;
//				~rec.channelOffset	= 2;
				~rec.numChannels	= 6; // 4;
//				~rec.channelOffset	= p.server.options.numOutputBusChannels + 2;
//				~rec.numChannels	= 6;
				~rec.sampleFormat	= "int24";
				~rec.makeWindow;
				
				wp.makeHelperWindow;
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
	
	prInitWacomPing { arg argPing;
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
//("CUES = "++~tapeCues).postln;
		
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
							box.channelSolo( chanSyncTime, \blink );
						}, {
							box.channelSelect( chanSyncTime, \blink );
						});
					});
				});
				box.channelSelect( chanSyncTime, \off );
				box.channelSolo( chanSyncTime, \off );
				box.channelPlay( chanSyncTime, \on );
				1.wait;
				box.channelPlay( chanSyncTime, \off );
			});
		});
		
		ping.addDependant( this );
		box	= WacomTwoFour.new;
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
// YYY
//		box.channelFader( chanSoloVol, specSoloVol.unmap( soloVol ));
box.soloFader( specSoloVol.unmap( soloVol ));
		if( ping.useAux, {
			box.channelFader( chanAuxVol, specAuxVol.unmap( auxVol ));
		});
// YYY
//		box.channelFader( chanFadeTime, specFadeTime.unmap( fadeTime ));
		
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
// YYY ???
							proc.stop( fadeTime );
						});
						box.channelPlay( ch, \blink );
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
		
//		UpdateListener.newFor( ping.server, {Êarg obj, what;
//			var newLED;
//			
//			if( what === \counts, {
//				newLED = obj.peakCPU.asInteger.div( 10 ).min( box.numAuxes );
//				if( newLED != oldLED, {
//					box.numAuxes.do({ arg i; box.aux( i, i < newLED ); });
//					oldLED = newLED;
//				});
//			});
//		});
		
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
			WacomPing.run;
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
//							{ ch === chanSoloVol }
//							{
//								this.prSoloVolume( args[ 3 ]);
//							}
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
							this.prKnobFuncChannel( args[ 2 ], args[ 3 ], args[ 4 ], args[ 5 ], this.prMapFadeTime( args[ 6 ]));
// YYY
//							knobFunc.value( args[ 2 ], args[ 3 ]);
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
						{ status === \channelPlay }
						{
							if( args[ 2 ] < numNormChans, {
								this.prChannelPlay( args[ 2 ], args[ 3 ], this.prMapFadeTime( args[ 4 ]));
							});
						}
						{ status === \kill }
						{
							if( selectedChannel.notNil, {Êthis.prChannelDelete( selectedChannel )});
						}
						{ status === \channelMove }
						{
							this.prChannelMove( args[ 2 ], args[ 3 ]);
						}
						{ status === \masterMove }
						{
							this.prChannelMove( -1, args[ 2 ]);
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
							this.prSoloClear( args[ 2 ], false ); // , this.prMapFadeTime( args[ 3 ])
						}
						{ status === \soloApply }
						{
							if( args[ 2 ], {
								this.prSoloApply( this.prMapFadeTime( args[ 3 ]));
							});
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
//						{ status === \nullMode }
//						{
//							nullPressed = args[ 2 ];
//							box.nullMode( nullPressed );
//						}
//						{ status === \joystick }
//						{
//							this.prJoystick( args[ 2 ], args[ 3 ]);
//						}
						{ status === \panorama }
						{
							this.prSetPanorama( args[ 2 ], args[ 3 ]);
						}
						{ status === \masterFader }
						{
							this.prMasterFader( args[ 2 ]);
						}
						{ status === \masterSelect }
						{
//							pressed	= args[ 2 ];
//							if( inputPressed, {
//								selectFuncInput.value( -1, pressed );
//							}, { if( outputPressed, {
//								selectFuncOutput.value( -1, pressed );
//							})});
						}
						{ status === \soloFader }
						{
							this.prSoloVolume( args[ 2 ]);
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
							this.prTransportRec( args[ 2 ], this.prMapFadeTime( args[ 3 ]));
						}
						{ status === \channelCreate }
						{
							this.prCreateNewProcess( args[ 2 ], args[ 3 ], args[ 2 ]);
						}
						{ status === \channelFilter }
						{
							this.prCreateNewFilter( args[ 2 ], args[ 3 ]);
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

	prMapFadeTime { arg normTime;
		^if( normTime.notNil, { specFadeTime.map( normTime )}, nil );
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
					this.prSetProcInput( ch, selectedChannel );
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
						this.prCreateNewProcess( createChannel, ufs[ ch ], selectedChannel );
					
					}, {					// --------- add process ---------
						this.prCreateNewFilter( createChannel, WacomTwoFour.fltNames[ ch - 8 ]);
					});

				}, {						// --------- duplicate ---------
					this.prChannelMove( ch, selectedChannel );
				});
			}, {
				this.playSpeechCue( \NotRunning );
			});
		});
	}

	// fltNames	= [ \filt, \hilb, \gate, \gendy, \disp, \achil, \sqrt, \magAbove, \magBelow, \comb ];

	prCreateNewFilter { arg createChannel, name;
		var unit, proc, procChain, oProc, pred, bus;
	
		procChain = ping.getProcChain( createChannel );
		if( procChain.notNil, {
			case
			{ name === \filt }
			{
				unit 	= FilterUnit.new;
				unit.setNormFreq( 0 );
			}
			{ name === \hilb }
			{
				unit  = AutoHilbertUnit.new;
				unit.setAmount( 0.5 );
			}
			{ name === \gate }
			{
				unit  = AutoGateUnit.new;
				unit.setAmount( 0.5 );
			}
			{ name === \gendy }
			{
				unit  = GendyUnit.new;
				unit.setAmount( 0.5 );
			}
			{ name === \disp }
			{
				unit  = DisperseUnit.new;
				unit.setPitchDispersion( 0.01 );
				unit.setTimeDispersion( 0.5 );
			}
			{ name === \achil }
			{
				unit  = AchillesUnit.new;
				unit.setSpeed( 0.5 );
			}
			{ name === \sqrt }
			{
				unit  = SqrtUnit.new;
				unit.setAmount( 0.5 );
			}
			{ name === \magAbove }
			{
				unit  = MagAboveUnit.new;
//				unit.setAmount( 0.5 );
			}
			{ name === \magBelow }
			{
				unit  = MagBelowUnit.new;
//				unit.setAmount( 0.5 );
			}
			{ name === \comb }
			{
				unit  = RectCombUnit.new;
				unit.setSpeed1( 10.0 );
				unit.setSpeed2( 0.1 );
			}
			{
				this.playSpeechCue( \NoProcess );
				^this;
			};
			
//			if( soloChannel !== createChannel, {
				if( soloChannel.notNil, {
					this.prSoloClear( force: true );
				});
//			});
			
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
//			oProc.getUnit.setInputBus( proc.getOutputBus );
			procChain = pred ++ [ proc, oProc ];
~test = procChain;
			ping.addProcChain( procChain, createChannel );
			procChainReps.put( createChannel, WacomProcChainRep( box, procChain, createChannel ));
			ping.server.sync;
//			proc.addDependant( procListener );
			proc.play;

			box.channelFader( createChannel, 0 );
			box.channelPlay( createChannel, \on );

			createChannel = nil;
			box.channelSelect( selectedChannel, \on );
//			this.prEnterChanMode;  // rebuilds knobGaga
			this.prEnterChanMode2( selectedChannel );
		}, {
			this.playSpeechCue( \NotRunning );
		});
	}
		

	prChannelMove { arg sourceChannel, targetChannel;
		var sourceChain, procChain, pred, proc, oProc;

		if( sourceChannel != targetChannel, {
//			sourceChain	= if( sourceChannel == targetChannel and: { soloProcChain.notNil }, {ÊsoloProcChain }, { ping.getProcChain( sourceChannel )});
			sourceChain = ping.getProcChain( sourceChannel );
			if( sourceChain.notNil, {
				if( ping.getProcChain( targetChannel ).isNil, {
				
					// ---------------- duplicate ----------------
				
	//				procChain = Array.newClear( sourceChain.size - 1 );
					procChain = Array.newClear( sourceChain.size );
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
	//				procChain = procChain.add( oProc ); // procChain ++ [ oProc ];
					procChain[ procChain.size - 1 ] = oProc;
					ping.addProcChain( procChain, targetChannel );
					procChainReps.put( targetChannel, WacomProcChainRep( box, procChain, targetChannel ));
					ping.server.sync;
					(procChain.size - 1).do({ arg i;
						procChain[ i ].play;
					});
	//~test = procChain;
					box.channelFader( targetChannel, 0 );
					box.channelPlay( targetChannel, \on );
					box.channelName( targetChannel, procChain.first.getUnit.getName.asString );
	
	//sourceChain.do({ arg p; var unit; unit = p.getUnit; ("source "++unit++" ; getOutputBus "++p.getOutputBus).inform; });
	//procChain.do({ arg p; var unit; unit = p.getUnit; ("source "++unit++" ; getOutputBus "++p.getOutputBus).inform; });
	
					createChannel = nil;
	//					box.channelSelect( targetChannel, \on );
					box.channelSelectMutex( targetChannel );
					selectedChannel = targetChannel;
//					this.prEnterChanMode;  // rebuilds knobGaga
					this.prEnterChanMode2( selectedChannel );
				}, {
					// ---------------- set input ----------------
					this.prSetProcInput( sourceChannel, targetChannel );
				});
				        // -1 indicates masterChannel!
			}, { if( (sourceChannel == -1) and: {Êping.getProcChain( targetChannel ).notNil }, {
				this.prSetProcInput( sourceChannel, targetChannel );
			}, {
				this.playSpeechCue( \Failed );
			})});
		}, {
			this.playSpeechCue( \Failed );
		});
	}

	prSetProcInput { arg sourceChannel, recorderChannel;
		var proc, procChain, unit, sourceChain, sourceProc, bus, ok = false;
		
		procChain = ping.getProcChain( recorderChannel );
		if( procChain.notNil, {
			proc	= procChain.first;
			unit	= proc.getUnit;
			if( unit.respondsTo( \isRecorder ), {
				if( unit.isRecording.not, {
					if( sourceChannel >= 0, {	// ---------- FROM OTHER STRIP
						sourceChain = ping.getProcChain( sourceChannel );
						if( sourceChain.notNil, {
							sourceProc = sourceChain[ sourceChain.size - 2 ];
							bus = sourceProc.getOutputBus;
							if( bus.notNil, {
								unit.setInputBus( bus );
								ok = true;
							}, {
								this.playSpeechCue( \NoRecordSource );
							});
						}, {
							this.playSpeechCue( \NoRecordSource );
						});
					}, {					// ---------- FROM MASTER SUM
						("setInputBus : "++masterStereoBus).inform;
						unit.setInputBus( masterStereoBus );
						ok = true;
					});
					if( ok, {
						selectedChannel = recorderChannel;
						box.channelSelectMutex( selectedChannel );
//						this.prEnterChanMode;  // rebuilds knobGaga
// YYY
//						this.prEnterChanMode2( selectedChannel );
					});
				}, {
					this.playSpeechCue( \AlreadyRecording );
				});
			}, {
				this.playSpeechCue( \Failed );
			});
		}, {
			this.playSpeechCue( \Failed );
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
	
	prTransportRec { arg pressed, fdt;
		var proc, procChain, unit, recFadeTime, slot;
	
//("DONG "++pressed).postln;
		if( pressed and: { selectedChannel.notNil }, {
//("FLONG "++pressed).postln;
			procChain = ping.getProcChain( selectedChannel );
			if( procChain.notNil, {
				proc	= procChain.first;
				unit	= proc.getUnit;
				if( unit.respondsTo( \isRecorder ), {
//("B:ONMG "++pressed).postln;
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
						recFadeTime = fdt ?? fadeTime;
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
	
	prKnobFuncChannel { arg ch, knobIdx, value, pressed, fdt;
		var str, procRep, soloProcRep, procChain, proc, procChainRep, attr, oldVal, newVal, knobVal, clpse, map;
		var map2;

		procChainRep	= if( (ch === soloChannel) and: {ÊsoloProcChainRep.notNil }, {ÊsoloProcChainRep }, { procChainReps[ ch ];});
//("KIEKA; ch == "++ch++"; soloChannel == "++soloChannel++"; soloProcChainRep.notNil == "++(soloProcChainRep.notNil)).inform;
		if( procChainRep.notNil, {
			procRep	= procChainRep.getProcAt( knobIdx );
			if( procRep.notNil, {
//("AGA").inform;
				attr	= procChainRep.getAttrAt( knobIdx );
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
						#knobVal, newVal, oldVal = procRep.calcNewValue2( attr, value );
						if( oldVal != newVal, {
							if( attr.shouldFade, {
//("POPO").inform;
//("AAA procRep = "++procRep.hash++"; proc = "++proc++" (hash "++proc.hash++"); map = "++map++"; fadeTime = "++fadeTime).inform;
//								clpse = procRep.getCollapse( attr );
								if( pressed.not, {
									map = Dictionary[ attr.name -> newVal ];
									if( soloChannel !== ch, {
("CROSS SOLO oldVal = "++oldVal++"; newVal = "++newVal).postln;
									    proc = procRep.getProc;
//("BBB procRep = "++procRep.hash++"; proc = "++proc++" (hash "++proc.hash++"); map = "++map++"; fadeTime = "++fadeTime).inform;
// ZZZ
//fdt = nil;
//("fdt = "++fdt++"; fadeTime = "++fadeTime).postln;
									    proc.crossFade( map, fdt ?? fadeTime );
									},Ê{ if( soloProcChain.notNil, {
("CROSS NORM oldVal = "++oldVal++"; newVal = "++newVal).postln;
									    proc = procRep.getProc;
									    proc.crossFade( map, 0.1 );  // no fuckn' fade
									}, {
									    procChain = ping.getProcChain( ch );
									    procChainRep = procChainReps[ ch ];
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
									        soloProcChainRep = WacomProcChainRep( box, soloProcChain, soloChannel );

// WARNING: DON'T USE procRep AS VARIABLE HERE!!
										// DENN SONST KANN soloChannel !== ch
										// BRANCH DEN ALTEN procRep NIT FINDEN
									        soloProcRep = soloProcChainRep.getProcAt( knobIdx );
									        proc    = soloProcRep.getProc;
									        proc.applyAttr( map );
									
									        soloProcChain.do({ arg proc;
									            proc.play;
									        });
										   
//									        this.prEnterChanMode;  // rebuilds knobGaga
									        this.prEnterChanMode2( ch );
								             ping.solo.solo( soloProcChain[ soloProcChain.size - 2 ]);
									        box.channelSolo( ch, \blink );  // blinkendorfer
								         }, {
									        TypeSafe.methodWarn( thisMethod, "Lost proc chain" );
									    });
									})});
//									this.prUpdateChanKnobs;
								});
							}, {
								map2 = Dictionary[ attr.name -> newVal ];
								if( soloChannel !== ch, {
								    proc = procRep.getProc;
								    proc.applyAttr( map2 );
								},Ê{ if( soloProcChain.notNil, {
								    proc = procRep.getProc;
									proc.applyAttr( map2 );
								}, {
								    procChain = ping.getProcChain( ch );
								    procChainRep = procChainReps[ ch ];
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
								        soloProcChainRep = WacomProcChainRep( box, soloProcChain, soloChannel );

									// WARNING: DON'T USE procRep AS VARIABLE HERE!!
									// DENN SONST KANN soloChannel !== ch
									// BRANCH DEN ALTEN procRep NIT FINDEN
								        soloProcRep = soloProcChainRep.getProcAt( knobIdx );
								        proc    = soloProcRep.getProc;
								        proc.applyAttr( map2 );
								
								        soloProcChain.do({ arg proc;
								            proc.play;
								        });
									   
//								        this.prEnterChanMode;  // rebuilds knobGaga
								        this.prEnterChanMode2( ch );
							             ping.solo.solo( soloProcChain[ soloProcChain.size - 2 ]);
								        box.channelSolo( ch, \blink );  // blinkendorfer
							         }, {
								        TypeSafe.methodWarn( thisMethod, "Lost proc chain" );
								    });
								})});
							});
							case {Êattr.type === \normal }
							{
								box.channelKnob( ch, knobIdx, knobVal );
							}
							{ attr.type === \pan }
							{
								box.channelKnobPan( ch, knobIdx, knobVal );
							}
							{ attr.type === \fill }
							{
								box.channelKnobFill( ch, knobIdx, knobVal );
							};
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
								box.channelSolo( selectedChannel, \blink );  // blinkendorfer

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
//						this.prEnterChanMode;
//						this.prEnterChanMode2( selectedChannel );
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
	
	prEnterChanMode2 { arg ch;
		var procChainRep;

		if( soloChannel === ch, {
			procChainRep = soloProcChainRep ?? { procChainReps[ ch ];};
		}, {
			procChainRep = procChainReps[ ch ];
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
// YYY
//			WacomProcChainRep.displayBlank( box );
		}, {
			if( pressed, {
				if(ÊknobMode === \pan, {
					this.prMasterMode;
				}, {
					this.prSetKnobMode( \pan );
// YYY
//					WacomProcChainRep.displayBlank( box );
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
// YYY
//		WacomProcChainRep.displayBlank( box );

//		24.do({ arg ch;
//			if( masterKnobs[ ch ].isNil, {
//				box.channelKnobFill( ch, 0, true );
//			}, {
//				box.channelKnobFill( ch, masterKnobs[ ch ], false );
//			});
//		});
	}
	

	// fork
	prSoloClear { arg pressed, force = false, fdt;
		var procChain, ch;
		
		if( force ||Êpressed, {
//("HAKLLO "++soloChannel).inform;
			if( soloChannel.notNil, {
				ch = soloChannel;
				ping.solo.unsolo;
				if( soloProcChain.notNil, {
					if( force ||ÊshiftPressed.not, {
						box.channelSolo( soloChannel, \off );
//						"disposing temp solo".inform;
						ping.server.sync;
						soloProcChain.do({ arg proc, i;
//							if( i == 0, { proc.removeDependant( procListener );});
							proc.dispose;
						});
						soloProcChain = nil;
						soloProcChainRep.dispose;
						soloProcChainRep = nil;
						soloChannel = nil;
//						this.prUpdateChanKnobs;
						this.prEnterChanMode2( ch );
					}, {
						this.prSoloApply( fdt );
					});
				}, {
					box.channelSolo( soloChannel, \off );
					soloChannel = nil;
//					this.prUpdateChanKnobs;
					this.prEnterChanMode2( ch );
				});
			});
		});
	}

	prSoloApply {Êarg fdt;
		var procChain, proc, attrNew, attrOld, attrChanged;
		if( soloChannel.notNil, {
			"appyling temp solo".inform;
			box.channelSolo( soloChannel, \off );
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
					proc.crossFade( attrChanged, fdt ?? fadeTime );
				});
	//							if( i == 0, { proc.removeDependant( procListener );});
				soloProc.dispose;
			});
			soloProcChain = nil;
			soloProcChainRep.dispose;
			soloProcChainRep = nil;
			soloChannel = nil;
			this.prUpdateChanKnobs;
		}, {
			this.playSpeechCue( \NoSolo );
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

	prSetPanorama { arg azi, spread;
		var procChain, unit;
		
		if( selectedChannel.notNil, {
			procChain = ping.getProcChain( selectedChannel );
			if( procChain.notNil, {
				unit		= procChain.last.getUnit;	// OutputUnit
//				[ azi, spread ].postln;
				azi		= azi / pi;
				unit.setSpread( spread );
				unit.setAzimuth( azi );
			});
		});
	}
	
	prChannelSelect { arg ch, pressed;
		var procChain;
		
		if( pressed, {
			if( selectedChannel === ch, {
				box.channelSelect( ch, \off );
				selectedChannel = nil;
//				if( knobMode === \chan, {Êthis.prMasterMode; });
			}, {
				procChain = ping.getProcChain( ch );
				if( procChain.notNil, {
					if( selectedChannel.notNil, { box.channelSelect( selectedChannel, \off );});
					selectedChannel = ch;
					box.channelSelect( selectedChannel, \on );
// YYY
//					this.prChanMode( force: true );
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
					box.channelSolo( ch, \on );
					ping.solo.solo( proc );
					soloChannel = ch;
					if( selectedChannel !== soloChannel, { this.prChannelSelect( ch, true );});
				});
			});
		});
	}
	
	// fork
	prChannelPlay { arg ch, pressed, fdt;
		var proc, oProc, unit, solo, procChain, current;
		
		if( pressed, {
			procChain = ping.getProcChain( ch );

			// pause, resume or dispose process
			if( procChain.notNil and: { fkeyPressed.not }, {
				if( shiftPressed, {
					this.prChannelDelete( ch );
				}, {
					if( procChain.first.isRunning, {
						procChain.do({ arg proc;
							proc.stop( fdt ?? fadeTime );
						});
						box.channelPlay( ch, \blink );
					}, {
						procChain.do({ arg proc;
							proc.play( fdt ?? fadeTime );
						});
						box.channelPlay( ch, \on );
					});
				});
				
			// instantiate new process
			}, {
this.playSpeechCue( \NoProcess );
//				createChannel = ch;
//				if( procChain.isNil, {
//					box.channelFader( ch, 0 );
//				});
//				if( selectedChannel.notNil, {
//					box.channelSelect( selectedChannel, \off );
//					selectedChannel = nil;
//				});
//				selectedChannel = ch;
//				box.channelSelect( ch, \blink );
//				this.prChanMode( force: true );
			});
		});
	}

	prChannelDelete { arg ch;
		var procChain;
		procChain = ping.getProcChain( ch );
		if( procChain.notNil, {
			if( ch === selectedChannel, {
				box.channelSelect( ch, \off );
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
			box.channelPlay( ch, \off );
			box.channelName( ch, nil );
			box.channelFader( ch, 0 );
			WacomProcChainRep.displayBlank( box, ch );
		}, {
			this.playSpeechCue( \NoProcess );
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
				box.channelSelect( chanSyncTime, \on );
				box.channelSolo( chanSyncTime, \off );
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
				box.channelSelect( chanSyncTime, \off );
				box.channelSolo( chanSyncTime, \on );
				box.channelFader( chanSyncTime, 1 );
				syncTask.stop;
				syncTask.reset;
				syncType = \term;
				syncTime = args[ 2 ].asInt.max( 20 ).min( 300 );
				syncTask.start;
			});
		};		
	}
	
	prCreateNewProcess { arg createChannel, uf, variation;
		var unit, proc, procChain, oProc;
	
		procChain = ping.getProcChain( createChannel );
		if( procChain.isNil, {	// --------- create new process ---------
			if( uf.notNil, {
				unit = uf.makeUnit( ping, variation );
			});
			if( unit.isNil, {
				this.playSpeechCue( \NoProcess );
				^this;
			});
	
			if( unit.isKindOf( TrigTapeUnit ), {
				trigs[ createChannel ] = TrigMachine( ping, unit, createChannel );
			});
	
//"CCCC".postln;
			proc = ping.createProcForUnit( unit );
			oProc = ping.createOutputForProc( proc );
			oProc.getUnit.setVolume( 0 );
			procChain = [ proc, oProc ];
			ping.addProcChain( procChain, createChannel );
			procChainReps.put( createChannel, WacomProcChainRep( box, procChain, createChannel ));
//"DDDD".postln;
			ping.server.sync;
			proc.addDependant( procListener );
			proc.play;
	
			box.channelFader( createChannel, 0 );
			box.channelPlay( createChannel, \on );
			box.channelName( createChannel, uf.type.asString );
//"AAAA".postln;
//			createChannel = nil;
//			box.channelSelect( selectedChannel, \on );
			box.channelSelectMutex( createChannel );
			selectedChannel = createChannel;
//			this.prEnterChanMode;  // rebuilds knobGaga
			this.prEnterChanMode2( selectedChannel );
//"BBBB".postln;
		}, {
			this.playSpeechCue( \Failed );
		});
	}
}

/**
 *	Interface represenation of a process chain
 */
WacomProcChainRep : Object {
	var box;
	var procReps;
	var procRepIdxOffs;
	var numProcs;
	var <isRecorder;	// (Boolean) re first proc (generator)
	var <ch;

	*new {Êarg box, procChain, ch;
		^super.new.prInitProcChainRep( box, procChain,ch );
	}
	
	prInitProcChainRep {Êarg argBox, procChain, argCh;
		var idx;
	
		TypeSafe.checkArgClasses( thisMethod, [ argBox, procChain ], [ WacomTwoFour, SequenceableCollection ], [ false, false ]);
	
		box				= argBox;
		numProcs			= procChain.size;
		procReps			= Array.newClear( numProcs );
		procRepIdxOffs	= Array.newClear( numProcs );
		ch				= argCh;

		idx = 0;
		procChain.do({ arg proc, i;
			procRepIdxOffs[ i ]	= idx;
			procReps[ i ]			= WacomProcRep( proc );
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
		var idx, val, attr, procRep, unit;
	
		idx = 0;
		block {Êarg break;
			(procReps.size - 1).do({ arg i;
				if( idx >= box.maxNumKnobs, break );
				procRep = procReps[ i ];
	
// YYY
//				box.channelKnobDisco( ch, idx, 1, true );
//				box.channelKnobLabel( idx, procRep.getName );
				box.channelKnobHide( ch, idx );
				box.channelKnobLabel( ch, idx, procRep.getName.asString );
				idx = idx + 1;
				procRep.getNumAttr.do({ arg i;
					if( idx >= box.maxNumKnobs, break );
					
					attr		= procRep.getAttrAt( i );
					val		= attr.getNormalizedValue( procRep.getUnit );
					
					case
					{Êattr.type === \normal }
					{
						box.channelKnob( ch, idx, val );
					}
					{ attr.type === \pan }
					{
						box.channelKnobPan( ch, idx, val );
					}
					{ attr.type === \fill }
					{
						box.channelKnobFill( ch, idx, val );
					}
					{
						TypeSafe.methodError( thisMethod, "Illegal display type " ++ attr.type );
					};
					box.channelKnobLabel( ch, idx, attr.name.asString );
					idx = idx +1;
				});
			});
			
			while({ idx < box.numChannels }, {
				box.channelKnobHide( ch, idx );
				box.channelKnobLabel( ch, idx, nil );
					idx = idx + 1;
			});
		};

		box.transportRec( isRecorder.if({ÊprocReps.first.getUnit.isRecording }, false ));

		if( ch == ~wp.selectedChannel, {
			unit = procReps.last.getUnit;
			box.panorama( unit.getAzimuth * pi, unit.getSpread );
		});
	}
	
	*displayBlank { arg box, ch;
		var idx = 0;

		while({ idx < box.maxNumKnobs }, {
			box.channelKnobHide( ch, idx );
			box.channelKnobLabel( ch, idx, nil );
			idx = idx + 1;
		});
	}
	
	/**
	 *	@return	(WacomProcRep)
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
WacomProcRep {
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
	
	getName {
		^proc.getUnit.getName;
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
	
	calcNewValue2 { arg attr, knobVal;
		var idx, oldVal, newVal, inc;
		
		idx = unitAttrs.indexOf( attr );
		if( idx.notNil, {
			if( collapses[ idx ].isNil, {
				oldVal = this.getUnit.perform( attr.getter );
			}, {
				oldVal = collapses[ idx ].args.first;
			});

			if( attr.spec.step != 0, {				
//				inc		= if( attr.spec.copy.step_( 0 ).map( knobVal ) > oldVal, 1, -1 );
//				inc		= (if( inc >= 64, {Ê64 - inc }, inc )).sign * attr.spec.step;
//				knobVal	= (attr.spec.unmap( oldVal + inc )).clip( 0, 1 );
//				knobVal	= (attr.spec.unmap( oldVal + inc )); // .clip( 0, 1 );
//				knobVal	= (attr.spec.unmap( oldVal + inc )); // .clip( 0, 1 );
newVal = attr.spec.copy.step_( 0 ).map( knobVal );
//				newVal	= attr.spec.constrain( oldVal + inc );
				knobVal	= attr.spec.unmap( newVal ); //  + (inc/2) );

//("inc = "++inc++"; oldVal == "++oldVal++"; newVal = "++newVal ++"; new knob = "++knobVal).postln;
			}, {
				newVal = attr.spec.map( knobVal );
			});
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
WacomUnitAttrRep {
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