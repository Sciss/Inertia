/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies:	TypeSafe, MasterUnit, OutputUnit, PingProc,
 *						SoloManager2
 *
 *	@version	0.14, 19-Oct-06
 *	@author	Hanns Holger Rutz
 */
Ping {
	classvar <dataFolder;
	
	// these are only effective until start is called!
	var <>soundCard 			= "Mobile I/O 2882 [2600]";
//	var <>soundCard 			= "MOTU 828";
	var <>numInputBusChannels	= 18;
	var <>numOutputBusChannels	= 16;
	var <>numAudioBusChannels	= 512; // 256;	// critical!!
	var <>soloIndex			= 0;
	var <>soloChannels			= 2;
	var <>masterIndex			= 2;
	var <>masterChannels		= 4;
	var <>auxIndex			= 6;
	var <>auxChannels			= 2;
	var <>useAux				= false; // true;

	var <server;
	var <solo;
	var <master;
	var <masterBus;
	var <aux;
	
	var procSlotToChain;		// IdentityDictionary : slot -> proc[]
	var procChainToSlot;		// IdentityDictionary : proc[] -> slot
	var procToChain;			// IdentityDictionary : proc -> proc[]

	var netResp;
	
	classvar <fritzelAddrs;

	*initClass {
		dataFolder	= "~/scwork/ping".standardizePath ++ "/";
		fritzelAddrs	= [
			NetAddr( "192.168.0.2", 57120 ),	// sciss
			NetAddr( "192.168.0.3", 25000 ),	// ludger
			NetAddr( "192.168.0.4", 25000 ),	// markus
			NetAddr( "192.168.0.5", 25000 )	// johannes
		];
	}
	
	*new { arg server;
		^super.new.prInitPing( server );
	}
	
	prInitPing { arg argServer;
		server 			= argServer ?? { Server.default; };
		procSlotToChain	= IdentityDictionary.new;
		procChainToSlot	= IdentityDictionary.new;
		procToChain		= IdentityDictionary.new;
("useAux = "++useAux).postln;
	}
	
	addProcChain { arg procChain, slot;
		procSlotToChain.put( slot, procChain );
		procChainToSlot.put( procChain, slot );
		procChain.do({ arg proc;
			procToChain.put( proc, procChain );
		});
	}
	
	getProcChain { arg slot;
		^procSlotToChain[ slot ];
	}
	
	getProcChainSlot { arg procChain;
		^procChainToSlot[ procChain ];
	}

	getProcSlot { arg proc;
		var procChain;
		procChain = procToChain[ proc ];
		if( procChain.notNil, {
			^procChainToSlot[ procChain ];
		}, {
			^nil;
		});
	}

	removeProcChain { arg procChain;
		var slot;
		
		slot = procChainToSlot.removeAt( procChain );
		procSlotToChain.removeAt( slot );
		procChain.do({ arg proc;
			procToChain.removeAt( proc );
		});
	}

	removeProcChainAt { arg slot;
		var procChain;
		
		procChain = procSlotToChain.removeAt( slot );
		procChainToSlot.removeAt( procChain );
		procChain.do({ arg proc;
			procToChain.removeAt( proc );
		});
	}
	
	createProcForUnit { arg unit;
		var proc;
		proc = PingProc( server );
		proc.setUnit( unit );
		^proc;
	}
	
	// has to be called from inside routine!
	createOutputForProc { arg proc;
		var oProc, unit;
		unit		= OutputUnit.new( server );
		oProc	= this.createProcForUnit( unit );
		unit.setInputBus( proc.getOutputBus );
		oProc.getGroup.moveAfter( proc.getGroup );
// XXX
//		oProc.setOutputBus( masterBus );
		oProc.addPlayBus( masterBus );
		server.sync;
		oProc.play;
		^oProc;
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
	
//	aux_ {Êarg val;
//		"GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG".postln;
//		// Error.new.throw;
//	}
	
	prInitAfterBoot {
		solo 	= SoloManager2( server );
// solo.verbose = true;
		solo.setOutputBus( Bus( \audio, soloIndex, soloChannels, server ));
		solo.setSpeechCuePath( Ping.dataFolder ++ "SpeechCuesPlain.aif" );
		solo.addSpeechCues( Ping.dataFolder ++ "speech.rgn" );
		solo.setSpeechVol( 0.2 );
		
		if( useAux, {
			aux		= SoloManager2( server );
			aux.setOutputBus( Bus( \audio, auxIndex, auxChannels, server ));
		});

		masterBus	= Bus.audio( server, 3 );
		master 	= MasterUnit.new( server );
		master.setInputBus( masterBus );
		master.setOutputBus( Bus( \audio, masterIndex, masterChannels ));
		
		// necessary to wait for synth defs
		server.sync;
		
		master.play;
		
		// ---------- set up OSC responders for network schoko
		
		netResp = OSCresponderNode( nil, '/fritzel', { arg time, resp, msg;
			var what;
			
			(Ping.hhmmss + msg.asString).inform;

			what = msg[ 1 ];
			
			case
			{ what === \sync  }
			{
				this.changed( \fritzelSync, msg[ 2 ]);
			}
			{ what === \terminate  }
			{
				this.changed( \fritzelTerminate, msg[ 2 ]);
			}
			{ what === \test  }
			{
				// well ...
			}
			{
				("Illegal OSC input : "++msg).warn;
			};
		}).add;
	}
	
	*hhmmss { arg date;
		var s;
		date	= date ?? {ÊDate.localtime };
		s	= date.secStamp;
		^(s.copyRange( 0, 1 ) ++ ":" ++ s.copyRange( 2, 3 ) ++ ":" ++ s.copyRange( 4, 5 ));
	}

	fritzelSendSync { arg time = 90;
		Ping.prFritzelSend( \sync, time );
	}

	fritzelSendTerminate { arg time = 90;
		Ping.prFritzelSend( \terminate, 90 );
	}

	*fritzelSendTest {
		this.prFritzelSend( \test );
	}
	
	*prFritzelSend { arg ... msg;
		fritzelAddrs.do({ arg addr;
			addr.sendMsg(  '/fritzel', *msg );
		});			
	}
	
	terminate {
		netResp.remove;
		master.dispose;
		solo.dispose;
		masterBus.free;
	}

	*test {
		var win, flow, cp, pompe, cursor, pompeWin;
		 
		pompeWin	= JSCWindow( "Pompe", Rect( 20, 80, 1024, 768 ));
		pompe 	= Pompe( pompeWin, pompeWin.view.bounds );
		cursor	= PompeCursor( pompe, nil, Color.red );
		
		win = JSCWindow( "Tape Unit Test" );
		flow = FlowLayout( win.view.bounds );
		win.view.decorator = flow;
		
//		JSCButton( win, Rect( 0, 0, 80, 24 ))
//			.states_([[ "New Tape" ]])
//			.action_({ arg b;
//				var path, block;
//				
//				path  = PompePath( pompe );
////				block = PompeBlock( path, nil, PingProc( nil, TapeUnit, [ \path -> "/Users/rutz/Music/DatNostaDub.aif" ]), "Tape" ++ rrand( 1, 10 ), 0 );
//				block = PompeBlock( path, nil, PingProc( nil, TapeUnit, [ \path -> "/Volumes/Weston/ping/recordings/k77_060519/K77_060519_EditA_St16b.aif" ]), "Tape" ++ rrand( 1, 10 ), 0 );
//				cursor.setBlock( block );
//			});
//
		
		JSCButton( win, Rect( 0, 0, 80, 24 ))
			.states_([[ "New Unit" ]])
			.action_({ arg b;
				var path, block;
				
				path  = PompePath( pompe );
//				block = PompeBlock( path, nil, PingProc( nil, TapeUnit, [ \path -> "/Users/rutz/Music/DatNostaDub.aif" ]), "Tape" ++ rrand( 1, 10 ), 0 );
				block = PompeBlock( path, nil, PingProc( nil, TapeUnit, [ \path -> "/Volumes/Weston/ping/recordings/k77_060519/K77_060519_EditA_St16b.aif" ]), "Tape" ++ rrand( 1, 10 ), 0 );
				cursor.setBlock( block );
			});

		
		JSCButton( win, Rect( 0, 0, 50, 24 ))
			.states_([[ "Csr Up" ]])
			.action_({ arg b;
				var idx, path, block, finished;
				
				finished = false;
				if( cursor.block.notNil, {
					path = cursor.block.path;
					while({ finished.not && path.notNil }, {
						idx = pompe.indexOf( path );
						if( idx > 0, {
							path	= pompe.getPathAt( idx - 1 );
							idx	= path.getNumBlocks - 1;
							if( idx >= 0, {
								block = path.getBlockAt( idx );
								cursor.setBlock( block );
								finished = true;
							});
						}, {
							finished = true;
						});
					});
				})
			});
		
		JSCButton( win, Rect( 0, 0, 50, 24 ))
			.states_([[ "Csr Dn" ]])
			.action_({ arg b;
				var idx, path, block, finished;
				
				finished = false;
				if( cursor.block.notNil, {
					path = cursor.block.path;
					while({ finished.not && path.notNil }, {
						idx = pompe.indexOf( path );
						if( idx < (pompe.getNumPaths - 1), {
							path	= pompe.getPathAt( idx + 1 );
							idx	= path.getNumBlocks - 1;
							if( idx >= 0, {
								block = path.getBlockAt( idx );
								cursor.setBlock( block );
								finished = true;
							});
						}, {
							finished = true;
						});
					});
				})
			});
		
		JSCButton( win, Rect( 0, 0, 50, 24 ))
			.states_([[ "Play" ], [ "Stop", Color.white, Color.green( 0.5 )]])
			.action_({ arg b;
				var block, synth;
				
				block = cursor.block;
				if( block.notNil, {
					fork {
						if( block.proc.isPlaying, {
							block.proc.stop;
						}, {
							block.proc.play;
							synth = Synth.basicNew( \test );
							SynthDef( \test, {
								OffsetOut.ar( 0, In.ar( block.proc.getOutputBus.index, block.proc.getOutputBus.numChannels ));
							}).send( block.proc.server, synth.newMsg( block.proc.getGroup, nil, \addToTail ));
						});
					};
				});
			});
		
		JSCButton( win, Rect( 0, 0, 50, 24 ))
			.states_([[ "Solo" ], [ "Solo", nil, Color.yellow( 0.5 )]])
			.action_({ arg b;
				var block, synth;
				
				block = cursor.block;
				if( block.notNil, {
					fork {
						if( block.proc.isPlaying, {
							block.proc.stop;
						}, {
							block.proc.play;
							synth = Synth.basicNew( \test );
							SynthDef( \test, {
								OffsetOut.ar( 0, In.ar( block.proc.getOutputBus.index, block.proc.getOutputBus.numChannels ));
							}).send( block.proc.server, synth.newMsg( block.proc.getGroup, nil, \addToTail ));
						});
					};
				});
			});
		
		win.front;
		pompeWin.front;
	}

	*loadRegions { arg fileName;
		var f, start, stop, name, stakes;
		
		stakes = List.new;
		
		try {
			f = File( fileName, "rb" );
			while({ f.pos < f.length }, {
				start	= f.getInt32;
				stop		= f.getInt32;
				name		= f.getPascalString;
				stakes.add( RegionStake( Span( start, stop ), name ));
			});
			f.close;
			^stakes;
		}
		{ arg error;
			error.reportError;
			^nil;
		};
	}

	*saveRegions { arg fileName, stakes;
		var f, pos, name;
				
		try {
			f = File( fileName, "wb" );
			stakes.do({ arg stake, idx;
				f.putInt32( stake.getSpan.start );
				f.putInt32( stake.getSpan.stop );
				f.putPascalString( stake.name.asString );
			});
			f.close;
			^true;
		}
		{ arg error;
			error.reportError;
			^false;
		};
	}
}

Pompe {
	var <panel;
	var nodeAllocator;
	var collPaths;

	*new { arg parent, bounds, argServer, id; 
		^super.new.prInitPompe( parent, bounds, argServer, id );
	}
	
	indexOf { arg path;
		^collPaths.indexOf( path );
	}
	
	getNumPaths {
		^collPaths.size;
	}
	
	getPathAt {Êarg idx;
		^collPaths[ idx ];
	}
	
	prInitPompe { arg parent, bounds, argServer, id;
		panel		= JSCPlugView( parent, bounds, argServer, id, 'PompePanel' );
		nodeAllocator	= NodeIDAllocator.new;
		collPaths		= List.new;
	}

	nextNodeID {
		^nodeAllocator.alloc
	}
	
	addPath { arg path;
		panel.createPath( path.id, collPaths.size );
		collPaths.add( path );
	}
}

PompePath {
	var <pompe, <id;
	var collBlocks;

	*new { arg pompe, id;
		^super.new.prInitPompePath( pompe, id );
	}
	
	prInitPompePath {Êarg argPompe, argID;
		pompe		= argPompe;
		id			= argID ?? { pompe.nextNodeID; };
		collBlocks	= List.new;
		pompe.addPath( this );
	}
	
	addBlock { arg block, name, icon;
		pompe.panel.createBlock( this.id, block.id, collBlocks.size, name, 0 );
		collBlocks.add( block );	
	}

	indexOf { arg block;
		^collBlocks.indexOf( block );
	}
	
	getNumBlocks {
		^collBlocks.size;
	}
	
	getBlockAt {Êarg idx;
		^collBlocks[ idx ];
	}
}

PompeBlock {
	var <path, <id, <proc, <name; 

	*new { arg path, id, proc, name, icon;
		^super.new.prInitPompePath( path, id, proc, name, icon );
	}
	
	prInitPompePath {Êarg argPath, argID, argProc, argName, icon;
		path	= argPath;
		id	= argID ?? { path.pompe.nextNodeID; };
		proc	= argProc;
		name	= argName;
		path.addBlock( this, name ?? 'proc', icon ?? 0 );
	}
}

PompeCursor {
	var <pompe, <id, <block;

	*new { arg pompe, id, color;
		^super.new.prInitPompePath( pompe, id, color );
	}
	
	dispose {
		pompe.panel.deleteCursor( id );
	}
	
	prInitPompePath {Êarg argPompe, argID, color;
		pompe	= argPompe;
		id		= argID ?? { pompe.nextNodeID; };
		pompe.panel.createCursor( id, color );
	}
	
	setBlock { arg argBlock;
		block = argBlock;
		pompe.panel.setCursor( id, block.notNil.if({ block.id }, -1 ));
	}
}