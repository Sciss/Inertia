/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe
 *
 *	@version	0.1, 13-May-06
 *	@author	Hanns Holger Rutz
 */
PingServer {
	var resp;
	var <players;		// Dictionary : String nick to PingPlayer
	var dumpMode = 0;
	var syncTask;
	var syncStopTime;

	*new {
		^super.new.prInitPingServer;
	}
	
	prInitPingServer {
		resp = OSCresponderNode( nil, '/fritzel', { arg time, resp, msg; // , addr;
			var cmd, selector;
			
			case {ÊdumpMode === 1 }
			{
				msg.postln;
			};
			
			cmd 		= msg[ 1 ].asSymbol;
			selector	= ("oscCmd_" ++ msg[ 1 ]).asSymbol;
			if( this.respondsTo( selector ), {
				this.perform( selector, msg.copyToEnd( 2 ));
			}, {
				("PingServer-receive : Illegal OSC command '" ++ cmd ++ "'" ).error;
			});
		});
		
		syncTask = Task({
			(syncStopTime - Main.elapsedTime).max( 0 ).wait;
			"BANG!".inform;
		});
		
		players = IdentityDictionary.new;
	}
	
//	prReplyIllegalCmd { arg addr, cmd;
//			addr.sendMsg( '/brutzel', 'failed', cmd );
//		});
//	}
	
	run {
		resp.add;
	}
	
	stop {
		resp.remove;
	}
	
	clear {
		this.stop;
		players = IdentityDictionary.new;
	}
	
	// -------------- OSC commands --------------
	
	oscCmd_join { arg msg;		// <nick>, <IP>, <port>
		var nick, player, replyMsg;

		nick = msg[ 0 ].asSymbol;
		if( players.includesKey( nick ), {
			TypeSafe.methodWarn( thisMethod, "Player '" ++ nick ++ "' already registered" );
		}, {
			player	= PingPlayer( nick, NetAddr( msg[ 1 ].asString, msg[ 2 ].asInteger ));
			replyMsg	= [ '/brutzel', 'join', nick ];
			players.put( nick, player );
			players.do({ arg otherP;
				otherP.listSendMsg( replyMsg );
				if( player !== otherP, { player.sendMsg( '/brutzel', 'join', otherP.nick );});
			});
		});
	}

	oscCmd_leave { arg msg;		// <nick>
		var nick, player, replyMsg;
		
		nick 	= msg[ 0 ].asSymbol;
		player	= players.removeAt( nick );
		if( player.isNil, {
			TypeSafe.methodWarn( thisMethod, "Player '" ++ nick ++ "' not found" );
		}, {
			replyMsg	= [ '/brutzel', 'leave', nick ];
			players.do({ arg otherP;
				otherP.listSendMsg( replyMsg );
			});
		});
	}

	oscCmd_sync { arg msg;		// <nick> <absTime>
		var replyMsg;
		
		if( syncTask.isPlaying, {
			TypeSafe.methodWarn( thisMethod, "Recent sync not yet expired" );
		}, {
			// ...
			// well, we have to find a different way of representing the time
		});
	}

	oscCmd_addproc { arg msg;	// <nick> <procName>
		var nick, player, procName, proc, replyMsg;
		
		nick 	= msg[ 0 ].asSymbol;
		player	= players[ nick ];
		procName	= msg[ 1 ].asSymbol;
		if( player.isNil, {
			TypeSafe.methodWarn( thisMethod, "Player '" ++ nick ++ "' not found" );
		}, {
			if( player.procs.includesKey( procName ), {
				TypeSafe.methodWarn( thisMethod, "Proc '" ++ procName ++ "' for player '" ++ nick ++ "' already registered" );
			}, {
				proc		= PingProcess( procName );
				replyMsg	= [ '/brutzel', 'addproc', nick, procName ];
				player.procs.put( procName, proc );
				players.do({ arg otherP;
					otherP.listSendMsg( replyMsg );
				});
			});
		});
	}

	oscCmd_delproc { arg msg;	// <nick> <procName>
		var nick, player, procName, proc, replyMsg;
		
		nick 	= msg[ 0 ].asSymbol;
		player	= players[ nick ];
		procName	= msg[ 1 ].asSymbol;
		if( player.isNil, {
			TypeSafe.methodWarn( thisMethod, "Player '" ++ nick ++ "' not found" );
		}, {
			proc = player.procs.removeAt( procName );
			if( proc.isNil, {
				TypeSafe.methodWarn( thisMethod, "Proc '" ++ procName ++ "' for player '" ++ nick ++ "' not found" );
			}, {
				replyMsg	= [ '/brutzel', 'delproc', nick, procName ];
				players.do({ arg otherP;
					otherP.listSendMsg( replyMsg );
				});
			});
		});
	}
	
	dumpOSC { arg mode = 1;
		dumpMode = mode;
	}
}

PingPlayer {
	var <nick, <addr, <procs;

	*new { arg nick, addr;
		^super.new.prInitPingPlayer( nick, addr );
	}
	
	prInitPingPlayer { arg argNick, argAddr;
		nick		= argNick;
		addr		= argAddr;
		procs	= IdentityDictionary.new;
	}
	
	sendMsg { arg ... args;
		^addr.sendMsg( *args );
	}

	listSendMsg { arg msg;
		^addr.sendMsg( *msg );
	}
}

PingProcess {
	var <name;

	*new {Êarg name;
		^super.new.prInitPingProcess( name );
	}
	
	prInitPingProcess { arg argName;
		name = argName;
	}
}