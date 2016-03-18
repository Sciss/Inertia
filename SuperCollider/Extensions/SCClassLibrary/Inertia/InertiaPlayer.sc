/**
 *	(C)opyright 2005-2007 by Hanns Holger Rutz. All rights reserved.
 *
 *	@version	0.3, 27-Jan-07
 *	@author	Hanns Holger Rutz
 */
InertiaPlayer {
	// allowed overlap in milliseconds for atoms
	// not to be considered to stand in the shadow
	classvar	<>kOverlap		= 0.5;	// NOTE: in seconds (not millis as in java)!
	classvar	<>kDurchgewunken	= 2.0;  // kOverlap << 2;  NOTE: in seconds (not millis as in java)!
	classvar <>kShadowCoin		= 0.5;
	classvar <>kDurchgewunkenOffset = 0;

	classvar 	kDiskBufSize	= 32768;	// buffer size in frames
	classvar	kDiskBufSizeH	= 16384;	// kDiskBufSize >> 1;

	classvar kLatencySafety	= 0.1;
	classvar kFutureSpan;

	classvar kRazDebug = false;
	
	var <doc;
	var rateScale;
	var absOffset;	// NOTE: in seconds (not millis as in java)!
	var relOffset, sourceRate;
	var <syncLayer;				// array index = movie quadrant ; element = layer ID
	var <track;	// simplification : only one track
	var <server;
	var <transport;

	var <mapRunningAtoms;	// map: (Integer) nodeID of soundfile player --> RunningAtom
	var <collRunningAtoms;	// one coll for each layer
	
	var <numLayers;
	var <numInputChannels	= 2;	// XXX fixed now

	var <synthsFilter;
	var <nw;
	var <oCfg, <oCfgF;
	var <volume;

	var <grpRoot, <grpOutput, <grpInPre, <grpFilter, <grpAccum;
//	var <grpInPost;
	
	var trigResp, nEndResp;

	// from Context
	var <synthsPan, <synthsPanF, <synthsRoute, <synthsRouteF;
	var <busAccum, <bussesFilter, <busInternal, <busInternalF, <busPan, <busPanF;
//	var <bussesRegular;
	
	var <transportTasks;
	
	var volumes;
	
	var <mobile;

	classvar kTickPeriod		= 0.5;		// must be <= FUTURESPAN / 2

	*initClass {
		kFutureSpan = [ 8.0, 6.0, 4.0, 2.0 ];
	}
	
	*new { arg doc, server, oCfg, volume = 1.0;
		^super.new.prInit( doc, server, oCfg, volume );
	}
	
	prInit { arg argDoc, argServer, oCfg, argVolume;
		var bndl;
		
		doc			= argDoc;
		transport		= doc.transport;
		
		numLayers		= doc.layers.numLayers;
		if( transport.numChannels != numLayers, {
			Error( "Assertion Failed : transport channels == number of layers" ).throw;
		});
		
		mapRunningAtoms = IdentityDictionary.new;
		rateScale		= Array.fill( InertiaSession.kNumMovies, 1.0 );
//		estLoopDur	= Array.fill( InertiaSession.kNumMovies, 30.0 );
		absOffset		= Array.fill( InertiaSession.kNumMovies, 0.0 );
		relOffset		= Array.fill( InertiaSession.kNumMovies, 0.0 );
		volumes		= Array.fill( InertiaSession.kNumMovies, 1.0 );
		sourceRate	= doc.timeline.rate;
		track	= doc.tracks.first;	// simplification : only one track
		server		= argServer ?? Server.default;
		nw		= NodeWatcher.newFrom( server );

		synthsFilter		= Array.newClear( numLayers );
		syncLayer		= Array.series( numLayers, 0 );

		collRunningAtoms	= Array.fill( numLayers, {ÊList.new });

		bndl			= List.new;

		grpRoot				= Group.basicNew( server );
//		grpRoot.setName( "Root_" ++ doc.getName().substring( 0, Math.min( 6, doc.getName().length() )));
		nw.register( grpRoot );
		bndl.add( grpRoot.newMsg( server.defaultGroup ));

		grpInPre			= Array.newClear( numLayers );
		grpFilter			= Array.newClear( numLayers );
//		grpInPost			= Array.newClear( numLayers );
		grpAccum			= Array.newClear( numLayers );
		
		numLayers.do({ arg ch;
			grpInPre[ ch ]	= Group.basicNew( server );
			nw.register( grpInPre[ ch ]);
			bndl.add( grpInPre[ ch ].newMsg( grpRoot, \addToTail ));
			grpFilter[ ch ] = Group.basicNew( server );
			nw.register( grpFilter[ ch ]);
			bndl.add( grpFilter[ ch ].newMsg( grpRoot, \addToTail ));
//			grpInPost[ ch ]	= Group.basicNew( server );
//			nw.register( grpInPost[ ch ]);
//			bndl.add( grpInPost[ ch ].newMsg( grpRoot, \addToTail ));
			grpAccum[ ch ]	= Group.basicNew( server );
			nw.register( grpAccum[ ch ]);
			bndl.add( grpAccum[ ch ].newMsg( grpRoot, \addToTail ));
		});
		grpOutput			= Group.basicNew( server );
//		grpOutput.setName( "Out" );
		nw.register( grpOutput );
		bndl.add( grpOutput.newMsg( grpRoot, \addToTail ));

		trigResp = OSCresponderNode( server.addr, '/tr', { arg time, resp, msg;
			var nodeID, clock, pos, run;
			
			nodeID	= msg[ 1 ];
			clock	= msg[ 3 ].asInteger + 1;
			run		= mapRunningAtoms[ nodeID ];
			if( run.notNil, {
				pos	 = run.fileStartFrame + (clock * kDiskBufSizeH);
				server.listSendMsg( run.bufDisk.readMsg( run.filePath, pos, kDiskBufSizeH, if( clock.even, 0, kDiskBufSizeH )));
			});
		});
		
		// UUU XXX can be put in a NodeWatcher listener
		nEndResp = OSCresponderNode( server.addr, '/n_end', { arg time, resp, msg;
			var run;
			run = mapRunningAtoms.removeAt( msg[ 1 ]);
			if( run.notNil, {
				collRunningAtoms[ run.layer ].remove( run );
				this.prDispatchAtomDeath( run );
				run.bufDisk.free;
			});
		});
		
		trigResp.add;
		nEndResp.add;
		
		server.listSendBundle( nil, bndl );

// XXX
oCfg = oCfg ?? InertiaRoutingConfig.defaultOutput;
oCfgF = oCfgF ?? InertiaRoutingConfig.defaultOutputF;
		this.setOutputConfigs( oCfg, oCfgF, argVolume );

transportTasks = Array.fill( numLayers, {Êarg ch;
	Task({
		var currentPos, incRad = true;
		inf.do({
//("time[ ch = "++ch++" ] = "++((thisThread.seconds - absOffset[ ch ]) + relOffset[ ch ])).postln;
if( kRazDebug, {Ê("time[ ch = "++ch++" ] = "++SMPTE((thisThread.seconds - absOffset[ ch ]) + relOffset[ ch ]).toString).postln; });
			currentPos = (((thisThread.seconds - absOffset[ ch ]) + relOffset[ ch ]) * sourceRate).asInteger;
			this.prRequestRaz( ch, currentPos, incRad );
			  incRad = false;
			kTickPeriod.wait;
		});
	});
});
		transport.addListener( this );
// XXX
//		transport.addRealtimeConsumer( this );
		doc.layers.addListener( this );
	}

	startMobile {
		if( mobile.isNil, {
			mobile = InertiaMobile( doc );
		}, {
			mobile.restart;
		});
		this.changed( \mobileOnOff );
	}
	
	stopMobile {
		if( mobile.notNil, {
			mobile.stopMobile;
			mobile = nil;
		});
		this.changed( \mobileOnOff );
	}
	
	isMobilePlaying {
		^(mobile.notNil and: { mobile.isPlaying });
	}

	setOutputConfigs { arg argOCfg, argOCfgF, argVolume;
		var channels, wasRunning, rate, pos;
	
		oCfg		= argOCfg;
		oCfgF	= argOCfgF;
		volume	= argVolume;

		if( server.serverRunning, {
			channels		= transport.numChannels;
			wasRunning	= Array.newClear( channels );
			rate			= Array.newClear( channels );
			pos			= Array.newClear( channels );
			
			channels.do({ arg ch;
				wasRunning[ ch ]	= transport.isRunning( ch );
				if( wasRunning[ ch ], {
					transport.stopAndWait( ch );
					rate[ ch ]		= transport.getRateScale( ch );
					pos[ ch ]		= transport.getPosition( ch );
				});
			});

			this.prRebuildSynths;
			
			channels.do({ arg ch;
				if( wasRunning[ ch ], {
					transport.goPlay( ch, pos[ ch ], rate[ ch ]);
				});
			});
		});
	}
	
	prRebuildSynths {
		var synth, orient, orientF, bndl;
	
		grpRoot.deepFree;
		
		this.prDisposeContext;
		
		if( oCfg.isNil || oCfgF.isNil, { ^this });
		
		this.prCreateContext;

		orient	= oCfg.startAngle.neg/360 * oCfg.numChannels;
		orientF	= oCfgF.startAngle.neg/360 * oCfgF.numChannels;
		bndl	= List.new;

		oCfg.numChannels.do({ arg ch;
			if( oCfg.mapping[ ch ] < server.options.numOutputBusChannels, {
				nw.register( synthsRoute[ ch ]);
				bndl.add( synthsRoute[ ch ].newMsg( grpOutput,
					[ \i_aInBus, busPan.index + ch, \i_aOutBus, oCfg.mapping[ ch ]]));
			});
		});
		oCfgF.numChannels.do({ arg ch;
			if( oCfgF.mapping[ ch ] < server.options.numOutputBusChannels, {
				nw.register( synthsRouteF[ ch ]);
				bndl.add( synthsRouteF[ ch ].newMsg( grpOutput,
					[ \i_aInBus, busPanF.index + ch, \i_aOutBus, oCfgF.mapping[ ch ]]));
			});
		});
		numInputChannels.do({ arg ch;
			nw.register( synthsPan[ ch ]);
			bndl.add( synthsPan[ ch ].newMsg( grpOutput,
				[ \i_aInBus, busInternal.index + ch, \i_aOutBus, busPan.index, \volume, volume, \orient, orient ]));
			nw.register( synthsPanF[ ch ]);
			bndl.add( synthsPanF[ ch ].newMsg( grpOutput,
				[ \i_aInBus, busInternalF.index + ch, \i_aOutBus, busPanF.index, \volume, volume, \orient, orientF ]));
		});

		this.prAddChannelPanMessages( bndl );
		
		grpAccum.do({ arg grp, ch;
			synth = Synth.basicNew( "inertia-accumW" ++ numInputChannels, server );
			nw.register( synth );
			bndl.add( synth.newMsg( grpAccum[ ch ],
				[ \i_aInBusA, busInternal.index,
				  \i_aInBusB, busInternalF.index, \i_aOutBus, busAccum.index ]));
		});

		server.listSendBundle( nil, bndl );
		server.sync;
//		if( !server.sync( 4.0f )) {
//			printTimeOutMsg();
//		}
	}

	prDisposeContext {
		busAccum.free;
		busAccum = nil;
		busInternal.free;
		busInternal = nil;
		busInternalF.free;
		busInternalF = nil;
		bussesFilter.do({ arg bus; bus.free });
		bussesFilter = nil;
//		bussesRegular.do({ arg bus; bus.free });
//		bussesRegular = nil;
		busPan.free;
		busPan = nil;
		busPanF.free;
		busPanF = nil;
	}

	prCreateContext {
		var numConfigOutputs, numConfigOutputsF;
		
		numConfigOutputs  = oCfg.numChannels;
		numConfigOutputsF = oCfgF.numChannels;
		
		synthsPan			= Array.fill( numInputChannels, {
			Synth.basicNew( "inertia-pan" ++ numConfigOutputs, server );
		});
		synthsPanF		= Array.fill( numInputChannels, {
			Synth.basicNew( "inertia-pan" ++ numConfigOutputs, server );
		});
		synthsRoute			= Array.fill( numConfigOutputs, {
			Synth.basicNew( "inertia-route1", server );
		});
		synthsRouteF			= Array.fill( numConfigOutputsF, {
			Synth.basicNew( "inertia-route1", server );
		});
		busAccum			= Bus.audio( server, numInputChannels );
		bussesFilter		= Array.fill( numLayers, {
			Bus.audio( server, numInputChannels );
		});
//		bussesRegular		= Array.fill( numLayers, {
//			Bus.audio( server, numInputChannels );
//		});
		busInternal				= Bus.audio( server, numConfigOutputs );
		busInternalF				= Bus.audio( server, numConfigOutputsF );
		busPan					= Bus.audio( server, numConfigOutputs );
		busPanF					= Bus.audio( server, numConfigOutputsF );
	}

	prAddChannelPanMessages { arg bndl;
		var pos, width;
		
		if( synthsPan.notNil, {
			numInputChannels.do({ arg ch;
				pos		= ((ch - 0.5) * 2) / numInputChannels;
				width	= 2.0;
				bndl.add( synthsPan[ ch ].setMsg( \pos, pos, \width, width ));
			});
		});
		if( synthsPanF.notNil, {
			numInputChannels.do({ arg ch;
				pos		= ((ch - 0.5) * 2) / numInputChannels;
				width	= 2.0;
				bndl.add( synthsPanF[ ch ].setMsg( \pos, pos, \width, width ));
			});
		});
	}
	
	// -------------- TransportListener interface --------------
		
	transportPlay { arg transport, ch = 0, pos = 0, argRateScale = 1.0;
		var now;
		
		now	= thisThread.seconds;
	
		rateScale[ ch ]			= argRateScale;
//		estLoopDur[ ch ]		= now - absOffset[ ch ];
		absOffset[ ch ]			= now;
		relOffset[ ch ]			= pos / sourceRate;
		volumes[ ch ]			= doc.layers.getVolume( ch );
//		this.prRequestRaz( ch, pos, true );
// FUCKING TASK RETURNS ROUTINE AFTER RESET
//transportTasks[ ch ].reset.start;
transportTasks[ ch ].reset;
transportTasks[ ch ].start;
	}
	
	transportStop { arg transport, ch = 0, pos;
//("STOP "++ch).postln;
transportTasks[ ch ].stop;
	}

	transportPosition { arg transport, ch, pos, rateScale;
		this.transportStop( transport, ch, pos );
		this.transportPlay( transport, ch, pos, rateScale );
	}
	
	prRequestRaz { arg ch, pos, incRad;
		var layer, startSec, stopSec, addToRA, collRaz, run, run2, nodeStartTime, nodeStopTime, fileStartFrame;
	
//("pos = "++pos++"; class = "++pos.class.name).postln;
//("sourceRate = "++sourceRate++"; class = "++sourceRate.class.name).postln;
//("kLatencySafety = "++kLatencySafety++"; class = "++kLatencySafety.class.name).postln;
		layer		= syncLayer[ ch ];
		startSec		= pos / sourceRate + kLatencySafety;
		stopSec		= startSec + kFutureSpan[ layer ] * rateScale[ ch ];
		addToRA		= absOffset[ ch ] - relOffset[ ch ];

		if( incRad, {
			if( kRazDebug, { ("incRad for ch "++ch).postln });
			track.increaseRadiation( ch );
		});
		collRaz = track.getRealizedAtoms( ch, startSec, stopSec );
		if( kRazDebug, { ("getRealizedAtoms "++startSec++" ... "++stopSec++" for ch "++ch++" : "++collRaz.size++" atoms.").postln });

		collRaz = collRaz.reject({ arg ra; ra.volume < -36 }); // quasi mute

		collRaz.do({ arg ra;
			nodeStartTime	= ra.startTime + addToRA;
			nodeStopTime	= ra.stopTime + addToRA;
			fileStartFrame	= (ra.fileStart * server.sampleRate).asInteger;
			run				= InertiaRunningAtom( ra, layer, nodeStartTime, nodeStopTime, fileStartFrame );
			block { arg razierKlinge;
				if( (nodeStopTime - nodeStartTime) > (kDurchgewunken.rand + kDurchgewunkenOffset), {
					layer.do({ arg k;
						collRunningAtoms[ k ].do({ arg run2;
							if( (run2.nodeStopTime - run2.nodeStartTime) > kDurchgewunken.rand, {
								if( nodeStartTime <= run2.nodeStartTime, {
									if( nodeStopTime > (run2.nodeStartTime + kOverlap), {
										if( kShadowCoin.coin, {
											// the hour is late and you know that i wait for no one...
											this.prDispatchAtomShadow( run );
										}, {
											this.playAtom( ch, run, ra.volume.dbamp, true );
											this.prDispatchAtomFilter( run );
										});
										razierKlinge.value; // continue razierKlinge;
									});
								},Ê{ if( (run2.nodeStopTime - kOverlap) > nodeStartTime, {
									if( kShadowCoin.coin, {
										this.prDispatchAtomShadow( run );
									}, {
										this.playAtom( ch, run, ra.volume.dbamp, true );
										this.prDispatchAtomFilter( run );
									});
									razierKlinge.value; // continue razierKlinge;
								})});
							});
						});
					});
				});
				// ok, we're on stage. but where the fuck is the crowd??
				this.playAtom( ch, run, ra.volume.dbamp, false );
				this.prDispatchAtomLight( run );
			};
		});
	}

	playAtom { arg ch, run, volume, filtered;
		var ra, synthSFPlay, interpolation, realRate, bndl1, time1, time2, time3, d, bufDisk;
	
		ra = run.ra;

//"haaaaalo".postln;
	
		if( ra.file.isNil, { ^this; });

volume = volume * volumes[ ch ];
	
		synthSFPlay		= Synth.basicNew( "inertia-sfplay" ++ numInputChannels, server );
		bndl1			= List.new; // new OSCBundle( run.nodeStartTime );
		d				= ra.pitch;
		realRate		= rateScale[ ch ] * (ra.pitch.midiratio);
		interpolation	= if( realRate == 1.0, 1, 4 );
		time1			= ra.fadeIn; // / realRate;
		time2			= (ra.stopTime - ra.startTime - ra.fadeOut - ra.fadeIn); // / realRate;
		time3			= ra.fadeOut; // / realRate;

		bufDisk		= Buffer.readNoUpdate( server, run.filePath, run.fileStartFrame, kDiskBufSize );
		run.bufDisk	= bufDisk;
		nw.register( synthSFPlay );

//		bndl1.add( synthSFPlay.newMsg( if( filtered, grpInPre[ run.layer ], grpInPost[ run.layer ]),
//			[ \i_aInBuf, bufDisk.bufnum, \i_aOutBus, bussesFilter[ run.layer ].index, \i_gain, volume, \rate, realRate,
//			  \i_interpolation, interpolation, \i_time1, time1, \i_time2, time2, \i_time3, time3 ]));

		bndl1.add( synthSFPlay.newMsg( grpInPre[ run.layer ],
			[ \i_aInBuf, bufDisk.bufnum, \i_aOutBus, if( filtered, {ÊbussesFilter[ run.layer ].index },
			  { busInternal.index }), \i_gain, volume, \rate, realRate,
			  \i_interpolation, interpolation, \i_time1, time1, \i_time2, time2, \i_time3, time3 ]));

//bndl1.postln;

		// XXX XXX run.nodeStartTime must be converted to relative time??
//			server.listSendBundle( run.nodeStartTime, bndl1 );
		server.listSendBundle( run.nodeStartTime - thisThread.seconds, bndl1 );

		mapRunningAtoms.put( synthSFPlay.nodeID, run );
		collRunningAtoms[ run.layer ].add( run );
	}

	prDispatchAtomLight { arg run;
// XXX
//		if( elmRAZ != null ) {
//			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.LIGHT, System.currentTimeMillis(), run ));
//		}
	}

	prDispatchAtomShadow { arg run;
// XXX
//		if( elmRAZ != null ) {
//			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.SHADOW, System.currentTimeMillis(), run ));
//		}
	}

	prDispatchAtomFilter { arg run;
// XXX
//		if( elmRAZ != null ) {
//			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.FILTER, System.currentTimeMillis(), run ));
//		}
	}

	prDispatchAtomDeath {Êarg run;
// XXX
//		if( elmRAZ != null ) {
//			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.DEATH, System.currentTimeMillis(), run ));
//		}
	}

	/**
	 *	Sets the shadowing filter for a given layer.
	 *
	 *	@param	layer	one of the layers, cannot be the topmost layer, i.e. must
	 *					be greater than zero
	 *	@param	when	server action time (OSCBundle time) or zero to execute immediately
	 *	@param	name	the filter name which is used to construct a synthdef name
	 *					<code>&quot;inertia-layerW&lt;name&gt;&lt;numberOfChannels&gt;&quot;</code>
	 *					; if <code>null</code> the current filter is removed
	 */
	setFilter {Êarg layer, when, name;
		var bndl;
	
		if( oCfg.isNil, { ^this });
		if( (layer < 1) || (layer >= synthsFilter.size), {
			("setFilter : illegal layer idx " ++ layer).error;
			^this;
		});
	
//		OSCBundle bndl = when == 0 ? new OSCBundle() : new OSCBundle( when );
		bndl = List.new;
	
		if( synthsFilter[ layer ].notNil, {
			bndl.add( synthsFilter[ layer ].freeMsg );
			synthsFilter[ layer ] = nil;
		});
		
		if( name.notNil, {
			synthsFilter[ layer ] = Synth.basicNew( "inertia-layerW" ++ name ++ numInputChannels, server );
			nw.register( synthsFilter[ layer ]);
			bndl.add( synthsFilter[ layer ].newMsg( grpFilter[ layer ],
				[ \i_aBusA, bussesFilter[ layer ].index,
				  \i_aBusB, busAccum.index, \i_aOutBus, busInternalF.index ]));
		});
//		server.listSendBundle( when, bndl );
		if( bndl.notEmpty, {Êserver.listSendBundle( nil, bndl )});
	}

// ------------- LayerManager.Listener interface -------------

	layersSwitched { arg e;
		syncLayer[ e.getManager.getMovieForLayer( e.getFirstLayer )]	= e.getFirstLayer;
		syncLayer[ e.getManager.getMovieForLayer( e.getSecondLayer )]	= e.getSecondLayer;
	}
	
	layersFiltered { arg e;
		this.setFilter( e.getFirstLayer, 0, e.getParam );
	}
}

