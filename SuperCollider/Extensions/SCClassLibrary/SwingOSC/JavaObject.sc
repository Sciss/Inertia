/**
 *	Based on the fact that Object.sc "catches" all
 *	calls to unknown methods in "doesNotUnderstand",
 *	we exploit this behaviour to create an easy wrapper
 *	class for Java object control in SwingOSC.
 *
 *	@version	0.14, 06-Jan-07
 *	@author	Hanns Holger Rutz
 */
JavaObject {
	classvar allObjects;
	var <server, <id;

	*initClass {
		UI.registerForShutdown({ this.destroyAll; });
	}

	*new { arg className, server ... args;
		^super.new.prInitJavaObject( className, server, args );
	}
	
	*getClass { arg className, server;
		^super.new.prInitJavaClass( className, server );
	}

	*newFrom { arg javaObject, selector ... args;
		^super.new.prInitJavaResult( javaObject, selector, args );
	}
	
	*basicNew {Êarg id, server;
		^super.newCopyArgs( server, id );
	}
	
	prInitJavaObject { arg className, argServer, args;
		var msg;
		
		server		= argServer ?? SwingOSC.default;
		allObjects	= allObjects.add( this );	// array grows
		id			= server.nextNodeID;
		
		msg			= List[ '/local', id, '[', '/new', className ];
		this.prAddArgs( msg, args );
		msg.add( ']' );
		
//		server.sendBundle( nil, [ '/local', id, '[', '/new', className ] ++ args ++ [ ']' ]);
//msg.postln;
		server.listSendMsg( msg );
	}
	
	prInitJavaClass { arg className, argServer;
		server		= argServer ?? SwingOSC.default;
		allObjects	= allObjects.add( this );	// array grows
		id			= server.nextNodeID;
		server.sendMsg( '/local', id, '[', '/method', 'java.lang.Class', \forName, className, ']' );
	}

	prInitJavaResult { arg javaObject, selector, args;
		var msg;
		
		server		= javaObject.server;
		allObjects	= allObjects.add( this );	// array grows
		id			= server.nextNodeID;
		msg			= List[ '/local', id, '[' ];
		javaObject.prMethodCall( msg, selector, args );
		msg.add( ']' );
		server.listSendMsg( msg );
	}
		
	destroy {
		server.sendMsg( '/free', id );
		allObjects.remove( this );
	}
	
	*destroyAll {
		var list;
		list = allObjects.copy;
		allObjects = Array.new( 8 );
		list.do({ arg obj; obj.destroy; });
	}

	doesNotUnderstand { arg selector ... args;
		var selStr;
		
		selStr = selector.asString;
		if( selStr.last === $_, {
			if( thisThread.isKindOf( Routine ), {
				^this.prMethodCallAsync( selStr.copyFromStart( selStr.size - 2 ), args );
			}, {
				"JavaObject : asynchronous call outside routine".warn;
				{ ("RESULT: " ++ this.prMethodCallAsync( selStr.copyFromStart( selStr.size - 2 ), args )).postln; }.fork( SwingOSC.clock );
			});
		}, {
			server.listSendMsg( this.prMethodCall( nil, selector, args ));
		});
	}
	
	prMethodCallAsync {Êarg selector, args;
		var id, msg;
		id	= UniqueID.next;
		msg	= List[ '/query', id, '[' ];
		this.prMethodCall( msg, selector, args );
		msg.add( ']' );
		msg	= server.sendMsgSync( msg, [ '/info', id ], nil );
		^if( msg.notNil, { msg[ 2 ]}, nil );
	}
	
	prAddArgs { arg list, args;
		args.do({ arg x;
			if( x.respondsTo( \id ), {
				list.addAll([ '[', '/ref', x.id, ']' ]);
			}, {
				list.addAll( x.asSwingArg );
			});
		});
	}

	prMethodCall { arg list, selector, args;
		list = list ?? { List.new; };
		list.add( '/method' );
		list.add( id );
		list.add( selector );
		this.prAddArgs( list, args );
		^list;
	}
	
	// ---- now override a couple of methods in Object that ----
	// ---- might produce name conflicts with java methods  ----
		
	size {Êarg ... args; this.doesNotUnderstand( \size, *args ); }
	do {Êarg ... args; this.doesNotUnderstand( \do, *args ); }
	generate {Êarg ... args; this.doesNotUnderstand( \generate, *args ); }
	copy {Êarg ... args; this.doesNotUnderstand( \copy, *args ); }
	dup {Êarg ... args; this.doesNotUnderstand( \dup, *args ); }
	poll {Êarg ... args; this.doesNotUnderstand( \poll, *args ); }
	value {Êarg ... args; this.doesNotUnderstand( \value, *args ); }
	next {Êarg ... args; this.doesNotUnderstand( \next, *args ); }
	reset {Êarg ... args; this.doesNotUnderstand( \reset, *args ); }
	first {Êarg ... args; this.doesNotUnderstand( \first, *args ); }
	iter {Êarg ... args; this.doesNotUnderstand( \iter, *args ); }
	stop {Êarg ... args; this.doesNotUnderstand( \stop, *args ); }
	free {Êarg ... args; this.doesNotUnderstand( \free, *args ); }
	repeat {Êarg ... args; this.doesNotUnderstand( \repeat, *args ); }
	loop {Êarg ... args; this.doesNotUnderstand( \loop, *args ); }
	throw {Êarg ... args; this.doesNotUnderstand( \throw, *args ); }
	rank {Êarg ... args; this.doesNotUnderstand( \rank, *args ); }
	slice {Êarg ... args; this.doesNotUnderstand( \slice, *args ); }
	shape {Êarg ... args; this.doesNotUnderstand( \shape, *args ); }
	obtain {Êarg ... args; this.doesNotUnderstand( \obtain, *args ); }
	switch {Êarg ... args; this.doesNotUnderstand( \switch, *args ); }
	yield {Êarg ... args; this.doesNotUnderstand( \yield, *args ); }
	release {Êarg ... args; this.doesNotUnderstand( \release, *args ); }
	update {Êarg ... args; this.doesNotUnderstand( \update, *args ); }
	layout {Êarg ... args; this.doesNotUnderstand( \layout, *args ); }
	inspect {Êarg ... args; this.doesNotUnderstand( \inspect, *args ); }
	crash {Êarg ... args; this.doesNotUnderstand( \crash, *args ); }
	freeze {Êarg ... args; this.doesNotUnderstand( \freeze, *args ); }
	blend {Êarg ... args; this.doesNotUnderstand( \blend, *args ); }
	pair {Êarg ... args; this.doesNotUnderstand( \pair, *args ); }
	source {Êarg ... args; this.doesNotUnderstand( \source, *args ); }
	clear {Êarg ... args; this.doesNotUnderstand( \clear, *args ); }
}