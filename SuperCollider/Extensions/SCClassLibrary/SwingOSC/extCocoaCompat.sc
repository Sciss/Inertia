/*
 *	you can use this extension so that
 *	cocoa gui behaves exactly as swingOSC gui
 *
 *	@version	0.45, 30-Jan-07
 *	@author	Hanns Holger Rutz
 */
+ Pen {
	*font_ { arg font;
		CocoaCompat.penFont = font;
	}

	*string { arg str;
		^str.drawAtPoint( Point( 0, 0 ), CocoaCompat.penFont ?? Font.default, CocoaCompat.penFillColor ?? Color.black );
	}
	
	*stringAtPoint { arg str, point;
		^str.drawAtPoint( point, CocoaCompat.penFont ?? Font.default, CocoaCompat.penFillColor ?? Color.black );
	}
	
	*stringInRect { arg str, rect;
		^str.drawInRect( rect, CocoaCompat.penFont ?? Font.default, CocoaCompat.penFillColor ?? Color.black );
	}
	
	*stringCenteredIn { arg str, inRect;
		^str.drawCenteredIn( inRect, CocoaCompat.penFont ?? Font.default, CocoaCompat.penFillColor ?? Color.black );
	}
	
	*stringLeftJustIn { arg str, inRect;
		^str.drawLeftJustIn( inRect, CocoaCompat.penFont ?? Font.default, CocoaCompat.penFillColor ?? Color.black );
	}
	
	*stringRightJustIn { arg str, inRect;
		^str.drawRightJustIn( inRect, CocoaCompat.penFont ?? Font.default, CocoaCompat.penFillColor ?? Color.black );
	}

	*strokeColor_ { arg color;
		CocoaCompat.penStrokeColor	= color;
		color.setStroke;
	}

	*fillColor_ { arg color;
		CocoaCompat.penFillColor 	= color;
		color.setFill;
	}
	
	*color_ { arg color;
		CocoaCompat.penFillColor	= color;
		CocoaCompat.penStrokeColor	= color;
		color.set;
	}
}

+ SCRangeSlider {
	setSpan { arg lo, hi;
		this.lo = lo;
		this.hi = hi;
	}
	
	setSpanActive { arg lo, hi;
		this.setSpan( lo, hi );
		this.doAction;
	}
}

+ SC2DSlider {
	setXY { arg x, y;
		this.x = x;
		this.y = y;
	}
	
	setXYActive { arg x, y;
		this.setXY( x, y );
		this.doAction;
	}
}

//+ SCSoundFileView {
//	setSelectionSpan { arg index, startFrame, numFrames;
//		this.setSelectionStart( index, startFrame );
//		this.setSelectionSize( index, numFrames );
//	}
//
//// doesn't work, always returns nil ...
////	timeCursorPosition {
////		^this.getProperty( \timeCursorPosition );
////	}
//}

+ Font {
	boldVariant {
		^if( name.endsWith( "-Bold" ), this, { this.class.new( name ++ "-Bold", size )});
	}
	
	*defaultSansFace {
		^"Helvetica";
	}
	
	*defaultSerifFace {
		^"Times";
	}
	
	*defaultMonoFace {
		^"Monaco";
	}
}
