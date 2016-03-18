/*
 *	JSCViews3
 *	(SwingOSC classes for SuperCollider)
 *
 *	Additional views.
 *
 *	@version		0.45, 02-Feb-07
 *	@author		Hanns Holger Rutz
 */
JSCCheckBox : JSCControlView {
	var acResp;	// OSCpathResponder for action listening

	*paletteExample { arg parent, bounds;
		^this.new( parent, bounds );
	}
	
	value_ { arg val;
		this.setProperty( \value, val );
	}
	
	value { ^this.getProperty( \value ); }
	
	valueAction_ { arg val;
		this.setPropertyWithAction( \value, val );
	}	

	font_ { arg font;
		this.setProperty( \font, font );
	}
	
	font { ^this.getProperty( \font ); }

	string_ { arg string;
		this.setProperty( \string, string );
	}

	string { ^this.getProperty( \string ); }
	
	properties {
		^super.properties ++ #[ \value, \font, \string ];
	}
	
	defaultGetDrag { 
		^this.value;
	}
	
	defaultCanReceiveDrag {
		^currentDrag.isNumber or: { currentDrag.isKindOf( Function ) or: {ÊcurrentDrag.isKindOf( Boolean )}};
	}
	
	defaultReceiveDrag {
		case
		{ currentDrag.isNumber }
		{
			this.valueAction = currentDrag != 0;
		}
		{ currentDrag.isKindOf( Boolean )}
		{
			this.valueAction = currentDrag;
		}
		{ currentDrag.isKindOf( Function )}
		{
			this.action = currentDrag;
		};
	}

	prNeedsTransferHandler {
		^true;
	}

	prClose {
		acResp.remove;
		^super.prClose([[ '/method', "ac" ++ this.id, \remove ],
					   [ '/free', "ac" ++ this.id ]]);
	}

	prSCViewNew {
		properties.put( \value, false );
		acResp = OSCpathResponder( server.addr, [ '/action', this.id ], { arg time, resp, msg;
			// don't call valueAction coz we'd create a loop
			properties.put( \value, msg[4] != 0 );
			{ this.doAction; }.defer;
		}).add;
		^super.prSCViewNew([
			[ '/local', this.id, '[', '/new', "de.sciss.swingosc.CheckBox", ']',
				"ac" ++ this.id,
				'[', '/new', "de.sciss.swingosc.ActionResponder", this.id, \selected, ']' ]
		]);
	}

	prSendProperty { arg key, value;
		key	= key.asSymbol;

		// fix keys
		case { key === \value }
		{
			key = \selected;
		}
		{ key === \string }
		{
			key = \text;
		};
		^super.prSendProperty( key, value );
	}
}

