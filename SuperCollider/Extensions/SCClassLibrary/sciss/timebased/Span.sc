/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: (none)
 *
 *	SuperCollider implementation of the java class de.sciss.io.Span
 *
 *  @version	0.11, 08-Jun-06
 *  @author	Hanns Holger Rutz
 */
Span {
	classvar <startComparator;
	classvar <stopComparator;

	var <start;
	var <stop;

	*initClass {
		startComparator = { arg o1, o2;
			var n1, n2;
		
			if( o1.respondsTo( \start ), {
				n1 = o1.start;
				if( o2.respondsTo( \start ), {
					n2 = o2.start;
				}, { if( o2.isNumber, {
					n2 = o2.asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, { if( o1.isNumber, {
				n1 = o1.asInteger;
				if( o2.respondsTo( \start ), {
					n2 = o2.start;
				}, { if( o2.isNumber, {
					n2 = o2.asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, {
				Error( "Class Cast : " ++ o1.class.name ).throw;
			})});
			
			if( n1 < n2, -1, { if( n1 > n2, 1, 0 )});
//			^(n1 <= n2);
		};

		stopComparator = { arg o1, o2;
			var n1, n2;
		
			if( o1.respondsTo( \stop ), {
				n1 = o1.stop;
				if( o2.respondsTo( \stop ), {
					n2 = o2.stop;
				}, { if( o2.isNumber, {
					n2 = o2.asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, { if( o1.isNumber, {
				n1 = o1.asInteger;
				if( o2.respondsTo( \stop ), {
					n2 = o2.stop;
				}, { if( o2.isNumber, {
					n2 = o2.asInteger;
				}, {
					Error( "Class Cast : " ++ o2.class.name ).throw;
				})});
			}, {
				Error( "Class Cast : " ++ o1.class.name ).throw;
			})});
			
			if( n1 < n2, -1, { if( n1 > n2, 1, 0 )});
//			^(n1 <= n2);
		};
	}
	
	*new { arg start = 0, stop = 0;
		^super.new.prInitSpan( start, stop );
	}
	
	prInitSpan { arg argStart, argStop;
		start	= argStart;
		stop	= argStop;
	}

	*newFrom { arg span;
		^this.new( span.start, span.stop );
	}

	/**
	 *  Checks if a position lies within the span.
	 *
	 *  @return		<code>true</code>, if <code>start <= postion < stop</code>
	 */
    containsPos { arg position;
        ^( (position >= start) && (position < stop) );
    }

	/**
	 *  Checks if another span lies within the span.
	 *
	 *	@param	anotherSpan	second span, may be <code>null</code> (in this case returns <code>false</code>)
	 *  @return		<code>true</code>, if <code>anotherSpan.start >= this.span &&
	 *				anotherSpan.stop <= this.stop</code>
	 */
    contains { arg anotherSpan;
        ^( anotherSpan.notNil and: {
			(anotherSpan.start >= start) && (anotherSpan.stop <= stop) });
    }

	/**
	 *  Checks if a two spans overlap each other.
	 *
	 *	@param	anotherSpan	second span, may be <code>null</code> (in this case returns <code>false</code>)
	 *  @return		<code>true</code>, if the spans
	 *				overlap each other
	 */
    overlaps { arg anotherSpan;
		if( anotherSpan.isNil, { ^false; });
		
		if( start <= anotherSpan.start, {
			^( stop > anotherSpan.start );
		}, {
			^( anotherSpan.stop > start );
		});
    }

	/**
	 *  Checks if a two spans overlap or touch each other.
	 *
	 *	@param	anotherSpan	second span, may be <code>null</code> (in this case returns <code>false</code>)
	 *  @return		<code>true</code>, if the spans
	 *				overlap each other
	 */
    touches { arg anotherSpan;
		if( anotherSpan.isNil, { ^false; });

		if( start <= anotherSpan.start, {
			^( stop >= anotherSpan.start );
		}, {
			^( anotherSpan.stop >= start );
		});
    }

	/**
	 *  Checks if the span is empty.
	 *
	 *  @return		<code>true</code>, if <code>start == stop</code>
	 */
  	isEmpty {
        ^( start == stop );
    }
    
	/**
	 *  Checks if this span is equal to an object.
	 *
	 *  @param  o   an object to compare to this span
	 *  @return		<code>true</code>, if <code>o</code> is a span with
	 *				the same start and end point
	 */
    equals { arg o;
        ^( o.notNil and: { o.isKindOf( Span ) and:
        	{ (o.start == start) && (o.stop == this.stop)}} );
    }

//	hashCode {
//		^( (int) start ^ (-(int) stop) );
//	}
    
	/**
	 *  Queries the span's start.
	 *
	 *  @return		the start point of the span
	 */
    getStart { ^start; }
    
	/**
	 *  Queries the span's end.
	 *
	 *  @return		the end point of the span
	 */
	getStop { ^stop; }
    
	/**
	 *  Queries the span's extent (duration, length etc.)
	 *
	 *  @return		length of the span, i.e. <code>stop - start</code>
	 */
  	getLength { ^( stop - start ); }
	
//	asString {
//		^( start.asString ++ " ... " ++ stop.asString );
//	}
	
	/**
	 *  Union operation on two spans.
	 *
	 *  @param  span1   first span to fuse (may be <code>null</code>)
	 *  @param  span2   second span to fuse (may be <code>null</code>)
	 *  @return		a new span whose extension
	 *				covers both span1 and span2, or <code>null</code> if
	 *				both <code>span1</code> and <code>span2</code> are <code>null</code>
	 */
	*union { arg span1, span2;
		if( span1.isNil, { ^span2; });
	
		^span1.union( span2 );
	}
	
	union { arg anotherSpan;
		if( anotherSpan.isNil, { ^this; });
	
		^Span( min( start, anotherSpan.start ),
			   max( stop, anotherSpan.stop ));
	}

	shift { arg delta;
		^Span( start + delta, stop + delta );
	}

	asString {
		^this.asCompileString;
	}
	
	asCompileString {
		^(this.class.name ++ "( "++start++", "++stop++" )");
	}
}