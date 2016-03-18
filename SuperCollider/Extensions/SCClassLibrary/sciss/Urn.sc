/**
 *	(C)opyright 2006-2007 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Urn Objekt similar to the one in Max.
 *
 *	An urn is a unordered collection of items. Random items
 *	are taken out of the urn using the 'next' method. Since the
 *	returned items are removed from the urn, it is garantueed that
 *	for example in an incremental series of numbers (lottery), no number is
 *	returned twice unless the urn is refilled. (the Urn is similiar
 *	to a series in serial music).
 *
 *	The urn is backed up by a "memory" so that it is possible to 'reset'
 *	(refill) the urn, i.e. put all memorized items back into the urn.
 *
 *	@author	Hanns Holger Rutz
 *	@version	0.12, 26-Jan-07
 */
Urn {
	var mem, urn;
	
	/**
	 *	(Boolean) whether the urn is automatically
	 *	refilled or not. defaults to 'false'
	 */
	var <autoReset = false;

	/**
	 *	Creates a new empty urn.
	 */
	*new {
		^super.new.prInitUrn;
	}
	
	/**
	 *	Creates an urn filled with the passed-in items.
	 *	You can also write Urn[ 1, 2, 3 ] as a shorthand for
	 *	Urn.new.addAll( ... )
	 *
	 *	@param	coll	(Collection) the items to put in the urn
	 *	@return	the new urn
	 */
	*newUsing { arg coll;
		^this.new.addAll( coll ).reset;
	}
	
	/**
	 *	Refills the urn with the items
	 *	that had been put into it before.
	 */
	reset {
		urn = mem.copy;
	}
	
	/**
	 *	Specifies whether the urn is to be automatically refilled or not.
	 *
	 *	@param	onOff	(Boolean) if 'true', the urn is automatically
	 *					refilled when it gets empty. if 'false', the
	 *					urn must be manually refilled by calling 'reset'
	 */
	autoReset_ { arg onOff;
		autoReset = onOff;
		if( urn.isEmpty && autoReset, {
			this.reset;
		});
	}
	
	/**
	 *	Picks (and removes) a new item from the urn.
	 *
	 *	@return	a new random item from the urn, or 'nil' if the urn is empty.
	 */
	next {
		var result;
		
		if( urn.isEmpty, { ^nil });
		result = urn.removeAt( urn.size.rand );
		if( urn.isEmpty && autoReset, {
			this.reset;
		});
		^result;
	}
	
	/**
	 *	Removes a particular item from the urn. If the urn becomes empty
	 *	and 'autoReset' is 'true', the urn is refilled. This method has no
	 *	return value. Note that unlike 'add', the item is not removed from
	 *	the memory of items that have been ever added to the urn, therefore
	 *	the 'item' reappears in the urn after calling 'reset'!
	 *
	 *	@param	item	the item to remove from the urn. if the item is not
	 *			in the urn, nothing will happen.
	 */
	remove {Êarg item;
		urn.remove( item );
		if( urn.isEmpty && autoReset, {
			this.reset;
		});
	}
	
	/**
	 *	Checks whether an item is still in the urn.
	 *
	 *	@param	item	the item to look for
	 *	@return	(Boolean) whether the 'item' is still in the urn
	 */
	includes { arg item;
		^urn.includes( item );
	}
	
	/**
	 *	Queries whether items are left in the urn. Note that
	 *	for an urn to which items have been added and whose 'autoReset'
	 *	is 'true', this method will always return 'true'.
	 *
	 *	@return	(Boolean) 'true', if the urn still contains items,
	 *			'false' otherwise
	 */
	hasNext {
		^urn.notEmpty;
	}
	
	/**
	 *	Adds a collection of items to both the urn and the memory.
	 *
	 *	@param	coll	(Collection) the items to add. Duplicate items are allowed
	 */
	addAll { arg coll;
		mem.addAll( coll );
		urn.addAll( coll );
	}
	
	/**
	 *	Adds an item to both the urn and the memory.
	 *
	 *	@param	item	the item to add. Duplicate items are allowed
	 */
	add {Êarg item;
		mem.add( item );
		urn.add( item );
	}

	/**
	 *	Removes all items from the urn and clears the memory.
	 *	You will have to add items again using 'add' or 'addAll'.
	 *	This is the only way the memory can be erased.
	 */	
	clear {
		mem.clear;
		urn.clear;
	}
	
	/**
	 *	Queries the size of items left in the urn.
	 *
	 *	@return	the number of items left in the urn
	 */
	size {
		^urn.size;
	}
	
	/**
	 *	Queries the size of items in the memory. That is
	 *	the number items that will appear in the urn when
	 *	'reset' is called.
	 *
	 *	@return	the total number of items in the memory
	 */
	fullSize {
		^mem.size;
	}

	// ----------------- private -----------------

	prInitUrn {
		mem	= List.new;
		urn	= List.new;
	}
}