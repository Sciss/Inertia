/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	A SuperCollider implementation of the java class de.sciss.timebased.BasicTrail
 *
 *	Class dependancies: Span, Stake, AbstractUndoableEdit, EventManager, IOException
 *
 *	Changelog:
 *		14-Sep-06		a couple of bug fixes
 *
 *	@version	0.13, 30-Dec-06
 *	@author	Hanns Holger Rutz
 *
 *	@todo	asCompileString
 */
Trail {
	classvar <kTouchNone		= 0;
	classvar <kTouchSplit		= 1;
	classvar <kTouchResize		= 2;

	classvar	kDebug			= false;
	classvar	startComparator;
	classvar	stopComparator;
	
	var		collStakesByStart;
	var		collStakesByStop;

	var		rate;
	
	var		elm				= nil;	// lazy creation
	var		dependants		= nil;	// lazy creation
	
	var		touchMode;

	*initClass {
		startComparator	= { arg o1, o2;

			if( o1.respondsTo( \getSpan ), { o1 = o1.getSpan; });
			if( o2.respondsTo( \getSpan ), { o2 = o2.getSpan; });
			
			Span.startComparator.value( o1, o2 );
		};

		stopComparator	= { arg o1, o2;

			if( o1.respondsTo( \getSpan ), { o1 = o1.getSpan; });
			if( o2.respondsTo( \getSpan ), { o2 = o2.getSpan; });
			
			Span.stopComparator.value( o1, o2 );
		};
	}
	
	*new { arg touchMode = kTouchNone;
		^super.new.prInitTrail( touchMode );
	}
	
	prInitTrail { arg argTouchMode;
		touchMode			= argTouchMode;
		collStakesByStart	= List.new;
		collStakesByStop	= List.new;
	}
	
	getRate {
		^rate;
	}
	
	setRate { arg newRate;
		rate	= newRate;
	}

	clear { arg source;
		var wasEmpty, span, stake;
		
		wasEmpty	= this.isEmpty;
		span		= this.getSpan;
	
		while({ collStakesByStart.isEmpty.not }, {
			stake = collStakesByStart.removeAt( 0 );
			stake.setTrail( nil );
		});
		collStakesByStop.clear;

		// ____ dep ____
		dependants.do({ arg dep;
			dep.clear( source );
		});

		if( source.notNil && wasEmpty.not, {
			this.prDispatchModification( source, span );
		});
	}

	dispose {
		collStakesByStart.do({ arg stake;
			stake.dispose;
		});
	
		collStakesByStart.clear;
		collStakesByStop.clear;

		// ____ dep ____
		dependants.do({ arg dep;
			dep.dispose;
		});
	}

	getSpan {
		^Span( this.prGetStart, this.prGetStop );
	}
	
	prGetStart {
		^collStakesByStart.isEmpty.if( 0, { collStakesByStart.first.getSpan.start });
	}
	
	prGetStop {
		^collStakesByStop.isEmpty.if( 0, { collStakesByStop.last.getSpan.stop });
	}

	prBinarySearch {Êarg coll, newObject, function;
		var index;
		var low	= 0;
		var high	= coll.size - 1;
//var cnt=0;
		
//("coll "++coll++"; newObject "++newObject++"; function "++function).postln;
//if( true, {Ê^-1; });
		
		while({ 
			index  = (high + low) div: 2;
//cnt = cnt + 1;
//if( cnt > 10, {
//	("FUCK! low = "++low++"; high = "++high++"; index = "++index).error;
//	^-1;
//});
//			low   <= high;
			low   <= high;
		}, {
//("compare:  coll.at( "++index++" ) = "++coll.at( index ) ++ "; newObject = "++newObject).inform;
			switch( function.value( coll.at( index ), newObject ),
			0,Ê{Ê^index; },
			-1, {
				low = index + 1;
			},
			1, {
				high = index - 1;
			},
			{
				"Illegal result from comparator".error;
				^-1;
			});
		});
		^(low.neg - 1);	// as in java.util.Collections.binarySearch !
	}

	// returns stakes that intersect OR TOUCH the span
	getRange { arg span, byStart = true;
		var	collUntil, collFrom, collResult, idx;
	
		// "If the list contains multiple elements equal to the specified object,
		//  there is no guarantee which one will be found"
		idx			= this.prBinarySearch( collStakesByStart, span.stop, startComparator );

		if( idx < 0, {
			idx		= (idx + 1).neg;
		}, {
			idx		= this.getRightMostIndex( idx, true ) + 1;
		});
//		collUntil	= collStakesByStart.subList( 0, idx );
		collUntil	= collStakesByStart.copyFromStart( idx - 1 );
		idx			= this.prBinarySearch( collStakesByStop, span.start, stopComparator );

		if( idx < 0, {
			idx		= (idx + 1).neg;
		}, {
			idx		= this.getLeftMostIndex( idx, false );
		});
//		collFrom	= collStakesByStop.subList( idx, collStakesByStop.size() );
		collFrom	= collStakesByStop.copyToEnd( idx );

		// XXX should be optimized
		collResult	= collUntil.select({ arg stake; collFrom.includes( stake )});

		^collResult;
	}

//	insert { arg source, span, ce;
//
//		this.insert( source, span, this.getDefaultTouchMode, ce );
//	}
	
	insertSpan { arg source, span, touchMode, ce;
		var start, stop, totStop, delta, collRange, collToAdd, collToRemove, modSpan, stake, stakeSpan;

		touchMode		= touchMode ?? { this.getDefaultTouchMode; };

		start		= span.start;
		stop			= span.stop;
		totStop		= this.prGetStop;
		delta		= span.getLength;
		
		if( (delta == 0) || (start > totStop), { ^this; });
		
		collRange		= this.getRange( Span( start, totStop ), true );
		
		if( collRange.isEmpty, {Ê^this; });
		
		collToAdd		= List.new;
		collToRemove	= List.new;
		
		switch( touchMode,
		kTouchNone, {
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.start >= start, {
					collToRemove.add( stake );
					collToAdd.add( stake.shiftVirtual( delta ));
				});
			});
		},
			
		kTouchSplit, {
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.stop > start, {
					collToRemove.add( stake );

					if( stakeSpan.start >= start, {			// not splitted
						collToAdd.add( stake.shiftVirtual( delta ));
					}, {
						collToAdd.add( stake.replaceStop( start ));
						stake = stake.replaceStart( start );
						collToAdd.add( stake.shiftVirtual( delta ));
						stake.dispose;	// delete temp product
					});
				});
			});
		},
			
		kTouchResize, {
"BasicTrail.insert, touchmode resize : not tested".warn;
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.stop > start, {
					collToRemove.add( stake );
					if( stakeSpan.start > start, {
						collToAdd.add( stake.shiftVirtual( delta ));
					}, {
						collToAdd.add( stake.replaceStop( stakeSpan.stop + delta ));
					});
				});
			});
		},
		
		// default:
		{
			MethodError( "Illegal Argument TouchMode : " ++ touchMode, thisMethod ).throw;
		});

		modSpan		= Span.union( this.prRemoveAll( collToRemove, ce ), this.prAddAll( collToAdd, ce ));

		// ____ dep ____
		dependants.do({ arg dep;
			dep.insertSpan( source, span, touchMode, ce );
		});

		if( source.notNil && modSpan.notNil, {
			this.prDispatchModification( source, modSpan );
			if( ce.notNil, { ce.addEdit( TrailEdit.newDispatch( modSpan ))});
		});
	}

