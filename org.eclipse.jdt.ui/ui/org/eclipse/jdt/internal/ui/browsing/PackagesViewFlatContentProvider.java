/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 *
 * Contributors:
 *     IBM Corporation - initial implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.browsing;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * XXX: not yet reviewed - part of experimental logical packages view
 * 
 * Table content provider for the hierarchical layout in the packages view.
 * <p> 
 * XXX: The standard Java browsing part content provider needs and calls
 * the browsing part/view. This class currently doesn't need to do so
 * but might be required to later.
 * </p>
 */
class PackagesViewFlatContentProvider extends LogicalPackagesProvider implements IStructuredContentProvider, IElementChangedListener, IPropertyChangeListener{
	PackagesViewFlatContentProvider(StructuredViewer viewer) {
		super(viewer);
	}
	
	/*
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object parentElement) {
		
		if(parentElement instanceof IJavaElement){
			IJavaElement element= (IJavaElement) parentElement;

			int type= element.getElementType();
			
			try {
				switch (type) {
					case IJavaElement.JAVA_PROJECT :
						IJavaProject project= (IJavaProject) element;
						IPackageFragment[] children= getPackageFragments(project.getPackageFragments());
						if(isInCompoundState()) {
							fMapToLogicalPackage.clear();
							fMapToPackageFragments.clear();
							return combineSamePackagesIntoLogialPackages(children);	
						} else
							return children;
				
					case IJavaElement.PACKAGE_FRAGMENT_ROOT :
						fMapToLogicalPackage.clear();
						fMapToPackageFragments.clear();
						IPackageFragmentRoot root= (IPackageFragmentRoot) element;
						return root.getChildren();
						
					case IJavaElement.PACKAGE_FRAGMENT :
						//no children in flat view
						break;
				
					default :
						//do nothing, empty array returned
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
			
		}
		return new Object[0];
	}
	
	/*
	 * Weeds out packageFragments from external jars
	 */
	private IPackageFragment[] getPackageFragments(IPackageFragment[] iPackageFragments) {
		List list= new ArrayList();
		for (int i= 0; i < iPackageFragments.length; i++) {
			IPackageFragment fragment= iPackageFragments[i];
			IJavaElement el= fragment.getParent();
			if (el instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot root= (IPackageFragmentRoot) el;
				if(root.isArchive() && root.isExternal())
					continue;
			}
			list.add(fragment);
		}
		return (IPackageFragment[]) list.toArray(new IPackageFragment[list.size()]);
	}
	
