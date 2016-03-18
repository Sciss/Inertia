/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe
 *
 *	Basically a copy-and-paste from Server and ServerPlusGUI
 *
 *	Changelog:
 *		30-Jun-06		added setTarget
 *
 *	@version	0.11, 30-Jun-06
 *	@author	Hanns Holger Rutz
 *
 *	@todo	peak meter
 *	@todo	timer task : should handle pause / resume
 *	@todo	isRecording method
 */
SimpleRecorder {
	var <server;
	var buf, <node, <>headerFormat = "aiff", <>sampleFormat = "float"; 	var <channelOffset		= 0;
	var <numChannels		= 2;
	var <>folder			= "recordings/";
	var window;
	var <isPrepared		= false;
	var recentPath		= nil;

	var target;
	var targetAddAction	= \addToTail;

	classvar headerSuffix;
	
	*initClass {
		headerSuffix		= IdentityDictionary.new;
		headerSuffix.put( \aiff, "aif" );
		headerSuffix.put( \next, "au" );
		headerSuffix.put( \wav, "wav" );
		headerSuffix.put( \ircam, "irc" );
		headerSuffix.put( \raw, "raw" );
	}

	*new { arg server;
		^super.new.prInitRecorder( server );
	}
	
	prInitRecorder { arg argServer;
		server = server ?? Server.default;
	}

	revealInFinder {
		var path;
		
		if( recentPath.notNil, {
			path = PathName( recentPath );
			unixCmd( "osascript -e 'tell application \"Finder\"' -e activate -e 'open location \"file:\/\/" ++ path.pathOnly ++
				"\"' -e 'select file \"" ++ path.fileName ++ "\" of folder of the front window' -e 'end tell'" );
		}, {
			TypeSafe.methodError( thisMethod, "Soundfile has not yet been specified" );
		});
	}
	
	channelOffset_ { arg off;
		channelOffset = off;
		this.changed( \channelOffset );
	}

	numChannels_ { arg num;
		numChannels = num;
		this.changed( \numChannels );
	}
	
	setTarget { arg group, addAction = \addToTail;
		target			= group;
		targetAddAction	= addAction;

		if( node.notNil, {
			case { addAction === \addToTail }
			{
				node.moveToTail( target );
			}
			{ addAction === \addToHead }
			{
				node.moveToHead( target );
			}
			{ addAction === \addBefore }
			{
				node.moveBefore( target );
			}
			{ addAction === \addAfter }
			{
				node.moveAfter( target );
			};
		});
	}

	record {
		if( buf.isNil, {
			TypeSafe.methodWarn( thisMethod, "Please execute prepareForRecord before recording" );
		}, {
			if( node.isNil, {
				node = Synth( "simpleRecorder" ++ buf.numChannels,
					[ \i_buf,  buf.bufnum, \i_bus, channelOffset ], target ?? { RootNode( server )}, targetAddAction );
				this.changed( \started );
			}, {
				node.run( true );
				this.changed( \resumed );
			});
			"Recording".postln;
		});
	}
	
	prepareAndRecord { arg path;
		fork {
			if( this.prepare( path ), {
				server.sync;
				this.record;
			});
		};
	}

	pause {
		if( node.notNil, {
			node.run( false );
			"Paused".postln;
			this.changed( \paused );
		}, {
			"Not Recording".warn;
		});
	}
	
	stop {
		if( node.notNil, { 
			node.free;
			node = nil;
			"Recording Stopped".postln;
		});
		if( buf.notNil, {
			buf.close({ arg buf; buf.free; });
			buf = nil; 
			isPrepared = false;
			this.changed( \stopped );
		}, {
			"Not Recording".warn;
		});
	}
	
	prepare { arg path;
		if( path.isNil, {
			path = folder ++ "SC_" ++ Date.localtime.stamp ++ "." ++ headerSuffix[ headerFormat.asSymbol ];
		});
		if( isPrepared, {
			TypeSafe.methodError( thisMethod, "Already armed or recording" );
			^false;
		}, {
			if( server.serverRunning, {
				recentPath = path;
				buf = Buffer.alloc( server, 65536, numChannels, { arg buf;
						buf.writeMsg( path, headerFormat, sampleFormat, 0, 0, true ); });
				if( buf.notNil, {
					SynthDef( "simpleRecorder" ++ numChannels, { arg i_bus, i_buf;
						DiskOut.ar( i_buf, In.ar( i_bus, numChannels )); 
					}).send( server );
					CmdPeriod.add( this );
					isPrepared = true;
					this.changed( \prepared );
					^true;
				}, {
					TypeSafe.methodError( thisMethod, "Cannot allocate buffer" );
					^false;
				});
			}, {
				TypeSafe.methodError( thisMethod, "Server not running" );
				^false;
			});
		});
	}
	
	cmdPeriod {
		if( node.notNil, {
			node = nil;
		});
		if( buf.notNil, {
			buf.close({ arg buf; buf.free; });
			buf = nil;
		});
		isPrepared = false;
		this.changed( \cmdPeriod );
		CmdPeriod.remove( this );
	}
	
	makeWindow { arg w;
		var ggRec, ggChannelOffset, ggTimer, ggNumChannels, serverRunning, serverStopped, ctlr, ctlr2,
		    recTimerTask, recTimerFunc;
		
		if( window.notNil, { ^window.front });
		
		if( w.isNil, {
			w = window		= SCWindow( "Recorder for Server '" ++ server.name.asString ++ "'",
								Rect( 10, SCWindow.screenBounds.height - 96, 340, 46 ));
			w.view.decorator	= FlowLayout( w.view.bounds );
		});

		ggRec = SCButton( w, Rect( 0, 0, 72, 24 ))
			.states_([
				[ "prepare",  Color.black, Color.clear ],
				[ "record >", Color.red,   Color.gray( 0.1 )],
				[ "stop []",  Color.black, Color.red ]
			])
			.action_({ arg b;
				case { b.value == 1 }
				{
					this.prepare;
				}
				{ b.value == 2 }
				{
					this.record;
				}
				{
					this.stop;
				};
			});
		
		w.view.decorator.shift( 4, 0 );

		ggTimer = SCStaticText( w, Rect( 0, 0, 72, 24 ))
			.string_( "00:00:00" );
		
		SCStaticText( w, Rect( 0, 0, 24, 24 ))
			.align_( \right )
			.string_( "Bus" );

		ggChannelOffset = SCNumberBox( w, Rect( 0, 0, 36, 24 ))
			.align_( \right )
			.object_( channelOffset )
			.action_({ arg b;
				this.channelOffset_( b.value );
			});
			
		SCStaticText( w, Rect( 0, 0, 48, 24 ))
			.align_( \right )
			.string_( "Chans" );

		ggNumChannels = SCNumberBox( w, Rect( 0, 0, 36, 24 ))
			.align_( \right )
			.object_( numChannels )
			.action_({ arg b;
				this.numChannels_( b.value );
			});
	
		serverRunning = {
			this.prInAppClock({
				ggRec.enabled = true;
			});
		};

		serverStopped = {
			recTimerTask.stop;
			this.prInAppClock({
				ggRec.setProperty( \value, 0 );
				ggRec.enabled = false;
				ggChannelOffset.setProperty( \enabled, true );
				ggNumChannels.setProperty( \enabled, true );
			});
		};
			
		if( server.serverRunning, serverRunning, serverStopped );

		recTimerFunc = {
			var str, startTime, t, oldT;
//			startTime		= Main.elapsedTime;
//			oldT			= 0;
			inf.do({ arg t;
//				t	= Main.elapsedTime - startTime;
//				if( t != oldT, {
					str	= ((t.div( 3600 ) % 60 * 100 + (t.div( 60 ) % 60)) * 100 + (t % 60) + 1000000).asString;
					{
						ggTimer.string = str.copyRange( 1, 2 ) ++
							":" ++ str.copyRange( 3, 4 ) ++ ":" ++ str.copyRange( 5, 6 );
					}.defer;
//					oldT	= t;
//				});
//				0.1.wait;
				1.0.wait;
			});
		};
		recTimerTask = Task( recTimerFunc, SystemClock );

		ctlr = SimpleController( server )
			.put( \serverRunning, {
				if( server.serverRunning, serverRunning, serverStopped );
			});

		ctlr2 = SimpleController( this )
			.put( \prepared, {
				this.prInAppClock({
					ggRec.setProperty( \value, 1 );
					ggChannelOffset.setProperty( \enabled, false );
					ggNumChannels.setProperty( \enabled, false );
				});
			})
			.put( \started, {
				recTimerTask.reset;
				recTimerTask.start;
				this.prInAppClock({
					ggRec.setProperty( \value, 2 );
				});
			})
			.put( \stopped, {
				recTimerTask.stop;
				this.prInAppClock({
					ggRec.setProperty( \value, 0 );
					ggChannelOffset.setProperty( \enabled, true );
					ggNumChannels.setProperty( \enabled, true );
				});
			})
			.put( \paused, {
				recTimerTask.pause;
				this.prInAppClock({
					ggRec.setProperty( \value, 1 );
				});
			})
			.put( \resumed, {
				recTimerTask.resume;
				this.prInAppClock({
					ggRec.setProperty( \value, 2 );
				});
			})
			.put( \channelOffset, {
				this.prInAppClock({
					ggChannelOffset.object_( channelOffset );
				});
			})
			.put( \numChannels, {
				this.prInAppClock({
					ggNumChannels.object_( numChannels );
				});
			})
			.put( \cmdPeriod, {
				serverStopped.value;
				recTimerTask = Task( recTimerFunc, SystemClock );
				if( server.serverRunning, serverRunning );
			});

		w.onClose = {
			window = nil;
			recTimerTask.stop;
			ctlr.remove;
			ctlr2.remove;
		};
		
		w.front;
		^w;
	}
	
	 prInAppClock { arg func;
	 	if( this.canCallOS, func, {Êfunc.defer; });
	 }
}