JSCTabbedPane : JSCContainerView {
	var tabs;		// List -> (IdentityDictionary properties)
	var acResp;	// OSCpathResponder for change listening
	
	// ------------- public general methods -------------

	font_ { arg font;
		this.setProperty( \font, font );
	}
	
	font { ^this.getProperty( \font ); }
	
	tabPlacement_ { arg type;
		this.setProperty( \placement, type );
	}
	
	tabPlacement { ^this.getProperty( \placement ); }
	
	numTabs {Ê^tabs.size; }
	
	value_ { arg index;
		this.setProperty( \value, index );
	}

	valueAction_ { arg index;
		this.setPropertyWithAction( \value, index );
	}	
	
	value { ^this.getProperty( \value ); }

	// ------------- public per tab methods -------------

	setTitleAt { arg index, title;
		this.prSetTabProperty( index, title, \title, \setTitleAt );
	}
	
	getTitleAt { arg index;
		^this.prGetTabProperty( index, \title );
	}

	setEnabledAt { arg index, enabled;
		this.prSetTabProperty( index, enabled, \enabled, \setEnabledAt );
	}

	getEnabledAt { arg index;
		^this.prGetTabProperty( index, \enabled );
	}

	setBackgroundAt { arg index, color;
		this.prSetTabProperty( index, color, \background, \setBackgroundAt );
	}

	getBackgroundAt { arg index;
		^this.prGetTabProperty( index, \background );
	}

	setForegroundAt { arg index, color;
		this.prSetTabProperty( index, color, \foreground, \setForegroundAt );
	}

	getForegroundAt { arg index;
		^this.prGetTabProperty( index, \foreground );
	}

	setToolTipAt { arg index, text;
		this.prSetTabProperty( index, text, \tooltip, \setToolTipTextAt );
	}

	getToolTipAt { arg index;
		^this.prGetTabProperty( index, \tooltip );
	}

	// ------------- private methods -------------
	
	prSetTabProperty {Êarg index, value, key, javaSelector;
		var tab;
		if( index == -1, {
			tabs.size.do({ arg index; this.prSetTabProperty( index, value, key, javaSelector )});
			^this;
		});
		tab = tabs[ index ];
		if( tab.notNil, {
			tab.put( key, value );
			server.listSendMsg([ '/method', this.id, javaSelector, index ]Ê++ value.asSwingArg );
		}, {
			this.prMethodError( thisMethod, "Illegal tab index " ++ index ++ " (" ++ key ++ ")" );
		});
	}
	
	prGetTabProperty { arg index, key;
		var tab;	
		tab = tabs[ index ];
		if( tab.notNil, {
			^tab[ key ];
		}, {
			this.prMethodError( thisMethod, "Illegal tab index " ++ index ++ " (" ++ key ++ ")" );
			^nil;
		});
	}

	add { arg child;
		var tab;
		tab = IdentityDictionary.new;
		tab.put( \enabled, true );
		tab.put( \component, child );
		tabs.add( tab );
		if( this.value.isNil, {Êproperties.put( \value, 0 )});
		^super.add( child );
	}
	
	prRemoveChild { arg child;
		block { arg break;
			tabs.do({ arg tab, index;
				if( tab[ \component ] === child, {
					tabs.removeAt( index );
					break.value;
				});
			});
			this.prMethodError( thisMethod, "Child was not a registered tab : " ++ child );
		};
		^super.prRemoveChild( child );
	}

	prSCViewNew {
		tabs = List.new;
		properties.put( \opaque, false );
		acResp = OSCpathResponder( server.addr, [Ê'/action', this.id ], { arg time, resp, msg;
			// don't call valueAction coz we'd create a loop
			properties.put( \value, msg[ 4 ]);
			{ this.doAction; }.defer;
		}).add;
		^super.prSCViewNew([
			[ '/local', this.id, '[', '/new', "de.sciss.swingosc.TabbedPane", ']',
				"ac" ++ this.id,
				'[', '/new', "de.sciss.swingosc.ActionResponder", this.id, \selectedIndex, ']' ]
		]);
	}

	prSendProperty { arg key, value;
		key	= key.asSymbol;

		// fix keys
		case
		{ key === \value }
		{
			key 		= \selectedIndexNoAction;
		}
		{ key === \placement }
		{
			key 		= \tabPlacement;
			value	= [ \top, \left, \bottom, \right ].indexOf( value ) + 1;
		};
		^super.prSendProperty( key, value );
	}

	prMethodError {Êarg methodName, message;
		(this.class.name ++ "." ++ methodName ++ " failed : " ++ message).error;
	}

	prClose {
		acResp.remove;
		^super.prClose([[ '/method', "ac" ++ this.id, \remove ],
					   [ '/free', "ac" ++ this.id ]]);
	}

//	public void setMnemonicAt(int tabIndex, int mnemonic)
}

JSCScrollPane : JSCContainerView {
	horizontalScrollBarShown_ { arg type;
		this.setProperty( \hPolicy, type );
	}

	horizontalScrollBarShown { ^this.getProperty( \hPolicy ); }

	verticalScrollBarShown_ { arg type;
		this.setProperty( \vPolicy, type );
	}

	verticalScrollBarShown { ^this.getProperty( \vPolicy ); }
	
	add { arg child;
		var bndl;

		if( children.size > 0, {
			MethodError( "Cannot add more than one child", this ).throw;
		});

		children = children.add( child );
		bndl = List.new;
		bndl.add([ '/method', this.id, \setViewportView,
				'[', '/ref', child.prIsInsideContainer.if({ "cn" ++ child.id }, child.id ), ']' ]);
//		if( this.prGetWindow.visible, {
//			bndl.add([ '/method', this.id, \revalidate ]);
//		});
		server.listSendBundle( nil, bndl );
	}

	prSendProperty { arg key, value;
		key	= key.asSymbol;

		// fix keys
		case
		{ key === \hPolicy }
		{
			key 		= \horizontalScrollBarPolicy;
			value	= [ \auto, \never, \always ].indexOf( value ) + 30;
		}
		{ key === \vPolicy }
		{
			key 		= \verticalScrollBarPolicy;
			value	= [ \auto, \never, \always ].indexOf( value ) + 20;
		};
		^super.prSendProperty( key, value );
	}
	
	prRemoveChild { arg child;
		children.remove( child );
		server.sendMsg( '/method', this.id, \setViewportView, '[', '/ref', \null, ']' );
	}

	prSCViewNew {
		^super.prSCViewNew([
			[ '/local', this.id, '[', '/new', "javax.swing.JScrollPane", ']' ]
		]);
	}
}