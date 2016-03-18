// last mod : 09-oct-05

m = 2;	// max number of input channels
p = "/UserOrdner/Rutz/Inertia/synthdefs/";
p = "/Volumes/Claude/Developer/Inertia/synthdefs/";
p = "/Applications/Inertia/synthdefs/";
p = "/Volumes/ProtoolsSound2/Users/Rutz/Trailer/Inertia/synthdefs/";

// information synth that
// reads from a mono audio bus
// and writes peak and mean-square
// to two adjectant control busses
// which can be queried using /b_getn
SynthDef( "inertia-recmeter", { arg i_aInBus = 0, i_kOutBus, i_peakFreq = 30;
	var in, rms, peak, trig, hold;
	
	in	= In.ar( i_aInBus );
	trig	= Impulse.kr( i_peakFreq );
	rms	= Lag.ar( in.squared, 0.1 );
	peak	= Latch.kr( Peak.ar( abs( in ), trig ), trig );
//	hold	= Peak.kr( peak, reset );
	
//		Out.kr( i_kOutBus, [ peak, rms ]);
	// latch because /c_getn is not synchronous,
	// so we're maximally 1/i_peakFreq behind realtime
	Out.kr( i_kOutBus, [ peak, rms ]);
	
}).writeDefFile( p );

m.do({ |i|
	var numChannels = i + 1;

	SynthDef( "inertia-sfplay" ++ numChannels, {
		arg i_aInBuf, i_aOutBus, i_gain = 1.0, rate = 1.0, i_interpolation = 1;

		var phasorRate, halfPeriod, numFrames, phasor, phasorTrig, clockTrig;
	
		phasorRate 	= BufRateScale.kr( bufnum: i_aInBuf ) * rate;
		halfPeriod	= BufDur.kr( bufnum: i_aInBuf ) / (2 * rate);
		numFrames		= BufFrames.kr( bufnum: i_aInBuf );
		phasor		= Phasor.ar( rate: phasorRate, start: 0, end: numFrames );
		phasorTrig	= Trig1.ar( in: phasor - (numFrames / 2), dur: 0.01 );
		clockTrig		= phasorTrig + TDelay.ar( phasorTrig, halfPeriod );
	
		SendTrig.ar( in: clockTrig, id: 0, value: PulseCount.ar( trig: clockTrig ));

		OffsetOut.ar( bus: i_aOutBus, channelsArray: BufRd.ar( numChannels: numChannels, bufnum: i_aInBuf,
			phase: phasor, loop: 0, interpolation: i_interpolation ) * i_gain );
	}).writeDefFile( p );
});

m.do({ |i|
	var numChannels = i + 1;

	SynthDef( "inertia-sfrec" ++ numChannels, {
		arg i_aInBus, i_aOutBuf;
		
		DiskOut.ar( i_aOutBuf, In.ar( i_aInBus, numChannels ));
	}).writeDefFile( p );
});

(
m.do({ |i|
	var numChannels = i + 1;

	SynthDef( "inertia-debugstrip" ++ numChannels, {
		arg i_aBus = 0, i_kOutBus, i_peakFreq = 30, debugVolume = 1.0, i_aSoloBus = 0, solo = 0;
		var in, rms, peak, trig;
		
		in	= In.ar( i_aBus, numChannels );
		trig	= Impulse.kr( i_peakFreq );
		rms	= Lag.ar( in.squared, 0.1 );
		peak	= Latch.kr( Peak.ar( abs( in ), trig ), trig );
		
		// latch because /c_getn is not synchronous,
		// so we're maximally 1/i_peakFreq behind realtime
		Out.kr( i_kOutBus, [ peak, rms ].flop.flatten );
		ReplaceOut.ar( i_aBus, in * debugVolume );
		OffsetOut.ar( i_aSoloBus, in * solo );
	}).writeDefFile( p );
});
)

SynthDef.new( "inertia-fadein", { |i_aBus, i_dur|
	var line, lineB, lineTemp;
	line 	= Line.ar( 0.0, 1.0, i_dur, doneAction: 2 );
	ReplaceOut.ar( i_aBus, In.ar( i_aBus ) * line );
}).writeDefFile( p );


SynthDef.new( "inertia-fadeout", { |i_aBus, i_dur|
	var line, lineB, lineTemp;
	line 	= Line.ar( 1.0, 0.0, i_dur, doneAction: 2 );
	ReplaceOut.ar( i_aBus, In.ar( i_aBus ) * line );
}).writeDefFile( p );

// reads input from bus
// and writes it to another bus
(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-route" ++ numChannels, {
		arg i_aInBus, i_aOutBus;

		OffsetOut.ar( bus: i_aOutBus, channelsArray: In.ar( bus: i_aInBus, numChannels: numChannels ));
	}).writeDefFile( p );
});
)

