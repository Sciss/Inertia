/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies:
 *
 *	SuperCollider implementation of the java class
 *	javax.swing.undo.AbstractUndoableEdit
 *
 *	@version	0.1, 30-Mar-06
 *	@author		Hanns Holger Rutz
 */
AbstractUndoableEdit {
	var alive			= true;
	var hasBeenDone	= true;

	*new {
		^super.new;
	}

	die {
		alive	= false;
	}

	/**
	 *	@throws	CannotUndoException
	 */
	undo {
		if( this.canUndo.not, {
			MethodError( "Cannot Undo " ++ this.getPresentationName, thisMethod ).throw;
		});
		hasBeenDone	= true;
	}

	canUndo {
		^(alive && hasBeenDone);
	}

	/**
	 *	@throws	CannotRedoException
	 */
	redo {
		if( this.canRedo.not, {
			MethodError( "Cannot Redo " ++ this.getPresentationName, thisMethod ).throw;
		});
		hasBeenDone	= true;
	}

	canRedo {
		^(alive && hasBeenDone.not);
	}

	addEdit { arg anEdit;
		^false;
	}
	
	replaceEdit {Êarg anEdit;
		^false;
	}

	isSignificant {
		^true;
	}

	getPresentationName {
		^"";
	}
    
	getUndoPresentationName {
		var pName;
		
		pName = this.getPresentationName;
		^if( pName == "", "Undo", { "Undo " ++ pName });
	}

	getRedoPresentationName {
		var pName;
		
		pName = this.getPresentationName;
		^if( pName == "", "Redo", { "Redo " ++ pName });
	}
}