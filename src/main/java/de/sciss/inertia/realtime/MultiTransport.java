/*
 *  MultiTransport.java
 *  Inertia
 *
 *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		28-Sep-05	created from ordinary transport
 */

package de.sciss.inertia.realtime;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.undo.*;

import de.sciss.inertia.timeline.*;
import de.sciss.util.LockManager;
import de.sciss.app.Document;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;

import de.sciss.io.Span;

/**
 *	The realtime "motor" or "clock". The transport
 *	deals with realtime playback of the timeline.
 *	It provides means for registering and unregistering
 *	realtime consumers and communicates with a
 *	RealtimeProducer which is responsible for the
 *	actual data production. Transort clocking is
 *	performed within an extra thread from within
 *	the consumer's methods are called and registered
 *	transport listeners are informed about actions.
 * 
 *  @author		Hanns Holger Rutz
 *  @version	0.28, 04-Nov-05
 *
 *	@todo	the methods for adding and removing consumers should
 *			be moved to the realtime host interface?
 *
 *	@todo	changing sample rate while playing doesn't have an effect
 *
 *	@todo	quit-and-wait don't work (timeout)
 */
public class MultiTransport
// extends Thread
implements Runnable, TimelineListener	// RealtimeHost
{
	private static final int	CMD_IGNORE	= 0;
	private static final int	CMD_STOP	= 1;
	private static final int	CMD_PLAY	= 2;
	private static final int	CMD_QUIT	= -1;

	private static final int	CMD_CONFIG_PAUSE	= 4;	// pause but don't tell the listeners
	private static final int	CMD_CONFIG_RESUME	= 5;	// go on where we stopped but don't tell the listeners
	private static final int	CMD_POSITION		= 6;	// go on at the context's timespan start

	// low level threading
//    private boolean threadRunning   = false;
    private	final Variables[] rt_variables;

    private final Timeline		timeline;
    private final Document		doc;
	private final LockManager	lm;
	private final int			doors;
	
	// high level listeners
	private final ArrayList	collTransportListeners  = new ArrayList();

	// realtime control
	private RealtimeContext				rt_context;
//	private RealtimeProducer			rt_producer;
	private RealtimeConsumer[]			rt_consumers		= new RealtimeConsumer[ 4 ];	// array will grow
	private RealtimeConsumerRequest[]	rt_requests			= new RealtimeConsumerRequest[ 4 ]; // ...automatically
	private int							rt_numConsumers		= 0;
	private int							rt_notifyTickStep;
	private int							rt_senseBufSize;
	
//	private final RealtimeContext		fakeContext = new RealtimeContext( this, new Vector(), new Vector(),
//																	   new Span(), 1000 );
	
	private Thread						thread;
	
	private final int					channels;
	
	// sync : call in event thread!
	/**
	 *	Creates a new transport. The thread will
	 *	be started and set to pause to await
	 *	transport commands.
	 *
	 *	@param	doc		Session document
	 */
    public MultiTransport( Document doc, Timeline timeline, int channels, LockManager lm, int doors )
    {
		this.timeline	= timeline;
        this.doc		= doc;
		this.lm			= lm;
		this.doors		= doors;
		this.channels	= channels;
		
		rt_variables	= new Variables[ channels ];
		for( int ch = 0; ch < channels; ch++ ) {
			rt_variables[ ch ]	= new Variables();
		}
		
//		rt_producer		= new RealtimeProducer( root, doc, this );
		rt_context		= null;
        
		timeline.addTimelineListener( this );
		launchThread();
    }
	
	public int getNumChannels()
	{
		return channels;
	}
	
// INERTIA
//	public Session getDocument()
//	{
//		return doc;
//	}
	
	private void launchThread()
	{
		thread	= new Thread( this, "Transport" );
        thread.setDaemon( true );
        thread.setPriority( thread.getPriority() + 1 );
		thread.start();
	}
	
	/**
	 *	Registers a new transport listener
	 *
	 *	@param	listener	the listener to register for information
	 *						about transport actions such as play or stop
	 */
	public void addListener( Listener listener )
	{
		synchronized( this ) {
			if( !collTransportListeners.contains( listener )) collTransportListeners.add( listener );
		}
	}

	/**
	 *	Unregisters a transport listener
	 *
	 *	@param	listener	the listener to remove from the event dispatching
	 */
	public void removeListener( Listener listener )
	{
		synchronized( this ) {
			collTransportListeners.remove( listener );
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchStop( int which, long pos )
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((Listener) collTransportListeners.get( i )).transportStop( this, which, pos );
			}
			catch( Exception e1 ) {
				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
			}
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchPosition( int which, long pos, float rateScale )
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((Listener) collTransportListeners.get( i )).transportPosition( this, which, pos, rateScale );
			}
			catch( Exception e1 ) {
				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
			}
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchPlay( int which, long pos, float rateScale )
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((Listener) collTransportListeners.get( i )).transportPlay( this, which, pos, rateScale );
			}
			catch( Exception e1 ) {
				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
			}
		}
	}

	// sync: to be called inside synchronized( this ) !
	private void dispatchQuit()
	{
		for( int i = 0; i < collTransportListeners.size(); i++ ) {
			try {
				((Listener) collTransportListeners.get( i )).transportQuit( this );
			}
			catch( Exception e1 ) {
				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
			}
		}
	}
	
	/**
	 *	Registers a new realtime consumer.
	 *	If transport is running, it will be interrupted briefly
	 *	and the realtime producer is reconfigured on the fly.
	 *
	 *	@param	consumer	the consumer to add to the realtime process.
	 *						it's <code>createRequest</code> will be
	 *						called to query the profile.
	 *
	 *	@synchronization	to be called from event thread
	 *
	 *	@see	RealtimeConsumer#createRequest( RealtimeContext )
	 *	@see	RealtimeProducer#requestAddConsumerRequest( RealtimeConsumerRequest )
	 */
	public void addRealtimeConsumer( RealtimeConsumer consumer )
	{
		RealtimeConsumerRequest[]  oldRequests;
		RealtimeConsumer[]		oldConsumers;
		RealtimeConsumerRequest	request;
		Variables				v;
		final boolean[]			wasPlaying	= new boolean[ channels ];

		for( int i = 0; i < rt_numConsumers; i++ ) {
			if( rt_consumers[ i ] == consumer ) return;
		}

		// pause
		for( int ch = 0; ch < channels; ch++ ) {
			v					= rt_variables[ ch ];
			synchronized( v ) {
				wasPlaying[ ch ]	= isRunning( ch );
				if( wasPlaying[ ch ] ) {
					v.command		= CMD_CONFIG_PAUSE;
					v.advance		= false;
					v.notifyAll();
					if( thread.isAlive() ) {
						try {
							v.wait( 5000 );
						}
						catch( InterruptedException e1 ) {}
					}
				}
			} // synchronized( v )
		}
		
		// add
		synchronized( this ) {
			if( rt_numConsumers == rt_consumers.length ) {
				oldConsumers	= rt_consumers;
				rt_consumers	= new RealtimeConsumer[ rt_numConsumers + 5 ];
				System.arraycopy( oldConsumers, 0, rt_consumers, 0, rt_numConsumers );
				oldRequests		= rt_requests;
				rt_requests		= new RealtimeConsumerRequest[ rt_numConsumers + 5 ];
				System.arraycopy( oldRequests, 0, rt_requests, 0, rt_numConsumers );
			}
			rt_consumers[ rt_numConsumers ] = consumer;

			if( rt_context != null ) {  // add new request on the fly
				request	= consumer.createRequest( rt_context );
				rt_requests[ rt_numConsumers ] = request;
				if( request.notifyTicks ) {
					rt_notifyTickStep = Math.min( rt_notifyTickStep, request.notifyTickStep );
				}
//				rt_producer.requestAddConsumerRequest( request );
java.util.List requests = new ArrayList( 1 );
requests.add( request );
activateConsumers( requests );
			} else {
				rt_requests[ rt_numConsumers ] = null;
			}
			
			rt_numConsumers++;
		} // synchronized( this )

		// resume
		for( int ch = 0; ch < channels; ch++ ) {
			if( !wasPlaying[ ch ]) continue;
			v					= rt_variables[ ch ];
			synchronized( v ) {
				v.command		= CMD_CONFIG_RESUME;
//				threadRunning   = true;
				if( thread.isAlive() ) {
					v.notifyAll();
				} else {
					goPlay( ch, v.pos, v.rateScale );
				}
			}
		}
	}

	/**
	 *	Unregisters a realtime consumer.
	 *	If transport is running, it will be interrupted briefly
	 *	and the realtime producer is reconfigured on the fly.
	 *
	 *	@param	consumer	the consumer to remove from the realtime process.
	 *
	 *	@synchronization	to be called from event thread
	 *
	 *	@see	RealtimeProducer#requestRemoveConsumerRequest( RealtimeConsumerRequest )
	 */
	public void removeRealtimeConsumer( RealtimeConsumer consumer )
	{
		int						i;
		RealtimeConsumerRequest[]  oldRequests;
		RealtimeConsumerRequest	request;
		Variables				v;
		final boolean[]			wasPlaying	= new boolean[ channels ];

		for( i = 0; i < rt_numConsumers; i++ ) {
			if( rt_consumers[ i ] == consumer ) break;
		}
		if( i == rt_numConsumers ) return;
		
		// pause
		for( int ch = 0; ch < channels; ch++ ) {
			v					= rt_variables[ ch ];
			synchronized( v ) {
				wasPlaying[ ch ]	= isRunning( ch );
				if( wasPlaying[ ch ] ) {
					v.command		= CMD_CONFIG_PAUSE;
					v.advance		= false;
					v.notifyAll();
					if( thread.isAlive() ) {
						try {
							v.wait( 5000 );
						}
						catch( InterruptedException e1 ) {}
					}
				}
			} // synchronized( v )
		}
		
		// remove
		synchronized( this ) {
			request	= rt_requests[ i ];
			System.arraycopy( rt_consumers, i + 1, rt_consumers, i, rt_numConsumers - i - 1 );
			System.arraycopy( rt_requests,  i + 1, rt_requests,  i, rt_numConsumers - i - 1 );

			rt_numConsumers--;
			rt_consumers[ rt_numConsumers ] = null;
			rt_requests[ rt_numConsumers ]  = null;

			if( rt_context != null ) {
				// eventuell wieder hoeher gehen
				if( request.notifyTicks && request.notifyTickStep <= rt_notifyTickStep ) {
					rt_notifyTickStep = 0x10000; // rt_producer.source.bufSizeH;
					for( i = 0; i < rt_numConsumers; i++ ) {
						if( rt_requests[ i ].notifyTicks ) {
							rt_notifyTickStep = Math.min( rt_notifyTickStep, rt_requests[ i ].notifyTickStep );
						}
					}
				}
//				rt_producer.requestRemoveConsumerRequest( request );
			}
		} // synchronized( this )

		// resume
		for( int ch = 0; ch < channels; ch++ ) {
			if( !wasPlaying[ ch ]) continue;
			v					= rt_variables[ ch ];
			synchronized( v ) {
				v.command		= CMD_CONFIG_RESUME;
//				threadRunning   = true;
				if( thread.isAlive() ) {
					v.notifyAll();
				} else {
					goPlay( ch, v.pos, v.rateScale );
				}
			}
		}
	}

	private void destroyContext()
	{
//		synchronized( this ) {
			for( int ch = 0; ch < channels; ch++ ) {
				if( isRunning( ch )) stopAndWait( ch );
			}
			
			synchronized( this ) {
	//			rt_producer.changeContext( fakeContext );
				rt_context = null;
				for( int i = 0; i < rt_requests.length; i++ ) {
					rt_requests[ i ] = null;
				}
			}
//		}
	}

	// will sync shared on timetrnsrcv
	// to be called inside synchronized( this ) block!
	// to be called in event thread
	private void createContext()
	{
		RealtimeConsumerRequest	request;
		ArrayList				collRequests;
		Variables				v;
		final boolean[]			wasPlaying	= new boolean[ channels ];
	
// INERTIA
//		if( !lm.attemptShared( doors | Session.DOOR_TRACKS, 250 )) {
if( !lm.attemptShared( doors, 250 )) {
			destroyContext();
			return;
		}
		try {
			// pause
			for( int ch = 0; ch < channels; ch++ ) {
				v					= rt_variables[ ch ];
				synchronized( v ) {
					wasPlaying[ ch ]	= isRunning( ch );
					if( wasPlaying[ ch ] ) {
						v.command		= CMD_CONFIG_PAUSE;
						v.advance		= false;
						v.notifyAll();
						if( thread.isAlive() ) {
							try {
								v.wait( 5000 );
							}
							catch( InterruptedException e1 ) {}
						}
					}
				} // synchronized( v )
			}

			// ------------------------- recontext ------------------------- 
// INERTIA
			synchronized( this ) {
	//			rt_context		= new RealtimeContext( this, doc.tracks.getAll(),
				rt_context		= new RealtimeContext( this, null,
													   new Span( 0, timeline.getLength() ),
													   timeline.getRate() );
	//			rt_context.setSourceBlockSize( rt_senseBufSize );
	//			rt_producer.changeContext( rt_context );
				rt_notifyTickStep   = 0x10000; // rt_producer.source.bufSizeH;
				collRequests		= new ArrayList( rt_numConsumers );
				for( int i = 0; i < rt_numConsumers; i++ ) {
					request			= rt_consumers[ i ].createRequest( rt_context );
					rt_requests[ i ]= request;
					if( request.notifyTicks ) {
						rt_notifyTickStep = Math.min( rt_notifyTickStep, request.notifyTickStep );
					}
					collRequests.add( request );
				}
	//			if( wasPlaying ) {
	//				rt_producer.requestAddConsumerRequests( collRequests );
	//			} else {
	//				rt_producer.addConsumerRequestsNow( collRequests );
					activateConsumers( collRequests );
//					offhandProduction();
	//			}
			}

			// resume
			for( int ch = 0; ch < channels; ch++ ) {
				if( !wasPlaying[ ch ]) continue;
				v					= rt_variables[ ch ];
				synchronized( v ) {
					v.command		= CMD_CONFIG_RESUME;
//					threadRunning   = true;
					if( thread.isAlive() ) {
						v.notifyAll();
					} else {
						goPlay( ch, v.pos, v.rateScale );
					}
				}
			}
		}
		finally {
// INERTIA
//			lm.releaseShared( doors | Session.DOOR_TRACKS );
lm.releaseShared( doors );
		}
	}

	// call in event thread
	private void activateConsumers( java.util.List requests )
	{
		RealtimeConsumerRequest	rcr;
		
		for( int j = 0; j < requests.size(); j++ ) {
			rcr = (RealtimeConsumerRequest) requests.get( j );
ourLp:		for( int i = 0; i < rt_numConsumers; i++ ) {
				if( rt_requests[ i ] == rcr ) {
					rt_requests[ i ].active = true;
					break ourLp;
				}
			}
		}
	}

	// to be called in event thread