//	remove { arg source, span, ce )
//	{
//		this.remove( source, span, this.getDefaultTouchMode, ce );
//	}

	/**
	 *	Removes a time span from the trail. Stakes that are included in the
	 *	span will be removed. Stakes that begin after the end of the removed span,
	 *	will be shifted to the left by <code>span.getLength()</code>. Stakes whose <code>stop</code> is
	 *	<code>&lt;=</code> the start of removed span, remain unaffected. Stakes that intersect the
	 *	removed span are traited according to the <code>touchMode</code> setting:
	 *	<ul>
	 *	<li><code>kTouchNone</code></li> : intersecting stakes whose <code>start</code> is smaller than
	 *		the removed span's start remain unaffected ; otherwise they are removed. This mode is usefull
	 *		for markers.
	 *	<li><code>kTouchSplit</code></li> : the stake is cut at the removed span's start and stop ; a
	 *		middle part (if existing) is removed ; the left part (if existing) remains as is ; the right part
	 *		(if existing) is shifted by <code>-span.getLength()</code>. This mode is usefull for audio regions.
	 *	<li><code>kTouchResize</code></li> : intersecting stakes whose <code>start</code> is smaller than
	 *		the removed span's start, will keep their start position ; if their stop position lies within the
	 *		removed span, it is truncated to the removed span's start. if their stop position exceeds the removed
	 *		span's stop, the stake's length is shortened by <code>-span.getLength()</code> . 
	 *		intersecting stakes whose <code>start</code> is greater or equal to the
	 *		the removed span's start, will by shortened by <code>(removed_span_stop - stake_start)</code> and
	 *		shifted by <code>-span.getLength()</code> . This mode is usefull for marker regions.
	 *	</ul>
	 *
	 *	@param	source		source object for event dispatching (or <code>null</code> for no dispatching)
	 *	@param	span		the span to remove
	 *	@param	touchMode	the way intersecting staks are handled (see above)
	 *	@param	ce			provided to make the action undoable ; may be <code>null</code>. if a
	 *						<code>CompoundEdit</code> is provided, disposal of removed stakes is deferred
	 *						until the edit dies ; otherwise (<code>ce == null</code>) removed stakes are
	 *						immediately disposed.
	 */
	removeSpan { arg source, span, touchMode, ce;
		var start, stop, totStop, delta, collRange, collToAdd, collToRemove, modSpan, stake, stakeSpan;

		touchMode		= touchMode ?? { this.getDefaultTouchMode; };

		start			= span.start;
		stop			= span.stop;
		totStop			= this.prGetStop;
		delta			= span.getLength.neg;
		
		if( (delta == 0) || (start > totStop), {Ê^this; });
		
		collRange		= this.getRange( Span( start, totStop ), true );
		
		if( collRange.isEmpty, { ^this; });
		
		collToAdd		= List.new;
		collToRemove	= List.new;
		
		switch( touchMode,
		kTouchNone, {
			// XXX could use binarySearch ?
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.start >= start, {
	
					collToRemove.add( stake );
	
					if( stakeSpan.start >= stop, {
						collToAdd.add( stake.shiftVirtual( delta ));
					});
				});
			});
		},
			
		kTouchSplit, {
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.stop > start, {
					
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start, {			// start portion not splitted
						if( stakeSpan.start >= stop, {		// just shifted
							collToAdd.add( stake.shiftVirtual( delta ));
						}, { if( stakeSpan.stop > stop, {	// stop portion splitted (otherwise completely removed!)
							stake = stake.replaceStart( stop );
							collToAdd.add( stake.shiftVirtual( delta ));
							stake.dispose;	// delete temp product
						})});
					}, {
						collToAdd.add( stake.replaceStop( start ));	// start portion splitted
						if( stakeSpan.stop > stop, {			// stop portion splitted
							stake = stake.replaceStart( stop );
							collToAdd.add( stake.shiftVirtual( delta ));
							stake.dispose;	// delete temp product
						});
					});
				});
			});
		},
			
		kTouchResize, {
"BasicTrail.remove, touchmode resize : not tested".warn;
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.stop > start, {
					
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start, {			// start portion not modified
						if( stakeSpan.start >= stop, {			// just shifted
							collToAdd.add( stake.shiftVirtual( delta ));
						}, { if( stakeSpan.stop > stop, {	// stop portion splitted (otherwise completely removed!)
							stake = stake.replaceStart( stop );
							collToAdd.add( stake.shiftVirtual( delta ));
							stake.dispose;	// delete temp product
						})});
					}, {
						if( stakeSpan.stop <= stop, {
							collToAdd.add( stake.replaceStop( start ));
						}, {
							collToAdd.add( stake.replaceStop( stakeSpan.stop + delta ));
						});
					});
				});
			});
		},
			
		// default:
		{
			MethodError( "Illegal Argument TouchMode : " ++ touchMode, thisMethod ).throw;
		});

