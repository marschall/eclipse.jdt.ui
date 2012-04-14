/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Philippe Marschall - package documentation support (Bug-86168)
 *     Michael Pellaton - package documentation support (Bug-86168)
 *******************************************************************************/
package org.eclipse.jdt.ui.wizards;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.StringTokenizer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateException;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContext;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;

/**
 * Wizard page to create a new package.
 *
 * <p>
 * Note: This class is not intended to be subclassed, but clients can instantiate.
 * To implement a different kind of a new package wizard page, extend <code>NewContainerWizardPage</code>.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class NewPackageWizardPage extends NewContainerWizardPage {

	private static final String PACKAGE_INFO_JAVA_FILENAME= "package-info.java"; //$NON-NLS-1$

	private static final String PACKAGE_HTML_FILENAME= "package.html"; //$NON-NLS-1$

	private static final String PAGE_NAME= "NewPackageWizardPage"; //$NON-NLS-1$

	private static final String PACKAGE= "NewPackageWizardPage.package"; //$NON-NLS-1$

	private final static String SETTINGS_CREATEPACKAGEDOCUMENTATION= "create_package_documentation"; //$NON-NLS-1$

	private StringDialogField fPackageDialogField;

	private SelectionButtonDialogField fCreatePackageDocumentationDialogField;

	/*
	 * Status of last validation of the package field
	 */
	private IStatus fPackageStatus;

	private IPackageFragment fCreatedPackageFragment;

	/**
	 * Creates a new <code>NewPackageWizardPage</code>
	 */
	public NewPackageWizardPage() {
		super(PAGE_NAME);

		setTitle(NewWizardMessages.NewPackageWizardPage_title);
		setDescription(NewWizardMessages.NewPackageWizardPage_description);

		fCreatedPackageFragment= null;

		PackageFieldAdapter adapter= new PackageFieldAdapter();

		fPackageDialogField= new StringDialogField();
		fPackageDialogField.setDialogFieldListener(adapter);
		fPackageDialogField.setLabelText(NewWizardMessages.NewPackageWizardPage_package_label);

		fCreatePackageDocumentationDialogField= new SelectionButtonDialogField(SWT.CHECK);
		fCreatePackageDocumentationDialogField.setDialogFieldListener(adapter);
		fCreatePackageDocumentationDialogField.setLabelText(NewWizardMessages.NewPackageWizardPage_package_CreatePackageDocumentation);

		fPackageStatus= new StatusInfo();
	}

	// -------- Initialization ---------

	/**
	 * The wizard owning this page is responsible for calling this method with the
	 * current selection. The selection is used to initialize the fields of the wizard
	 * page.
	 *
	 * @param selection used to initialize the fields
	 */
	public void init(IStructuredSelection selection) {
		IJavaElement jelem= getInitialJavaElement(selection);

		initContainerPage(jelem);
		String pName= ""; //$NON-NLS-1$
		if (jelem != null) {
			IPackageFragment pf= (IPackageFragment) jelem.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
			if (pf != null && !pf.isDefaultPackage())
				pName= pf.getElementName();
		}
		setPackageText(pName, true);

		IDialogSettings dialogSettings= getDialogSettings();
		if (dialogSettings != null) {
			IDialogSettings section= dialogSettings.getSection(PAGE_NAME);
			if (section != null) {
				boolean createPackageDocumentation= section.getBoolean(SETTINGS_CREATEPACKAGEDOCUMENTATION);
				fCreatePackageDocumentationDialogField.setSelection(createPackageDocumentation);
			}
		}

		updateStatus(new IStatus[] { fContainerStatus, fPackageStatus });
	}

	// -------- UI Creation ---------

	/*
	 * @see WizardPage#createControl
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());
		int nColumns= 3;

		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		composite.setLayout(layout);

		Label label= new Label(composite, SWT.WRAP);
		label.setText(NewWizardMessages.NewPackageWizardPage_info);
		GridData gd= new GridData();
		gd.widthHint= convertWidthInCharsToPixels(60);
		gd.horizontalSpan= 3;
		label.setLayoutData(gd);

		createContainerControls(composite, nColumns);
		createPackageControls(composite, nColumns);

		setControl(composite);
		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.NEW_PACKAGE_WIZARD_PAGE);
	}

	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			setFocus();
		}
	}

	/**
	 * Sets the focus to the package name input field.
	 */
	protected void setFocus() {
		fPackageDialogField.setFocus();
	}


	private void createPackageControls(Composite composite, int nColumns) {
		fPackageDialogField.doFillIntoGrid(composite, nColumns - 1);
		fCreatePackageDocumentationDialogField.doFillIntoGrid(composite, 3);
		Text text= fPackageDialogField.getTextControl(null);
		LayoutUtil.setWidthHint(text, getMaxFieldWidth());
		LayoutUtil.setHorizontalGrabbing(text);
		DialogField.createEmptySpace(composite);

		TextFieldNavigationHandler.install(text);
	}

	// -------- PackageFieldAdapter --------

	private class PackageFieldAdapter implements IDialogFieldListener {

		// --------- IDialogFieldListener

		public void dialogFieldChanged(DialogField field) {
			fPackageStatus= packageChanged();
			// tell all others
			handleFieldChanged(PACKAGE);
		}
	}

	// -------- update message ----------------

	/*
	 * @see org.eclipse.jdt.ui.wizards.NewContainerWizardPage#handleFieldChanged(String)
	 */
	@Override
	protected void handleFieldChanged(String fieldName) {
		super.handleFieldChanged(fieldName);
		if (fieldName == CONTAINER) {
			fPackageStatus= packageChanged();
		}
		// do status line update
		updateStatus(new IStatus[] { fContainerStatus, fPackageStatus });
	}

	// ----------- validation ----------

	private IStatus validatePackageName(String text) {
		IJavaProject project= getJavaProject();
		if (project == null || !project.exists()) {
			return JavaConventions.validatePackageName(text, JavaCore.VERSION_1_3, JavaCore.VERSION_1_3);
		}
		return JavaConventionsUtil.validatePackageName(text, project);
	}

	/*
	 * Verifies the input for the package field.
	 */
	private IStatus packageChanged() {
		StatusInfo status= new StatusInfo();
		String packName= getPackageText();
		if (packName.length() > 0) {
			IStatus val= validatePackageName(packName);
			if (val.getSeverity() == IStatus.ERROR) {
				status.setError(Messages.format(NewWizardMessages.NewPackageWizardPage_error_InvalidPackageName, val.getMessage()));
				return status;
			} else if (val.getSeverity() == IStatus.WARNING) {
				status.setWarning(Messages.format(NewWizardMessages.NewPackageWizardPage_warning_DiscouragedPackageName, val.getMessage()));
			}
		} else {
			status.setError(NewWizardMessages.NewPackageWizardPage_error_EnterName);
			return status;
		}

		IPackageFragmentRoot root= getPackageFragmentRoot();
		if (root != null && root.getJavaProject().exists()) {
			IPackageFragment pack= root.getPackageFragment(packName);
			try {
				IPath rootPath= root.getPath();
				IPath outputPath= root.getJavaProject().getOutputLocation();
				if (rootPath.isPrefixOf(outputPath) && !rootPath.equals(outputPath)) {
					// if the bin folder is inside of our root, don't allow to name a package
					// like the bin folder
					IPath packagePath= pack.getPath();
					if (outputPath.isPrefixOf(packagePath)) {
						status.setError(NewWizardMessages.NewPackageWizardPage_error_IsOutputFolder);
						return status;
					}
				}
				if (pack.exists()) {
					if (pack.containsJavaResources() || !pack.hasSubpackages()) {
						status.setError(NewWizardMessages.NewPackageWizardPage_error_PackageExists);
					} else {
						status.setError(NewWizardMessages.NewPackageWizardPage_error_PackageNotShown);
					}
				} else {
					IResource resource= pack.getResource();
					if (resource != null && !ResourcesPlugin.getWorkspace().validateFiltered(resource).isOK()) {
						status.setError(NewWizardMessages.NewPackageWizardPage_error_PackageNameFiltered);
						return status;
					}
					URI location= pack.getResource().getLocationURI();
					if (location != null) {
						IFileStore store= EFS.getStore(location);
						if (store.fetchInfo().exists()) {
							status.setError(NewWizardMessages.NewPackageWizardPage_error_PackageExistsDifferentCase);
						}
					}
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
		return status;
	}

	/**
	 * Returns the content of the package input field.
	 *
	 * @return the content of the package input field
	 */
	public String getPackageText() {
		return fPackageDialogField.getText();
	}

	/**
	 * Returns the content of the create package documentation input field.
	 *
	 * @return the content of the create package documentation input field
	 * @since 3.8
	 */
	public boolean isCreatePackageDocumentation() {
		return fCreatePackageDocumentationDialogField.isSelected();
	}

	/**
	 * Sets the content of the package input field to the given value.
	 *
	 * @param str the new package input field text
	 * @param canBeModified if <code>true</code> the package input
	 * field can be modified; otherwise it is read-only.
	 */
	public void setPackageText(String str, boolean canBeModified) {
		fPackageDialogField.setText(str);

		fPackageDialogField.setEnabled(canBeModified);
	}

	/**
	 * Returns the resource handle that corresponds to the element to was created or
	 * will be created.
	 * @return A resource or null if the page contains illegal values.
	 * @since 3.0
	 */
	public IResource getModifiedResource() {
		IPackageFragmentRoot root= getPackageFragmentRoot();
		if (root != null) {
			return root.getPackageFragment(getPackageText()).getResource();
		}
		return null;
	}


	// ---- creation ----------------

	/**
	 * Returns a runnable that creates a package using the current settings.
	 *
	 * @return the runnable that creates the new package
	 */
	public IRunnableWithProgress getRunnable() {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				try {
					createPackage(monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
	}

	/**
	 * Returns the created package fragment. This method only returns a valid value
	 * after <code>getRunnable</code> or <code>createPackage</code> have been
	 * executed.
	 *
	 * @return the created package fragment
	 */
	public IPackageFragment getNewPackageFragment() {
		return fCreatedPackageFragment;
	}

	/**
	 * Creates the new package using the entered field values.
	 *
	 * @param monitor a progress monitor to report progress. The progress
	 * monitor must not be <code>null</code>
	 * @throws CoreException Thrown if creating the package failed.
	 * @throws InterruptedException Thrown when the operation has been canceled.
	 * @since 2.1
	 */
	public void createPackage(IProgressMonitor monitor) throws CoreException, InterruptedException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}

		IPackageFragmentRoot root= getPackageFragmentRoot();
		String packName= getPackageText();
		fCreatedPackageFragment= root.createPackageFragment(packName, true, monitor);

		if (isCreatePackageDocumentation()) {
			if (JavaModelUtil.is50OrHigher(getJavaProject())) {
				createPackageInfoJava(root, monitor);
			} else {
				createPackageHtml(root, monitor);
			}
		}

		// save whether package documentation should be created
		IDialogSettings dialogSettings= getDialogSettings();
		if (dialogSettings != null) {
			IDialogSettings section= dialogSettings.getSection(PAGE_NAME);
			if (section == null) {
				section= dialogSettings.addNewSection(PAGE_NAME);
			}
			section.put(SETTINGS_CREATEPACKAGEDOCUMENTATION, isCreatePackageDocumentation());
		}

		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}
	}

	private void createPackageInfoJava(IPackageFragmentRoot root, IProgressMonitor monitor) throws CoreException {
		String lineDelimiter= StubUtility.getLineDelimiterUsed(root.getJavaProject());
		StringBuilder content = new StringBuilder();
		String fileComment= getFileComment(root, lineDelimiter);
		String typeComment= getTypeComment(root, lineDelimiter);
		
		if (fileComment != null) {
			content.append(fileComment);
			content.append(lineDelimiter);
		} 

		if (typeComment != null) {
			content.append(typeComment);
			content.append(lineDelimiter);
		} else if (fileComment != null) {
			// insert an empty file comment to avoid that the file comment becomes the type comment
			content.append("/**");  //$NON-NLS-1$
			content.append(lineDelimiter);
			content.append(" *"); //$NON-NLS-1$
			content.append(lineDelimiter);
			content.append(" */"); //$NON-NLS-1$
			content.append(lineDelimiter);
		}

		content.append("package "); //$NON-NLS-1$
		content.append(fCreatedPackageFragment.getElementName());
		content.append(";"); //$NON-NLS-1$

		ICompilationUnit compilationUnit= fCreatedPackageFragment.createCompilationUnit(PACKAGE_INFO_JAVA_FILENAME, content.toString(), true, monitor);

		JavaModelUtil.reconcile(compilationUnit);

		compilationUnit.becomeWorkingCopy(monitor);
		try {
			IBuffer buffer= compilationUnit.getBuffer();
			ISourceRange sourceRange= compilationUnit.getSourceRange();
			String originalContent= buffer.getText(sourceRange.getOffset(), sourceRange.getLength());

			String formattedContent= CodeFormatterUtil.format(CodeFormatter.K_COMPILATION_UNIT, originalContent, 0, lineDelimiter, root.getJavaProject());
			formattedContent= Strings.trimLeadingTabsAndSpaces(formattedContent);
			buffer.replace(sourceRange.getOffset(), sourceRange.getLength(), formattedContent);
			compilationUnit.commitWorkingCopy(true, new SubProgressMonitor(monitor, 1));
		} finally {
			compilationUnit.discardWorkingCopy();
		}
	}

	private String getFileComment(IPackageFragmentRoot root, String lineDelimiterUsed) throws CoreException {
		Template template= StubUtility.getCodeTemplate(CodeTemplateContextType.FILECOMMENT_ID, root.getJavaProject());
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), root.getJavaProject(), lineDelimiterUsed);
		context.setVariable(CodeTemplateContextType.PROJECTNAME, root.getJavaProject().getElementName());
		context.setVariable(CodeTemplateContextType.PACKAGENAME, fCreatedPackageFragment.getElementName());
		context.setVariable(CodeTemplateContextType.TYPENAME, PACKAGE_INFO_JAVA_FILENAME);
		context.setVariable(CodeTemplateContextType.FILENAME, PACKAGE_INFO_JAVA_FILENAME);
		return evaluateTemplate(context, template);
	}

	private String getTypeComment(IPackageFragmentRoot root, String lineDelimiterUsed) throws CoreException {
		Template template= StubUtility.getCodeTemplate(CodeTemplateContextType.TYPECOMMENT_ID, root.getJavaProject());
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), root.getJavaProject(), lineDelimiterUsed);
		context.setVariable(CodeTemplateContextType.PROJECTNAME, root.getJavaProject().getElementName());
		context.setVariable(CodeTemplateContextType.PACKAGENAME, fCreatedPackageFragment.getElementName());
		context.setVariable(CodeTemplateContextType.TYPENAME, PACKAGE_INFO_JAVA_FILENAME);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, root.getElementName());
		context.setVariable(CodeTemplateContextType.FILENAME, PACKAGE_INFO_JAVA_FILENAME);
		return evaluateTemplate(context, template);
	}

	private static String evaluateTemplate(CodeTemplateContext context, Template template) throws CoreException {
		TemplateBuffer buffer;
		try {
			buffer= context.evaluate(template);
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		if (buffer == null)
			return null;
		String str= buffer.getString();
		if (Strings.containsOnlyWhitespaces(str)) {
			return null;
		}
		return str;
	}

	private void createPackageHtml(IPackageFragmentRoot root, IProgressMonitor monitor) throws CoreException {

		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IFolder createdPackage= workspace.getRoot().getFolder(fCreatedPackageFragment.getPath());
		IFile packageHtml= createdPackage.getFile(PACKAGE_HTML_FILENAME);
		String charset= packageHtml.getCharset();
		String content= buildPackageHtmlContent(root, charset);
		try {
			packageHtml.create(new ByteArrayInputStream(content.getBytes(charset)), false, monitor);
		} catch (UnsupportedEncodingException e) {
			String message= "charset " + charset + " not supported by platform"; //$NON-NLS-1$ //$NON-NLS-2$
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, message, e));
		}
	}

	private String buildPackageHtmlContent(IPackageFragmentRoot root, String charset) throws CoreException {
		String lineDelimiter= StubUtility.getLineDelimiterUsed(root.getJavaProject());
		StringBuilder content = new StringBuilder();
		String fileComment= getFileComment(root, lineDelimiter);
		String typeComment= getTypeComment(root, lineDelimiter);

		if (fileComment != null) {
			content.append("<!--"); //$NON-NLS-1$
			content.append(lineDelimiter);
			content.append(stripJavaComments(fileComment, lineDelimiter));
			content.append(lineDelimiter);
			content.append("-->"); //$NON-NLS-1$
			content.append(lineDelimiter);
		}
		content.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("<html>"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("<head>"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=");  //$NON-NLS-1$
		content.append(charset);
		content.append("\">"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("<title>"); //$NON-NLS-1$
		content.append(fCreatedPackageFragment.getElementName());
		content.append("</title>"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("</head>"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("<body>"); //$NON-NLS-1$
		content.append(lineDelimiter);

		if (typeComment != null) {
			content.append(stripJavaComments(typeComment, lineDelimiter));
			content.append(lineDelimiter);
		}

		content.append("</body>"); //$NON-NLS-1$
		content.append(lineDelimiter);
		content.append("</html>"); //$NON-NLS-1$

		return content.toString();
	}

	private String stripJavaComments(String comment, String lineDelimiter) {
		StringBuilder content = new StringBuilder();
		StringTokenizer tokenizer= new StringTokenizer(comment, lineDelimiter);

		// preserve leading line delimiter
		if (comment.startsWith(lineDelimiter)) {
			content.append(lineDelimiter);
		}

		boolean first = true;
		while (tokenizer.hasMoreTokens()) {
			if (!first) {
				content.append(lineDelimiter);
			}
			String line = tokenizer.nextToken();
			content.append(stripComment(line));
			first = false;
		}

		// preserve trailing line delimiter
		if (comment.endsWith(lineDelimiter)) {
			content.append(lineDelimiter);
		}

		return content.toString();
	}

	private String stripComment(String line) {
		String candiate= line;

		// strip the first of the following encountered *, // or /*.*
		for (int i= 0; i < line.length(); i++) {
			char c= line.charAt(i);
			if (!Character.isWhitespace(c)) {
				if ('*' == c) {
					if (i < line.length() - 1) {
						candiate= line.substring(i + 1);
					} else {
						candiate= ""; //$NON-NLS-1$
					}

				} else if ('/' == c) {
					if (i + 1 <= line.length() - 1 && line.charAt(i + 1) == '/') {
						candiate= line.substring(i + 2);
					} else if (i + 1 <= line.length() - 1 && line.charAt(i + 1) == '*') {
						int end = i + 2;
						while (end < line.length() && '*' == line.charAt(end)) {
							end += 1;
						}
						candiate= line.substring(end);
					}
				}
				break;
			}
		}
		// strip trailing */
		for (int i= line.length() - 1; i > 0; i--) {
			char c= line.charAt(i);
			if (!Character.isWhitespace(c)) {
				if ('/' == c && line.charAt(i - 1) == '*') {
					int start = i - 2;
					while (start >= 0 && '*' == line.charAt(start)) {
						start -= 1;
					}
					candiate= candiate.substring(0, start);
				}
				break;
			}
		}

		return candiate;
	}

}