//	private void offhandProduction()
//	{
//		final long prodStart = timeline.getPosition();
//		
////		rt_producer.produceOffhand( prodStart );
//		for( int i = 0; i < rt_numConsumers; i++ ) {
//			if( rt_requests[ i ].notifyOffhand ) {
////				rt_consumers[ i ].offhandTick( rt_context, rt_producer.source, prodStart );
//				rt_consumers[ i ].offhandTick( rt_context, prodStart );
//			}
//		}
//	}

	/**
	 *	The transport core is
	 *	executed within the thread's run method
	 */
    public void run()
    {
		// all initial values are just here to please the compiler
		// who doesn't know commandLp is exited only after at least
		// one CMD_PLAY (see assertion in CMD_CONFIG_RESUME)
        long			startTime = 0, sysTime;
        int				currentRate, i, ch;
//		UndoableEdit	edit;
		RealtimeConsumerRequest	r;
		Variables		v;

		do {
			for( ch = 0; ch < channels; ch++ ) {
				v = rt_variables[ ch ];
newCommand:		synchronized( v ) {
					if( v.advance ) break newCommand;
					switch( v.command ) {
					case CMD_STOP:
						synchronized( this ) {
							dispatchStop( ch, v.pos );
						}
						// translate into a valid time offset
//							if( !lm.attemptExclusive( doors, 400 )) break;
//							try {
//								rt_pos	= Math.max( 0, Math.min( timeline.getLength(), rt_pos ));
//
//							if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
//								PrefsUtil.KEY_INSERTIONFOLLOWSPLAY, false )) {
//
//								timeline.setPosition( this, rt_pos );
//							} else {
//								timeline.setPosition( this, timeline.getPosition() );
//							}
//							}
//							finally {
//								lm.releaseExclusive( doors );
//							}
						break;

					case CMD_CONFIG_RESUME:
						v.advance			= true;
						v.yield				= false;
						break;
						
					case CMD_PLAY:
					case CMD_POSITION:
						synchronized( this ) {
							if( v.command == CMD_PLAY ) {
								dispatchPlay( ch, v.startFrame, v.rateScale );
							} else {
								dispatchPosition( ch, v.startFrame, v.rateScale );
							}
						}
						v.targetRate		= (int) (rt_context.getSourceRate() * v.rateScale);
						// e.g. bufSizeH == 512 --> 0x1FF . Maske fuer frameCount
						// wir geben dem producer einen halben halben buffer zeit (in millisec)
						// d.h. bei 1000 Hz und halber buffer size von 512 sind das 256 millisec.
						v.startTime			= System.currentTimeMillis() - 1;   // division by zero vermeiden
						v.frameCount		= 0;
						v.pos				= v.startFrame;
						v.advance			= true;
						v.yield				= false;
						break;
						
					case CMD_QUIT:
//System.err.println( "received quit" );
						synchronized( this ) {
//System.err.println( "enter dispatch" );
							dispatchQuit();
//System.err.println( "exited dispatch" );
						}
						v.notifyAll();
//System.err.println( "returning" );
						return;

					default:
						break;
					}
					
					v.command = CMD_IGNORE;
					v.notifyAll();
				} // synchronized( v )
 
				if( !v.advance ) continue;
				if( !v.yield ) {
					v.frameCount	+= rt_notifyTickStep;
					v.pos			+= rt_notifyTickStep;
				}
				sysTime				= System.currentTimeMillis();
				currentRate			= (int) (1000 * v.frameCount / (sysTime - v.startTime));
				v.yield				= currentRate > v.targetRate;
				if( v.yield ) continue;		// wir sind der zeit voraus

				// handle stop + loop
				if( v.pos >= v.stopFrame ) {
					if( v.looping ) {
						v.startFrame = v.loopStart;
						if( v.startFrame >= v.stopFrame ) {
							goStop( ch );
							continue;
						}
						dispatchPosition( ch, v.startFrame, v.rateScale );
						v.pos				= v.startFrame;
						v.startTime			= System.currentTimeMillis() - 1;
						v.frameCount		= 0;
//						rt_producer.requestProduction(
//							new Span( rt_startFrame, rt_startFrame + rt_producer.source.bufSizeH ),
//							true, sysTime + deadline );
//						rt_producer.requestProduction(
//							new Span( rt_startFrame + rt_producer.source.bufSizeH,
//									  rt_startFrame + rt_producer.source.bufSize ),
//							false, sysTime + deadline );

					} else {
						goStop( ch );
						continue;
					}
				}
				
				synchronized( this ) {
					for( i = 0; i < rt_numConsumers; i++ ) {
						// XXX performativer mit bitshifted mask + AND ?
						r = rt_requests[ i ];
						if( r.active && r.notifyTicks && (v.frameCount % r.notifyTickStep == 0) ) {
							rt_consumers[ i ].realtimeTick( rt_context, ch, v.pos );
	//						rt_consumers[ i ].realtimeTick( rt_context, rt_producer.source, rt_pos );
						}
					}
				} // synchronized( this )
			} // for channels

			Thread.yield();
		} while( true );
    }
	
	public void goPosition( int ch, long startFrame, float rate )
	{
		final Variables	v	= rt_variables[ ch ];
	
        synchronized( v ) {
			if( !isRunning( ch )) {
				goPlay( ch, startFrame, rate );
				return;
			}

			v.command		= CMD_CONFIG_PAUSE;
			v.advance		= false;
			v.notifyAll();
			if( thread.isAlive() ) {
				try {
					v.wait( 5000 );
				}
				catch( InterruptedException e1 ) {}
			}
			
			// full buffer precalc
			v.startFrame	= startFrame; // timeline.getPosition();   // XXX sync?
			v.stopFrame		= isLooping( ch ) && v.loopStop > v.startFrame ?
								  v.loopStop : timeline.getLength();
			v.command		= CMD_POSITION;
			v.rateScale		= rate;
			if( thread.isAlive() ) {
				v.notifyAll();
			} else {
				System.err.println( "!! TRANSPORT DIED !! Restarting..." );
				launchThread();
			}
        } // synchronized( v )
    }
    
	/**
	 *  Requests the thread to start
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really starts.
	 *
	 *  @synchronization	To be called in the event thread.
	 */
    public void goPlay( int ch, long startFrame, float rate )
    {
		final Variables	v	= rt_variables[ ch ];
	
        synchronized( v ) {
			if( isRunning( ch )) {
				stopAndWait( ch );
			};
			
			if( rt_context == null ) {
//				synchronized( this ) {
					createContext();
//				}
			}
			// full buffer precalc
			v.startFrame	= startFrame; // timeline.getPosition();   // XXX sync?
			v.stopFrame		= isLooping( ch ) && v.loopStop > v.startFrame ?
								  v.loopStop : timeline.getLength();
			v.command		= CMD_PLAY;
			v.rateScale		= rate;
			if( thread.isAlive() ) {
				v.notifyAll();
			} else {
				System.err.println( "!! TRANSPORT DIED !! Restarting..." );
				launchThread();
			}
        } // synchronized( v )
    }
	
	public float getRateScale( int ch )
	{
		return rt_variables[ ch ].rateScale;
	}
    
	/**
	 *  Sets the loop span for playback
	 *
	 *  @param  loopSpan	Span describing the new loop start and stop.
	 *						Passing null stops rt_looping. 
	 *
	 *	@synchronization	If loopSpan != null, the caller must have sync on timeline!
	 */
	public void setLoop( int ch, Span loopSpan )
	{
		final Variables	v	= rt_variables[ ch ];
		
        synchronized( v ) {
			if( (loopSpan != null) && !loopSpan.isEmpty() ) {
				v.loopStart			= loopSpan.getStart();
				v.loopStop			= loopSpan.getStop();
				v.looping			= true;
				if( isRunning( ch ) && v.pos < v.loopStop ) {
					v.stopFrame		= v.loopStop;
				}
			} else {
				if( isRunning( ch )) {
					v.stopFrame		= rt_context.getTimeSpan().getLength();
				}
				v.looping			= false;
			}
		} // synchronized( v )
	}

	/**
	 *  Returns whether rt_looping
	 *  is active or not
	 *
	 *	@return	<code>true</code> if rt_looping is used
	 */
	public boolean isLooping( int ch )
	{
		return rt_variables[ ch ].looping;
	}

	public long getPosition( int ch )
	{
		return rt_variables[ ch ].pos;
	}
	
	/**
	 *  Requests the thread to stop
	 *  playing. TransportListeners
	 *  are informed when the
	 *  playing really stops.
	 */
    public void goStop( int ch )
    {
		final Variables	v	= rt_variables[ ch ];

        synchronized( v ) {
			if( !isRunning( ch )) return;
			
			v.command		= CMD_STOP;
			v.advance		= false;
            v.notifyAll();
        } // synchronized( v )
    }
	
    public void stopAllAndWait()
	{
		for( int ch = 0; ch < channels; ch++ ) {
			stopAndWait( ch );
		}
	}

	/**
	 *  Requests the thread to stop
	 *  playing. Waits until transport
	 *  has really stopped.
	 */
    public void stopAndWait( int ch )
    {
		final Variables	v	= rt_variables[ ch ];

		try {
			synchronized( v ) {
				v.command			= CMD_STOP;
				v.advance			= false;
				v.notifyAll();
				if( thread.isAlive() ) v.wait( 5000 );
			} // synchronized( v )
		}
		catch( InterruptedException e1 ) {}
    }

	/**
	 *  Sends quit rt_command to the transport
	 *  returns only after the transport thread
	 *  stopped!
	 */
    public void quit()
    {
		Variables	v;

		for( int ch = 0; ch < channels; ch++ ) {
			v = rt_variables[ ch ];
			synchronized( v ) {
//System.err.println( "funke quit at ch "+ch );
				v.command			= CMD_QUIT;
				v.advance			= false;
				v.notifyAll();
				try {
					if( thread.isAlive() ) {
//System.err.println( "enter wait" );
						thread.join( 5000 );
//						v.wait( 5000 );
//System.err.println( "exited wait" );
					}
//System.err.println( "transport stopped" );
				} catch( InterruptedException e1 ) {}
			} // synchronized( v )
		}
    }

