/**
 *  @version	0.1, 25-Nov-06
 *  @author		Hanns Holger Rutz
 */
InertiaTransport {
//implements Runnable, TimelineListener	// RealtimeHost
	var <doc;
	var <numChannels;
	var collTransportListeners;
//	var timeline;
	var running;
	var rateScale;

	*new { arg doc;
		^super.new.prInit( doc );
	}
	
	prInit { arg argDoc;
		doc 					= argDoc;
		numChannels				= doc.layers.numLayers;
		collTransportListeners	= List.new;
//		timeline				= doc.timeline;
//		timeline.addTimelineListener( this );
		running					= Array.fill( numChannels, false );
		rateScale				= Array.fill( numChannels, 1.0 );
	}

	/**
	 *	Registers a new transport listener
	 *
	 *	@param	listener	the listener to register for information
	 *						about transport actions such as play or stop
	 */
	addListener { arg listener;
		collTransportListeners.add( listener );
	}

	/**
	 *	Unregisters a transport listener
	 *
	 *	@param	listener	the listener to remove from the event dispatching
	 */
	removeListener { arg listener;
		collTransportListeners.remove( listener );
	}
		
	// sync: to be called inside synchronized( this ) !
	prDispatchStop { arg which, pos;
		collTransportListeners.do({ arg l;
			l.transportStop( this, which, pos );
		});
	}

	// sync: to be called inside synchronized( this ) !
	prDispatchPosition { arg which, pos, rateScale;
		collTransportListeners.do({ arg l;
			l.transportPosition( this, which, pos, rateScale );
		});
	}

	// sync: to be called inside synchronized( this ) !
	prDispatchPlay { arg which, pos, rateScale;
		collTransportListeners.do({ arg l;
			l.transportPlay( this, which, pos, rateScale );
		});
	}

//	// sync: to be called inside synchronized( this ) !
//	private void dispatchQuit()
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((Listener) collTransportListeners.get( i )).transportQuit( this );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
	
	goPosition { arg ch, startFrame, rate;
		if( running[ ch ].not, {
			this.goPlay( ch, startFrame, rate );
			^this;
		});
		
//			// full buffer precalc
//			v.startFrame	= startFrame; // timeline.getPosition();   // XXX sync?
//			v.stopFrame		= isLooping( ch ) && v.loopStop > v.startFrame ?
//								  v.loopStop : timeline.getLength();
//			v.command		= CMD_POSITION;
			rateScale[ ch ]	= rate;
running[ ch ] = true;
this.prDispatchPosition( ch, startFrame, rate );
    }
    
	/**
	 *  Requests the thread to start
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really starts.
	 *
	 *  @synchronization	To be called in the event thread.
	 */
    goPlay { arg ch, startFrame, rate = 1.0;
		if( running[ ch ], {
			this.stopAndWait( ch );
		});
			
//			// full buffer precalc
//			v.startFrame	= startFrame; // timeline.getPosition();   // XXX sync?
//			v.stopFrame		= isLooping( ch ) && v.loopStop > v.startFrame ?
//								  v.loopStop : timeline.getLength();
//			v.command		= CMD_PLAY;
running[ ch ]	= true;
		rateScale[ ch ]		= rate;
this.prDispatchPlay( ch, startFrame, rate );
    }
	
	getRateScale { arg ch;
		^rateScale[ ch ];
	}
    
//	/**
//	 *  Sets the loop span for playback
//	 *
//	 *  @param  loopSpan	Span describing the new loop start and stop.
//	 *						Passing null stops rt_looping. 
//	 *
//	 *	@synchronization	If loopSpan != null, the caller must have sync on timeline!
//	 */
//	public void setLoop( int ch, Span loopSpan )
//	{
//		final Variables	v	= rt_variables[ ch ];
//		
//        synchronized( v ) {
//			if( (loopSpan != null) && !loopSpan.isEmpty() ) {
//				v.loopStart			= loopSpan.getStart();
//				v.loopStop			= loopSpan.getStop();
//				v.looping			= true;
//				if( isRunning( ch ) && v.pos < v.loopStop ) {
//					v.stopFrame		= v.loopStop;
//				}
//			} else {
//				if( isRunning( ch )) {
//					v.stopFrame		= rt_context.getTimeSpan().getLength();
//				}
//				v.looping			= false;
//			}
//		} // synchronized( v )
//	}

//	/**
//	 *  Returns whether rt_looping
//	 *  is active or not
//	 *
//	 *	@return	<code>true</code> if rt_looping is used
//	 */
//	public boolean isLooping( int ch )
//	{
//		return rt_variables[ ch ].looping;
//	}
//
//	public long getPosition( int ch )
//	{
//		return rt_variables[ ch ].pos;
//	}
	
	/**
	 *  Requests the thread to stop
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really stops.
	 */
    goStop { arg ch;
    	if( running[ ch ].not, { ^this });

//			v.command		= CMD_STOP;
//			v.advance		= false;
//            v.notifyAll();

this.prDispatchStop( ch, 0, rateScale[ ch ]);	// XXX pos
    }
	
    stopAllAndWait {
    	numChannels.do({ arg ch;
			this.stopAndWait( ch );
		});
	}
	
	isRunning { arg ch;
		^running[ ch ];
	}

	/**
	 *  Requests the thread to stop
	 *  playing. Waits until transport
	 *  has really stopped.
	 */
    stopAndWait { arg ch;
this.goStop( ch );
//		final Variables	v	= rt_variables[ ch ];
//
//		try {
//			synchronized( v ) {
//				v.command			= CMD_STOP;
//				v.advance			= false;
//				v.notifyAll();
//				if( thread.isAlive() ) v.wait( 5000 );
//			} // synchronized( v )
//		}
//		catch( InterruptedException e1 ) {}
    }

//	/**
//	 *  Sends quit rt_command to the transport
//	 *  returns only after the transport thread
//	 *  stopped!
//	 */
//    public void quit()
//    {
//		Variables	v;
//
//		for( int ch = 0; ch < channels; ch++ ) {
//			v = rt_variables[ ch ];
//			synchronized( v ) {
////System.err.println( "funke quit at ch "+ch );
//				v.command			= CMD_QUIT;
//				v.advance			= false;
//				v.notifyAll();
//				try {
//					if( thread.isAlive() ) {
////System.err.println( "enter wait" );
//						thread.join( 5000 );
////						v.wait( 5000 );
////System.err.println( "exited wait" );
//					}
////System.err.println( "transport stopped" );
//				} catch( InterruptedException e1 ) {}
//			} // synchronized( v )
//		}
//    }

// ---------------- TimelineListener interface ---------------- 

//	timelineChanged { arg e;
////			createContext();
//	}
//
//	timelinePositioned { arg e;
//
//	}
//
//	timelineSelected { arg e; }
//    timelineScrolled { arg e; }
}