if( kDebug, {
	(this.class.name ++ " : removing : ").inform;
	collToRemove.do({ arg stake;
		("  span "++stake.getSpan).inform;
	});
	" : adding : ".inform;
	collToAdd.do({ arg stake;
		("  span "++stake.getSpan).inform;
	});
});
		modSpan		= Span.union( this.prRemoveAll( collToRemove, ce ), this.prAddAll( collToAdd, ce ));

		// ____ dep ____
		dependants.do({ arg dep;
			dep.removeSpan( source, span, touchMode, ce );
		});

		if( source.notNil && modSpan.notNil, {
			this.prDispatchModification( source, modSpan );
			if( ce.notNil, { ce.addEdit( TrailEdit.newDispatch( modSpan )); });
		});
	}

//	clear { arg source, span, ce;
//	{
//		this.clear( source, span, this.getDefaultTouchMode, ce );
//	}
	
	clearSpan { arg source, span, touchMode, ce;
		var start, stop, collRange, collToAdd, collToRemove, modSpan, stake, stakeSpan;

		touchMode		= touchMode ?? { this.getDefaultTouchMode; };

		start			= span.start;
		stop			= span.stop;
		collRange		= this.getRange( span, true );
		
		if( collRange.isEmpty, { ^this; });
		
		collToAdd		= List.new;
		collToRemove	= List.new;
		
		switch( touchMode,
		kTouchNone, {
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.start >= start, {
					collToRemove.add( stake );
				});
			});
		},
			
		kTouchSplit, {
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.stop > start, {
					
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start, {			// start portion not splitted
						if( stakeSpan.stop > stop, {			// stop portion splitted (otherwise completely removed!)
							collToAdd.add( stake.replaceStart( stop ));
						});
					}, {
						collToAdd.add( stake.replaceStop( start ));	// start portion splitted
						if( stakeSpan.stop > stop, {				// stop portion splitted
							collToAdd.add( stake.replaceStart( stop ));
						});
					});
				});
			});
		},
			
		kTouchResize, {
"BasicTrail.clear, touchmode resize : not tested".warn;
			collRange.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.stop > start, {
					
					collToRemove.add( stake );
	
					if( stakeSpan.start >= start, {		// start portion not modified
						if( stakeSpan.stop > stop, {		// stop portion splitted (otherwise completely removed!)
							collToAdd.add( stake.replaceStart( stop ));
						});
					}, {
						if( stakeSpan.stop <= stop, {
							collToAdd.add( stake.replaceStop( start ));
						}, {
							collToAdd.add( stake.replaceStop( stakeSpan.stop - span.getLength ));
						});
					});
				});
			});
		},
			
		// default:
		{
			MethodError( "Illegal Argument TouchMode : " ++ touchMode, thisMethod ).throw;
		});

