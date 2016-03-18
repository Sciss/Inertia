/**
 *	(C)opyright 2006-2007 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Kontrollzentrale fuer Wacom Intuos (emulating a Tascam US-2400 ;-)
 *
 *	@version	0.11, 19-Jan-07
 *	@author	Hanns Holger Rutz
 */
WacomTwoFour {
	// as specified in the wacom prefs pane
	classvar <>tabletLeft	= 200;
	classvar <>tabletTop	= 100;
	classvar <>tabletBottom	= 724;
	classvar <>tabletRight	= 1200;

	classvar <>genNames, <>fltNames;

//	var <out;
	var <numChannels	= 8;
	var <numAuxes		= 6;
	var <maxNumKnobs	= 10;
	
//	var <fader;		// not really used ?
//	var faderFine;
	var joyH = 63.5, joyV = 63.5;

	var <win, <tGUI;
	var ggChannelNames, ggChannelFaders, ggChannelSolos, ggChannelPlays;
//	var ggChannelSelects;
	var ggMasterFader, ggMasterSelect, ggSoloFader;
	var ggSoloClear, ggSoloApply;
//	var ggDelete;
	var ggRec;
	var ggChannelKnobs, ggChannelKnobNames;
	var ggJoystick;
	
	*initClass {
		genNames	= [ \tape, \mic, \loop ];  // , \trigTape
		fltNames	= [ \filt, \hilb, \gate, \gendy, \disp, \achil, \sqrt, \magAbove, \magBelow, \comb ];
	}

	*new {
		^super.new.prInitWacom;
	}
	
	prInitWacom {
		var cc;
	
//		MIDIClient.init( 1, 1 );
//		MIDIIn.connect;
//		
//		out = MIDIOut( 0, MIDIClient.destinations[ 0 ].uid );
		
		// + 1 wegen master
//		fader		= Array.fill( numChannels + 1, 0 );
//		faderFine		= Array.fill( numChannels + 1, 0 );
		
		this.prMakeGUI;
		
//		numChannels.do({ arg ch;
//			this.channelKnob( ch, 0 );
//			this.channelSelect( ch, 0 );
//			this.channelSolo( ch, 0 );
//			this.channelPlay( ch, 0 );
//			this.channelFader( ch, 0 );
//		});
		
		this.masterFader( 0 );
//		this.chanMode( false );
//		this.panMode( false );
		
//		CCResponder({ arg src, ch, num, val;
//			if( num <= numChannels, {
//				fader[ num ] = (faderFine[ num ] / 128 + val) / 127.875;
//				if( num === numChannels, {
//					this.changed( \masterFader, fader[ num ]);
//				}, {
//					this.changed( \channelFader, num, fader[ num ]);
//				});
//			}, {
//				num = num - 32;
//				if( num <= numChannels, {
//					faderFine[ num ] = val;
//				}, {
//					num = num - 32;
//					if( num < numChannels, {
//						this.changed( \channelKnob, num, val );
//					});
//				});
//			});
//		}, 0, 0 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \soloClear, val === 127 ); }, 0, 1, 98 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \chanMode, val === 127 ); }, 0, 1, 100 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \aux, 0, val === 127 ); }, 0, 1, 101 );
//		CCResponder({ arg src, ch, num, val; this.changed( \aux, 1, val === 127 ); }, 0, 1, 102 );
//		CCResponder({ arg src, ch, num, val; this.changed( \aux, 2, val === 127 ); }, 0, 1, 103 );
//		CCResponder({ arg src, ch, num, val; this.changed( \aux, 3, val === 127 ); }, 0, 1, 104 );
//		CCResponder({ arg src, ch, num, val; this.changed( \aux, 4, val === 127 ); }, 0, 1, 105 );
//		CCResponder({ arg src, ch, num, val; this.changed( \aux, 5, val === 127 ); }, 0, 1, 106 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \meterMode, val === 127 ); }, 0, 1, 107 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \panMode, val === 127 ); }, 0, 1, 108 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \fkeyPressed, val === 127 ); }, 0, 1, 109 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \nullMode, val === 127 ); }, 0, 1, 110 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \inputPressed, val === 127 ); }, 0, 1, 114 );
//		CCResponder({ arg src, ch, num, val; this.changed( \outputPressed, val === 127 ); }, 0, 1, 115 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \shiftPressed, val === 127 ); }, 0, 1, 116 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \transportRwd, val === 127 ); }, 0, 1, 117 );
//		CCResponder({ arg src, ch, num, val; this.changed( \transportFFwd, val === 127 ); }, 0, 1, 118 );
//		CCResponder({ arg src, ch, num, val; this.changed( \transportStop, val === 127 ); }, 0, 1, 119 );
//		CCResponder({ arg src, ch, num, val; this.changed( \transportPlay, val === 127 ); }, 0, 1, 120 );
//		CCResponder({ arg src, ch, num, val; this.changed( \transportRec, val === 127 ); }, 0, 1, 121 );
//
//		CCResponder({ arg src, ch, num, val;
//			var row;
//		
//			if( (num > 0) && (num < 96), {
//				num = num - 1;
//				row = num.bitAnd( 3 );
//				num = num >> 2;
//				
//				case { row === 0 }
//				{
//					this.changed( \channelSelect, num, val === 127 );
//				}
//				{ row === 1 }
//				{
//					this.changed( \channelSolo, num, val === 127 );
//				}
//				{ row === 2 }
//				{
//					this.changed( \channelPlay, num, val === 127 );
//				};				
//			});
//		}, 0, 1 );
//
//		CCResponder({ arg src, ch, num, val; this.changed( \masterSelect, val === 127 ); }, 0, 1, 97 );
//
//		CCResponder({ arg src, ch, num, val;
//			case { num === 90 }
//			{
//				joyH = val;
//				this.changed( \joystick, joyH, joyV );
//			}
//			{ num === 91 }
//			{
//				joyV = val;
//				this.changed( \joystick, joyH, joyV );
//			};
//		}, 0, 14 );
	}
	
	prMakeGUI {
		var gui, scrB, ggUser, ggTablet;
		var gx, gy, gw, gh;
		
		gui			= GUI.current;
		scrB			= gui.window.screenBounds;
		win			= gui.window.new( "Wolkenpumpe", Rect( tabletLeft, scrB.height - tabletBottom - 2,
								   	tabletRight - tabletLeft + 2, tabletBottom - tabletTop + 2 ),
									resizable: false, border: false );
		win.onClose	= {ÊtGUI.dispose };
		win.view.background = Color.black;
		ggUser		= gui.userView.new( win, win.view.bounds );
		ggTablet		= gui.tabletView.new( win, win.view.bounds );
		tGUI			= TabletGUI( ggTablet, ggUser );
		win.front;
		
		ggChannelNames 	= Array( numChannels );
		ggChannelFaders 	= Array( numChannels );
//		ggChannelSelects	= Array( numChannels );
		ggChannelSolos	= Array( numChannels );
		ggChannelPlays	= Array( numChannels );
		ggChannelKnobs	= Array.newClear( numChannels );
		ggChannelKnobNames	= Array.newClear( numChannels );

		gy				= 20;
		gh				= 40;
		numChannels.do({Êarg ch; var lastX, oldVal, nameTarget, nameDelete = false;
			gx = 20;
			gw = 40;
			
			ggChannelNames.add( TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
				.action_({ arg b, what, x, y; var child;
					case
					{ what === \drag }
					{
						child			= b.parent.findChildAt( xÊ@Êy );
//						if( child == ggDelete, {
//							nameDelete	= true;
//							nameTarget	= nil;
//						}, {
							nameTarget	= ggChannelNames.indexOf( child );
//							nameDelete	= false;
//						});
					}
					{ what === \down }
					{
						this.changed( \channelSelect, ch, true );
					}
					{ what === \up }
					{
						this.changed( \channelSelect, ch, false );
						if( nameTarget.notNil and: {ÊnameTarget != ch }, {
							this.changed( \channelMove, ch, nameTarget );
							nameTarget = nil;
						});
//						({}, { if( nameDelete, {
//							this.changed( \channelDelete, ch ); // , this.prFadeTimeFromTilt( tiltY );
//							nameDelete = false;
//						})});
					};
				});
			);

			gx	= gx + gw + 20;
			gw	= 80;
			ggChannelFaders.add( TabletGUIPassiveSlider( tGUI, Rect( gx, gy, gw, gh ))
				.action_({ arg b, what, x;
					case
					{ what === \down }
					{
						lastX = x;
					}
					{ what === \drag }
					{
						oldVal	= b.value;
						b.value	= b.value + ((x - lastX) / 120);
						lastX	= x;
						if( oldVal != b.value, {
							this.changed( \channelFader, ch, b.value );
						});
					};
				});
			);

//			gx	= gx + gw + 20;
//			gw	= 40;
//			ggChannelSelects.add( TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
//				.label_( "SEL" )
//				.action_({ arg b, what;
//					case
//					{ what === \down }
//					{
//						this.changed( \channelSelect, ch, true );
//					}
//					{ what === \up }
//					{
//						this.changed( \channelSelect, ch, false );
//					};
//				});
//			);

			gx	= gx + gw + 20;
			gw	= 40;
			ggChannelSolos.add( TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
				.label_( "SOLO" )
				.action_({ arg b, what;
					case
					{ what === \down }
					{
						this.changed( \channelSolo, ch, true );
					}
					{ what === \up }
					{
						this.changed( \channelSolo, ch, false );
					};
				});
			);

			gx	= gx + gw + 20;
			ggChannelPlays.add( TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
				.label_( "PLAY" )
				.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
					case
					{ what === \down }
					{
						this.changed( \channelPlay, ch, true, this.prFadeTimeFromTilt( tiltY ));
					}
					{ what === \up }
					{
						this.changed( \channelPlay, ch, false, this.prFadeTimeFromTilt( tiltY ) );
					};
				});
			);
			
//			gx	= gx + gw + 60;
			
			ggChannelKnobs[ ch ] = Array( maxNumKnobs );
			ggChannelKnobNames[ ch ] = Array( maxNumKnobs );
			maxNumKnobs.do({ arg knobIdx; var lastKnobX, newKnobVal, oldKnobVal;
				gx	= gx + gw + 20;
				gw	= 40;
				ggChannelKnobNames[ ch ].add( TabletGUILabel( tGUI, Rect( gx, gy - 15, gw, 15 )));
				ggChannelKnobs[ ch ].add( TabletGUIPassiveSlider( tGUI, Rect( gx, gy, gw, gh ))
					.visible_( false )
					.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
						case
						{ what === \down }
						{
							lastKnobX = x;
						}
						{ what === \drag }
						{
							oldKnobVal	= b.value;
							newKnobVal	= oldKnobVal + ((x - lastKnobX) / 120);
							lastKnobX		= x;
							if( oldKnobVal != newKnobVal, {
								this.changed( \channelKnob, ch, knobIdx, newKnobVal, true, this.prFadeTimeFromTilt( tiltY ));
							});
						}
						{ what === \up }
						{
							oldKnobVal	= b.value;
							newKnobVal	= oldKnobVal + ((x - lastKnobX) / 120);
							lastKnobX		= x;
							this.changed( \channelKnob, ch, knobIdx, newKnobVal, false, this.prFadeTimeFromTilt( tiltY ));
						};
					});
				);
			});

			gy = gy + gh + 20;
		});

