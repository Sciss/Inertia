/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: GeneratorUnit, TypeSafe
 *
 *	Changelog:
 *
 *	@version	0.1, 17-Jun-06
 *	@author	Hanns Holger Rutz
 */
MicUnit : GeneratorUnit {
	var defName = nil;
	var synth = nil;
	
	classvar <allUnits;				// elem = instances of MicUnit

	var attributes;
	var mics;
	var micIdx	= nil;
	var micBus;
	var boost		= 1.0;

	var micAttrDirty			= true;
	
	var feedback	= false;

	*initClass {
		allUnits				= IdentitySet.new;
	}

	*new { arg server;
		^super.new( server ).prInitMicUnit;
	}
	
	prInitMicUnit {
		mics				= List.new;

		attributes		= [
			UnitAttr( \boost,       ControlSpec( 0.1, 10, \exp ), \normal, \getBoost,       \setBoost,       nil ),
			UnitAttr( \feedbackInt, ControlSpec( 0, 1, \lin, 1 ), \normal, \getFeedbackInt, \setFeedbackInt, nil ),
			nil	// \micIndex
		];

		allUnits.add( this );
	}
	
	addMic { arg bus;
		var file, name;
		this.prCacheDefs( bus.numChannels );
		mics.add( bus );
		micAttrDirty = true;
	}
	
	prSetMics { arg micList;
		mics = List.newFrom( micList );
		micAttrDirty = true;
	}
	
	setMicIndex { arg idx;
		var mic;
		
		mic = mics[ idx ];
		if( mic.notNil, {
			this.setMic( mic );
			micIdx = idx;
		});
	}
	
	getMicIndex {
		^micIdx;
	}
	
	getNumMics {
		^mics.size;
	}

	setBoost { arg vol;
		boost = vol;
		if( synth.notNil, {
			synth.set( \boost, vol );
		});
	}
	
	getBoost {
		^boost;
	}
	
	setFeedbackInt { arg onOff;
		this.setFeedback( onOff != 0 );
	}
	
	setFeedback { arg onOff;
		var wasPlaying;
	
		wasPlaying = playing;
		if( wasPlaying, { this.stop });
		feedback		= onOff;
		defName		= ("micUnit" ++ numChannels ++ if( feedback, "fb", "" )).asSymbol;
		if( wasPlaying, { this.play; });
	}
	
	getFeedbackInt {
		^feedback.binaryValue;
	}	
	
	getFeedback {
		^feedback;
	}
	
	getAttributes {
		if( micAttrDirty, {
			attributes[ attributes.size - 1 ] = UnitAttr( \micIndex, ControlSpec( 0, this.getNumMics - 1, \lin, 1 ), \normal, \getMicIndex, \setMicIndex, nil );
			micAttrDirty		= false;
		});
		^attributes;
	}

	setMic { arg bus;
		var wasPlaying;
	
		wasPlaying = playing;
		if( wasPlaying, { this.stop });
		this.prCacheDefs( bus.numChannels );
		micBus		= bus;
		numChannels	= micBus.numChannels;
		defName		= ("micUnit" ++ numChannels ++ if( feedback, "fb", "" )).asSymbol;
		if( wasPlaying, { this.play; });
	}

	cmdPeriod {
		var result;
		
		result		= super.cmdPeriod;
		synth		= nil;
		^result;
	}

	play {
		var bndl;

		bndl = List.new;
		this.playToBundle( bndl );
		server.listSendBundle( nil, bndl );
	}
	
	playToBundle { arg bndl;
		var outBus;

		if( playing, {
			this.stop;
			if( synth.notNil, {Êthis.protRemoveNode( synth );});
			synth = nil;
		});

		if( micBus.notNil, {
			synth	= Synth.basicNew( defName, server );
			outBus	= this.getOutputBus;
			bndl.add( synth.newMsg( target, [ \out, outBus.notNil.if({ outBus.index }, 0 ),
				\channel, micBus.index, \boost, boost ], \addToHead ));
			this.protAddNode( synth );
			this.protSetPlaying( true );
		}, {
			TypeSafe.methodWarn( thisMethod, "No mic has been specified" );
		});
	}

	dispose {
		allUnits.remove( this );
		^super.dispose;
	}
	
	*disposeAll {
		var all;
		
		all = List.newFrom( allUnits );
		all.do({ arg unit; unit.dispose; });
	}
	
	prCacheDefs { arg numChannels;
		var defName, def;

		defName = ("micUnit" ++ numChannels).asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, {
				arg out, channel, boost = 1.0;
							
				var ins, outs;
				
//				in = AudioIn.ar( (1 .. numChannels), boost );
				ins	= (In.ar( channel, numChannels ) * boost).asArray;
				outs	= Array.fill( numChannels, { arg ch;
					ins.at( ch );
				});
				OffsetOut.ar( bus: out, channelsArray: outs );
			});
			defCache.add( def );
		});

		defName = ("micUnit" ++ numChannels ++ "fb").asSymbol;
		if( defCache.contains( defName ).not, {
			def = SynthDef( defName, {
				arg out, channel, boost = 1.0;
				var ins, outs, dly, amp, slope, comp, bandFreqs, flt, band;
				
				bandFreqs	= [ 150, 800, 3000 ];
				ins		= (HPZ1.ar( In.ar( channel, numChannels )) * boost).asArray;
				outs		= 0;
				flt		= ins;
				bandFreqs.do({ arg maxFreq, idx;
					if( maxFreq != bandFreqs.last, {
						band	= LPF.ar( flt, maxFreq );
						flt	= HPF.ar( flt, maxFreq );
					}, {
						band	= flt;
					});
					amp		= Amplitude.kr( band, 2, 2 );
					slope	= Slope.kr( amp );
					comp		= Compander.ar( band, band, 0.1, 1, slope.max( 1 ).reciprocal, 0.01, 0.01 );
					outs		= outs + band;
				});
				dly = DelayC.ar( outs, 0.0125, LFDNoise1.kr( 5, 0.006, 0.00625 ));
				outs	= Array.fill( numChannels, { arg ch;
					dly.at( ch );
				});
				OffsetOut.ar( bus: out, channelsArray: outs );
			});
			defCache.add( def );
		});
	}

	n_end { arg node;
		if( node === synth, {
			synth		= nil;
			this.protSetPlaying( false );
		});
		^super.n_end( node );
	}

	protDuplicate { arg dup;
		dup.prSetMics( mics );
		dup.setBoost( this.getBoost );
		dup.setFeedback( this.getFeedback );
		dup.setMicIndex( this.getMicIndex );
	}
}