// ---------------- TimelineListener interface ---------------- 

	public void timelineChanged( TimelineEvent e )
	{
//        synchronized( this ) {
//			calcSenseBufSize();
			createContext();
//		}
	}

	public void timelinePositioned( TimelineEvent e )
	{
//		synchronized( this ) {
//			if( isRunning() ) {
//				rt_command		= CMD_CONFIG_PAUSE;
//				threadRunning   = false;
//				notifyAll();
//				if( thread.isAlive() ) {
//					try {
//						wait();
//					}
//					catch( InterruptedException e1 ) {}
//				}
//				// full buffer precalc
//				rt_startFrame	= timeline.getPosition();   // XXX sync?
//				rt_stopFrame	= isLooping() && rt_loopStop > rt_startFrame ? rt_loopStop : timeline.getLength();
////				rt_producer.produceNow( new Span( rt_startFrame,
////												  rt_startFrame + rt_producer.source.bufSizeH ), true );
////				rt_producer.produceNow( new Span( rt_startFrame + rt_producer.source.bufSizeH,
////												  rt_startFrame + rt_producer.source.bufSize ), false );
//				rt_command		= CMD_POSITION;
//				threadRunning   = true;
//				if( thread.isAlive() ) {
//					notifyAll();
//				} else {
//					System.err.println( "!! TRANSPORT DIED !! Restarting..." );
//					launchThread();
//				}
//			} else {
//				if( rt_context == null ) {
//					createContext();	// will invoke offhandProduction!
//				} else {
//					offhandProduction();
//				}
//			}
//		} // synchronized( this )
	}

	public void timelineSelected( TimelineEvent e ) {}
    public void timelineScrolled( TimelineEvent e ) {}

