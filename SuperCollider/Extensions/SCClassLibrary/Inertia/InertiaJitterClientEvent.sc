InertiaJitterClientEvent : BasicEvent {
	classvar <kPlay	= 0;
	
	var <ch, <movieName, <movieTime, <movieRate;
	
	*new { arg source, id, when, ch, movieName, movieTime, movieRate;
		^super.new( source, id, when ).prInitIJCE( ch, movieName, movieTime, movieRate );
	}
	
	prInitIJCE {�arg argCh, argMovieName, argMovieTime, argMovieRate;
		ch			= argCh;
		movieName	= argMovieName;
		movieTime	= argMovieTime;
		movieRate	= argMovieRate;
	}
		
	incorporate�{�arg e;
		^false;
	}
}