InertiaRunningAtom {
	var	<ra;
	var <bufDisk;
	var <filePath;
	var <fileStartFrame;
	var <nodeStartTime, <nodeStopTime;
	var <layer;
	var <>bufDisk;
	
	*new { arg ra, layer, nodeStartTime, nodeStopTime, fileStartFrame;
		^super.new.prInit( ra, layer, nodeStartTime, nodeStopTime, fileStartFrame );
	}
	
	prInit { arg argRA, argLayer, argNodeStartTime, argNodeStopTime, argFileStartFrame;
		ra				= argRA;
		filePath		= argRA.file;
		fileStartFrame	= argFileStartFrame;
		nodeStartTime	= argNodeStartTime;
		nodeStopTime	= argNodeStopTime;
		layer			= argLayer;
	}
}

InertiaRoutingConfig {
	classvar <defaultOutput, <defaultOutputF;

	var <name, <numChannels, <mapping, <startAngle;

	*initClass {
		defaultOutput  = InertiaRoutingConfig( "default", 2, [ 0, 1 ], -90 );
		defaultOutputF = InertiaRoutingConfig( "default", 2, [ 2, 3 ], -90 );
	}

	*new { arg name, numChannels, mapping, startAngle;
		^super.new.prInit( name, numChannels, mapping, startAngle );
	}
	
	prInit { arg argName, argNumChannels, argMapping, argStartAngle;
		name		= argName;
		numChannels	= argNumChannels;
		mapping		= argMapping;
		startAngle	= argStartAngle;
	}
}