// --------------- RealtimeHost interface ---------------

	/**
	 *  Returns whether the
	 *  thread is currently playing
	 *
	 *	@return	<code>true</code> if the transport is currently playing
	 */
	public boolean isRunning( int ch )
	{
//		return( thread.isAlive() && threadRunning );
		return( rt_variables[ ch ].advance );
	}

	public void	showMessage( int type, String text )
	{
		System.err.println( text );
//		((ProgressComponent) root.getComponent( Main.COMP_MAIN )).showMessage( type, text );
	}

// --------------- internal actions ---------------

	private static class Variables
	{
		private long	loopStart, loopStop;
		private boolean	looping		= false;
		private int		command		= CMD_IGNORE;
		private long	startFrame, stopFrame, pos;
		private float	rateScale	= 1.0f;
		private boolean	advance		= false;
		private boolean	yield		= false;
		private long	startTime;
		private long	frameCount;
		private int		targetRate;
	
		private Variables()
		{
		}
	}
	
	public interface Listener
	{
		/**
		 *	Invoked when the transport is about to stop.
		 *
		 *	@param	pos	the position of the timeline after stopping
		 */
		public void transportStop( MultiTransport transport, int which, long pos );
		/**
		 *	Invoked when the transport position was altered,
		 *	for example when jumping backward in loop mode.
		 *
		 *	@param	pos	the new position of the timeline at which
		 *			transport will continue to play
		 */
		public void transportPosition( MultiTransport transport, int which, long pos, float rate );
		/**
		 *	Invoked when the transport is about to start.
		 *
		 *	@param	pos	the position of the timeline when starting to play
		 */
		public void transportPlay( MultiTransport transport, int which, long pos, float rate );
		/**
		 *	Invoked when the transport was running when the
		 *	application was about to quit. Vital cleanup should
		 *	be performed in here.
		 */
		public void transportQuit( MultiTransport transport );
	}
}