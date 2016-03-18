/**
 *	(C)opyright 2007 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: TapeUnit
 */
TapeUnitGUI {
	var <unit, <gui;

	var <ggGoStart, <ggStop, <ggPlay, <ggRwd, <ggFFwd, <parentView, pen;
	var colrWhiteDis, colrBlackDis, taskUpdate, keepTaskRunning, condUpdate;
	var taskTransportUpdate; // , modelPos;
	var <ggTime;

	*new {Êarg parentView, rect;
		^super.new.prInitGUI( parentView, rect );
	}
	
	unit_ {Êarg tape;
		if( unit.notNil, {
			unit.removeDependant( this );
			unit = nil;
		});
		
		unit = tape;
		if( unit.notNil, {
			unit.addDependant( this );
		});
	}

	update { arg obj, what ... params;
		if( obj != unit, {Ê^this });
		
		case
		{Êwhat === \unitDisposed }
		{
			this.unit_( nil );
			{
				ggGoStart.enabled	= false;
				ggStop.enabled	= false;
				ggPlay.enabled	= false;
				ggRwd.enabled		= false;
				ggFFwd.enabled	= false;
			}.defer;
		}
		{Êwhat === \unitPlaying }
		{
			if( params.first, {
				taskTransportUpdate.start;
				{
					ggPlay.refresh;
					ggStop.refresh;
				}.defer;
			}, {
				taskTransportUpdate.stop;
				{
					this.prUpdatePos;
					ggPlay.refresh;
					ggStop.refresh;
				}.defer;
			});
		};
	}
	
	prInitGUI { arg argParentView, rect;
		var taskUpdate, flagPlay = false, flagStop = false, flagUpdatePosition = false;
		var flagUpdateLoop = false, updateLoop = nil;
		var updatePosition = 0;
		
		gui			= GUI.current;
		pen			= gui.pen;
		parentView	= argParentView;
		
		colrWhiteDis	= Color( 1.0, 1.0, 1.0, 0.5 );
		colrBlackDis	= Color( 0.0, 0.0, 0.0, 0.5 );
		
		condUpdate	= Condition.new;
//		modelPos		= Object.new;

		taskUpdate	= Task({ var sel, pos, uLoop, uPos, uPlay, uStop, wasPlaying; while({ÊkeepTaskRunning }, {
			condUpdate.wait;
			condUpdate.test	= false;
			uLoop			= flagUpdateLoop;	// need to save because of server.sync!
			uPos				= flagUpdatePosition;
			uPlay			= flagPlay;
			uStop			= flagStop;
			flagUpdateLoop	= false;
			flagUpdatePosition	= false;
			flagPlay			= false;
			flagStop			= false;
			if( unit.notNil, {
				sel 	= updateLoop;
				pos	= updatePosition;
				unit.server.sync;
				wasPlaying = unit.isPlaying;
				if( uStop, {
//					monitors[ idx ].stop;
					unit.stop;
					unit.server.sync;
				}, { if( wasPlaying, {
					unit.stop;
					unit.server.sync;
				})});
				if( unit.notNil, {
					if( uLoop, {
						if( sel.notNil and: { sel.isEmpty }, {
							unit.loopAll;
						}, {
							unit.setLoop( sel );
						});
					});
					if( uPlay, {
						unit.play;
	// XXX
	//					unit.play( ggWave.timeCursorPosition );
						unit.server.sync;
					
					}, {Êif( wasPlaying && uStop.not, {
						unit.play( if( uPos, pos, nil ));
						unit.server.sync;
					})});
				});
			});
		})}, AppClock );
		
		ggGoStart		= IconButton( parentView, Rect( rect.left, rect.top, 40, 30 ), { arg b, bounds;
				var extent;
				bounds		= bounds.insetBy( 2, 2 );
				pen.color		= if( b.enabled, Color.black, colrBlackDis );
				pen.line( (bounds.left + 8.5) @ (bounds.top + 4),
						 (bounds.left + 8.5) @Ê(bounds.top + bounds.height - 4) );
				pen.stroke;
				pen.moveTo( (bounds.left + 8) @ (bounds.top + (bounds.height / 2)) );
				pen.lineTo( (bounds.left + bounds.width - 8) @ (bounds.top + 4) );
				pen.lineTo( (bounds.left + bounds.width - 8) @ (bounds.top + bounds.height - 4) );
				pen.lineTo( (bounds.left + 8) @ (bounds.top + (bounds.height / 2)) );
				pen.fill;
			})
			.action_({Êarg b;
				updatePosition	= 0;
				flagUpdatePosition	= true;
				condUpdate.test	= true;
				condUpdate.signal;
			});
	
		ggStop	= IconButton( parentView, Rect( rect.left + 48, rect.top, 40, 30 ), { arg b, bounds;
				var extent;
				bounds		= bounds.insetBy( 2, 2 );
				if( unit.notNil and: {Êunit.isPlaying.not }, {
					pen.fillColor = Color.red;
					pen.fillRect( bounds );
					pen.color	= if( b.enabled, Color.white, colrWhiteDis );
				}, {
					pen.color	= if( b.enabled, Color.black, colrBlackDis );
				});
				extent = min( bounds.width, bounds.height ) - 8;
				pen.fillRect( Rect.aboutPoint( bounds.center, extent/2, extent/2 ));
			})
			.action_({Êarg b;
				flagStop			= true;
				condUpdate.test	= true;
				condUpdate.signal;
			});

		ggPlay		= IconButton( parentView, Rect( rect.left + 96, rect.top, 40, 30 ), { arg b, bounds;
				var extent;
				bounds		= bounds.insetBy( 2, 2 );
				if( unit.notNil and: {Êunit.isPlaying }, {
					pen.fillColor = Color.green( 0.7 );
					pen.fillRect( bounds );
					pen.color	= if( b.enabled, Color.white, colrWhiteDis );
				}, {
					pen.color	= if( b.enabled, Color.black, colrBlackDis );
				});
				pen.moveTo( (bounds.left + 8) @ (bounds.top + 4) );
				pen.lineTo( (bounds.left + bounds.width - 8) @ (bounds.top + (bounds.height / 2)) );
				pen.lineTo( (bounds.left + 8) @ (bounds.top + bounds.height - 4) );
				pen.fill;
			})
			.action_({Êarg b;
				flagPlay			= true;
				condUpdate.test	= true;
				condUpdate.signal;
			});
	
		ggRwd		= IconButton( parentView, Rect( rect.left + 144, rect.top, 40, 30 ), { arg b; });
		ggFFwd		= IconButton( parentView, Rect( rect.left + 192, rect.top, 40, 30 ), { arg b; });

		ggTime 		= gui.staticText.new( parentView, Rect( rect.left + 240, rect.top, 170, 30 ))
			.background_( Color.black )
			.stringColor_( Color.yellow )
			.font_( gui.font.new( gui.font.defaultMonoFace, 20 ));

		keepTaskRunning = true;
		taskUpdate.start;	// XXX need to register for stopping somehow

		taskTransportUpdate = Task({ inf.do({
			this.prUpdatePos;
			0.05.wait;
		})}, AppClock );
	}

	prUpdatePos {
		var frame;
		if( unit.notNil, {
//			modelPos.changed( \value, unit.getCurrentPos );
			frame = unit.getCurrentPos;
			ggTime.string = this.prAsTimeString( frame / unit.getSampleRate );
		});
	}

	// like LJP's one but a period instead of a colon to the millis, leading space
	prAsTimeString { arg secs;
		var decimal, hours, minutes, seconds, mseconds, string;
		decimal = secs.asInteger;
		
		hours = (decimal.div(3600)).asString;
		if(hours.size < 2, { hours = "0" ++ hours });
		
		minutes = (decimal.div(60) % 60).asString;
		if(minutes.size < 2, { minutes = "0" ++ minutes });
		
		seconds = (decimal % 60).asString;
		if(seconds.size < 2, { seconds = "0" ++ seconds });
		
		mseconds = (secs.frac*1000).round.asInteger.asString;
		if(mseconds.size < 3, { 
			mseconds = "0" ++ mseconds;
			if(mseconds.size < 3, { mseconds = "0" ++ mseconds });
		});
		
		^(" " ++ hours ++ ":" ++ minutes ++ ":" ++ seconds ++ "." ++ mseconds);
	}
	
	dispose {
		taskTransportUpdate.stop;
		keepTaskRunning	= false;
		this.unit_( nil );
		condUpdate.test	= true;
		condUpdate.signal;
	}
}

IconButton {
	var ggBut, ggUser, gui;

	*new {Êarg parent, bounds, iconFunc;
		^super.new.prInit( parent, bounds, iconFunc );
	}
	
	prInit { arg parent, bounds, iconFunc;
		gui		= GUI.current;
		ggUser	= gui.userView.new( parent, bounds );
		
		ggBut	= gui.button.new( parent, bounds );
		ggBut.states_([[ "", Color.black, Color.clear ]]);
		ggBut.canFocus_( false );

		ggUser.drawFunc_({ arg b; iconFunc.value( this, b.bounds )});
	}
	
	action {
		^ggBut.action;
	}
	
	action_ { arg func;
		ggBut.action = func;
	}
	
	refresh {
		if( ggUser.isClosed.not, { ggUser.refresh });
	}
	
	enabled {
		^ggBut.enabled;
	}
	
	enabled_ {Êarg onOff;
		if( ggBut.isClosed.not && ggUser.isClosed.not, {
			if( onOff != ggBut.enabled, {
				ggBut.enabled = onOff;
				ggUser.refresh;
			});
		});
	}
}