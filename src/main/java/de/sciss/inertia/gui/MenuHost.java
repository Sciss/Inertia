//
//  MenuHost.java
//  Eisenkraut
//
//  Created by Hanns Holger Rutz on 24.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.util.*;

public class MenuHost
{
	public final BasicFrame			who;
	public final java.util.List		syncs	= new ArrayList();	// element : SyncedMenuAction
		
	public MenuHost( BasicFrame who )
	{
		this.who			= who;
	}
}
