/*
 *	JMacUGens
 *	(SwingOSC classes for SuperCollider)
 *
 *	Replacements for the mac only mouse control UGens
 *	and ScopeOut
 *
 *	@author		Hanns Holger Rutz
 *	@version		0.50, 08-feb-07
 */

/**
 *	Base class for mouse based UGens.
 *	By default, mouse data is passed on a control bus
 *	of the default server (Server.default). To use a
 *	different server, modify the server class field.
 *	To force re-allocation of the control bus, call
 *	JMouseBase.clear .
 *
 *	You can set the mouse coordinates directly:
 *
 *	JMouseX.set( <mouseX> );	// 0...1
 *	JMouseY.set( <mouseY> );	// 0...1
 *	JMouseButton.set( <mouseButton> );	// 0 or 1
 *
 *	; or you can create a GUI window if a SwingOSC server is running:
 *
 *	JMouseBase.makeGUI
 */
JMouseBase : Object
{
	classvar <controlBus;
	classvar <>server;
	
	*clear {
		if( controlBus.isNil.not, {ÊcontrolBus.free; });
		controlBus = nil;
	}

	// warp 0 = linear
	// warp 1 = exponential
	*kr { arg minval = 0, maxval = 1, warp = 0, lag = 0.2;
		
		var mouseUGen, outputUGen;
		
		if( controlBus.isNil, {
			this.prAllocBus;
		});
		
		mouseUGen = Lag.kr( In.kr( controlBus.index + this.prChannel, 1 ), lag );
	
		if( (warp == \linear) || (warp == 0), {
			outputUGen = LinLin.kr( mouseUGen, 0.0, 1.0, minval, maxval );
		}, {
			outputUGen = LinExp.kr( mouseUGen, 0.0, 1.0, minval, maxval );
		});

		^outputUGen;
	}
	
	*set { arg val;
		if( controlBus.isNil.not, {
			controlBus.server.sendMsg( "/c_set", controlBus.index + this.prChannel, val );
		}, {
			"JMouseBase.set : control bus has not yet been created".error;
		});
	}

	*makeGUI { arg panelSize = 360;
		var swing, bounds, win, userView, fPos;
		
		swing	= SwingOSC.default;
		bounds	= Rect( max( 0, (swing.screenWidth - panelSize - 40) >> 1 ),
					   max( 0, (swing.screenHeight - panelSize - 60) >> 1 ),
					   panelSize + 40, panelSize + 60 );		win		= JSCWindow( "Mouse Control", bounds )
					.acceptsMouseOver_( true );
		fPos		= { arg me, x, y, modifiers;
			// holding down shift freezes mouse
			if( (modifiers & 0x20000) == 0, {
				JMouseX.set( (x / me.bounds.width).clip( 0, 1 ));
				JMouseY.set( (y / me.bounds.height).clip( 0, 1 ));
			});
		};
		userView	= JSCUserView( win, win.view.bounds )
			.resize_( 5 )
			.mouseDownAction_({ arg me, x, y, modifiers;
				JMouseButton.set( 1 );
			})
			.mouseUpAction_({ arg me, x, y, modifiers;
				JMouseButton.set( 0 );
			})
			.mouseMoveAction_( fPos )
			.mouseOverAction_( fPos )
			.drawFunc_({ arg me;
				var bounds, x, y, sx, sy;
				
				bounds	= me.bounds;
				sx		= bounds.width / 8;
				sy		= bounds.height / 8;
				
				JPen.fillColor = Color.blue( 0.5, 0.25 );
				JPen.fillRect( bounds );
				
				JPen.width = 0.5;
				JPen.strokeColor = Color.blue( 0.5 );
			
				7.do({ arg xi;
					x = (xi + 1) * sx;
					JPen.line( x @ 0, x @ bounds.height );
				});
				7.do({ arg yi;
					y = (yi + 1) * sy;
					JPen.line( 0 @ y, bounds.width @ y );
				});
				JPen.stroke;
				
				JPen.font = JFont.default;
				JPen.fillColor = Color.white;
				JPen.stringAtPoint( "shift = freeze", 16 @ 16 );
			});
		
		win.front;
	}

	*prAllocBus {
		var serv;
		
		serv			= server ?? Server.default;
		controlBus	= Bus.control( serv, 3 );
	}
}

JMouseX : JMouseBase
{
	*prChannel {
		^0;
	}
}

JMouseY : JMouseBase
{
	*prChannel {
		^1;
	}
}

JMouseButton : JMouseBase {
	*kr {
		arg minval=0, maxval=1, lag=0.2;

		// uses control bus channel 2
		^super.kr( minval, maxval, \linear, lag );
	}

	*prChannel {
		^2;
	}
}

// not yet implemented:
//
//KeyState : UGen {
//	*kr {
//		arg keycode=0, minval=0, maxval=1, lag=0.2;
//		^this.multiNew('control', keycode, minval, maxval, lag)
//	}
//}

JScopeOut : UGen {
	*ar { arg inputArray , bufnum=0;
		^RecordBuf.ar( inputArray, bufnum );
	}
	*kr { arg inputArray , bufnum=0;
		^RecordBuf.ar( K2A.ar( inputArray ), bufnum );
	}
}
