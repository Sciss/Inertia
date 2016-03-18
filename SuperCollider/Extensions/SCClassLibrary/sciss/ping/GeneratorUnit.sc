/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe, SynthDefCache
 *
 *	@author	Hanns Holger Rutz
 *	@version	0.12, 19-Jul-06
 *
 *	@todo	buffers must be stored per instance (of subclass) !!!
 */
GeneratorUnit : Object {
	var <server;
	var volume = 1.0;
	var numChannels = 0, inBus, outBus, playing, target = nil;
	var <>verbose = false;
	var nw;
	var defCache;
	var nodes;			// Set . elem = Node
	var globalBuffers;		// Set . elem = Buffer
	var nodeBuffers;		// IdentityDictionary . key = nodeID, value = Set (elem = Buffer)
	
	// debugging
	var debugSynthsCreated;
	var debugSynthsBorn;
	var debugSynthsDestroyed;
	var debugSynthsDied;
	var debugBufsAlloced;
	var debugBufsFreed;
	
	var <disposed	= false;
	
	var name;

	// ----------- instantiation -----------

	*new { arg server;
		^super.new.prInitGeneratorUnit( server );
	}
	
	asString {
		^("a "++this.class.name++" (hash "++this.hash++")");
	}
	
	prInitGeneratorUnit { arg argServer;
		server		= argServer ?? Server.default;
		nw			= NodeWatcher.newFrom( server );
		defCache		= SynthDefCache.newFrom( server );
		name			= this.class.name.asString;
		name			= name.copyFromStart( name.size - 5 ).asSymbol;

		CmdPeriod.add( this );
		server.addDependant( this );
		this.prReinitialize;
	}

	// ----------- class methods -----------

	// ----------- public instance methods -----------

	getAttributes {
		^this.subclassResponsibility( thisMethod );
	}
	
	getName {
		^name;
	}
	
	setName { arg n;
		name = n;
	}

	dispose {
		if( disposed, {
			TypeSafe.methodError( thisMethod, "Object already disposed" );
			^this;
		});

		this.stop;
		this.freeBuffers;
		CmdPeriod.remove( this );
		server.removeDependant( this );
		disposed = true;
		this.changed( \unitDisposed );
	}
	
	freeBuffers {
		this.prFreeNodeBuffers;
		this.prFreeGlobalBuffers;
	}
	
	isPlaying {
		^playing;
	}

	/**
	 *	Returns the number of output channels
	 *	generated by this object. This value
	 *	may initially be zero and change depending
	 *	on object state (e.g. setting sound file path
	 *	for a TapeUnit).
	 *
	 *	@returns	(Integer) number of current output channels
	 */
	getNumChannels {
		^numChannels;
	}
	
	/**
	 *	Throws an exception. Do not use this method.
	 *	This overrides numChannels in Object.sc
	 */
	numChannels {
		^this.shouldNotImplement( thisMethod );
	}

	setGroup { arg group;
		var bndl;
		target = group;
		if( nodes.notEmpty, {
			bndl = List.new;
			nodes.do({ arg node;
				bndl.add( node.moveToHeadMsg( group ));
			});
			server.listSendBundle( nil, bndl );
		});
	}
	
	getGroup {
		^target;
	}

	setOutputBus { arg bus;
		var bndl;
		outBus = bus;
		if( nodes.notEmpty, {
			nodes.do({ arg node;
				bndl.add( node.setMsg( \out, outBus.index ));
			});
			server.listSendBundle( nil, bndl );
		});
	}
	
	setOutputBusToBundle { arg bndl, bus;
		outBus = bus;
		if( nodes.notEmpty, {
			nodes.do({ arg node;
				bndl.add( node.setMsg( \out, outBus.index ));
			});
//			bndl.postln;
		});
	}
	
	getOutputBus {
		^outBus;
	}
	
	setInputBus { arg bus;
		var bndl;
		inBus = bus;
		if( nodes.notEmpty, {
			nodes.do({ arg node;
				bndl.add( node.setMsg( \in, inBus.index ));
			});
			server.listSendBundle( nil, bndl );
		});
	}
	
	getInputBus {
		^inBus;
	}
	
	setVolume { arg vol;
		var bndl;
		volume = vol;
		if( nodes.notEmpty, {
			bndl = List.new;
			nodes.do({ arg node;
				bndl.add( node.setMsg( \volume, volume ));
			});
			server.listSendBundle( nil, bndl );
		});
	}

	duplicate {
		var dup;
		
		if( disposed, {
			TypeSafe.methodError( thisMethod, "Trying to revive a disposed object" );
			^nil;
		});
		
		dup = this.class.new( server );
		dup.setGroup( this.getGroup );
		dup.setInputBus( this.getInputBus );
		dup.setOutputBus( this.getOutputBus );
		dup.setVolume( this.getVolume );
		this.protDuplicate( dup );
		^dup;
	}
	
	getVolume {
		^volume;
	}

	stop { arg rls;
		var bndl;
		if( nodes.notEmpty, {
			bndl = List.new;
			nodes.do({ arg node;
				if( rls.notNil, {
					bndl.add( node.releaseMsg( rls ));
				}, {
					bndl.add( node.freeMsg );
				});
			});
			server.listSendBundle( nil, bndl );
		});
	}

	stopToBundle { arg bndl, rls;
		if( nodes.notEmpty, {
			nodes.do({ arg node;
				if( rls.notNil, {
					bndl.add( node.releaseMsg( rls ));
				}, {
					bndl.add( node.freeMsg );
				});
			});
		});
	}

	debugTrace {
		nodes.do({ arg node;
			node.trace;
		});
	}
	
	/**
	 *	@todo	should check consistency, e.g. look for orphaned node buffers
	 */
	debugDump {
		this.asString.postln;
		("  Buffers alloc'ed " ++ debugBufsAlloced ++ "; freed " ++ debugBufsFreed).postln;
		("  Synths created   " ++ debugSynthsCreated ++ "; born  " ++ debugSynthsBorn ++
			"; destroyed " ++ debugSynthsDestroyed ++ "; died  " ++ debugSynthsDied).postln;
	}

	// ----------- protected instance methods -----------

// ZZZ DON'T USE NOW
	protAddNodeBuffer { arg node, buf;
		var bufSet;
	
		if( nodes.includes.not( node ), {
			TypeSafe.methodError( thisMethod, "Node not found " ++ node );
		});
		
		bufSet = nodeBuffers[ node.nodeID ];
		if( bufSet.isNil, {
			bufSet = Set.new;
			nodeBuffers.put( node.nodeID, bufSet );
		});
		bufSet.add( buf );
		debugBufsAlloced = debugBufsAlloced + 1;
	}

// ZZZ DON'T USE NOW
	/**
	 *	@returns	the removed buffer
	 */
	protRemoveNodeBuffer { arg node, buf;
		var bufSet;
	
		if( nodes.includes.not( node ), {
			TypeSafe.methodError( thisMethod, "Node not found " ++ node );
		});
		
		bufSet = nodeBuffers[ node.nodeID ];
		bufSet.remove( buf );
		debugBufsFreed = debugBufsFreed + 1;
		^buf;
	}

// ZZZ DON'T USE NOW
	protAddGlobalBuffer { arg buf;
		globalBuffers.add( buf );
		debugBufsAlloced = debugBufsAlloced + 1;
	}

// ZZZ DON'T USE NOW
	/**
	 *	@returns	the removed buffer
	 */
	protRemoveGlobalBuffer { arg buf;
		globalBuffers.remove( buf );
		debugBufsFreed = debugBufsFreed + 1;
		^buf;
	}

	protAddNode { arg node;
		nodes.add( node );
		nw.register( node );
		node.addDependant( this );
		debugSynthsCreated = debugSynthsCreated + 1;
	}

	protRemoveNode { arg node;
		if( nodes.includes( node ), {
			nodes.remove( node );
			nw.unregister( node );
			node.removeDependant( this );

			debugSynthsDestroyed = debugSynthsDestroyed + 1;
		});
	}
	
	protSetPlaying {�arg state;
		playing = state;
		this.changed( \unitPlaying, state );
	}

	protDuplicate {�arg dup; }

	// ----------- private instance methods -----------

// ZZZ DON'T USE NOW
	prFreeNodeBuffers {
		var bndl;
		if( nodeBuffers.notEmpty, {
			nodeBuffers.do({ arg bufSet;
				bufSet.do({ arg buf;
					bndl.add( buf.closeMsg( buf.freeMsg ));
				});
				debugBufsFreed = debugBufsFreed + bufSet.size;
			});
			server.listSendBundle( bndl );
			nodeBuffers = IdentityDictionary.new;
		});
	}
	
// ZZZ DON'T USE NOW
	prFreeGlobalBuffers {
		var bndl;
		if( globalBuffers.notEmpty, {
			globalBuffers.do({ arg buf;
				bndl.add( buf.closeMsg( buf.freeMsg ));
			});
			debugBufsFreed = debugBufsFreed + globalBuffers.size;
			server.listSendBundle( bndl );
			globalBuffers = Set.new;
		});
	}

	prReinitialize {
		nodes				= Set.new;
		globalBuffers			= Set.new;
		nodeBuffers			= IdentityDictionary.new;

		debugSynthsCreated		= 0;
		debugSynthsBorn		= 0;
		debugSynthsDestroyed	= 0;
		debugSynthsDied		= 0;
		debugBufsAlloced		= 0;
		debugBufsFreed		= 0;
		
		target				= nil;
		this.protSetPlaying( false );
	}

	// ----------- quasi-interface methods -----------
	
	update { arg obj, what;
		// from Nodes
		case { what === \n_go }
		{
			this.n_go( obj );
		}
		{ what === \n_end }
		{
			this.n_end( obj );
		}
		{ what === \n_on }
		{
			this.n_on( obj );
		}
		{ what === \n_off }
		{
			this.n_off( obj );
		}
		// from Server
		{ what === \serverRunning }
		{
			if( obj.serverRunning, {
				fork {
					1.0.wait;		// node allocators are slow in doWhenBooted !!!
					this.protServerStarted;
				};
			}, {
				this.protServerStopped;
			});
		};
	}
	
	/**
	 *	Subclasses can override this but are
	 *	obliged to INITIALLY call super.cmdPeriod !
	 */
	cmdPeriod {
		this.freeBuffers;
		this.prReinitialize;
	}
	
	/**
	 *	Subclasses can override this but are
	 *	obliged to INITIALLY call super.protServerStarted !
	 */
	protServerStarted {
	}

	/**
	 *	Subclasses can override this but are
	 *	obliged to INITIALLY call super.protServerStopped !
	 */
	protServerStopped {
		this.prReinitialize;
	}

	/**
	 *	Subclasses can override this but are
	 *	obliged to call super.n_go !
	 */
	n_go {�arg node;
		debugSynthsBorn = debugSynthsBorn + 1;
	}

	/**
	 *	Subclasses can override this but are
	 *	obliged to call super.n_end !
	 */
	n_end {�arg node;
		debugSynthsDied = debugSynthsDied + 1;
		this.protRemoveNode( node );
	}

	/**
	 *	Subclasses can override this but are
	 *	obliged to call super.n_go !
	 */
	n_off { arg node;	
	}	

	/**
	 *	Subclasses can override this but are
	 *	obliged to call super.n_go !
	 */
	n_on { arg node;
	}

	// ----------- abstract methods -----------
 
	play {
		^this.subclassResponsibility( thisMethod );
	}
	
	playToBundle {
		^this.subclassResponsibility( thisMethod );
	}
}

UnitAttr : Object {
//	var <unit;
	var <name;
	var <spec;
	var <type;
	var <getter;
	var <setter;
	var <updates;
	var <shouldFade;

	*new { arg name, spec, type, getter, setter, updates, shouldFade = true;
		^super.new.prInitUnitAttr( name, spec, type, getter, setter, updates, shouldFade );
	}
	
	prInitUnitAttr { arg argName, argSpec, argType, argGetter, argSetter, argUpdates, argShouldFade;
//		var str;

		TypeSafe.checkArgClasses( thisMethod, [ argName, argSpec,     argType, argGetter, argSetter, argUpdates ],
		                                      [ Symbol,  ControlSpec, Symbol,  Symbol,    Symbol,    Set ],
		                                      [ false,   false,       false,   true,      true,      true ]);

//		unit		= argUnit;
		name		= argName;
		spec		= argSpec;
		type		= argType;
		getter	= argGetter;
		setter	= argSetter;
		updates	= argUpdates;
		shouldFade = argShouldFade;
	}
	
//	getValue {
//		^unit.perform( getter );
//	}

	getValue { arg unit;
		^unit.perform( getter );
	}

	getNormalizedValue { arg unit;
		^spec.unmap( this.getValue( unit ));
	}
}