if( kDebug, {
	(this.class.name ++ " : removing : ").inform;
	collToRemove.do({ arg stake;
		("  span "++stake.getSpan).inform;
	});
	" : adding : ".inform;
	collToAdd.do({ arg stake;
		("  span "++stake.getSpan).inform;
	});
});
		modSpan		= Span.union( this.prRemoveAll( collToRemove, ce ), this.prAddAll( collToAdd, ce ));

		// ____ dep ____
		dependants.do({ arg dep;
			dep.removeSpan( source, span, touchMode, ce );
		});

		if( source.notNil && modSpan.notNil, {
			this.prDispatchModification( source, modSpan );
			if( ce.notNil, { ce.addEdit( TrailEdit.newDispatch( modSpan ))});
		});
	}

	getCuttedTrail { arg span, touchMode, shiftVirtual = 0;
		var trail, stakes;
	
		touchMode	= touchMode ?? { this.getDefaultTouchMode; };
		trail	= this.protCreateEmptyCopy;
		stakes	= this.getCuttedRange( span, true, touchMode, shiftVirtual );
		
//		trail.setRate( this.getRate() );

		trail.collStakesByStart.addAll( stakes );
//		Collections.sort( stakes, startComparator );
		stakes.sort( stopComparator );
		trail.collStakesByStop.addAll( stakes );
	
		^trail;
	}
	
	// XXX could use this.class.new ...
	protCreateEmptyCopy {
//		^this.subclassResponsibility( thisMethod );
		^this.class.new( this.getDefaultTouchMode );
	}

	getDefaultTouchMode {
		^touchMode;
	}

	*getCuttedRange {Êarg stakes, span, byStart = true, touchMode, shiftVirtual = 0;
		var collResult, start, stop, shift, stake, stake2, stakeSpan;

		if( stakes.isEmpty, {Ê^stakes; });
		
		collResult		= List.new;
		start			= span.start;
		stop				= span.stop;
		shift			= shiftVirtual != 0;
		
		switch( touchMode,
		kTouchNone, {
			stakes.do({ arg stake;
				stakeSpan	= stake.getSpan;
				if( stakeSpan.start >= start, {
					if( shift, {
						collResult.add( stake.shiftVirtual( shiftVirtual ));
					}, {
						collResult.add( stake.duplicate );
					});
				});
			});
		},
			
		kTouchSplit, {
			stakes.do({ arg stake;
				stakeSpan	= stake.getSpan;
				
				if( stakeSpan.start >= start, {			// start portion not splitted
					if( stakeSpan.stop <= stop, {			// completely included, just make a copy
						if( shift, {
							collResult.add( stake.shiftVirtual( shiftVirtual ));
						}, {
							collResult.add( stake.duplicate );
						});
					}, {								// adjust stop
						stake = stake.replaceStop( stop );
						if( shift, {
							stake2	= stake;
							stake	= stake.shiftVirtual( shiftVirtual );
							stake2.dispose;	// delete temp product
						});
						collResult.add( stake );
					});
				}, {
					if( stakeSpan.stop <= stop, {			// stop included, just adjust start
						stake = stake.replaceStart( start );
						if( shift, {
							stake2	= stake;
							stake	= stake.shiftVirtual( shiftVirtual );
							stake2.dispose;	// delete temp product
						});
						collResult.add( stake );
					}, {								// adjust both start and stop
						stake2	= stake.replaceStart( start );
						stake	= stake2.replaceStop( stop );
						stake2.dispose;	// delete temp product
						if( shift, {
							stake2	= stake;
							stake	= stake.shiftVirtual( shiftVirtual );
							stake2.dispose;	// delete temp product
						});
						collResult.add( stake );
					});
				});
			});
		},
			
		// default:
		{
			MethodError( "Illegal Argument TouchMode : " ++ touchMode, thisMethod ).throw;
		});
		
		^collResult;
	}

	getCuttedRange { arg span, byStart = true, touchMode, shiftVirtual = 0;
		touchMode	= touchMode ?? { this.getDefaultTouchMode; };
		^Trail.getCuttedRange( this.getRange( span, byStart ), span, byStart, touchMode, shiftVirtual );
	}

	get { arg idx, byStart = true;
		var coll;

		coll = byStart.if( collStakesByStart, collStakesByStop );
		^coll.at( idx );
	}
	
	getNumStakes {
		^collStakesByStart.size;
	}
	
	size { ^this.shouldNotImplement( thisMethod ); }
	
	isEmpty {
		^collStakesByStart.isEmpty;
	}
	
	contains { arg stake;
		^( this.indexOf( stake, true ) >= 0 );
	}

	indexOf { arg stake, byStart = true;
		var coll, comp, idx, idx2, stake2;

//		coll	= byStart.if( collStakesByStart, collStakesByStop );
//		comp	= byStart.if( startComparator, stopComparator );
		coll	= byStart.if({ collStakesByStart },{ collStakesByStop });
		comp	= byStart.if({ startComparator },{ stopComparator });
	
		// "If the list contains multiple elements equal to the specified object,
		//  there is no guarantee which one will be found"
		idx = this.prBinarySearch( coll, stake, comp );

		if( idx >= 0, {
			stake2 = coll.at( idx );
//			if( stake2.equals( stake ), { ^idx; });
			if( stake2 == stake, { ^idx; });
			idx2 = idx - 1;
			while({ idx2 >= 0 }, {
				stake2 = coll.at( idx2 );
//				if( stake2.equals( stake ), {Ê^idx2; });
				if( stake2 == stake, {Ê^idx2; });
				idx2 = idx2 - 1;
			});
			idx2 = idx + 1;
			while({ idx2 < coll.size }, {
				stake2 = coll.at( idx2 );
//				if( stake2.equals( stake ), {Ê^idx2; });
				if( stake2 == stake, {Ê^idx2; });
				idx2 = idx2 + 1;
			});
		});
		^idx;
	}

	indexOfPos {Êarg pos, byStart = true;
		if( byStart, {
			^this.prBinarySearch( collStakesByStart, pos, startComparator );
		}, {
			^this.prBinarySearch( collStakesByStop, pos, stopComparator );
		});
	}
	
	getLeftMost { arg idx, byStart = true;
		var coll, lastStake, pos, nextStake;
	
		if( idx < 0, {
			idx = (idx + 2).neg;
			if( idx < 0, {Ê^nil; });
		});
		
		coll		= byStart.if( collStakesByStart, collStakesByStop );
		lastStake	= coll.at( idx );
		pos		= byStart.if({ lastStake.getSpan.start }, { lastStake.getSpan.stop });
		
		while({ idx > 0 }, {
			idx			= idx - 1;
			nextStake 	= coll.at( idx );
			if( byStart.if({ nextStake.getSpan.start }, { nextStake.getSpan.stop }) != pos, {
				^lastStake;
			});
			lastStake	= nextStake;
		});
		
		^lastStake;
	}

	getRightMost { arg idx, byStart = true;
		var coll, sizeM1, lastStake, pos, nextStake;
	
		coll		= byStart.if( collStakesByStart, collStakesByStop );
		sizeM1		= coll.size - 1;
		
		if( idx < 0, {
			idx = (idx + 1).neg;
			if( idx > sizeM1, { ^nil; });
		});
		
		lastStake	= coll.at( idx );
		pos			= byStart.if({ lastStake.getSpan.start }, { lastStake.getSpan.stop });
		
		while({ idx < sizeM1 }, {
			idx			= idx + 1;
			nextStake	= coll.at( idx );
			if( byStart.if({ nextStake.getSpan.start }, { nextStake.getSpan.stop }) != pos, {
				^lastStake;
			});
			lastStake	= nextStake;
		});
		
		^lastStake;
	}

	getLeftMostIndex { arg idx, byStart = true;
		var coll, stake, pos;
	
		if( idx < 0, {
			idx = (idx + 2).neg;
			if( idx < 0, { ^-1; });
		});
		
		coll		= byStart.if( collStakesByStart, collStakesByStop );
		stake		= coll.at( idx );
		pos			= byStart.if({ stake.getSpan.start }, { stake.getSpan.stop });
		
		while({ idx > 0 }, {
			stake	= coll.at( idx - 1 );
			if( byStart.if({ stake.getSpan.start }, { stake.getSpan.stop }) != pos, {
				^idx;
			});
			idx		= idx - 1;
		});
		
		^idx;
	}

	getRightMostIndex { arg idx, byStart = true;
		var coll, sizeM1, stake, pos;
	
		coll		= byStart.if( collStakesByStart, collStakesByStop );
		sizeM1		= coll.size - 1;
		
		if( idx < 0, {
			idx = (idx + 1).neg;
			if( idx > sizeM1, {Ê^-1; });
		});
		
		stake		= coll.at( idx );
		pos			= byStart.if({ stake.getSpan.start }, { stake.getSpan.stop });
		
		while({ idx < sizeM1 }, {
			stake = coll.at( idx + 1 );
			if( byStart.if({ stake.getSpan.start }, { stake.getSpan.stop }) != pos, {
				^idx;
			});
			idx = idx + 1;
		});
		
		^idx;
	}

	getAll { arg byStart = true;
		var coll;
		
		coll = byStart.if( collStakesByStart, collStakesByStop );
		^List.newFrom( coll );
	}

	/**
	 *	@throws	IOException
	 */
	add { arg source, stake, ce;
		if( kDebug, { ("add "++stake.class.name).inform; });
		this.addAll( source, List.newUsing( stake ), ce );	// ____ dep ____ handled there
	}

	/**
	 *	@throws	IOException
	 */
	addAll { arg source, stakes, ce;
		var span;
	
		if( kDebug, { ("addAll "++stakes.size).inform; });
		if( stakes.size == 0, {Ê^this; });
	
		span = this.prAddAll( stakes, ce );

		// ____ dep ____
		dependants.do({ arg dep;
			dep.protAddAllDep( source, stakes, ce, span );
		});

		if( source.notNil && span.notNil, {
			this.prDispatchModification( source, span );
			if( ce.notNil, { ce.addEdit( TrailEdit.newDispatch( span )); });
		});
	}
	
	/**
	 *	To be overwritten by dependants.
	 *
	 *	@throws	IOException
	 */
	protAddAllDep { arg source, stakes, ce, span;
	
	}

	prAddAll { arg stakes, ce;
		var start, stop;
	
		if( stakes.size == 0, { ^this; });

		start	= inf.asInteger;	// XXX 32-bit only!!! Long.MAX_VALUE;
		stop		= -inf.asInteger;	// XXX 32-bit only!!! Long.MIN_VALUE;
		
		stakes.do({ arg stake;
			this.prSortAddStake( stake );
			start	= min( start, stake.getSpan.start );
			stop		= max( stop, stake.getSpan.stop );
		});
		if( ce.notNil, { ce.addEdit( TrailEdit( stakes, TrailEdit.kEditAdd )); });

		^Span( start, stop );
	}

	/**
	 *	@throws	IOException
	 */
	remove { arg source, stake, ce;
		this.removeAll( source, List.newUsing( stake ), ce );	// ____ dep ____ handled there
	}

	/**
	 *	@throws	IOException
	 */
	removeAll { arg source, stakes, ce;
		var span;

		if( stakes.size == 0, {Ê^this; });

		span = this.prRemoveAll( stakes, ce );

		// ____ dep ____
		dependants.do({ arg dep;
			dep.protRemoveAllDep( source, stakes, ce, span );
		});

		if( source.notNil && span.notNil, {
			this.prDispatchModification( source, span );
			if( ce.notNil, { ce.addEdit( TrailEdit.newDispatch( span ))});
		});
	}
	
	/**
	 *	To be overwritten by dependants.
	 *
	 *	@throws	IOException
	 */
	protRemoveAllDep { arg source, stakes, ce, span;

	}

	prRemoveAll { arg stakes, ce;
		var start, stop, stake;

		if( stakes.size == 0, {Ê^this; });
	
		start	= inf.asInteger;		// XXX this is 32bit only!!!! Long.MAX_VALUE;
		stop		= (-inf).asInteger;	// XXX this is 32bit only!!!! Long.MIN_VALUE;

		if( ce.isNil, {
			stakes.do({ arg stake;
				this.prSortRemoveStake( stake );
				start	= min( start, stake.getSpan.start );
				stop	= max( stop, stake.getSpan.stop );
				stake.dispose;
			});
		}, {
			stakes.do({ arg stake;
				this.prSortRemoveStake( stake );
				start	= min( start, stake.getSpan.start );
				stop		= max( stop, stake.getSpan.stop );
			});
			ce.addEdit( TrailEdit( stakes, TrailEdit.kEditRemove ));
		});

		^Span( start, stop );
	}

    debugDump {
		"collStakesByStart : ".postln;
		collStakesByStart.postln;
		"collStakesByStop : ".postln;
		collStakesByStop.postln;
	}

	prSortAddStake { arg stake;
		var idx;

		idx		= this.indexOfPos( stake.getSpan.start, true );	// look for position only!
		if( idx < 0, { idx = (idx + 1).neg; });
//("collStakesByStart.insert( "++idx++", "++stake++" )").postln;
		collStakesByStart.insert( idx, stake );
		idx		= this.indexOfPos( stake.getSpan.stop, false );
		if( idx < 0, { idx = (idx + 1).neg; });
//("collStakesByStop.insert( "++idx++", "++stake++" )").postln;
		collStakesByStop.insert( idx, stake );
		
		stake.setTrail( this );
	}
	
	prSortRemoveStake { arg stake;
		var idx;

		idx		= this.indexOf( stake, true );
		if( idx >= 0, { collStakesByStart.removeAt( idx ); });
	// look for object equality!
		idx		= this.indexOf( stake, false );
		if( idx >= 0, { collStakesByStop.removeAt( idx ); });

		stake.setTrail( nil );
	}
	
	addListener { arg listener;
		if( elm.isNil, {
			elm = EventManager( this );
		});
		elm.addListener( listener );
	}

	removeListener { arg listener;
	
		elm.removeListener( listener );
	}

	addDependant { arg sub;
		if( dependants.isNil, {
			dependants = List.new;
		});
//		synchronized( dependants ) {
			if( dependants.indexOf( sub ) >= 0, {
				"BasicTrail.addDependant : WARNING : duplicate add".warn;
			});
			dependants.add( sub );
//		}
	}

	removeDependant { arg sub;
//		synchronized( dependants ) {
			if( dependants.remove( sub ).not, {
				"BasicTrail.removeDependant : WARNING : was not in list".warn;
			});
//		}
	}
	
	getNumDependants {
		if( dependants.isNil, {
			^0;
		}, {
//			synchronized( dependants ) {
				^dependants.size;
//			}
		});
	}
	
	getDependant { arg i;
//		synchronized( dependants ) {
			^dependants.at( i );
//		}
	}
	
	prDispatchModification { arg source, span;
		if( elm.notNil, {
			elm.dispatchEvent( TrailEvent( this, source, span ));
		});
	}

