/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TypeSafe, LoopUnit, MicUnit, Ping, TapeUnit
 *
 *	Dynamic Unit Generator tool for Ping
 *
 *	@version	0.1, 19-Oct-06
 *	@author	Hanns Holger Rutz
 */
UnitFactory {
	var <paramNames, <type;
	var params;
	
	*allTypes {
		^IdentitySet[ \tape, \mic, \loop, \trigTape ];
	}

	*new { arg type;
		^super.new.prInitFactory( type );
	}
	
	prInitFactory { arg argType;
		type		= argType;
		params	= IdentityDictionary.new;
	}
	
//	asCompileString {
//		var assocArray;
//		
//		assocArray = Array.new( params.size );
//		params.keysDo({ arg key; assocArray.add( params.associationAt( key )); });
//	
//		^"UnitFactory( " ++ type.asCompileString ++ " ).putAll( " ++
//		  assocArray.asCompileString ++ " )"
//	}

	asCompileString {
		^"UnitFactory( " ++ type.asCompileString ++ " ).putAll( " ++
		  params.asCompileString ++ " )"
	}
	
	putAll { arg dict;
		params.putAll( dict );
	}

	makeGUI { arg view;
		var flow, b, mapToGUI, mapToParam, funcToGUI, funcToParams;

		flow			= view.decorator;

//		mapToGUI 		= IdentityDictionary.new;
//		mapToParam 	= IdentityDictionary.new;
		
//		funcToGUI		= { arg dict;
//			dict.keysValuesDo({ arg key, value;
//				mapToGUI[ key ].value( value );
//			});
//		};
//		
//		funcToParams	= {
//			var params;
//			params = IdentityDictionary.new;
//			mapToParam.keysValuesDo({ arg key, func;
//				params.put( key, func.value );
//			});
//			params;
//		};

		case
		{ type === \tape }
		{
//			this.prMakeIntegerParam( \cueIndex, nil, view );
//			flow.nextLine;
		}
		{ type === \mic }
		{
			this.prMakeStringParam( \name, nil, view );
			flow.nextLine;
			this.prMakeIntegerParam( \inputBusIndex, nil, view );
			flow.nextLine;
			this.prMakeIntegerParam( \numChannels, nil, view );
			flow.nextLine;
		}
		{ type === \loop }
		{
			this.prMakeIntegerParam( \duration, nil, view );
			flow.nextLine;
			this.prMakeIntegerParam( \numChannels, nil, view );
			flow.nextLine;
		}
		{ type === \trigTape }
		{
		}
		{
			TypeSafe.methodError( thisMethod, "Illegal type "++type );
		};
	}
	
	makeUnit { arg ping, selectedChannel;
		var unit, val, val2, micOff;
		
		case
		{ type === \tape }
		{
			unit = TapeUnit.new;
			~tapeCues.do({ arg cue;
				unit.addCue( cue, nil );
			});
			unit.setCueIndex( selectedChannel % ~tapeCues.size );
		}
		{ type === \mic }
		{
			micOff = ping.server.options.numOutputBusChannels;
			unit = MicUnit.new;
			val = params[ \name ];
			if( val.notNil, {Êunit.setName( val.asSymbol ); });
			val 	= params[ \inputBusIndex ] ?? 0;
			val2	= params[ \numChannels ] ?? 2;
			unit.addMic( Bus( \audio, micOff + val, val2 )); 
			unit.setMicIndex( 0 );
		}
		{ type === \loop }
		{
			unit = LoopUnit.new;
			val = params[ \duration ] ?? 30;
			val2	= params[ \numChannels ] ?? 2;
			unit.setNumFrames( (ping.server.sampleRate * val).asInteger );
			unit.setNowNumFrames( unit.getNumFrames );
			unit.setNumChannels( val2 );
		}
		{ type === \trigTape }
		{
			unit = TrigTapeUnit.new;
		}
		{
			TypeSafe.methodError( thisMethod, "Illegal type "++type );
		};
		
		^unit;
	}

	prMakeStringParam { arg key, label, view;
		var b, val;
		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
			.string_( (label ?? key).asString ++ ":" );
		b = GUI.textField.new( view, Rect( 0, 0, 160, 24 ))
			.action_({ arg b;
				params.put( key, b.string );
			});
		val = params[ key ];
		if( val.notNil, {Êb.string_( val );});
	}

	prMakeIntegerParam { arg key, label, view;
		var b, val;
		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
			.string_( (label ?? key).asString ++ ":" );
		b = GUI.numberBox.new( view, Rect( 0, 0, 90, 24 ))
			.align_( \right )
			.action_({ arg b;
				params.put( key, b.value );
			});
		val = params[ key ];
		if( val.notNil, {Êb.value_( val );});
	}

//	prMakeBooleanParam { arg key, label, view, mapToGUI, mapToParam;
//		var b;
//		GUI.staticText.new( view, Rect( 0, 0, 160, 24 ))
//			.string_( (label ?? key).asString ++ ":" );
//		b = GUI.button.new( view, Rect( 0, 0, 40, 24 ))
//			.states_([[ "[   ]" ], [ "[ X ]", Color.white, Color.blue ]]);
//		mapToGUI.put( key, { arg value; b.value_( value.if( 1, 0 )); });
//		mapToParam.put( key, { b.value == 1; });
//	}
}