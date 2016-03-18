/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	SuperCollider implementation of the java class de.sciss.app.EventManager
 *
 *	Class dependancies: BasicEvent, Collapse
 *
 *	@version	0.1, 31-Mar-06
 *	@author	Hanns Holger Rutz
 */

EventManager {
	classvar kDebug	= false;
	
	var eventProcessor;
	var	paused	= false;
	var	collListeners, collQueue, runFunc, eventQueue;

	*new {Êarg eventProcessor;
		^super.new.prInitEventManager( eventProcessor );
	}

	prInitEventManager { arg argProc;
		eventProcessor	= argProc;
		collListeners	= List.new;
		collQueue		= List.new;
		runFunc = {
			var o, eventsInCycle;
	
			if( paused.not, {
				eventsInCycle = collQueue.size;
		
				while({ eventsInCycle > 0 }, {
					o = collQueue.removeAt( 0 );
					if( o.isKindOf( BasicEvent ), {
						eventProcessor.processEvent( o );
					}, {
						if( o[ \state ], {
							if( collListeners.includes( o[ \listener ]).not, {
								collListeners.add( o[ \listener ]);
							});
						}, {
							collListeners.remove( o[ \listener ]);
						});
					});
					eventsInCycle = eventsInCycle - 1;
				});
			});
		};
		eventQueue	= Collapse( runFunc, 0.0, AppClock );
	}

	dispose {
//		synchronized( this ) {
			collListeners.clear;
			collQueue.clear;
//		}
	}
	
	addListener { arg listener;
		var a;
		if( listener.notNil, {
//			synchronized( this ) {
				// since methods executed within the eventProcessor's run method
				// are possible candidates for calling addListener(), we postpone
				// the adding so it is acertained that the getListener() calls
				// in the eventProcessor's run method won't be disturbed!!
				a = IdentityDictionary.new;
				a.put( \listener, listener );
				a.put( \state, true );
				collQueue.add( a );
				eventQueue.defer;
//				EventQueue.invokeLater( this );
//			}
		});
	}
	
	removeListener { arg listener;
		var a;
		if( listener.notNil, {
//			synchronized( this ) {
				// since methods excuted within the eventProcessor's run method
				// are possible candidates for calling removeListener(), we postpone
				// the adding so it is acertained that the getListener() calls
				// in the eventProcessor's run method won't be disturbed!!
				a = IdentityDictionary.new;
				a.put( \listener, listener );
				a.put( \state, false );
				collQueue.add( a );
				eventQueue.defer;
//				EventQueue.invokeLater( this );
//			}
		});
	}

	getListener { arg index;
		^collListeners.at( index );
	}

	countListeners {
		^collListeners.size;
	}
	
	debugDump {
		collListeners.do({ arg li, i;
			( "listen " ++ i ++ " = " ++ li ).inform;
		});
	}

	/**
	 *  Puts a new event in the queue.
	 *  If the most recent event can
	 *  be incorporated by the new event,
	 *  it will be replaced, otherwise the new
	 *  one is appended to the end. The
	 *  eventProcessor is invoked asynchronously
	 *  in the Swing event thread
	 *
	 *  @param  e   the event to add to the queue.
	 *				before it's added, the event's incorporate
	 *				method will be checked against the most
	 *				recent event in the queue.
	 */
	dispatchEvent { arg e;
		var i, o, invoke;

//sync:	synchronized( this ) {
		block {Êarg break;
			invoke  = paused.not;
			i		= collQueue.size - 1;
			if( i >= 0, {
				o = collQueue.at( i );
				if( o.isKindOf( BasicEvent ) and: { e.incorporate( o )}, {
//					collQueue.set( i, e );
// XXX ?? not replace method in List ??
					collQueue.removeAt( i );
					collQueue.insert( i, e );
//					break sync;
					break.value;
				});
			});
			collQueue.add( e );
		};

//		if( invoke, { EventQueue.invokeLater( this );});
		if( invoke, { eventQueue.defer; });
	}
	
	/**
	 *  Pauses event dispatching.
	 *  Events will still be queued, but the
	 *  dispatcher will wait to call any processors
	 *  until resume() is called.
	 */
	pause {
//		synchronized( this ) {
			paused = true;
//		} // synchronized( this )
	}
	
	/**
	 *  Resumes event dispatching.
	 *  Any events in the queue will be
	 *  distributed as normal.
	 */
	resume {
		var invoke;
	
//		synchronized( this ) {
			paused = false;
			invoke = collQueue.isEmpty.not;
//		} // synchronized( this )

//		if( invoke, { EventQueue.invokeLater( this );});
		if( invoke, { eventQueue.defer; });
	}
}