// ---------------- TreeNode interface ---------------- 

//	public TreeNode getChildAt( int childIndex )
//	{
//		return get( childIndex, true );
//	}
//	
//	public int getChildCount()
//	{
//		return getNumStakes();
//	}
//	
//	public TreeNode getParent()
//	{
//		return null;
//	}
//	
//	public int getIndex( TreeNode node )
//	{
//		if( node instanceof Stake ) {
//			return indexOf( (Stake) node, true );
//		} else {
//			return -1;
//		}
//	}
//	
//	public boolean getAllowsChildren()
//	{
//		return true;
//	}
//	
//	public boolean isLeaf()
//	{
//		return false;
//	}
//	
//	public Enumeration children()
//	{
//		return new ListEnum( getAll( true ));
//	}
// XXX

// --------------------- EventManager.Processor interface ---------------------
	
	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed. This
	 *  will invoke the listener's <code>trailModified</code> method.
	 */
	processEvent { arg e;
		var i, listener;

		i = 0;
		while({ i <  elm.countListeners; }, {
			listener = elm.getListener( i );
			switch( e.getID,
			TrailEvent.kModified, {
				listener.trailModified( e );
//			},
//			// default:
//			{
//				assert false : e.getID;
//				break;
			});
			i = i + 1;
		});
	}
}

