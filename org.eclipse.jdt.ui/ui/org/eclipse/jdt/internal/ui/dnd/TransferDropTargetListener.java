/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.dnd;

import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.Transfer;

/**
 * A special drop target listener which is typed with a <code>TransferData</code>.
 */
public interface TransferDropTargetListener extends DropTargetListener {

	/**
	 * Returns the transfer used by this drop target.
	 */
	public Transfer getTransfer();
	
	/**
	 * Returns whether the listener is able to handle the given
	 * drop traget event.
	 * 
	 * @param event the drop target event
	 * 
	 * @return <code>true</code> if the listener can handle the event;
	 *  otherwise <code>false</code>
	 */
	public boolean isEnabled(DropTargetEvent event);
}
