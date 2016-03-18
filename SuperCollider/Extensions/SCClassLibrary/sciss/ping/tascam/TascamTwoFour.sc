/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Kontrollzentrale fuer Tascam US-2400
 *
 *	How to boot the US-2400 : Turn off US-2400, keep SEL+CHAN pressed, then hit power again
 *	(chan flashes three times!)
 *
 *	@version	0.1, 15-Jun-06
 *	@author	Hanns Holger Rutz
 */
TascamTwoFour {
	var <out;
	var <numChannels	= 24;
	var <numAuxes		= 6;
	
	var <fader;		// not really used ?
	var faderFine;
	var joyH = 63.5, joyV = 63.5;

	*new {
		^super.new.prInitTascam;
	}
	
	prInitTascam {
		var cc;
	
		MIDIClient.init( 1, 1 );
		MIDIIn.connect;
		
		out = MIDIOut( 0, MIDIClient.destinations[ 0 ].uid );
		
		// + 1 wegen master
		fader		= Array.fill( numChannels + 1, 0 );
		faderFine		= Array.fill( numChannels + 1, 0 );
		
		numChannels.do({ arg ch;
			this.channelKnob( ch, 0 );
			this.channelSelect( ch, 0 );
			this.channelSolo( ch, 0 );
			this.channelMute( ch, 0 );
			this.channelFader( ch, 0 );
		});
		
		this.masterFader( 0 );
		this.chanMode( false );
		this.panMode( false );
		
		CCResponder({ arg src, ch, num, val;
			if( num <= numChannels, {
				fader[ num ] = (faderFine[ num ] / 128 + val) / 127.875;
				if( num === numChannels, {
					this.changed( \masterFader, fader[ num ]);
				}, {
					this.changed( \channelFader, num, fader[ num ]);
				});
			}, {
				num = num - 32;
				if( num <= numChannels, {
					faderFine[ num ] = val;
				}, {
					num = num - 32;
					if( num < numChannels, {
						this.changed( \channelKnob, num, val );
					});
				});
			});
		}, 0, 0 );

		CCResponder({ arg src, ch, num, val; this.changed( \soloClear, val === 127 ); }, 0, 1, 98 );

		CCResponder({ arg src, ch, num, val; this.changed( \chanMode, val === 127 ); }, 0, 1, 100 );

		CCResponder({ arg src, ch, num, val; this.changed( \aux, 0, val === 127 ); }, 0, 1, 101 );
		CCResponder({ arg src, ch, num, val; this.changed( \aux, 1, val === 127 ); }, 0, 1, 102 );
		CCResponder({ arg src, ch, num, val; this.changed( \aux, 2, val === 127 ); }, 0, 1, 103 );
		CCResponder({ arg src, ch, num, val; this.changed( \aux, 3, val === 127 ); }, 0, 1, 104 );
		CCResponder({ arg src, ch, num, val; this.changed( \aux, 4, val === 127 ); }, 0, 1, 105 );
		CCResponder({ arg src, ch, num, val; this.changed( \aux, 5, val === 127 ); }, 0, 1, 106 );

		CCResponder({ arg src, ch, num, val; this.changed( \meterMode, val === 127 ); }, 0, 1, 107 );

		CCResponder({ arg src, ch, num, val; this.changed( \panMode, val === 127 ); }, 0, 1, 108 );

		CCResponder({ arg src, ch, num, val; this.changed( \fkeyPressed, val === 127 ); }, 0, 1, 109 );

		CCResponder({ arg src, ch, num, val; this.changed( \nullMode, val === 127 ); }, 0, 1, 110 );

		CCResponder({ arg src, ch, num, val; this.changed( \inputPressed, val === 127 ); }, 0, 1, 114 );
		CCResponder({ arg src, ch, num, val; this.changed( \outputPressed, val === 127 ); }, 0, 1, 115 );

		CCResponder({ arg src, ch, num, val; this.changed( \shiftPressed, val === 127 ); }, 0, 1, 116 );

		CCResponder({ arg src, ch, num, val; this.changed( \transportRwd, val === 127 ); }, 0, 1, 117 );
		CCResponder({ arg src, ch, num, val; this.changed( \transportFFwd, val === 127 ); }, 0, 1, 118 );
		CCResponder({ arg src, ch, num, val; this.changed( \transportStop, val === 127 ); }, 0, 1, 119 );
		CCResponder({ arg src, ch, num, val; this.changed( \transportPlay, val === 127 ); }, 0, 1, 120 );
		CCResponder({ arg src, ch, num, val; this.changed( \transportRec, val === 127 ); }, 0, 1, 121 );

		CCResponder({ arg src, ch, num, val;
			var row;
		
			if( (num > 0) && (num < 96), {
				num = num - 1;
				row = num.bitAnd( 3 );
				num = num >> 2;
				
				case { row === 0 }
				{
					this.changed( \channelSelect, num, val === 127 );
				}
				{ row === 1 }
				{
					this.changed( \channelSolo, num, val === 127 );
				}
				{ row === 2 }
				{
					this.changed( \channelMute, num, val === 127 );
				};				
			});
		}, 0, 1 );

		CCResponder({ arg src, ch, num, val; this.changed( \masterSelect, val === 127 ); }, 0, 1, 97 );

		CCResponder({ arg src, ch, num, val;
			case { num === 90 }
			{
				joyH = val;
				this.changed( \joystick, joyH, joyV );
			}
			{ num === 91 }
			{
				joyV = val;
				this.changed( \joystick, joyH, joyV );
			};
		}, 0, 14 );
	}
	
	dispose {
	
	}
	
	prControl { arg ch, num, val;
		out.control( ch, num, val );
	}
	
	channelFader {Êarg ch, val;
		var coarse, fine;
		
		val		= val * 127.875;
		coarse	= val.asInteger;
		fine		= (val - coarse) * 128;
		
		this.prControl( 0, ch + 32, fine );
		this.prControl( 0, ch, coarse );
	}
	
	masterFader {Êarg val;
		var coarse, fine;
		
		val		= val * 127.875;
		coarse	= val.asInteger;
		fine		= (val - coarse) * 128;
		
		this.prControl( 0, 56, fine );
		this.prControl( 0, 24, coarse );
	}
	
	channelSelect { arg ch, mode;
		this.prControl( 1, (ch << 2) + 1, mode );
	}
	
	channelSolo { arg ch, mode;
		this.prControl( 1, (ch << 2) + 2, mode );
	}

	channelMute { arg ch, mode;
		this.prControl( 1, (ch << 2) + 3, mode );
	}

	channelKnob { arg ch, val, lamp = false;
//		this.prControl( 0, ch + 64, (val * 15).asInteger + if( lamp, 64, 0 ));
		this.prControl( 0, ch + 64, (val * 14).asInteger + if( lamp, 65, 1 ));
	}

	// note: 0 ... 1
	channelKnobPan { arg ch, val, lamp = false;
//		this.prControl( 0, ch + 64, (val * 7).asInteger + if( lamp, 88, 24 ));
		this.prControl( 0, ch + 64, (val * 14).asInteger + if( lamp, 81, 17 ));
	}

	channelKnobFill { arg ch, val, lamp = false;
		this.prControl( 0, ch + 64, (val * 15).asInteger + if( lamp, 96, 32 ));
	}

	channelKnobDisco { arg ch, val, lamp = false;
		this.prControl( 0, ch + 64, (val * 15).asInteger + if( lamp, 112, 48 ));
	}

	channelSelectMutex { arg chan;
		var cc;
		numChannels.do({ arg i;
			cc = (i * 2) + 1;
			out.control( 1, cc, if( i == chan, 127, 0 ));
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
		this.prControl( 1, 121, onOff.if( 127, 0 ));
	}

	channelSoloMutex { arg chan;
		var cc;
		numChannels.do({ arg i;
			cc = (i << 1) + 2;
			out.control( 1, cc, if( i == chan, 127, 0 ));
		});
	}
}