// reads input from bus
// and writes it to two other busses
(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-accum" ++ numChannels, {
		arg i_aInBus, i_aOutBus, i_aAccumBus;
		var in;

		in	= In.ar( bus: i_aInBus, numChannels: numChannels );

		OffsetOut.ar( bus: i_aOutBus, channelsArray: in );
		OffsetOut.ar( bus: i_aAccumBus, channelsArray: in );
	}).writeDefFile( p );
});
)

// ------------------------- filter ---------------------------------

// gate
(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-layerFilter1" ++ numChannels, {
		arg i_aBusA, i_aBusB;
		var inA, inB, inBAbs, flt, trig;

		inA		= In.ar( bus: i_aBusA, numChannels: numChannels );
		inB		= In.ar( bus: i_aBusB, numChannels: numChannels );
		trig		= Impulse.kr( 20 );
		inBAbs	= Limiter.ar( LPF.ar( Latch.ar( Peak.ar( inB, trig ), trig ), 20 ) * 128 );
//		inBAbs	= Limiter.ar( LPF.kr( Latch.kr( Peak.kr( A2K.kr( inB ), trig ), trig ), 20 ) * 128 );
//		flt		= inA * 0; // TEST LPF.ar( inA, inBAbs * -20000 + 20020 );
		flt		= Compander.ar( LPZ1.ar( HPZ1.ar( inA )), inBAbs, Amplitude.ar( inBAbs ) * 1.25, 10, 1, 0.005, 0.03, mul: 4 );

		ReplaceOut.ar( bus: i_aBusA, channelsArray: flt );
	}).writeDefFile( p );
});
)

// conv
(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-layerFilter2" ++ numChannels, {
		arg i_aBusA, i_aBusB;
		var inA, inB, inBLim, flt;

		inA		= In.ar( bus: i_aBusA, numChannels: numChannels );
		inB		= In.ar( bus: i_aBusB, numChannels: numChannels );
		inBLim	= Limiter.ar( Mix.ar( inB ) * 4 );
//		inBAbs	= Limiter.ar( LPF.kr( Latch.kr( Peak.kr( A2K.kr( inB ), trig ), trig ), 20 ) * 128 );
//		flt		= inA * 0; // TEST LPF.ar( inA, inBAbs * -20000 + 20020 );
		flt		= Convolution.ar( inA, inBLim, 512, mul: 0.25 );

		ReplaceOut.ar( bus: i_aBusA, channelsArray: HPZ1.ar( flt ));
	}).writeDefFile( p );
});
)

(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-layerFilter3" ++ numChannels, {
		arg i_aBusA, i_aBusB;
		var inA, inB, inBAbs, trig, flt;

		inA		= In.ar( bus: i_aBusA, numChannels: numChannels );
		inB		= In.ar( bus: i_aBusB, numChannels: numChannels );
		trig		= Impulse.kr( 20 );
//		inBLim	= Limiter.ar( Mix.ar( inB ) * 4 );
		inBAbs	= Limiter.ar( LPF.kr( Latch.kr( Peak.kr( A2K.kr( inB ), trig ), trig ), 20 ) * 128, 23 );
//		flt		= inA * 0; // TEST LPF.ar( inA, inBAbs * -20000 + 20020 );
		flt		= MantissaMask.ar( inA, inBAbs );
//		flt		= (flt * 0.9) + (0.05 * Mix.ar( CombL.ar( flt, 0.1, LFNoise1.kr( Array.fill( 8, {rrand(0.01,0.05)}), 0.02, 0.03), 1 )));
		8.do({
			flt	= (flt * 0.95) + (0.05 * CombN.ar( flt, 0.4, LFNoise1.kr( {rrand(0.01,0.05)}, 0.1, 0.2), 0.5 ));
		});
		ReplaceOut.ar( bus: i_aBusA, channelsArray: LPZ1.ar( flt ));
	}).writeDefFile( p );
});
)

(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-layerLowpass" ++ numChannels, {
		arg i_aBusA, i_aBusB;
		var inA, inB, inBAbs, flt, trig;

		inA		= In.ar( bus: i_aBusA, numChannels: numChannels );
		inB		= In.ar( bus: i_aBusB, numChannels: numChannels );
		trig		= Impulse.kr( 20 );
		inBAbs	= Limiter.ar( LPF.ar( Latch.ar( Peak.ar( inB, trig ), trig ), 20 ) * 128 );
		flt		= LPF.ar( inA, inBAbs * -20000 + 20020 );

		ReplaceOut.ar( bus: i_aBusA, channelsArray: flt );
	}).writeDefFile( p );
});
)