//		gy = gy + 60;
		gx = 20;
		gw = 40;
		ggMasterSelect = {Êvar target; TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "SUM" )
			.action_({ arg b, what, x, y; var child;
				case
				{ what === \drag }
				{
					child	= b.parent.findChildAt( xÊ@Êy );
					target	= ggChannelNames.indexOf( child );
				}
				{ what === \down }
				{
					this.changed( \masterSelect, true );
				}
				{ what === \up }
				{
					this.changed( \masterSelect, false );
					if( target.notNil, {
						this.changed( \masterMove, target );
						target = nil;
					});
				};
			});
		}.value;

		gx = gx + gw + 20;
		gw = 80;
		ggMasterFader = { var lastX, oldVal;
			TabletGUIPassiveSlider( tGUI, Rect( gx, gy, gw, gh ))
				.action_({ arg b, what, x;
					case
					{ what === \down }
					{
						lastX = x;
					}
					{ what === \drag }
					{
						oldVal	= b.value;
						b.value	= b.value + ((x - lastX) / 120);
						lastX	= x;
						if( oldVal != b.value, {
							this.changed( \masterFader, b.value );
						});
					};
				});
		}.value;

		gx = gx + gw + 140;
		gw = 40;
		genNames.do({ arg name, idx; var target, uf;
			uf = UnitFactory( name );
			TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
				.label_( this.prStringToUpper( name.asString ))   // there's no toUpperCase ???
				.action_({ arg b, what, x, y;
					case
					{ what === \drag }
					{
						target = ggChannelNames.indexOf( b.parent.findChildAt( xÊ@Êy ));
					}
					{ what === \down }
					{
						b.state = \on;
					}
					{ what === \up }
					{
						b.state = \off;
						if( target.notNil, {
							this.changed( \channelCreate, target, uf );
							target = nil;
						});
					};
				});
			gx = gx + gw + 20;
		});

		gx = gx + 60;
		gw = 80;
		TabletGUILabel( tGUI, Rect( gx, gy - 15, gw, 15 )).label_( "solo" );
		ggSoloFader = { var lastX, oldVal;
			TabletGUIPassiveSlider( tGUI, Rect( gx, gy, gw, gh ))
				.action_({ arg b, what, x;
					case
					{ what === \down }
					{
						lastX = x;
					}
					{ what === \drag }
					{
						oldVal	= b.value;
						b.value	= b.value + ((x - lastX) / 120);
						lastX	= x;
						if( oldVal != b.value, {
							this.changed( \soloFader, b.value );
						});
					};
				});
		}.value;

		gx = gx + gw + 120;
		TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "---" )
			.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
				case
				{ what === \down }
				{
					this.changed( \transportFFwd, true );
				}
				{ what === \up }
				{
					this.changed( \transportFFwd, false );
				};
			});

		gx = gx + gw + 20;
		TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "+++" )
			.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
				case
				{ what === \down }
				{
					this.changed( \transportRwd, true );
				}
				{ what === \up }
				{
					this.changed( \transportRwd, false );
				};
			});

		gy = gy + gh + 20;
		gx = 20;
		gw = 40;
		TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "S.CLR" )
			.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
				case
				{ what === \down }
				{
					this.changed( \soloClear, true, this.prFadeTimeFromTilt( tiltY ));
				}
				{ what === \up }
				{
					this.changed( \soloClear, false, this.prFadeTimeFromTilt( tiltY ) );
				};
			});
		
		gx = gx + gw + 20;
		TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "S.APP" )
			.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
				case
				{ what === \down }
				{
					this.changed( \soloApply, true, this.prFadeTimeFromTilt( tiltY ));
				}
				{ what === \up }
				{
					this.changed( \soloApply, false, this.prFadeTimeFromTilt( tiltY ) );
				};
			});

		gx = gx + gw + 20;
		TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "KILL" )
			.action_({ arg b, what;
				if( what === \down, {Êthis.changed( \kill )});
			});
		
		gx = gx + gw + 20;
		ggRec = TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
			.label_( "REC" )
			.action_({ arg b, what, x, y, pressure, tiltX, tiltY;
				case
				{ what === \down }
				{
					this.changed( \transportRec, true, this.prFadeTimeFromTilt( tiltY ));
				}
				{ what === \up }
				{
					this.changed( \transportRec, false, this.prFadeTimeFromTilt( tiltY ) );
				};
			});
		
		gx = gx + gw + 60;
		gw = 40;
		fltNames.do({ arg name, idx; var target;
			TabletGUIPassiveButton( tGUI, Rect( gx, gy, gw, gh ))
				.label_( this.prStringToUpper( name.asString ))   // there's no toUpperCase ???
				.action_({ arg b, what, x, y;
					case
					{ what === \drag }
					{
						target = ggChannelNames.indexOf( b.parent.findChildAt( xÊ@Êy ));
					}
					{ what === \down }
					{
						b.state = \on;
					}
					{ what === \up }
					{
						b.state = \off;
						if( target.notNil, {
							this.changed( \channelFilter, target, name );
							target = nil;
						});
					};
				});
			gx = gx + gw + 20;
		});

		
		ggJoystick = { var dAzi, dSpread, lastAzi, newAzi, newSpread, lastSpread, c, oldAzi, oldSpread;
			TabletGUIPassiveRound2DSlider( tGUI, Rect( 640, 495, 50, 50 ))
				.action_({ arg b, what, x, y;
					case
					{ what === \down }
					{
						c			= b.bounds.center;
						x	 		= (x - c.x) / b.bounds.width;
						y	 		= (c.y - y) / b.bounds.height;
						lastAzi		= atan2( y, x );
						lastSpread	= (x.squared + y.squared).sqrt;
					}
					{ what === \drag }
					{
						c			= b.bounds.center;
						x	 		= (x - c.x) / b.bounds.width;
						y	 		= (c.y - y) / b.bounds.height;
						newAzi		= atan2( y, x );
						dAzi			= newAzi - lastAzi;
						if( dAzi > pi, { dAzi = dAzi - 2pi });
						if( dAzi < -pi, { dAzi = dAzi + 2pi });
						newSpread		= (x.squared + y.squared).sqrt;
						dSpread		= newSpread - lastSpread;
						oldAzi		= b.azimuth;
						oldSpread		= b.spread;
						lastAzi		= newAzi;
						lastSpread	= newSpread;
//						b.azimuth		= oldAzi + dAzi;
//						b.spread		= oldSpread  + dSpread;
						b.setAzimuthAndSpread( oldAzi + dAzi, oldSpread  + dSpread );
						if( (oldAzi != b.azimuth) or: { oldSpread != b.spread }, {
							this.changed( \panorama, b.azimuth, b.spread );
						});
					};
				});
		}.value;
	}
	
	prFadeTimeFromTilt { arg tilt;
//("TILT = "++tilt).postln;
		^(0.9 - tilt.clip( -0.9, 0.9 ))/1.8;
	}
	
	prStringToUpper { arg str;
//		^str.collect({ arg ch; if( (ch.ascii < 97) or: { ch.ascii > 122 }, ch, { (ch.ascii - 32).asAscii })});
		^str.collect({ arg ch; ch.toUpper });
	}
	
	dispose {
		win.close;
	}
	
	prControl { arg ch, num, val;
//		out.control( ch, num, val );
warn( "prControl NOT WORKING" );
	}
	
	panorama { arg azi, spread;
		ggJoystick.setAzimuthAndSpread( azi, spread );
	}
	
	channelFader {Êarg ch, val;
		ggChannelFaders[ ch ].value = val;
	}
	
	masterFader {Êarg val;
		ggMasterFader.value = val;
	}
	
	soloFader {Êarg val;
		ggSoloFader.value = val;
	}
	
	channelSelect { arg ch, mode;
//		ggChannelSelects[ ch ].state = mode;
		ggChannelNames[ ch ].state = mode;
	}
	
	channelSolo { arg ch, mode;
		ggChannelSolos[ ch ].state = mode;
	}

	channelPlay { arg ch, mode;
		ggChannelPlays[ ch ].state = mode;
	}
	
	channelName { arg ch, str;
		ggChannelNames[ ch ].label = this.prStringToUpper( str );
	}

	channelKnob { arg ch, knobIdx, val, lamp = false;
//		this.prControl( 0, ch + 64, (val * 14).asInteger + if( lamp, 65, 1 ));
		this.prSetChannelKnob( ch, knobIdx, val, false, false );
	}

	prSetChannelKnob { arg ch, knobIdx, val, filled, centered;
		var knob;
		
		knob = ggChannelKnobs[ ch ][ knobIdx ];
		knob.filled 	= filled;
		knob.centered	= centered;
		knob.value 	= val;
		knob.visible	= true;
	}

	channelKnobHide { arg ch, knobIdx;
		var knob;
		knob = ggChannelKnobs[ ch ][ knobIdx ];
		knob.visible = false;
	}

	channelKnobLabel { arg ch, knobIdx, label;
		var gg;
		gg = ggChannelKnobNames[ ch ][ knobIdx ];
//("SET "++label).postln;
		gg.label = label;
	}

	// note: 0 ... 1
	channelKnobPan { arg ch, knobIdx, val, lamp = false;
//		this.prControl( 0, ch + 64, (val * 14).asInteger + if( lamp, 81, 17 ));
//		this.prSetChannelKnob( ch, knobIdx, val, false, true );
this.prSetChannelKnob( ch, knobIdx, val, true, false );
	}

	channelKnobFill { arg ch, knobIdx, val, lamp = false;
//		this.prControl( 0, ch + 64, (val * 15).asInteger + if( lamp, 96, 32 ));
		this.prSetChannelKnob( ch, knobIdx, val, true, false );
	}

	channelKnobDisco { arg ch, val, lamp = false;
		this.prControl( 0, ch + 64, (val * 15).asInteger + if( lamp, 112, 48 ));
	}

	channelSelectMutex { arg chan;
		numChannels.do({ arg ch;
			this.channelSelect( ch, if( ch == chan, \on, \off ));
		});
	}
	
	aux { arg num, onOff;
		this.prControl( 1, num + 101, onOff.if( 127, 0 ));
	}

	shift { arg onOff;
		this.prControl( 1, 116, onOff.if( 127, 0 ));
	}

	fkey { arg onOff;
		this.prControl( 1, 109, onOff.if( 127, 0 ));
	}

	meterMode { arg onOff;
		this.prControl( 1, 107, onOff.if( 127, 0 ));
	}

	inputMode { arg onOff;
		this.prControl( 1, 114, onOff.if( 127, 0 ));
	}

	outputMode { arg onOff;
		this.prControl( 1, 115, onOff.if( 127, 0 ));
	}

	nullMode { arg onOff;
		this.prControl( 1, 110, onOff.if( 127, 0 ));
	}

	chanMode { arg onOff;
		this.prControl( 1, 100, onOff.if( 127, 0 ));
	}

	panMode { arg onOff;
		this.prControl( 1, 108, onOff.if( 127, 0 ));
	}

	transportRwd { arg onOff;
		this.prControl( 1, 117, onOff.if( 127, 0 ));
	}

	transportFFwd { arg onOff;
		this.prControl( 1, 118, onOff.if( 127, 0 ));
	}

	transportStop { arg onOff;
		this.prControl( 1, 119, onOff.if( 127, 0 ));
	}

	transportPlay { arg onOff;
		this.prControl( 1, 120, onOff.if( 127, 0 ));
	}

	transportRec { arg onOff;
//		this.prControl( 1, 121, onOff.if( 127, 0 ));
		ggRec.state = onOff.if( \on, \off );
	}

	channelSoloMutex { arg chan;
		numChannels.do({ arg ch;
			this.channelSolo( ch, if( ch == chan, \on, \off ));
		});
	}
}