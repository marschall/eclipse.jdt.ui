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
package org.eclipse.jdt.ui.tests.refactoring.reorg;

import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

import org.eclipse.jdt.ui.tests.reorg.MockReorgQueries;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;


public class AbstractMoveCompilationUnitPrefTest extends RepeatingRefactoringPerformanceTestCase {

	public AbstractMoveCompilationUnitPrefTest(String name) {
		super(name);
	}
	
	protected void doExecuteRefactoring(int numberOfCus, int numberOfRefs, boolean measure) throws Exception {
		ICompilationUnit cunit= generateSources(numberOfCus, numberOfRefs);
		JavaMoveProcessor processor= JavaMoveProcessor.create(
			new IResource[0], 
			new IJavaElement[] {cunit},
			JavaPreferencesSettings.getCodeGenerationSettings());
		IPackageFragment destination= fTestProject.getSourceFolder().createPackageFragment("destination", false, null); 
		processor.setDestination(destination);
		processor.setReorgQueries(new MockReorgQueries());
		processor.setUpdateReferences(true);
		executeRefactoring(new MoveRefactoring(processor), measure, RefactoringStatus.WARNING, false);
	}

	private ICompilationUnit generateSources(int numberOfCus, int numberOfRefs) throws Exception {
		IPackageFragment source= fTestProject.getSourceFolder().createPackageFragment("source", false, null); 
		StringBuffer buf= new StringBuffer();
		buf.append("package source;\n");
		buf.append("public class A {\n");
		buf.append("}\n");
		ICompilationUnit result= source.createCompilationUnit("A.java", buf.toString(), false, null);
	
		IPackageFragment references= fTestProject.getSourceFolder().createPackageFragment("ref", false, null);
		for(int i= 0; i < numberOfCus; i++) {
			createReferenceCu(references, i, numberOfRefs);
		}
		return result;
	}

	private static void createReferenceCu(IPackageFragment pack, int index, int numberOfRefs) throws Exception {
		StringBuffer buf= new StringBuffer();
		buf.append("package " + pack.getElementName() + ";\n");
		buf.append("public class Ref" + index + " {\n");
		for (int i= 0; i < numberOfRefs - 1; i++) {
			buf.append("    source.A field" + i +";\n");
		}
		buf.append("}\n");
		pack.createCompilationUnit("Ref" + index + ".java", buf.toString(), false, null);
	}
}
