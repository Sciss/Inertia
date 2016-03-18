/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: UpdateListener, TypeSafe, SynthDefCache
 *
 *	Kind of a replacement for wolkenpumpe 2005 SynthProxy
 *
 *	@version	0.13, 19-Jul-06
 *	@author	Hanns Holger Rutz
 *
 *	@todo	when stop is called and the fadein not yet completed, the fade level should not jump
 *			; therefore : maybe just afford one fade synth playing all the time with gated env
 *	@todo	use SynthDefCache
 *	@todo	lazy tempBus creation (optional)
 */
PingProc {
// implements PlayableToBus
	classvar fadeShapes;

	var <>verbose 	= false;

	var <server, unit, tempBus;
	var weMadeDemBus	= false;		// = the proxyBus
	var proxyBus;
	var group, playGroup, fadeSynth;
	
	var <>fadeTime 	= 0.1;
	var <isFading		= false;

//	var mapPlayBusToMonitor;		// Bus: to which to play, to Group: group inside playGroup
	var mapPlayBusToGroup;			// Bus: to which to play, to Group: group inside playGroup

	var unitAttrMap;				// IdentityDictionary
	
	var pending		= false;
//	var pendingUnit;
	var pendingAttr;				// IdentityDictionary
	var pendingFadeTime;			// Number
	var pendingFadeType;			// Symbol: either of \none, \lin, \eqp
	var pendingDispose;			// Boolean

	var nw;
	var defCache;

	var neverPause	= false;
	
	var eqPFadeDefName, linFadeDefName;

	*initClass {
		fadeShapes	= IdentityDictionary.new;
		fadeShapes.put( \lin, 1 );
		fadeShapes.put( \sin, 3 );
		fadeShapes.put( \eqp, 4 );	// welch
	}

	// -------------- instantiation --------------

	*new { arg server;
		^super.new.prInitPingProc( server );
	}
	
	asString {
		^"PingProc( " ++ unit ++ " )";
	}

	prInitPingProc { arg argServer;
		server				= argServer ?? {ÊServer.default; };

		TypeSafe.checkArgClasses( thisMethod, [ server ], [ Server ], [ false ]);

		nw					= NodeWatcher.newFrom( server );
		defCache				= SynthDefCache.newFrom( server );
		unitAttrMap			= IdentityDictionary.new;
		CmdPeriod.add( this );
		
		this.prReinitialize;
	}
	
	// duplicates unit and attribute map ;
	// does not duplicate busses!
	duplicate {
		var dup;
		
		dup = this.class.new( server );
		dup.setUnit( this.getUnit.duplicate );
		dup.prAddAttr( unitAttrMap );
		^dup;
	}

	prAddAttr { arg attr;
		unitAttrMap.putAll( attr );
	}
	
	// -------------- public class methods --------------

//	*flushCache {
//		defSet.clear;
//	}
	
	// -------------- public instance methods --------------

	getAttr {
		^IdentityDictionary.newFrom( unitAttrMap );
	}
	
	setUnit {Êarg argUnit, disposeOld = false;
		if( unit.notNil and: { argUnit.notNil } and: { unit.getNumChannels != argUnit.getNumChannels }, {
			TypeSafe.methodError( thisMethod, "Trying to change # of channels from " ++
				unit.getNumChannels ++ " to " ++  argUnit.getNumChannels );
			^this;
		});
		if( unit.notNil, {
			unit.removeDependant( this );
			if( disposeOld && unit.disposed.not, {
				unit.dispose;
			});
		});
		unit = argUnit;
		if( unit.notNil, {
			unit.setGroup( playGroup );
			unit.addDependant( this );
		});
	}

	getUnit {
		^unit;
	}
	
	getGroup {
		^group;
	}
	
	crossFade {Êarg newAttr, fadeTime, fadeType = \eqp, disposeOld = true;
		fadeTime = fadeTime ?? this.fadeTime;
		
		TypeSafe.checkArgClasses( thisMethod, [ newAttr, fadeTime, fadeType ],
			[ Dictionary, Number, Symbol ], [ true, false, false ]);

		if( isFading, {
			if( verbose, { "PingProc : saving pending crossFade".postln; });
			if( newAttr.isNil.not, {ÊpendingAttr.putAll( newAttr );});
			pendingFadeTime	= fadeTime;
			pendingFadeType	= fadeType;
			pendingDispose	= disposeOld;
			pending			= true;
		},{ if( unit.notNil and: {Êunit.isPlaying }, {
			this.prCrossPlay( newAttr, fadeTime, fadeType, disposeOld );
		}, {
			this.applyAttr( newAttr );
			if( unit.notNil, {Êthis.play( fadeTime, fadeType );});
		})});
	}

	applyAttr {Êarg newAttr;
		var keyStr;
	
		if( newAttr.isNil.not, {
			unitAttrMap.putAll( newAttr );
			if( unit.notNil, {
				newAttr.keysValuesDo({ arg key, value;
					// better catch exceptions here
					try {
						keyStr = key.asString;
						unit.perform( ("set" ++ keyStr[ 0 ].toUpper ++ keyStr.copyToEnd( 1 )).asSymbol, value );
					}
					{ arg error;
						TypeSafe.methodError( thisMethod, error.errorString );
					};
				});
			});
		});
	}

	isRunning {
//		^true; // XXX currentSynth.isNil.not;
		^group.isRunning;
	}

	isPlaying {
		^group.isPlaying;
	}
	
	// call inside Routine!
	play { arg fadeTime, fadeType = \sin;
		var bndl;

		if( unit.isPlaying.not, {		
			if( this.getOutputBus.notNil, {
				if( this.cacheDefs( unit.getNumChannels ), {
					server.sync;
				});
				bndl = List.new;
				// always resume since group.run info might not yet be updated!
				bndl.add( group.runMsg( true ));
				bndl.add( playGroup.freeAllMsg );	// smarty killed fades
//				unit.setOutputBus( proxyBus );
				this.prFadeToBundle( bndl, 0, 1, fadeTime, fadeType, 2, false );
				unit.playToBundle( bndl );
				server.listSendBundle( nil, bndl );
			});			
		});
	}
	
	stop { arg fadeTime, fadeType = \sin;
		var bndl;

		if( unit.isPlaying, {
			// doneAction 7 = free this synth and all preceeding in the group
			// doneAction 5 = free this synth; if the preceding node is a group then do g_freeAll on it, else free it
			bndl = List.new;
			if( this.prFadeToBundle( bndl, 1, 0, fadeTime, fadeType, 5, true ), {
				server.listSendBundle( nil, bndl );
			}, {
				unit.stopToBundle( bndl );
				bndl.add( playGroup.freeAllMsg );	// smarty killed fades
				server.listSendBundle( nil, bndl );
			});
			isFading = true;
		});
	}
	
	setNeverPausing { arg onOff;
		neverPause = onOff;
		if( neverPause, {
			this.resume;
		});
	}

	isNeverPausing {
		^neverPause;
	}

	pause {
		if( neverPause.not, {
			if( verbose, { ("PingProc.addSynth : pause "++group).postln; });
//			"PAUSE".postln;
			group.run( false );
			this.changed( \paused );
		});
	}

	resume {
		if( verbose, { ("PingProc.addSynth : resume "++group).postln; });
		group.run( true );
	}

	dispose {
		if( unit.notNil, {
			if( unit.disposed.not, { unit.dispose; });
			unit = nil;
		});
		if( group.notNil, {Ênw.unregister( group ); });
		if( playGroup.notNil, {Ênw.unregister( playGroup ); });
		group.removeDependant( this );
		playGroup.removeDependant( this );
		this.prKillFades( false );
		this.killPending;
		group.free;
		group = nil;
		if( weMadeDemBus, {
			proxyBus.free;
			weMadeDemBus = false;
		});
		proxyBus = nil;
		tempBus.free;
		tempBus = nil;
		CmdPeriod.remove( this );
//"signal disposed".inform;
		this.changed( \disposed );
	}
	
	debugDump {
		("Group "++group).postln;
		("PlayGroup "++playGroup).postln;
		("FadeSynth "++fadeSynth).postln;
		("ProxyBus "++proxyBus).postln;
		("TempBus "++tempBus).postln;
	}
	
	getOutputBus {
		var numChannels;
		
		if( proxyBus.isNil, {
			if( unit.notNil, {
				numChannels	= unit.getNumChannels;
				if( numChannels > 0, {
					proxyBus	= Bus.audio( server, numChannels );
					tempBus	= Bus.audio( server, numChannels );
					if( proxyBus.notNil and: { tempBus.notNil }, {
						unit.setOutputBus( proxyBus );
						weMadeDemBus = true;
					}, {
						proxyBus.free;
						tempBus.free;
						proxyBus	= nil;
						tempBus	= nil;
						TypeSafe.methodError( thisMethod, "Bus allocator exhausted" );
					});
				}, {
					TypeSafe.methodError( thisMethod, "Unit returns illegal numChannels of " ++ numChannels );
				});
			});
		});
		^proxyBus;
	}
	
	setOutputBus { arg bus;
		var numChannels;

		if( weMadeDemBus, {
			this.stop;
			proxyBus.free;
			proxyBus		= nil;
			weMadeDemBus	= false;
		});
		if( unit.notNil, {
			numChannels	= unit.getNumChannels;
			if( numChannels > 0, {
				if( numChannels != bus.numChannels, {
					TypeSafe.methodError( thisMethod, "Cannot change # of channels (" ++ numChannels ++ " -> " ++ bus.numChannels ++ ")" );
					^this;
				});
				if( tempBus.isNil, {
					tempBus	= Bus.audio( server, numChannels );
					if( tempBus.isNil, {
						TypeSafe.methodError( thisMethod, "Bus allocator exhausted" );
						^this;
					});
				});
				proxyBus = bus;
				unit.setOutputBus( proxyBus );
			}, {
				TypeSafe.methodError( thisMethod, "Unit returns illegal numChannels of " ++ numChannels );
			});
		}, {
			if( tempBus.isNil, {
				tempBus	= Bus.audio( server, bus.numChannels );
				if( tempBus.isNil, {
					TypeSafe.methodError( thisMethod, "Bus allocator exhausted" );
					^this;
				});
			});
			proxyBus = bus;
		});
	}

	/**
	 *	@returns	(Boolean) true, if sync to server needed
	 */
	cacheDefs { arg numChannels;
		var def, defName, sync = false;
		
		defName = ("procFade" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			// shapes: 1 = linear, 3 = sine, 4 = welch
			def = SynthDef( defName, { arg bus = 0, dur = 1, start = 0, end = 1, shape = 1, doneAction = 2;
				var env, envGen, inp;
				
				inp		= In.ar( bus, numChannels );
				env		= Env.new([ start, end ], [ durÊ], 1 ).asArray;
				env[ 6 ]	= shape;
				envGen	= EnvGen.ar( env, doneAction: doneAction );
				ReplaceOut.ar( bus, inp * envGen );
			});
			sync = sync || defCache.add( def );
		});
		defName = ("procXFade" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			// shapes: 1 = linear, 3 = sine, 4 = welch
			def = SynthDef( defName, { arg busA, busB, dur = 1, shape = 1, doneAction = 2;
				var envA, envB, envGenA, envGenB, inpA, inpB;
				
				inpA		= In.ar( busA, numChannels );
				inpB		= In.ar( busB, numChannels );
				envA		= Env.new([ 1, 0 ], [ durÊ], 1 ).asArray;
				envA[ 6 ]	= shape;
				envB		= Env.new([ 0, 1 ], [ durÊ], 1 ).asArray;
				envB[ 6 ]	= shape;
				envGenA	= EnvGen.ar( envA, doneAction: doneAction );
				envGenB	= EnvGen.ar( envB, doneAction: 0 );
				ReplaceOut.ar( busB, (inpA * envGenA) + (inpB * envGenB) );
			});
			sync = sync || defCache.add( def );
		});

		^sync;
	}

	killPending {
//		pendingUnit	= nil;
		pendingAttr	= IdentityDictionary.new;
		pending		= false;
	}

	// -------------- private methods --------------

	prCrossPlay { arg newAttr, fadeTime, fadeType, disposeOld;
		var oldUnit, bndl, bndl2, upd, bndlTest;
		
		fadeTime = fadeTime ?? this.fadeTime;

//("Fading "++fadeTime).postln;

		oldUnit	= unit;
//		unit		= unit.duplicate;
		this.setUnit( unit.duplicate, false );	// handles dependancies
		this.applyAttr( newAttr );
		
		bndl		= List.new;
		bndl2	= List.new;
// XXX
//bndlTest = List.new;
//oldUnit.stopToBundle( bndlTest );
		oldUnit.setOutputBusToBundle( bndl, tempBus );
		
		if( disposeOld, {
//			("---- UL for "++oldUnit).inform;
			upd = UpdateListener.newFor( oldUnit, { arg obj, what;
				case { what === \unitPlaying }
				{
					upd.removeFrom( obj );
					if( obj.disposed.not, {
						if( verbose, { ("PingProc.unitPlaying : disposing " ++ obj).postln; });
						obj.dispose;
					});
				}
				{ what === \unitDisposed }
				{
					upd.removeFrom( obj );
				};
			});
		});
		
		isFading = true;
		if( fadeTime > 0, {
			fadeSynth = Synth.basicNew( ("procXFade" ++ unit.getNumChannels).asSymbol, server );
			nw.register( fadeSynth );
			fadeSynth.addDependant( this );
			bndl.add( fadeSynth.newMsg( playGroup,
				[ \busA, tempBus.index, \busB, proxyBus.index, \dur, fadeTime, \shape,
				  fadeShapes[ fadeType ] ?? 1, \doneAction, 2 ], \addAfter ));
		});
		
		oldUnit.stopToBundle( bndl2 );
		unit.playToBundle( bndl );
//bndl.add([ '/n_set', playGroup.nodeID, \schoko, 0 ]);
// XXX
//bndlTest = List.new;
//bndlTest.add([ '/n_set', playGroup.nodeID, \schoko, 1 ]);
//bndlTest.postln;

//if( verbose, {
//	("time = "++server.latency++"; bundle = "++bndl).postln;
//	("time = "++(server.latency+fadeTime)++"; bundle = "++bndl2).postln;
//});
		server.listSendBundle( server.latency, bndl );
//		server.listSendBundle( server.latency + 0.1, bndlTest );
		server.listSendBundle( server.latency + fadeTime, bndl2 );

// XXX
//		server.listSendBundle( server.latency, bndlTest );
//		server.listSendBundle( server.latency + 2, bndl );
//		server.listSendBundle( server.latency + 2 + fadeTime, bndl2 );
	}

	prFadeToBundle { arg bndl, start, end, fadeTime, fadeType, doneAction, smartKiller;
		fadeTime = fadeTime ?? this.fadeTime;

		this.prKillFades( smartKiller );

		isFading = true;
		if( fadeTime > 0, {
			fadeSynth = Synth.basicNew( ("procFade" ++ unit.getNumChannels).asSymbol, server );
			nw.register( fadeSynth );
			fadeSynth.addDependant( this );
			bndl.add( fadeSynth.newMsg( playGroup,
				[ \bus, proxyBus.index, \start, start, \end, end, \dur, fadeTime, \shape,
				  fadeShapes[ fadeType ] ?? 1, \doneAction, doneAction ], \addAfter ));
			^true;
		}, {
			^false;
		});
	}