// TODO: now it needs initial non-zero trigger to start playing
(
m.do({ |i|
	var numChannels = i + 1;
	SynthDef( "inertia-layerAmpMod" ++ numChannels, {
		arg i_aBusA, i_aBusB;
		var inA, inB, flt, inBAbs, trig;

		inA		= In.ar( bus: i_aBusA, numChannels: numChannels );
		inB		= In.ar( bus: i_aBusB, numChannels: numChannels );
		trig		= Impulse.kr( 20 );
//		inBAbs	= Limiter.ar( LPF.ar( Latch.ar( Peak.ar( inB, trig ), trig ), 10 ) * 4 );
		inBAbs	= Limiter.ar( Decay2.ar( Latch.ar( Peak.ar( inB, trig ), trig ), 0.1, 0.5 ) * 2 );
//		inBAbs	= min( 1.0, Decay.ar( in: abs( inB ), decayTime: 0.1 ));
		flt		= inA * (1.0 - inBAbs + inB); // * Gate.ar( sign( inB ), inBAbs );

		ReplaceOut.ar( bus: i_aBusA, channelsArray: flt );
	}).writeDefFile( p );
});
)


// ---------------------------------------------------------------------

// have a try with integrated envelope
m.do({ |i|
	var numChannels = i + 1;

	SynthDef( "inertia-sfplay" ++ numChannels, {
		arg	i_aInBuf, i_aOutBus, i_gain = 1.0, rate = 1.0, i_interpolation = 1,
			i_time1 = 0.0, i_time2, i_time3 = 0.0;

		var phasorRate, halfPeriod, numFrames, phasor, phasorTrig, clockTrig, env;
	
		env			= EnvGen.kr( envelope: Env.new([ 0, 1, 1, 0 ], [ i_time1, i_time2, i_time3 ], 'linear' ),
						levelScale: i_gain, doneAction: 2 );
		phasorRate 	= BufRateScale.kr( bufnum: i_aInBuf ) * rate;
		halfPeriod	= BufDur.kr( bufnum: i_aInBuf ) / (2 * rate);
		numFrames		= BufFrames.kr( bufnum: i_aInBuf );
		phasor		= Phasor.ar( rate: phasorRate, start: 0, end: numFrames );
		phasorTrig	= Trig1.ar( in: phasor - (numFrames / 2), dur: 0.01 );
		clockTrig		= phasorTrig + TDelay.ar( phasorTrig, halfPeriod );
	
		SendTrig.ar( in: clockTrig, id: 0, value: PulseCount.ar( trig: clockTrig ));

		OffsetOut.ar( bus: i_aOutBus, channelsArray: BufRd.ar( numChannels: numChannels, bufnum: i_aInBuf,
			phase: phasor, loop: 0, interpolation: i_interpolation ) * env );
	}).writeDefFile( p );
});

//// replacing the output (when writing to filter bus)
//(
//m.do({ |i|
//	var numChannels = i + 1;
//
//	SynthDef( "inertia-sfplayRplc" ++ numChannels, {
//		arg	i_aInBuf, i_aOutBus, i_gain = 1.0, rate = 1.0, i_interpolation = 1,
//			i_time1 = 0.0, i_time2, i_time3 = 0.0;
//
//		var phasorRate, halfPeriod, numFrames, phasor, phasorTrig, clockTrig, env;
//	
//		env			= EnvGen.kr( envelope: Env.new([ 0, 1, 1, 0 ], [ i_time1, i_time2, i_time3 ], 'linear' ),
//						levelScale: i_gain, doneAction: 2 );
//		phasorRate 	= BufRateScale.kr( bufnum: i_aInBuf ) * rate;
//		halfPeriod	= BufDur.kr( bufnum: i_aInBuf ) / (2 * rate);
//		numFrames		= BufFrames.kr( bufnum: i_aInBuf );
//		phasor		= Phasor.ar( rate: phasorRate, start: 0, end: numFrames );
//		phasorTrig	= Trig1.ar( in: phasor - (numFrames / 2), dur: 0.01 );
//		clockTrig		= phasorTrig + TDelay.ar( phasorTrig, halfPeriod );
//	
//		SendTrig.ar( in: clockTrig, id: 0, value: PulseCount.ar( trig: clockTrig ));
//
//		ReplaceOut.ar( bus: i_aOutBus, channelsArray: BufRd.ar( numChannels: numChannels, bufnum: i_aInBuf,
//			phase: phasor, loop: 0, interpolation: i_interpolation ) * env );
//	}).writeDefFile( p );
//});
//)

(
n = 8;

(n - 1).do({ |i|
	var numChannels = i + 2;

	SynthDef( "inertia-pan" ++ numChannels, {
		arg i_aInBus, i_aOutBus, pos = 0.0, width = 2.0, orient = 0.0, volume = 1.0;
		
		OffsetOut.ar( bus: i_aOutBus, channelsArray: PanAz.ar( numChans: numChannels,
			in: In.ar( i_aInBus ), pos: pos, level: volume, width: width, orientation: orient ));
	}).writeDefFile( p );
});
)
