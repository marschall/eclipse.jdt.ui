/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.compare;

import java.io.*;
import java.util.ResourceBundle;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.*;

import org.eclipse.ui.IActionDelegate;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.compare.*;
import org.eclipse.compare.structuremergeviewer.DiffNode;


public class JavaCompareAction implements IActionDelegate {
	
	class TypedElement implements ITypedElement, IStreamContentAccessor {
		
		private ISourceReference fSource;
		
		TypedElement(ISourceReference s) {
			fSource= s;
		}
		
		public String getName() {
			return fJavaElementLabelProvider.getText(fSource);
		}
		
		public String getType() {
			return "JAVA"; //$NON-NLS-1$
		}
		
		public Image getImage() {
			return fJavaElementLabelProvider.getImage(fSource);
		}
		
		public InputStream getContents() throws CoreException {
			
			String s= null;
			try {
				s= getExtendedSource(fSource);
			} catch(JavaModelException ex) {
			}
			if (s == null)
				s= ""; //$NON-NLS-1$
			return new ByteArrayInputStream(s.getBytes());
		}
		
		String getExtendedSource(ISourceReference ref) throws JavaModelException {
			
			// get parent
			if (ref instanceof IJavaElement) {
				IJavaElement parent= ((IJavaElement) ref).getParent();
				if (parent instanceof ISourceReference) {
					ISourceReference sr= (ISourceReference) parent;
					String parentContent= sr.getSource();
					ISourceRange parentRange= sr.getSourceRange();
					
					ISourceRange childRange= ref.getSourceRange();
					
					int start= childRange.getOffset() - parentRange.getOffset();
					int end= start + childRange.getLength();
					
					// search backwards for beginning of line
					while (start > 0) {
						char c= parentContent.charAt(start-1);
						if (c == '\n' || c == '\r')
							break;
						start--;
					}
					
					return parentContent.substring(start, end);
				}
			}
			
			return ref.getSource();
		}
	}
	
	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.ui.compare.CompareAction"; //$NON-NLS-1$

	private ISelection fSelection;
	private ISourceReference fLeft;
	private ISourceReference fRight;
	
	private JavaElementLabelProvider fJavaElementLabelProvider;
	

	public void selectionChanged(IAction action, ISelection selection) {
		fSelection= selection;
		action.setEnabled(isEnabled(selection));
	}
	
	public void run(IAction action) {
		Shell shell= JavaPlugin.getActiveWorkbenchShell();
		ResourceBundle bundle= ResourceBundle.getBundle(BUNDLE_NAME);
		CompareDialog d= new CompareDialog(shell, bundle);
					
		fJavaElementLabelProvider= new JavaElementLabelProvider(
					JavaElementLabelProvider.SHOW_PARAMETERS |
					JavaElementLabelProvider.SHOW_OVERLAY_ICONS |
					JavaElementLabelProvider.SHOW_ROOT);
		
		d.compare(new DiffNode(
			new TypedElement(fLeft), 
			new TypedElement(fRight))
		);
		
		fJavaElementLabelProvider.dispose();
		fJavaElementLabelProvider= null;
	}
	
	protected boolean isEnabled(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			Object[] sel= ((IStructuredSelection) selection).toArray();
			if (sel.length == 2) {
				for (int i= 0; i < 2; i++) {
					Object o= sel[i];
					if (!(o instanceof ISourceReference))
						return false;
				}
				fLeft= (ISourceReference) sel[0];
				fRight= (ISourceReference) sel[1];				
				return true;
			}
		}
		return false;
	}
}
