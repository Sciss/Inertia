/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe
 *
 *	@version	0.12, 11-Jul-06
 *	@author	Hanns Holger Rutz
 *
 *	@todo	a way to remove all defs belonging to one machine
 */
SynthDefCache {
	classvar all;		// (IdentityDictionary). key = (Server) s, value = (SynthDefCache) instance

	/**
	 *	(Server) The SuperCollider server
	 *	used by this cache.
	 */
	var <server;

	var defs;			// (IdentityDictionary). key = (Symbol) defName, value = (SynthDef) def
	var defsPending;	// (IdentitySet). elem = (SynthDef) def

	classvar uniqueNumber = 0;

	*initClass {
		all = IdentityDictionary.new;
		CmdPeriod.add( this );
	}

	// -------------- instantiation --------------

	/**
	 *	Creates a new instance for a given server.
	 *	Usually you should use *newFrom instead.
	 *	Note that instances created this way are
	 *	not stored in the all-instances table!
	 *
	 *	@param	server	the SuperCollider server to use
	 */
	*new {Êarg server;
		^super.new.prInitSynthDefCache( serverÊ);
	}

	// -------------- public class methods --------------
	
	/**
	 *	Disposes all instances of this class
	 */
	*disposeAll {
		var copy;
		
		copy = all.copy;
		copy.do({ arg item; item.dispose; });
		
		all = IdentityDictionary.new;
	}

	/**
	 *	Returns a cache instance for a given server.
	 *	Note that this instance is shared, so there
	 *	is just one cache for each server.
	 *
	 *	@param	server	the SuperCollider server to use
	 *	@return			a SynthDefCache instance
	 */
	*newFrom { arg server;
		var result;
		result = all[ server.name ];
		if( result.isNil, {
			result = this.new( server );
			all.put( server.name, result );
		});
		^result;
	}

	// -------------- public instance methods --------------

	/**
	 *	Clears the cache. Removes all
	 *	registered and all pending defs.
	 */
	clear {
		defs = IdentityDictionary.new;
		this.flush;
	}

	/**
	 *	Flushes the cache. This means
	 *	that all registered defs are considered
	 *	pending, so they will be sent again to
	 *	the server once it is started.
	 */
	flush {
		defsPending = IdentitySet.newFrom( defs );
	}
	
	/**
	 *	Disposes the cache. Frees all
	 *	resources associated with this object.
	 *	You should not use this object again.
	 *	Note that you should not call this method
	 *	on caches which were obtained through
	 *	*newFrom since other objects may still
	 *	rely on the cache. In debugging contexts,
	 *	you may find *disposeAll more usefull.
	 */
	dispose {
//		var result;
		
		server.removeDependant( this );
		this.clear;
//		result = all[ server.name ];
//		if( result === this, {
//			all.removeAt( server.name );
//		});
	}
	
	/**
	 *	Adds a def to the cache. If the server
	 *	is online, the def is sent immediately,
	 *	otherwise it's marked pending and sent
	 *	as soon as the server becomes available.
	 *
	 *	@param	def	(SynthDef) the def to cache
	 *	@return	(Boolean) true if the def was sent immediately
	 *			; note that in this case to instantiate a synth
	 *			immediately, you should insert a server.sync
	 *			statement to be sure that the asynchronous
	 *			/d_recv message has been processed by the server
	 */
	add { arg def;
		defs.put( def.name.asSymbol, def );
		if( server.serverRunning, {
			def.send( server );
			^true;
		}, {
			defsPending.add( def );
			^false;
		});
	}
	
	/**
	 *	Adds a def to the cache. The def is marked
	 *	pending even if the server is online.
	 *	You can force the cache to send pending defs
	 *	by calling sendPending.
	 *
	 *	@param	def	(SynthDef) the def to cache
	 */
	addPending { arg def;
		defs.put( def.name.asSymbol, def );
		defsPending.add( def );
	}
	
	/**
	 *	If the server is running, sends out all pending defs.
	 *	If the server is not running, this is a no-op
	 *	but a warning is printed.
	 */
	sendPending {
		if( server.serverRunning, {
			// XXX wait ?
			defsPending.do({ arg def;
				def.send( server );
			});
			defsPending = IdentitySet.new;
		}, {
			TypeSafe.methodWarn( thisMethod, "Server not running" );
		});
	}
	
	remove { arg defName;
		var def;
		
		defName	= defName.asSymbol;
		def		= defs.removeAt( defName );
		defsPending.remove( def );
	}
	
	/**
	 *	Constructs a unique synth def name you can
	 *	use. This avoid name space conflicts in complex
	 *	environments with a lot of different classes
	 *	creating defs.
	 *
	 *	@return	(Symbol) a unique (as far as this cache knows) synth def name
	 */
	uniqueName {
		var n;
		
		n			= uniqueNumber;
		uniqueNumber	= uniqueNumber + 1;
	
		^("tmp" ++ n).asSymbol;
	}
	
	/**
	 *	Checks whether a def was registered with the cache.
	 *
	 *	@param	defName	(Symbol or String) name of the def to check
	 *	@return	(Boolean) true if the cache was registered (added)
	 */
	contains {Êarg defName;
		defName = defName.asSymbol;
		^defs.includesKey( defName );
	}
	
	/**
	 *	Checks whether a synth def has already been sent to the server
	 *
	 *	@param	defName	(Symbol or String) name of the synth def to check
	 *	@return	(Boolean) true if the def was registered with the cache and is not pending
	 */
	isOnline { arg defName;
		defName = defName.asSymbol;
		^(defs.includesKey( defName ) and: {ÊdefsPending.includes( defName ).not; });
	}
	
	/**
	 *	Checks whether a synth def has already been sent to the server
	 *
	 *	@param	defName	(Symbol or String) name of the synth def to check
	 *	@return	(Boolean) true if the def was registered with the cache and is still pending
	 */
	isOffline { arg defName;
		defName = defName.asSymbol;
		^defsPending.includes( defName );
	}
	
	// --------------- quasi-interface methods ---------------

	update {Êarg obj, what;
		if( obj === server and: { what === \serverRunning }, {
			if( server.serverRunning, {
				this.sendPending;
			}, {	// stopped
				this.flush;
			});
		});
	}

	// flushes all instances
	*cmdPeriod { all.do({ arg item; item.flush; }); }

	// --------------- private methods ---------------

	prInitSynthDefCache { arg argServer;
		server = argServer;
		this.clear;
		server.addDependant( this );
	}
}