TrailEdit : AbstractUndoableEdit
{
	// undable edits
		
	classvar <kEditAdd		= 0;
	classvar <kEditRemove	= 1;
	classvar <kEditDispatch	= 2;

	var		trail, cmd, stakes, key, removed, span;

	*newDispatch {Êarg trail, span;
		^this.new( trail, nil, kEditDispatch, "editChangeTrail", span );
	}

	*new {Êarg trail, stakes, cmd, key = "editChangeTrail", span;
		^super.new.prInitTrailEdit( trail, stakes, cmd, key, span );
	}
	
	prInitTrailEdit { arg argTrail, argStakes, argCmd, argKey, argSpan;
		trail		= argTrail;
		stakes		= argStakes;
		cmd			= argCmd;
		key			= argKey;
		span			= argSpan;
		removed		= cmd == kEditRemove;
//		perform();
	}
	
	prAddAll {
		stakes.do({ arg stake;
			trail.prSortAddStake( stake );
		});
		removed		= false;
	}

	prRemoveAll {
		stakes.do({ arg stake;
			trail.prSortRemoveStake( stake );
		});
		removed		= true;
	}
	
	prDisposeAll {
		stakes.do({ arg stake;
			stake.dispose;
		});
	}

	undo {
		super.undo;
		
		switch( cmd,
		kEditAdd, {
			this.prRemoveAll;
		},
		kEditRemove, {
			this.prAddAll;
		},
		kEditDispatch, {
			trail.prDispatchModification( trail, span );
		});
	}
	
	redo {
		super.redo;
		
		switch( cmd,
		kEditAdd, {
			this.prAddAll;
		},
		kEditRemove, {
			this.prRemoveAll;
		},
		kEditDispatch, {
			trail.prDispatchModification( trail, span );
		});
	}
	
	die	{
		super.die;
		if( removed, {
			this.prDisposeAll;
		});
	}
	
	getPresentationName {
//		return AbstractApplication.getApplication().getResourceString( key );
// XXX
		^key;
	}
}
