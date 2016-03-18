/**
 *  @version	0.1, 25-Nov-05
 *  @author		Hanns Holger Rutz
 */
InertiaJitterClient {
	var <doc, <jitterAddr;
	var kOSCChannels;

	classvar	<kCoupSlave 	= 0;
	classvar	<kCoupMaster 	= 1;
	classvar	<kCoupNone	= 2;
		
	var <coupling	= 0; // kCoupSlave;
	
	var elm;

	classvar kOSCMain				= "/inertia/main";
	
	var <collMarkers;
	var <collMarkersByName;
	
	classvar <nameComparator, <posComparator;
	
	var <jitResp, <jitWelcomeResp;
	
	var ignoreMovies;
	
	*initClass {
		posComparator = { arg o1, o2;
			var n1, n2;
		
			if( o1.respondsTo( \pos ), {
				n1 = o1.pos;
				if( o2.respondsTo( \pos ), {
					n2 = o2.pos;
				}, { if( o2.isNumber, {
					n2 = o2; // .asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, { if( o1.isNumber, {
				n1 = o1; // .asInteger;
				if( o2.respondsTo( \pos ), {
					n2 = o2.pos;
				}, { if( o2.isNumber, {
					n2 = o2; // .asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, {
				Error( "Class Cast : " ++ o1.class.name ).throw;
			})});
			
			if( n1 < n2, -1, { if( n1 > n2, 1, 0 )});
//			^(n1 <= n2);
		};

		nameComparator = { arg o1, o2;
			var n1, n2;
			if( o1.isKindOf( String ), {
				n1 = o1;
				if( o2.isKindOf( String ), {
					n2 = o2;
				}, { if( o2.respondsTo( \name ), {
					n2 = o2.name;
				}, {
					Error( "Illegal type for name comparator : "++o2.class.name ).throw;
				})});
			}, { if( o1.respondsTo( \name ), {
				n1 = o1.name;
				if( o2.isKindOf( String ), {
					n2 = o2;
				}, { if( o2.respondsTo( \name ), {
					n2 = o2.name;
				}, {
					Error( "Illegal type for name comparator : "++o2.class.name ).throw;
				})});
			}, {
				Error( "Illegal type for name comparator : "++o1.class.name ).throw;
			})});
			
			if( n1 < n2, -1, { if( n1 > n2, 1, 0 )});
		};
	}
	
	*new { arg doc;
		^super.new.prInit( doc );
	}
	
	prInit { arg argDoc;	
		doc			= argDoc;
		
		kOSCChannels = Array.fill( InertiaSession.kNumMovies, { arg ch;
			"/inertia/" ++ (ch + 65).asAscii;
		});
		
		ignoreMovies = Set.new;
		InertiaSession.kNumMovies.do({ arg ch;
			ignoreMovies.add( (ch + 65).asAscii ++ "intro.mov" );
			ignoreMovies.add( (ch + 65).asAscii ++ "outro.mov" );
		});
		
		// default
		jitterAddr	= NetAddr( "127.0.0.1", 51111 );

//		doc.getMap().addListener( this );
//		doc.timeline.addTimelineListener( this );

		collMarkers			= doc.markers;
		collMarkersByName	= doc.markers.copy.sort({ arg a, b; a.name <= b.name });

		doc.transport.addListener( this );

		jitResp	= kOSCChannels.collect({ arg cmd, ch;
			OSCresponderNode( nil, cmd, { arg time, resp, msg;
				var cmd, movieName, startTime, rateScale, markIdx, pos, volume;
				
				if( coupling == kCoupSlave, {
					cmd = msg[ 1 ].asString;
					// play <(string)movieName> <(number)startTimeSecs> <(number)rateScale>
					case
					{ cmd == "play" }
					{
						movieName	= msg[ 2 ].asString;
						if( ignoreMovies.includes( movieName ).not, {
							startTime	= msg[ 3 ].asFloat;
							rateScale	= msg[ 4 ].asFloat;
							volume	= msg[ 5 ].asFloat;
							
							pos			= (doc.timeline.rate * startTime + 0.5).asInteger;
	//						markIdx		= Collections.binarySearch( collMarkers, movieName, Marker.nameComparator );
							markIdx		= this.prBinarySearch( collMarkersByName, movieName, nameComparator );
							if( markIdx >= 0, {
								pos	    = pos + collMarkersByName[ markIdx ].pos;
							});
							if( (pos >= 0) && (pos < doc.timeline.length), {
								doc.layers.setVolume( ch, volume );
								doc.transport.goPosition( ch, pos, rateScale );
								if( elm.notNil, {
									elm.dispatchEvent( InertiaJitterClientEvent( this, InertiaJitterClientEvent.kPlay, thisThread.seconds, 
										ch, movieName, startTime, rateScale ));
								});
							});
						});
					}
					// stop
					{ cmd == "stop" }
					{
//"RECEIVED stop".postln;
						doc.transport.goStop( ch );
					};
				});
			}).add;
		});

		jitWelcomeResp = OSCresponderNode( nil, kOSCMain, { arg time, resp, msg;
			if( msg[ 1 ].asString == "welcome", {
				"Handshake successfull!".postln;
				jitterAddr.sendMsg( "/inertia/all", "virgin" );
			});
		}).add;
	}
	
	setJitterAddr {Êarg addr;
		jitterAddr = addr;
	}
	
	addListenerÊ{Êarg l;
		if( elm.isNil, {
			elm = EventManager( this );
		});
		elm.addListener( l );
	}
	
	removeListener { arg l;
		if( elm.notNil, { elm.removeListener( l )});
	}

	processEventÊ{Êarg e;
		var listener;
		
		elm.countListeners.do({ arg i;
			listener = elm.getListener( i );
			switch( e.getID,
				InertiaJitterClientEvent.kPlay, { listener.moviePlay( e )},
				{
					("Assertion Failure : illegal event ID " ++ e.getID).error;
				}
			);
		});
	}

//	public void dumpOSC( int mode )
//	{
//		if( rcv != null ) rcv.dumpOSC( mode, System.err );
//		if( trns != null ) trns.dumpOSC( mode, System.err );
//	}

//	private String getResourceString( String key )
//	{
//		return AbstractApplication.getApplication().getResourceString( key );
//	}

	setCouplingÊ{Êarg mode;
		coupling = mode;
	}

	handshake { arg local = false;
		if( jitterAddr.isNil, {
			("! no address was specified for jitter client !").error;
			^this;
		});
	
		// berkeley 'otudp read' provides no means
		// of querying the sender's address, so we
		// provide it directly NetAddr
		("Sending handshake to jitter : " ++ jitterAddr.hostname ++ ":" ++ jitterAddr.port).postln;
		
		jitterAddr.sendMsg( kOSCMain, "welcome", if( local, "127.0.0.1", {ÊNetAddr.myIP }), 57120 );
	}

//	public void quit()
//	{
//		try {
//			if( rcv != null ) rcv.stopListening();
//			if( dch != null ) dch.close();
//		}
//		catch( IOException e1 ) {
//			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
//		}
//	}

	prBinarySearch {Êarg coll, newObject, function;
		var index;
		var low	= 0;
		var high	= coll.size - 1;

		while({ 
			index  = (high + low) div: 2;
			low   <= high;
		}, {
			switch( function.value( coll.at( index ), newObject ),
			0,Ê{Ê^index; },
			-1, {
				low = index + 1;
			},
			1, {
				high = index - 1;
			},
			{
				"Illegal result from comparator".error;
				^-1;
			});
		});
		^(low.neg - 1);	// as in java.util.Collections.binarySearch !
	}
	
	// sync: attempts shared on DOOR_TIME
	sendPlaySeekCmd {Êarg ch = 0, pos = 0, rateScale = 1.0, play = true;
		var markIdx, startTime, mark, msg;

		if( play.not, { pos = doc.timeline.position });
//		markIdx			= doc.markers.indexOf( pos + 1, false );
		markIdx			= this.prBinarySearch( collMarkers, pos + 1, posComparator );
		if( markIdx == -1, { ^this });
	   if( markIdx < 0, { markIdx = (markIdx + 1).neg });
		mark			= collMarkers[ markIdx ];
		startTime		= (pos - mark.pos) / doc.timeline.rate;
		
		if( jitterAddr.notNil, {
			if( play, {
				jitterAddr.sendMsg( kOSCChannels[ ch ], "play", mark.name, startTime, rateScale );
			}, {
				jitterAddr.sendMsg( kOSCChannels[ ch ], "seek", mark.name, startTime );
			});
		});
	}

	transportStop { arg transport, ch, pos;
		if( (coupling == kCoupMaster) and: { jitterAddr.notNil }, {
			jitterAddr.sendMsg( kOSCChannels[ ch ], "stop" );
		});
	}
	
	transportPositionÊ{Êarg transport, ch, pos, rateScale;
		this.transportStop( transport, ch, pos );
		this.transportPlay( transport, ch, pos, rateScale );
	}
	
	transportPlay { arg transport, ch, pos, rateScale;
		if( coupling == COUP_MASTER, {
			this.sendPlaySeekCmd( ch, pos, rateScale, true );
		});
	}

//	public void transportQuit( MultiTransport transport ) {}

//	timelinePositioned { arg e;
//		if( coupling == kCoupMaster, {
//			final DocumentFrame f = doc.getFrame();
//			if( f != null ) {
//				final int ch = f.getActiveChannel();
//				if( !doc.getTransport().isRunning( ch )) {
//					sendPlaySeekCmd( ch, 0, 0.0f, false );
//				}
//			}
//		});
//	}
//
//	timelineChanged { arg e; }
//	timelineScrolled { arg e; }
//	timelineSelected { arg e; }
}