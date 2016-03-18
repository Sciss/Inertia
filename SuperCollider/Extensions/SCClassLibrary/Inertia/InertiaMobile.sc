/**
 *	@version	0.1, 25-Nov-06
 *	@author	Hanns Holger Rutz
 */
InertiaMobile {
	classvar <>kMinDelay	= 5000;
	classvar <>kMaxDelay	= 60000;
	classvar <>kMaxStep	= 10000;
	
	var	timer;
	var	delay;
	var	lastSwitch	= -1;
	var keepRunning;

	var	<doc;

	*new { arg doc;
		^super.new.prInit( doc );
	}
	
	prInit { arg argDoc;
		doc		= argDoc;
		delay	= this.expo.linlin( 0, 1, kMinDelay, kMaxDelay ).asInteger;
		timer	= Task({
			while({ÊkeepRunning }, {
				("waiting for "++(delay/1000)++" secs").postln;
				(delay/1000).wait;
				"dang".postln;
				this.actionPerformed;
			});
		});
		this.reschedule;
timer.start;
	}

	stopMobile {
		keepRunning = false;
		timer.stop;
	}
	
	restart {
		timer.stop;
		timer.reset;
		keepRunning = true;
		timer.start;
	}
	
	isPlaying {
		^timer.isPlaying;
	}
	
	actionPerformed {
		var layer;
		layer = (doc.player.syncLayer.size - 1).rand;
		if( layer == lastSwitch, {
			layer = (layer + 0.5.coin.if( 1, -1 )) % (doc.player.syncLayer.size - 1);
//			layer = layer + 0.5.coin.if( 1, -1 );
//			if( layer < 0, { layer = syncLayer.size - 2 },
//			{ if( layer == syncLayer.length - 1 ) layer--;
		});
		doc.layers.switchLayers( this, layer, layer + 1 );
		lastSwitch = layer;
	
		this.reschedule;
	}
	
	expo {
		var doubleYouBushiThatSonOfABitch = 1.0.rand;
		^doubleYouBushiThatSonOfABitch.squared;
	}
	
	reschedule {
		var schnucki6000;
		schnucki6000	= (this.expo * (0.5.coin.if( kMaxStep, kMaxStep.neg ))).asInteger;
		delay			= (delay + schnucki6000).clip( kMinDelay, kMaxDelay );
//		timer.setInitialDelay( delay );
//		timer.restart();
keepRunning = true;
//		this.restart;
	}
}