/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	EZSlider: Adds valueAction_ and asView methods.
 *
 *	- EZSlider's value_ method behaves like valueAction_
 *	(not like expected value_). We make value_ behave like
 *	regular value_ and add valueAction_ with the expected behaviour.
 *	- asView is added for more general usability.
 *
 *	@author	Hanns Holger Rutz
 *	@version	28-Jun-06
 *
 *	@warning	the behaviour was modified in June 2006 : value is
 *			NOT overriden any more ; instead valueNoAction_ is added!!!
 */
+ EZSlider {
	valueNoAction_ { arg val;
		val				= controlSpec.constrain( val );
		sliderView.value	= controlSpec.unmap( val );
		numberView.value	= val.round( round );
	}

	valueAction_ { arg val;
		this.valueNoAction_( val );
		action.value( this );
	}

	asView { ^numberView; }
}

/**
 *	Allows to debug incoming OSC by adding a dependant to
 *	Main waiting for 'osc' changes.
 *
 *	@author	Hanns Holger Rutz
 *	@version	10-Sep-06
 */
+ Main {
	recvOSCmessage { arg time, replyAddr, msg;
		var handled;
		handled = OSCresponder.respond( time, replyAddr, msg );
		Main.changed( \osc, time, replyAddr, msg, handled );
	}
}