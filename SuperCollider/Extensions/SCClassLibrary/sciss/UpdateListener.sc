/**
 *	(C)opyright 2006-2007 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Useful adapter for descendants/update mechanism
 *
 *	@author	Hanns Holger Rutz
 *	@version	0.12, 07-Jan-07
 */
UpdateListener // interface
{
	var <>verbose = false;

	// adapter implementation
	var funcUpdate, objects;

	// interface definition
	*names {
		^[ \update ];
	}

	*new { arg update;
		^super.new.prInitUpdateListener( update );
	}
	
	*newFor {Êarg object, update;
		^this.new( update ).addTo( object );
	}
	
	addTo { arg object;
		object.addDependant( this );
		if( objects.isNil, {
			objects = IdentitySet[ object ];
		}, {
			if( objects.includes( object ), {
				MethodError( "Cannot attach to the same object more than once", thisMethod ).throw;
			});
			objects.add( object );
		});
	}

	removeFrom { arg object;
		object.removeDependant( this );
		if( objects.includes( object ).not, {
			MethodError( "Was not attached to this object", thisMethod ).throw;
		});
		objects.remove( object );
	}
	
	removeFromAll {
		objects.do({ arg object;
			object.removeDependant( this );
		});
		objects = nil;
	}
	
	// same as removeFromAll ; makes transition from Updater easier
	remove {
		^this.removeFromAll;
	}

	prInitUpdateListener { arg update;
		funcUpdate = update;
	}
	
	update { arg object ... args;
		if( verbose, {
			("UpdateListener.update : object = "++object++"; status = "++args.first).postln;
		});
		^funcUpdate.value( this, object, *args );
	}
}