///*
// *  Transport.java
// *  Eisenkraut
// *
// *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
// *
// *	This software is free software; you can redistribute it and/or
// *	modify it under the terms of the GNU General Public License
// *	as published by the Free Software Foundation; either
// *	version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	This software is distributed in the hope that it will be useful,
// *	but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	General Public License for more details.
// *
// *	You should have received a copy of the GNU General Public
// *	License (gpl.txt) along with this software; if not, write to the Free Software
// *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// *
// *
// *	For further information, please contact Hanns Holger Rutz at
// *	contact@sciss.de
// *
// *
// *  Changelog:
// *		07-Aug-05	copied from de.sciss.eisenkraut.realtime.Transport
// */
//
//package de.sciss.inertia.realtime;
//
//import java.awt.*;
//import java.awt.event.*;
//import java.util.*;
//import java.util.prefs.*;
//import javax.swing.*;
//import javax.swing.undo.*;
//
//// INERTIA
////import de.sciss.eisenkraut.*;
////import de.sciss.eisenkraut.edit.*;
////import de.sciss.eisenkraut.gui.*;
////import de.sciss.eisenkraut.session.*;
////import de.sciss.eisenkraut.timeline.*;
////import de.sciss.eisenkraut.util.*;
//import de.sciss.inertia.timeline.*;
//import de.sciss.util.LockManager;
//import de.sciss.app.Document;
//import de.sciss.inertia.util.PrefsUtil;
//
//import de.sciss.app.AbstractApplication;
//
//import de.sciss.io.Span;
//
///**
// *	The realtime "motor" or "clock". The transport
// *	deals with realtime playback of the timeline.
// *	It provides means for registering and unregistering
// *	realtime consumers and communicates with a
// *	RealtimeProducer which is responsible for the
// *	actual data production. Transort clocking is
// *	performed within an extra thread from within
// *	the consumer's methods are called and registered
// *	transport listeners are informed about actions.
// *
// *  @author		Hanns Holger Rutz
// *  @version	0.5, 02-Aug-05
// *
// *	@todo	the methods for adding and removing consumers should
// *			be moved to the realtime host interface?
// *
// *	@todo	changing sample rate while playing doesn't have an effect
// */
//public class Transport
//// extends Thread
//implements Runnable, TimelineListener	// RealtimeHost
//{
//	private static final int	CMD_IGNORE	= 0;
//	private static final int	CMD_STOP	= 1;
//	private static final int	CMD_PLAY	= 2;
//	private static final int	CMD_QUIT	= -1;
//
//	private static final int	CMD_CONFIG_PAUSE	= 4;	// pause but don't tell the listeners
//	private static final int	CMD_CONFIG_RESUME	= 5;	// go on where we stopped but don't tell the listeners
//	private static final int	CMD_POSITION		= 6;	// go on at the context's timespan start
//
//	// low level threading
//    private boolean threadRunning   = false;
//    private	boolean looping			= false;
//	private int		rt_command		= CMD_IGNORE;
//
//    private final Timeline		timeline;
//    private final Document		doc;
//	private final LockManager	lm;
//	private final int			doors;
//	private long				loopStart, loopStop;
//
//	// high level listeners
//	private final ArrayList	collTransportListeners  = new ArrayList();
//
//	// realtime control
//	private RealtimeContext				rt_context;
////	private RealtimeProducer			rt_producer;
//	private RealtimeConsumer[]			rt_consumers		= new RealtimeConsumer[ 4 ];	// array will grow
//	private RealtimeConsumerRequest[]	rt_requests			= new RealtimeConsumerRequest[ 4 ]; // ...automatically
//	private int							rt_numConsumers		= 0;
//	private int							rt_notifyTickStep;
//	private long						rt_startFrame;
//	private long						rt_stopFrame;
//	private long						rt_pos;
//	private int							rt_senseBufSize;
//
//	private float						rateScale	= 1.0f;
//
////	private final RealtimeContext		fakeContext = new RealtimeContext( this, new Vector(), new Vector(),
////																	   new Span(), 1000 );
//
//	private Thread						thread;
//
//	// sync : call in event thread!
//	/**
//	 *	Creates a new transport. The thread will
//	 *	be started and set to pause to await
//	 *	transport commands.
//	 *
//	 *	@param	doc		Session document
//	 */
//    public Transport( Document doc, Timeline timeline, LockManager lm, int doors )
//    {
//		this.timeline	= timeline;
//        this.doc		= doc;
//		this.lm			= lm;
//		this.doors		= doors;
//
////		rt_producer		= new RealtimeProducer( root, doc, this );
//		rt_context		= null;
//
//		timeline.addTimelineListener( this );
//		launchThread();
//    }
//
//// INERTIA
////	public Session getDocument()
////	{
////		return doc;
////	}
//
//	private void launchThread()
//	{
//		thread	= new Thread( this, "Transport" );
//        thread.setDaemon( true );
//        thread.setPriority( thread.getPriority() + 1 );
//		thread.start();
//	}
//
//	/**
//	 *	Registers a new transport listener
//	 *
//	 *	@param	listener	the listener to register for information
//	 *						about transport actions such as play or stop
//	 */
//	public void addTransportListener( TransportListener listener )
//	{
//		synchronized( this ) {
//			if( !collTransportListeners.contains( listener )) collTransportListeners.add( listener );
//		}
//	}
//
//	/**
//	 *	Unregisters a transport listener
//	 *
//	 *	@param	listener	the listener to remove from the event dispatching
//	 */
//	public void removeTransportListener( TransportListener listener )
//	{
//		synchronized( this ) {
//			collTransportListeners.remove( listener );
//		}
//	}
//
//	// sync: to be called inside synchronized( this ) !
//	private void dispatchStop( long pos )
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportStop( this, pos );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	// sync: to be called inside synchronized( this ) !
//	private void dispatchPosition( long pos )
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportPosition( this, pos, rateScale );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	// sync: to be called inside synchronized( this ) !
//	private void dispatchPlay( long pos )
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportPlay( this, pos, rateScale );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	// sync: to be called inside synchronized( this ) !
//	private void dispatchQuit()
//	{
//		for( int i = 0; i < collTransportListeners.size(); i++ ) {
//			try {
//				((TransportListener) collTransportListeners.get( i )).transportQuit( this );
//			}
//			catch( Exception e1 ) {
//				System.err.println( "[@transport]" + e1.getLocalizedMessage() );
//			}
//		}
//	}
//
//	/**
//	 *	Registers a new realtime consumer.
//	 *	If transport is running, it will be interrupted briefly
//	 *	and the realtime producer is reconfigured on the fly.
//	 *
//	 *	@param	consumer	the consumer to add to the realtime process.
//	 *						it's <code>createRequest</code> will be
//	 *						called to query the profile.
//	 *
//	 *	@synchronization	to be called from event thread
//	 *
//	 *	@see	RealtimeConsumer#createRequest( RealtimeContext )
//	 *	@see	RealtimeProducer#requestAddConsumerRequest( RealtimeConsumerRequest )
//	 */
//	public void addRealtimeConsumer( RealtimeConsumer consumer )
//	{
//		int						i;
//		RealtimeConsumerRequest[]  oldRequests;
//		RealtimeConsumer[]		oldConsumers;
//		RealtimeConsumerRequest	request;
//		boolean					wasPlaying;
//
//		synchronized( this ) {
//			for( i = 0; i < rt_numConsumers; i++ ) {
//				if( rt_consumers[ i ] == consumer ) return;
//			}
//
//			// pause
//			wasPlaying			= isRunning();
//			if( wasPlaying ) {
//				rt_command		= CMD_CONFIG_PAUSE;
//				threadRunning   = false;
//				notifyAll();
//				if( thread.isAlive() ) {
//					try {
//						wait();
//					}
//					catch( InterruptedException e1 ) {}
//				}
//			}
//
//			// add
//			if( rt_numConsumers == rt_consumers.length ) {
//				oldConsumers	= rt_consumers;
//				rt_consumers	= new RealtimeConsumer[ rt_numConsumers + 5 ];
//				System.arraycopy( oldConsumers, 0, rt_consumers, 0, rt_numConsumers );
//				oldRequests		= rt_requests;
//				rt_requests		= new RealtimeConsumerRequest[ rt_numConsumers + 5 ];
//				System.arraycopy( oldRequests, 0, rt_requests, 0, rt_numConsumers );
//			}
//			rt_consumers[ rt_numConsumers ] = consumer;
//
//			if( rt_context != null ) {  // add new request on the fly
//				request	= consumer.createRequest( rt_context );
//				rt_requests[ rt_numConsumers ] = request;
//				if( request.notifyTicks ) {
//					rt_notifyTickStep = Math.min( rt_notifyTickStep, request.notifyTickStep );
//				}
////				rt_producer.requestAddConsumerRequest( request );
//java.util.List requests = new ArrayList( 1 );
//requests.add( request );
//activateConsumers( requests );
//			} else {
//				rt_requests[ rt_numConsumers ] = null;
//			}
//
//			rt_numConsumers++;
//
//			// resume
//			if( wasPlaying ) {
//				rt_command		= CMD_CONFIG_RESUME;
//				threadRunning   = true;
//				if( thread.isAlive() ) {
//					notifyAll();
//				} else {
//					goPlay( rateScale );
//				}
//			}
//		} // synchronized( this )
//	}
//
//	/**
//	 *	Unregisters a realtime consumer.
//	 *	If transport is running, it will be interrupted briefly
//	 *	and the realtime producer is reconfigured on the fly.
//	 *
//	 *	@param	consumer	the consumer to remove from the realtime process.
//	 *
//	 *	@synchronization	to be called from event thread
//	 *
//	 *	@see	RealtimeProducer#requestRemoveConsumerRequest( RealtimeConsumerRequest )
//	 */
//	public void removeRealtimeConsumer( RealtimeConsumer consumer )
//	{
//		int						i;
//		RealtimeConsumerRequest[]  oldRequests;
//		RealtimeConsumerRequest	request;
//		boolean					wasPlaying;
//
//		synchronized( this ) {
//			for( i = 0; i < rt_numConsumers; i++ ) {
//				if( rt_consumers[ i ] == consumer ) break;
//			}
//			if( i == rt_numConsumers ) return;
//
//			// pause
//			wasPlaying			= isRunning();
//			if( wasPlaying ) {
//				rt_command		= CMD_CONFIG_PAUSE;
//				threadRunning   = false;
//				notifyAll();
//				if( thread.isAlive() ) {
//					try {
//						wait();
//					}
//					catch( InterruptedException e1 ) {}
//				}
//			}
//
//			// remove
//			request	= rt_requests[ i ];
//			System.arraycopy( rt_consumers, i + 1, rt_consumers, i, rt_numConsumers - i - 1 );
//			System.arraycopy( rt_requests,  i + 1, rt_requests,  i, rt_numConsumers - i - 1 );
//
//			rt_numConsumers--;
//			rt_consumers[ rt_numConsumers ] = null;
//			rt_requests[ rt_numConsumers ]  = null;
//
//			if( rt_context != null ) {
//				// eventuell wieder hoeher gehen
//				if( request.notifyTicks && request.notifyTickStep <= rt_notifyTickStep ) {
//					rt_notifyTickStep = 0x10000; // rt_producer.source.bufSizeH;
//					for( i = 0; i < rt_numConsumers; i++ ) {
//						if( rt_requests[ i ].notifyTicks ) {
//							rt_notifyTickStep = Math.min( rt_notifyTickStep, rt_requests[ i ].notifyTickStep );
//						}
//					}
//				}
////				rt_producer.requestRemoveConsumerRequest( request );
//			}
//
//			// resume
//			if( wasPlaying ) {
//				rt_command		= CMD_CONFIG_RESUME;
//				threadRunning   = true;
//				if( thread.isAlive() ) {
//					notifyAll();
//				} else {
//					goPlay( rateScale );
//				}
//			}
//		} // synchronized( this )
//	}
//
//	private void destroyContext()
//	{
//		int i;
//
//		synchronized( this ) {
//			if( isRunning() ) stopAndWait();
//
////			rt_producer.changeContext( fakeContext );
//			rt_context = null;
//			for( i = 0; i < rt_requests.length; i++ ) {
//				rt_requests[ i ] = null;
//			}
//		}
//	}
//
//	// will sync shared on timetrnsrcv
//	// to be called inside synchronized( this ) block!
//	// to be called in event thread
//	private void createContext()
//	{
//		int						i;
//		boolean					wasPlaying;
//		RealtimeConsumerRequest	request;
//		ArrayList				collRequests;
//
//// INERTIA
////		if( !lm.attemptShared( doors | Session.DOOR_TRACKS, 250 )) {
//if( !lm.attemptShared( doors, 250 )) {
//			destroyContext();
//			return;
//		}
//		try {
//			// pause
//			wasPlaying			= isRunning();
//			if( wasPlaying ) {
//				rt_command		= CMD_CONFIG_PAUSE;
//				threadRunning   = false;
//				notifyAll();
//				if( thread.isAlive() ) {
//					try {
//						wait();
//					}
//					catch( InterruptedException e1 ) {}
//				}
//			}
//
//			// ------------------------- recontext -------------------------
//// INERTIA
////			rt_context		= new RealtimeContext( this, doc.tracks.getAll(),
//			rt_context		= new RealtimeContext( this, null,
//												   new Span( 0, timeline.getLength() ),
//												   timeline.getRate() );
////			rt_context.setSourceBlockSize( rt_senseBufSize );
////			rt_producer.changeContext( rt_context );
//			rt_notifyTickStep   = 0x10000; // rt_producer.source.bufSizeH;
//			collRequests		= new ArrayList( rt_numConsumers );
//			for( i = 0; i < rt_numConsumers; i++ ) {
//				request			= rt_consumers[ i ].createRequest( rt_context );
//				rt_requests[ i ]= request;
//				if( request.notifyTicks ) {
//					rt_notifyTickStep = Math.min( rt_notifyTickStep, request.notifyTickStep );
//				}
//				collRequests.add( request );
//			}
////			if( wasPlaying ) {
////				rt_producer.requestAddConsumerRequests( collRequests );
////			} else {
////				rt_producer.addConsumerRequestsNow( collRequests );
//				activateConsumers( collRequests );
//				offhandProduction();
////			}
//
//			// resume
//			if( wasPlaying ) {
//				rt_command		= CMD_CONFIG_RESUME;
//				threadRunning   = true;
//				if( thread.isAlive() ) {
//					notifyAll();
//				} else {
//					goPlay( rateScale );
//				}
//			}
//		}
//		finally {
//// INERTIA
////			lm.releaseShared( doors | Session.DOOR_TRACKS );
//lm.releaseShared( doors );
//		}
//	}
//
//	// call in event thread
//	private void activateConsumers( java.util.List requests )
//	{
//		int i, j;
//		RealtimeConsumerRequest	rcr;
//
//		for( j = 0; j < requests.size(); j++ ) {
//			rcr = (RealtimeConsumerRequest) requests.get( j );
//ourLp:		for( i = 0; i < rt_numConsumers; i++ ) {
//				if( rt_requests[ i ] == rcr ) {
//					rt_requests[ i ].active = true;
//					break ourLp;
//				}
//			}
//		}
//	}
//
//	// sync : to be called within synchronized( this )
//	// to be called in event thread
//	private void offhandProduction()
//	{
//		long	prodStart;
//		int		i;
//
//		prodStart = timeline.getPosition();
////		rt_producer.produceOffhand( prodStart );
//		for( i = 0; i < rt_numConsumers; i++ ) {
//			if( rt_requests[ i ].notifyOffhand ) {
////				rt_consumers[ i ].offhandTick( rt_context, rt_producer.source, prodStart );
//				rt_consumers[ i ].offhandTick( rt_context, prodStart );
//			}
//		}
//	}
//
//	/**
//	 *	The transport core is
//	 *	executed within the thread's run method
//	 */
//    public void run()
//    {
//		// all initial values are just here to please the compiler
//		// who doesn't know commandLp is exited only after at least
//		// one CMD_PLAY (see assertion in CMD_CONFIG_RESUME)
//        long			startTime = 0, sysTime;
//        long			frameCount = 0, oldFrameCount = 0;
//        int				currentRate, targetRate = 1, i;
////		UndoableEdit	edit;
//		RealtimeConsumerRequest	r;
//
//		do {
//			synchronized( this ) {
//commandLp:		do {
//					switch( rt_command ) {
//					case CMD_CONFIG_PAUSE:
//						notifyAll();
//						break;
//
//					case CMD_CONFIG_RESUME:
//						assert startTime > 0 : startTime;
//						notifyAll();
//						break commandLp;
//
//					case CMD_STOP:
//						dispatchStop( rt_pos );
//						// translate into a valid time offset
//						if( !lm.attemptExclusive( doors, 400 )) break;
//						try {
//							rt_pos	= Math.max( 0, Math.min( timeline.getLength(), rt_pos ));
//
//							if( AbstractApplication.getApplication().getUserPrefs().getBoolean(
//								PrefsUtil.KEY_INSERTIONFOLLOWSPLAY, false )) {
//
//								timeline.setPosition( this, rt_pos );
//							} else {
//								timeline.setPosition( this, timeline.getPosition() );
//							}
//						}
//						finally {
//							lm.releaseExclusive( doors );
//						}
//						notifyAll();
//						break;
//
//					case CMD_PLAY:
//					case CMD_POSITION:
//						if( rt_command == CMD_PLAY ) {
//							dispatchPlay( rt_startFrame );
//						} else {
//							dispatchPosition( rt_startFrame );
//						}
//						// THRU
//						targetRate		= (int) (rt_context.getSourceRate() * rateScale);
//						// e.g. bufSizeH == 512 --> 0x1FF . Maske fuer frameCount
//						// wir geben dem producer einen halben halben buffer zeit (in millisec)
//						// d.h. bei 1000 Hz und halber buffer size von 512 sind das 256 millisec.
//						startTime		= System.currentTimeMillis() - 1;   // division by zero vermeiden
//						frameCount		= 0;
//						rt_pos			= rt_startFrame;
//						notifyAll();
//						break commandLp;
//
//					case CMD_QUIT:
//						dispatchQuit();
//						notifyAll();
//						return;
//
//					default:
//						assert rt_command == CMD_IGNORE : rt_command;
//						break;
//					}
//					// sleep until next rt_command arrives
//					try {
//						wait();
//					}
//					catch( InterruptedException e1 ) {}
//				} while( true );
//			} // synchronized( this )
//
//rt_loop:	while( threadRunning ) {
//				frameCount += rt_notifyTickStep;
//				rt_pos	   += rt_notifyTickStep;
//				sysTime		= System.currentTimeMillis();
//				currentRate = (int) (1000 * frameCount / (sysTime - startTime));
//				while( currentRate > targetRate ) { // wir sind der zeit voraus
//					thread.yield();
//					sysTime		= System.currentTimeMillis();
//					currentRate = (int) (1000 * frameCount / (sysTime - startTime));
//				}
//
//				// handle stop + loop
//				if( rt_pos >= rt_stopFrame ) {
//					if( isLooping() ) {
//						rt_startFrame   = loopStart;
//						if( rt_startFrame >= rt_stopFrame ) {
//							goStop();
//							break rt_loop;
//						}
//						dispatchPosition( rt_startFrame );
//						rt_pos		= rt_startFrame;
//						startTime	= System.currentTimeMillis() - 1;
//						frameCount	= 0;
////						rt_producer.requestProduction(
////							new Span( rt_startFrame, rt_startFrame + rt_producer.source.bufSizeH ),
////							true, sysTime + deadline );
////						rt_producer.requestProduction(
////							new Span( rt_startFrame + rt_producer.source.bufSizeH,
////									  rt_startFrame + rt_producer.source.bufSize ),
////							false, sysTime + deadline );
//
//					} else {
//						goStop();
//						break rt_loop;
//					}
//				}
//
//				for( i = 0; i < rt_numConsumers; i++ ) {
//					// XXX performativer mit bitshifted mask + AND ?
//					r = rt_requests[ i ];
//					if( r.active && r.notifyTicks && (frameCount % r.notifyTickStep == 0) ) {
//						rt_consumers[ i ].realtimeTick( rt_context, rt_pos );
////						rt_consumers[ i ].realtimeTick( rt_context, rt_producer.source, rt_pos );
//					}
//				}
//
//				try {
//					thread.sleep( 0, 1 );
//				} catch( InterruptedException e1 ) {}
//			} // while( threadRunning )
//		} while( true );
//    }
//
//	/**
//	 *  Requests the thread to start
//	 *  playing. TransportListeners
//	 *  are informed when the
//	 *  playing really starts.
//	 *
//	 *  @synchronization	To be called in the event thread.
//	 */
//    public void goPlay( float rate )
//    {
//		Span prodSpan;
//
//        synchronized( this ) {
//			if( isRunning() ) return;
//
//			if( rt_context == null ) {
//				createContext();
//			}
//			// full buffer precalc
//			rt_startFrame	= timeline.getPosition();   // XXX sync?
//			rt_stopFrame	= isLooping() && loopStop > rt_startFrame ?
//							  loopStop : timeline.getLength();
//			rt_command		= CMD_PLAY;
//			rateScale		= rate;
//			threadRunning   = true;
//			if( thread.isAlive() ) {
//				notifyAll();
//			} else {
//				System.err.println( "!! TRANSPORT DIED !!" );
//			}
//        }
//    }
//
//	public float getRateScale()
//	{
//		return rateScale;
//	}
//
//	/**
//	 *  Sets the loop span for playback
//	 *
//	 *  @param  loopSpan	Span describing the new loop start and stop.
//	 *						Passing null stops looping.
//	 *
//	 *	@synchronization	If loopSpan != null, the caller must have sync on timeline!
//	 */
//	public void setLoop( Span loopSpan )
//	{
//        synchronized( this ) {
//			if( loopSpan != null ) {
//				loopStart   = loopSpan.getStart();
//				loopStop	= loopSpan.getStop();
//				looping		= true;
//				if( isRunning() && rt_pos < loopStop ) {
//					rt_stopFrame	= loopStop;
//				}
//			} else {
//				looping		= false;
//				if( isRunning() ) {
//					rt_stopFrame	= rt_context.getTimeSpan().getLength();
//				}
//			}
//		}
//	}
//
//	/**
//	 *  Returns whether looping
//	 *  is active or not
//	 *
//	 *	@return	<code>true</code> if looping is used
//	 */
//	public boolean isLooping()
//	{
//		return looping;
//	}
//
//	/**
//	 *  Requests the thread to stop
//	 *  playing. TransportListeners
//	 *  are informed when the
//	 *  playing really stops.
//	 */
//    public void goStop()
//    {
//        synchronized( this ) {
//			if( !isRunning() ) return;
//
//			rt_command		= CMD_STOP;
//            threadRunning   = false;
//            notifyAll();
//        }
//    }
//
//	/**
//	 *  Requests the thread to stop
//	 *  playing. Waits until transport
//	 *  has really stopped.
//	 */
//    public void stopAndWait()
//    {
//		try {
//			synchronized( this ) {
//				rt_command		= CMD_STOP;
//				threadRunning   = false;
//				notifyAll();
//				if( thread.isAlive() ) wait();
//			}
//		}
//		catch( InterruptedException e1 ) {}
//    }
//
//	/**
//	 *  Sends quit rt_command to the transport
//	 *  returns only after the transport thread
//	 *  stopped!
//	 */
//    public void quit()
//    {
//		try {
//			synchronized( this ) {
//				rt_command		= CMD_QUIT;
//				threadRunning   = false;
//				notifyAll();
//				if( thread.isAlive() ) wait();
////System.err.println( "transport stopped" );
//			}
//		}
//		catch( InterruptedException e1 ) {}
//    }
//
//// ---------------- TimelineListener interface ----------------
//
//	public void timelineChanged( TimelineEvent e )
//	{
//        synchronized( this ) {
////			calcSenseBufSize();
//			createContext();
//		}
//	}
//
//	public void timelinePositioned( TimelineEvent e )
//	{
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
//				rt_stopFrame	= isLooping() && loopStop > rt_startFrame ? loopStop : timeline.getLength();
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
//	}
//
//	public void timelineSelected( TimelineEvent e ) {}
//    public void timelineScrolled( TimelineEvent e ) {}
//
//// --------------- RealtimeHost interface ---------------
//
//	/**
//	 *  Returns whether the
//	 *  thread is currently playing
//	 *
//	 *	@return	<code>true</code> if the transport is currently playing
//	 */
//	public boolean isRunning()
//	{
////		return( thread.isAlive() && threadRunning );
//		return( threadRunning );
//	}
//
//	public void	showMessage( int type, String text )
//	{
//		System.err.println( text );
////		((ProgressComponent) root.getComponent( Main.COMP_MAIN )).showMessage( type, text );
//	}
//
//// --------------- internal actions ---------------
//}