//	prNewClearUnitMap {
//		TypeSafe.methodWarn( thisMethod, "N.Y.I." );
//	}

	prKillFades { arg smartKiller = false;
		if( isFading, {
			if( fadeSynth.notNil, {
				nw.unregister( fadeSynth );
				fadeSynth.removeDependant( this );
				if( smartKiller, {
					fadeSynth.moveToTail( playGroup );
				}, {
					fadeSynth.free;
				});
				fadeSynth	= nil;
			});
			isFading	= false;
		});
	}
	
	prProcessPending {
		if( pending, {
			if( unit.notNil, {
				if(Êunit.isPlaying, {
//					"KIEKA".postln;
					this.prCrossPlay( pendingAttr, pendingFadeTime, pendingFadeType, pendingDispose );
				}, {
//					"KOOKA".postln;
					this.applyAttr( pendingAttr );
					this.play( pendingFadeTime, pendingFadeType );
				});
			}, {
				this.applyAttr( pendingAttr );
			});
			this.killPending;
			^true;
		}, {
			fadeSynth.removeDependant( this );
			fadeSynth	= nil;
			isFading = false;
			^false;
		});
	}
	
	prReinitialize {
		var bndl;

//		this.prNewClearUnitMap;
		
//		mapPlayBusToMonitor	= IdentityDictionary.new;
		mapPlayBusToGroup		= IdentityDictionary.new;
		
		fadeSynth.removeDependant( this );
		fadeSynth				= nil;
		isFading 				= false;
		this.killPending;

		group				= Group.basicNew( server );
//		fadeGroup				= Group.basicNew( server );
		playGroup				= Group.basicNew( server );
		nw.register( group );
//		nw.register( fadeGroup );
		nw.register( playGroup );
//		fadeGroup.addDependant( this );
//		if( verbose, {
			group.addDependant( this );
			playGroup.addDependant( this );
//		});

		bndl					= List.new;
// XXX
//		bndl.add( group.newMsg( target.asTarget, addAction ));
		bndl.add( group.newMsg );
		if( neverPause.not, { bndl.add( group.runMsg( false ));});
//		bndl.add( fadeGroup.newMsg( group, addAction: \addToTail ));
//		bndl.add( fadeGroup.runMsg( false ));
		bndl.add( playGroup.newMsg( group, addAction: \addToTail ));
		server.listSendBundle( nil, bndl );
	}

	// -------------- quasi-interface methods --------------

	update {Êarg obj, status;
	
		case { status === \unitPlaying }
		{
			if( obj === unit, {
//				"AAAA".inform;
				if( fadeSynth.isNil, {
//					"BBBB".inform;
					if( this.prProcessPending, {
						^this;
					});
				});
				if( unit.isPlaying.not, {
					this.prKillFades( false );
					this.pause;
//					"HIER".inform;
				});
//			}, {
//				if( obj.disposed.not, {
//					if( verbose, { ("PingProc.unitPlaying : disposing " ++ obj).postln; });
//					obj.dispose;
//				});
			});
		}
		{ status === \n_off }
		{
			if( verbose, { ("PingProc.n_off : " ++ obj.nodeID).postln; });
		}
		{ status === \n_on }
		{
			if( verbose, { ("PingProc.n_on : " ++ obj.nodeID).postln; });
		}
		{ status === \n_go }
		{
			if( verbose, { ("PingProc.n_go : " ++ obj.nodeID).postln; });
		}
		{ status === \n_end }
		{
			if( verbose, { ("PingProc.n_end : " ++ obj.nodeID).postln; });
			case { obj === fadeSynth }
			{
				if( this.prProcessPending.not, {
					if( unit.isPlaying.not, {
						this.pause;
//						"HUHU".inform;
					});
				});
			}
			{ obj === group }
			{
				TypeSafe.methodWarn( thisMethod, "Main group died!!" );
				group = Group( server );
				playGroup.moveToHead( group );
			}
			{ obj === playGroup }
			{
				TypeSafe.methodWarn( thisMethod, "Play group died!!" );
				playGroup = Group( group );
			};
		};
	}
	
	cmdPeriod {
		this.prReinitialize;
	}

	// -------------- PlayableToBus interface --------------

	// Note: fucking Monitor class is unusable, looses nodes
	// when triggering play / stop too fast. returning to
	// original concept from SynthProxy

	// has to be called from inside routine
	addPlayBus { arg bus, vol = 1.0;
		var synth, grp, bndl, bndl2, numChannels, def, defName;

		TypeSafe.checkArgClasses( thisMethod, [ bus, vol ], [ Bus, Number ], [ false, false ]);

		if( mapPlayBusToGroup.includesKey( bus ), {
			TypeSafe.methodWarn( thisMethod, "Bus already playing" );
		}, {
			if( this.getOutputBus.notNil, {
				grp 			= Group.basicNew( server );
				mapPlayBusToGroup.put( bus, grp );
				bndl			= List.new;
//				bndl.add( grp.newMsg( playGroup.asTarget, addAction: \addToTail ));
				bndl.add( grp.newMsg( group.asTarget, addAction: \addToTail ));
	
				if( bus.numChannels == proxyBus.numChannels, {   // use one synth
					numChannels	= bus.numChannels;
					defName		= ("pingProcPlay" ++ numChannels).asSymbol;
					if( defCache.contains( defName ).not, {
						def = SynthDef( defName, { arg aInBus, aOutBus, vol = 1.0;
							OffsetOut.ar( aOutBus, In.ar( aInBus, numChannels ) * vol );
						}, [ nil, nil, 0.1 ]);
						defCache.add( def );
						server.sync;
					});
					synth = Synth.basicNew( defName, server );
					bndl.add( synth.newMsg( grp,
						[ \aInBus, proxyBus.index, \aOutBus, bus.index, \vol, 0.0 ]));
				}, {		// use multiple duplicated or wrapped monos
					defName		= \pingProcPlay1;
					if( defCache.contains( defName ).not, {
						def = SynthDef( defName, { arg aInBus, aOutBus, vol = 1.0;
							OffsetOut.ar( aOutBus, In.ar( aInBus, 1 ) * vol );
						}, [ 0, 0, 0.1 ]);
						defCache.add( def );
						server.sync;
					});
					numChannels 	= max( bus.numChannels, proxyBus.numChannels );
					numChannels.do({ arg ch;
						synth = Synth.basicNew( defName, server );
						bndl.add( synth.newMsg( grp,
							[ \aInBus, proxyBus.index + (ch % proxyBus.numChannels),
							  \aOutBus, bus.index + (ch % bus.numChannels), \vol, 0.0 ]));
					});
				});
				bndl2 = List.new;
				bndl2.add( grp.setMsg( \vol, vol ));	// i.e. 100ms lagged fade-in
				server.listSendBundle( server.latency, bndl );
				server.listSendBundle( server.latency + 0.001, bndl2 );
			});
		});
	}