	/*
	 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
	 */
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	/*
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (newInput != null) {
			JavaCore.addElementChangedListener(this);
		} else {
			JavaCore.removeElementChangedListener(this);
		}
	}
	

	/*
	 * @see org.eclipse.jdt.core.IElementChangedListener#elementChanged(org.eclipse.jdt.core.ElementChangedEvent)
	 */
	public void elementChanged(ElementChangedEvent event) {
		try {
			processDelta(event.getDelta());
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
	}

	
	private void processDelta(IJavaElementDelta delta) throws JavaModelException {

		int kind= delta.getKind();
		final IJavaElement element= delta.getElement();

		if (element instanceof IPackageFragment) {
			final IPackageFragment frag= (IPackageFragment) element;

			if (kind == IJavaElementDelta.REMOVED) {
				removeElement(frag);

			} else if (kind == IJavaElementDelta.ADDED) {
				addElement(frag);

			} else if (kind == IJavaElementDelta.CHANGED) {
				//just refresh 
				Object toBeRefreshed= element;
				if (element instanceof IPackageFragment) {
					IPackageFragment pkgFragment= (IPackageFragment)element;
					LogicalPackage logicalPkg= findLogicalPackage(pkgFragment);
					//deal with packages that have been filtered and are now visible
					if (logicalPkg != null)
						toBeRefreshed= findElementToRefresh(logicalPkg);
					else
						toBeRefreshed= findElementToRefresh(pkgFragment);
					
				}
				postRefresh(toBeRefreshed);
			}
			//in this view there will be no children of PackageFragment to refresh
			return;
		}
		processAffectedChildren(delta);
	}
	
	//test to see if element to be refreshed is being filtered out
	//and if so refresh the viewers input element (JavaProject or PackageFragmentRoot)
	private Object findElementToRefresh(IPackageFragment fragment) {
		if (fViewer.testFindItem(fragment) == null) {
			if(fProjectViewState)
				return fragment.getJavaProject();
			else return fragment.getParent();
		} 
		return fragment;
	}
	
	//test to see if element to be refreshed is being filtered out
	//and if so refresh the viewers input element (JavaProject or PackageFragmentRoot)
	private Object findElementToRefresh(LogicalPackage logicalPackage) {
		if (fViewer.testFindItem(logicalPackage) == null) {
			IPackageFragment fragment= logicalPackage.getFragments()[0];
			return fragment.getJavaProject();
		}
		return logicalPackage;
	}


	private void processAffectedChildren(IJavaElementDelta delta) throws JavaModelException {
		IJavaElementDelta[] children= delta.getAffectedChildren();
		for (int i= 0; i < children.length; i++) {
			IJavaElementDelta elementDelta= children[i];
			processDelta(elementDelta);
		}
	}

	private void postAdd(final Object child) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl = fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					((TableViewer)fViewer).add(child);
				}
			}
		});
	}


	private void postRemove(final Object object) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl = fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					((TableViewer)fViewer).remove(object);
				}
			}
		});
	}
	
	private void postRunnable(final Runnable r) {
		Control ctrl= fViewer.getControl();
		if (ctrl != null && !ctrl.isDisposed()) {
		//	fBrowsingPart.setProcessSelectionEvents(false);
			try {
				Display currentDisplay= Display.getCurrent();
				if (currentDisplay != null && currentDisplay.equals(ctrl.getDisplay()))
					ctrl.getDisplay().syncExec(r);
				else				
					ctrl.getDisplay().asyncExec(r);
			} finally {
		//		fBrowsingPart.setProcessSelectionEvents(true);
			}
		}
	}
	
	private void removeElement(IPackageFragment frag) {
		String key= getKey(frag);
		LogicalPackage lp= (LogicalPackage)fMapToLogicalPackage.get(key);
	
		if(lp != null){
			lp.remove(frag);
			//if you need to change the LogicalPackage to a PackageFragment 
			if(lp.getFragments().length == 1){
				IPackageFragment fragment= lp.getFragments()[0];
				fMapToLogicalPackage.remove(key);
				fMapToPackageFragments.put(key,fragment);		

				//@Improve: Should I replace this with a refresh of the parent?
				postRemove(lp);
				postAdd(fragment);
			} return;
		} else {
			fMapToPackageFragments.remove(key);	
			postRemove(frag);	
		}
	}
	

	private void postRefresh(final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					fViewer.refresh(element);
				}
			}
		});
	}
	
	private void addElement(IPackageFragment frag) {
		String key= getKey(frag);
		LogicalPackage lp= (LogicalPackage)fMapToLogicalPackage.get(key);
	
		if(lp != null && lp.belongs(frag)){
			lp.add(frag);
			return;	
		} 
		
		IPackageFragment fragment= (IPackageFragment)fMapToPackageFragments.get(key);
		if(fragment != null){
			//must create a new CompoundElement
			if(!fragment.equals(frag)){
				lp= new LogicalPackage(fragment);
				lp.add(frag);
				fMapToLogicalPackage.put(key, lp);

				//@Improve: should I replace this with a refresh?
				postRemove(fragment);
				postAdd(lp); 
				
				return;
			}
		} 
		
		else {
			fMapToPackageFragments.put(key, frag);
			postAdd(frag);
		}
	}
}
