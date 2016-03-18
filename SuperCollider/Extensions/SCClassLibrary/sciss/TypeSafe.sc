/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: (none)
 *
 *	@version	0.11, 20-Jul-06
 *	@author	Hanns Holger Rutz
 */
TypeSafe {
	classvar <>enabled = true;

	*methodInform {Êarg method, message;
		(method.ownerClass.name ++ "." ++ method.name ++ " : " ++ message).inform;
	}

	*methodWarn {Êarg method, message;
		(method.ownerClass.name ++ "." ++ method.name ++ " : " ++ message).warn;
	}

	*methodError {Êarg method, message;
		(method.ownerClass.name ++ "." ++ method.name ++ " failed: " ++ message).error;
	}

	*checkArgResp {Êarg method, obj ... selectors;
		if( enabled.not, { ^true; });
		
		selectors.do({ arg selector;
			if( obj.respondsTo( selector ).not, {
				(method.ownerClass.name ++ "." ++ method.name ++ " : Argument type mismatch : " ++
				obj.class ++ " does not respond to '" ++ selector ++ "'").error;
				^false;
			});
		});
		^true;
	}
	
	*checkInterface {Êarg method, obj, interf, nilAllowed;
		if( enabled.not, { ^true; });

		if( obj.isNil, {
			if( nilAllowed.not, {
				(method.ownerClass.name ++ "." ++ method.name ++
					" : Argument type mismatch : nil not allowed").error;
				^false;
			});
		}, { if( obj.isKindOf( interf ).not, {
			^this.checkArgResp( method, obj, *interf.names );
		})});
		^true;
	}

	*checkAnyArgClass {Êarg method, obj ... classes;
		if( enabled.not, { ^true; });
		
		classes.do({ arg class;
			if( obj.isKindOf( class ), { ^true; });
		});
		
		(method.ownerClass.name ++ "." ++ method.name ++
			" : Argument type mismatch").error;
		^false;
	}

	*checkArgClass {Êarg method, obj, kind, nilAllowed = true;
		if( enabled.not, { ^true; });
		
		if( nilAllowed.not && obj.isNil, {
			(method.ownerClass.name ++ "." ++ method.name ++
				" : Argument type mismatch : nil not allowed").error;
			^false;
		}, { if( obj.isNil.not && obj.isKindOf( kind ).not, {
			(method.ownerClass.name ++ "." ++ method.name ++ " : Argument type mismatch : " ++
				obj.class ++ " is not a kind of " ++ kind).error;
			^false;
		})});
		^true;
	}

	*checkArgClasses {Êarg method, args, classes, nilAllowed;
		var success = true;
		
		if( enabled.not, { ^true; });
	
//		method.argNames.postln;
		
		args.do({ arg agga, idx;
			if( nilAllowed[ idx ].not && agga.isNil, {
				(method.ownerClass.name ++ "." ++ method.name ++
					" : Argument type mismatch (" ++ method.argNames[ idx + 1 ] ++
					") : nil not allowed").error;
				success = false;
			}, { if( agga.isNil.not && agga.isKindOf( classes[ idx ]).not, {
				(method.ownerClass.name ++ "." ++ method.name ++
					" : Argument type mismatch (" ++ method.argNames[ idx + 1 ] ++
					") : " ++ agga.class ++ " is not a kind of " ++ classes[ idx ]).error;
				success = false;
			})});
		});
		^success;
	}
}