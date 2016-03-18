/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: ScissUtil, Ping, UnitFactory
 *
 *	Setup tool for Ping
 *
 *	@version	0.1, 19-Oct-06
 *	@author	Hanns Holger Rutz
 */
PingSetup {
	classvar <presets;
	
	*initClass {
		presets = (
			motu: (
				\soundCard:			"MOTU 828",
				\numInputBusChannels:	18,
				\numOutputBusChannels:	16,
				\soloIndex:			0,
				\soloChannels:		2,
				\masterIndex:			2,
				\masterChannels:		4,
				\auxIndex:			6,
				\auxChannels:			2,
				\useAux:				true,

				\numNormChans:		20,
				\useSync:				false,
				\chanSyncTime:		20,
				\chanFadeTime:		21,
				\chanAuxVol:			22,
				\chanSoloVol:			23
			),
			mio: (
				\soundCard:			"Mobile I/O 2882 [2600]",
				\numInputBusChannels:	18,
				\numOutputBusChannels:	16,
				\soloIndex:			0,
				\soloChannels:		2,
				\masterIndex:			2,
				\masterChannels:		4,
				\auxIndex:			6,
				\auxChannels:			2,
				\useAux:				true,

				\numNormChans:		20,
				\useSync:				false,
				\chanSyncTime:		20,
				\chanFadeTime:		21,
				\chanAuxVol:			22,
				\chanSoloVol:			23
			)
		);
	}

	*makeGUI {
		var win, view, flow, b, mapToGUI, mapToParam, funcToGUI, funcToParams,
			pUnitFactory, ufs, numUFs = 8, funcUpdateUFGUI, ggUFChannel;
		
		
		ufs = Array.newClear( numUFs );
		
		win 	= GUI.window.new( "Ping Setup", Rect( 0, 0, 700, 560 ));
		view = GUI.compositeView.new( win, Rect( 0, 0, 340, 550 ));
		flow	= FlowLayout( view.bounds );
		view.decorator = flow;
		
		mapToGUI 		= IdentityDictionary.new;
		mapToParam 	= IdentityDictionary.new;
		
		funcToGUI		= { arg dict;
			dict.keysValuesDo({ arg key, value;
				mapToGUI[ key ].value( value );
			});
		};
		
		funcToParams	= {
			var params;
			params = IdentityDictionary.new;
			mapToParam.keysValuesDo({ arg key, func;
				params.put( key, func.value );
			});
			params;
		};
		
		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
			.string_( "Recall Preset:" );
		
		GUI.popUpMenu.new( view, Rect( 0, 0, 90, 24 ))
			.items_( presets.keys.asArray )
			.action_({ arg b;
				var pst;
				
				pst = presets[ b.items[ b.value ]];
				if( pst.notNil, {
					funcToGUI.value( pst );
				}, {
					("Preset " ++ b.items[ b.value ] ++ " not found").error;
				});
			});
		
		flow.nextLine;
		
		this.prMakeStringParam( \soundCard, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \numInputBusChannels, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \numOutputBusChannels, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \soloIndex, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \soloChannels, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \masterIndex, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \masterChannels, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \auxIndex, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \auxChannels, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeBooleanParam( \useAux, nil, view, mapToGUI, mapToParam );
		flow.nextLine;

		this.prMakeIntegerParam( \numNormChans, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \chanSyncTime, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \chanFadeTime, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \chanAuxVol, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeIntegerParam( \chanSoloVol, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		this.prMakeBooleanParam( \useSync, nil, view, mapToGUI, mapToParam );
		flow.nextLine;
		
		GUI.button.new( view, Rect( 0, 0, 90, 24 ))
			.states_([[ "Load" ]])
			.action_({ arg b;
				var params;
				
				params = this.prLoad;
				if( params.notNil, {
					funcToGUI.value( params );
				}, {
					("File could not be loaded").error;
				});
			});

		GUI.button.new( view, Rect( 0, 0, 90, 24 ))
			.states_([[ "Save" ]])
			.action_({ arg b;
				this.prSave( funcToParams.value );
			});
		
		view = GUI.compositeView.new( win, Rect( 350, 0, 340, 250 ));
		flow	= FlowLayout( view.bounds );
		view.decorator = flow;
		
		pUnitFactory = GUI.compositeView.new( win, Rect( 350, 260, 340, 250 ));

		funcUpdateUFGUI = { arg ch;
			pUnitFactory.removeAll;
			pUnitFactory.decorator = FlowLayout( pUnitFactory.bounds );
			ufs[ ch ].makeGUI( pUnitFactory );
			win.refresh;
		};
		
		numUFs.do({ arg ch;
			var key, pop;
			
			GUI.staticText.new( view, Rect( 0, 0, 60, 24 ))
				.string_( "Sel " ++ (ch + 1).asString ++ ":" );
			pop = GUI.popUpMenu.new( view, Rect( 0, 0, 160, 24 ))
				.items_( UnitFactory.allTypes.asArray )
				.action_({ arg b;
					ufs[ ch ] = UnitFactory( b.items[ b.value ]);
//					funcUpdateUFGUI.value( ch );
					ggUFChannel.valueAction_( ch );
				});
			flow.nextLine;
			
			key = ("setUnitFactory$" ++ ch).asSymbol;
			mapToParam.put( key, {
				ufs[ ch ].asCompileString;
			});
			mapToGUI.put( key, { arg value;
				ufs[ ch ] = value.interpret;
				if( ufs[ ch ].notNil, {
					pop.value_( pop.items.indexOf( ufs[ ch ].type ));
				});
			});
		});
		
		ggUFChannel = GUI.popUpMenu.new( view, Rect( 0, 0, 60, 24 ))
			.items_( Array.fill( numUFs, { arg ch; (ch + 1).asString; }))
			.action_({ arg b;
				if( ufs[ b.value ].notNil, {
					funcUpdateUFGUI.value( b.value );
				});
			});

		ScissUtil.positionOnScreen( win );
		win.front;
	}
	
	*prMakeStringParam { arg key, label, view, mapToGUI, mapToParam;
		var b;
		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
			.string_( (label ?? key).asString ++ ":" );
		b = GUI.textField.new( view, Rect( 0, 0, 160, 24 ));
		mapToGUI.put( key, { arg value; b.string_( value ); });
		mapToParam.put( key, { b.string; });
	}

	*prMakeIntegerParam { arg key, label, view, mapToGUI, mapToParam;
		var b;
		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
			.string_( (label ?? key).asString ++ ":" );
		b = GUI.numberBox.new( view, Rect( 0, 0, 90, 24 ))
			.align_( \right );
		mapToGUI.put( key, { arg value; b.value_( value ); });
		mapToParam.put( key, { b.value.asInteger; });
	}

	*prMakeBooleanParam { arg key, label, view, mapToGUI, mapToParam;
		var b;
		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
			.string_( (label ?? key).asString ++ ":" );
		b = GUI.button.new( view, Rect( 0, 0, 40, 24 ))
			.states_([[ "[   ]" ], [ "[ X ]", Color.white, Color.blue ]]);
		mapToGUI.put( key, { arg value; b.value_( value.if( 1, 0 )); });
		mapToParam.put( key, { b.value == 1; });
	}
	
	*prLoad {
		var f, params;
		
		f = File( Ping.dataFolder ++ "setup.txt", "r" );
		params = f.contents.interpret;
		f.close;
		^params;
	}
	
	*prSave { arg params;
		var f;
		
		f = File( Ping.dataFolder ++ "setup.txt", "w" );
		params.storeOn( f );
		f.close;
	}
	
	*load { arg ping, tascam;
		var params, setter;
		
		params = this.prLoad;
		params.keysValuesDo({ arg key, value;
			key = key.asString;
			if( key.includes( $$ ), {
				key = key.split( $$ );
				setter = (key[ 0 ]).asSymbol;
				value = value.interpret;
				if( ping.respondsTo( setter ), {
					ping.perform( setter, key[ 1 ].asInteger, value );
				});
				if( tascam.respondsTo( setter ), {
					tascam.perform( setter, key[ 1 ].asInteger, value );
				});
			}, {
				setter = (key ++ "_").asSymbol;
				if( ping.respondsTo( setter ), {
					ping.perform( setter, value );
				});
				if( tascam.respondsTo( setter ), {
					tascam.perform( setter, value );
				});
			});
		});
	}
}