//	addPlayBus { arg bus, vol = 1.0;
//		var mon;
//
//		TypeSafe.checkArgClasses( thisMethod, [ bus, vol ], [ Bus, Number ], [ false, false ]);
//
//		if( mapPlayBusToMonitor.includesKey( bus ), {
//			TypeSafe.methodWarn( thisMethod, "Bus already playing" );
//		}, {
//			if( this.getOutputBus.notNil, {
//				mon	= Monitor.new;
//				mon.play( proxyBus.index, proxyBus.numChannels, bus.index, bus.numChannels,
//					group, false, vol, addAction: \addToTail );
//				mapPlayBusToMonitor.put( bus, mon );
//			});
//		});
//	}
	
	removePlayBus { arg bus;
		var grp, bndl, bndl2;
		
		grp = mapPlayBusToGroup.removeAt( bus );
		if( grp.isNil, {
			TypeSafe.methodWarn( thisMethod, "Bus was not registered" );
		}, {
			bndl = List.new;
			bndl.add( grp.setMsg( \vol, 0.0 ));	// i.e. 100ms lagged fade-out
			bndl2 = List.new;
			bndl2.add( grp.freeMsg );
			server.listSendBundle( server.latency, bndl );
			server.listSendBundle( server.latency + 0.12, bndl2 );
		});
	}

//	removePlayBus { arg bus;
//		var mon;
//		
//		mon = mapPlayBusToMonitor.removeAt( bus );
//		if( mon.isNil, {
//			TypeSafe.methodWarn( thisMethod, "Bus was not registered" );
//		}, {
//			mon.stop;
//		});
//	}

	setPlayBusVolume { arg bus, vol;
		var grp;
		
		if( bus.isNil, { grp = playGroup; }, { grp = mapPlayBusToGroup.at( bus ); });
		if( grp.isNil, {
			TypeSafe.methodError( thisMethod, "Unknown bus" );
		}, {
			if( verbose, { ("PingProc.setVolume : group = " ++ grp.nodeID ++
						  ", vol = " ++ vol).postln; });
			grp.set( \vol, vol );
		});
	}
	
//	setPlayBusVolume { arg bus, vol;
//		var mon;
//		
//		mon = mapPlayBusToMonitor.at( bus );
//		if( mon.isNil, {
//			TypeSafe.methodError( thisMethod, "Unknown bus" );
//		}, {
////			if( verbose, { ("PingProc.setPlayBusVolume : group = " ++ grp.nodeID ++
////						  ", vol = " ++ vol).postln; });
//			mon.vol = vol;
//		});
//	}
}