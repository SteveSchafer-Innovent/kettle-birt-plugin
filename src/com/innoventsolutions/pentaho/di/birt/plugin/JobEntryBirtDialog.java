/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2013 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.innoventsolutions.pentaho.di.birt.plugin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.entry.JobEntryDialogInterface;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.ui.core.gui.WindowProperty;
import org.pentaho.di.ui.job.dialog.JobDialog;
import org.pentaho.di.ui.job.entry.JobEntryDialog;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

/**
 * This class is part of the demo job entry plug-in implementation. It
 * demonstrates the basics of developing a plug-in job entry for PDI.
 *
 * The demo job entry is configurable to yield a positive or negative result.
 * The job logic will follow the respective path during execution.
 *
 * This class is the implementation of JobEntryDialogInterface. Classes
 * implementing this interface need to:
 *
 * - build and open a SWT dialog displaying the job entry's settings (stored in
 * the entry's meta object) - write back any changes the user makes to the job
 * entry's meta object - report whether the user changed any settings when
 * confirming the dialog
 *
 */
public class JobEntryBirtDialog extends JobEntryDialog implements
		JobEntryDialogInterface {
	/**
	 * The PKG member is used when looking up internationalized strings. The
	 * properties file with localized keys is expected to reside in {the package
	 * of the class specified}/messages/messages_{locale}.properties
	 */
	private static Class<?> PKG = JobEntryBirt.class; // for i18n purposes
	// the text box for the job entry name
	private Text wName;
	private Text wSource;
	private Text wTargetFile;
	// the job entry configuration object
	private JobEntryBirt meta;
	// flag saving the changed status of the job entry configuration object
	private boolean changed;

	/**
	 * The constructor should call super() and make sure that the name of the
	 * job entry is set.
	 *
	 * @param parent
	 *            the SWT Shell to use
	 * @param jobEntryInt
	 *            the job entry settings object to use for the dialog
	 * @param rep
	 *            the repository currently connected to, if any
	 * @param jobMeta
	 *            the description of the job the job entry belongs to
	 */
	public JobEntryBirtDialog(final Shell parent,
			final JobEntryInterface jobEntryInt, final Repository rep,
			final JobMeta jobMeta) {
		super(parent, jobEntryInt, rep, jobMeta);
		// it is safe to cast the JobEntryInterface object to the object handled
		// by this dialog
		meta = (JobEntryBirt) jobEntryInt;
		// ensure there is a default name for new job entries
		if (this.meta.getName() == null) {
			this.meta.setName(BaseMessages.getString(PKG, "Birt.Default.Name"));
		}
	}

	/**
	 * This method is called by Spoon when the user opens the settings dialog of
	 * the job entry. It should open the dialog and return only once the dialog
	 * has been closed by the user.
	 *
	 * If the user confirms the dialog, the meta object (passed in the
	 * constructor) must be updated to reflect the new job entry settings. The
	 * changed flag of the meta object must reflect whether the job entry
	 * configuration was changed by the dialog.
	 *
	 * If the user cancels the dialog, the meta object must not be updated, and
	 * its changed flag must remain unaltered.
	 *
	 * The open() method must return the met object of the job entry after the
	 * user has confirmed the dialog, or null if the user cancelled the dialog.
	 */
	@Override
	public JobEntryInterface open() {
		// SWT code for setting up the dialog
		final Shell parent = getParent();
		final Display display = parent.getDisplay();
		shell = new Shell(parent, props.getJobsDialogStyle());
		props.setLook(shell);
		JobDialog.setShellImage(shell, meta);
		// save the job entry's changed flag
		changed = meta.hasChanged();
		// The ModifyListener used on all controls. It will update the meta
		// object to indicate that changes are being made.
		final ModifyListener lsMod = new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent arg0) {
				meta.setChanged();
			}
		};
		// ------------------------------------------------------- //
		// SWT code for building the actual settings dialog //
		// ------------------------------------------------------- //
		final FormLayout formLayout = new FormLayout();
		formLayout.marginWidth = Const.FORM_MARGIN;
		formLayout.marginHeight = Const.FORM_MARGIN;
		shell.setLayout(formLayout);
		shell.setText(BaseMessages.getString(PKG, "Birt.Shell.Title"));
		final int middle = props.getMiddlePct();
		final int margin = Const.MARGIN;
		{
			// Job entry name line
			final Label label = new Label(shell, SWT.RIGHT);
			label.setText(BaseMessages
					.getString(PKG, "Birt.JobEntryName.Label"));
			props.setLook(label);
			{
				final FormData formData = new FormData();
				formData.left = new FormAttachment(0, 0);
				formData.right = new FormAttachment(middle, 0);
				formData.top = new FormAttachment(0, margin);
				label.setLayoutData(formData);
			}
			final Text text = new Text(shell, SWT.SINGLE | SWT.LEFT
					| SWT.BORDER);
			props.setLook(text);
			text.addModifyListener(lsMod);
			{
				final FormData formData = new FormData();
				formData.left = new FormAttachment(middle, 0);
				formData.top = new FormAttachment(0, margin);
				formData.right = new FormAttachment(100, 0);
				text.setLayoutData(formData);
			}
			wName = text;
		}
		{
			// Source
			final Label label = new Label(shell, SWT.RIGHT);
			label.setText(BaseMessages.getString(PKG, "Birt.Source.Label"));
			props.setLook(label);
			{
				final FormData formData = new FormData();
				formData.left = new FormAttachment(0, 0);
				formData.right = new FormAttachment(middle, -margin);
				formData.top = new FormAttachment(wName, margin);
				label.setLayoutData(formData);
			}
			final Text text = new Text(shell, SWT.SINGLE | SWT.LEFT
					| SWT.BORDER);
			text.addModifyListener(lsMod);
			props.setLook(text);
			{
				final FormData formData = new FormData();
				formData.left = new FormAttachment(middle, 0);
				formData.top = new FormAttachment(wName, margin);
				formData.right = new FormAttachment(100, 0);
				// fdOutcome.bottom = new FormAttachment(100, -40);
				text.setLayoutData(formData);
			}
			wSource = text;
		}
		{
			// TargetFile
			final Label label = new Label(shell, SWT.RIGHT);
			label.setText(BaseMessages.getString(PKG, "Birt.TargetFile.Label"));
			props.setLook(label);
			{
				final FormData formData = new FormData();
				formData.left = new FormAttachment(0, 0);
				formData.right = new FormAttachment(middle, -margin);
				formData.top = new FormAttachment(wSource, margin);
				label.setLayoutData(formData);
			}
			final Text text = new Text(shell, SWT.SINGLE | SWT.LEFT
					| SWT.BORDER);
			text.addModifyListener(lsMod);
			props.setLook(text);
			{
				final FormData formData = new FormData();
				formData.left = new FormAttachment(middle, 0);
				formData.top = new FormAttachment(wSource, margin);
				formData.right = new FormAttachment(100, 0);
				// fdOutcome.bottom = new FormAttachment(100, -40);
				text.setLayoutData(formData);
			}
			wTargetFile = text;
		}
		final Button wOK = new Button(shell, SWT.PUSH);
		wOK.setText(BaseMessages.getString(PKG, "System.Button.OK"));
		final Button wCancel = new Button(shell, SWT.PUSH);
		wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
		// at the bottom
		BaseStepDialog.positionBottomButtons(shell,
				new Button[] { wOK, wCancel }, margin, null);
		// Add listeners
		final Listener lsCancel = new Listener() {
			@Override
			public void handleEvent(final Event arg0) {
				cancel();
			}
		};
		final Listener lsOK = new Listener() {
			@Override
			public void handleEvent(final Event arg0) {
				ok();
			}
		};
		wCancel.addListener(SWT.Selection, lsCancel);
		wOK.addListener(SWT.Selection, lsOK);
		/*
		 * // default listener (for hitting "enter") final SelectionAdapter
		 * lsDef = new SelectionAdapter() {
		 *
		 * @Override public void widgetDefaultSelected(final SelectionEvent e) {
		 * ok(); } }; wName.addSelectionListener(lsDef);
		 */
		// Detect X or ALT-F4 or something that kills this window and cancel the
		// dialog properly
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(final ShellEvent e) {
				cancel();
			}
		});
		// populate the dialog with the values from the meta object
		populateDialog();
		// restore the changed flag to original value, as the modify listeners
		// fire during dialog population
		meta.setChanged(changed);
		// restore dialog size and placement, or set default size if none saved
		// yet
		BaseStepDialog.setSize(shell, 250, 140, false);
		// open dialog and enter event loop
		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		// at this point the dialog has closed, so either ok() or cancel() have
		// been executed
		return meta;
	}

	/**
	 * This helper method is called once the dialog is closed. It saves the
	 * placement of the dialog, so it can be restored when it is opened another
	 * time.
	 */
	private void dispose() {
		// save dialog window placement to use when reopened
		final WindowProperty winprop = new WindowProperty(shell);
		props.setScreen(winprop);
		// close dialog window
		shell.dispose();
	}

	/**
	 * Copy information from the meta-data input to the dialog fields.
	 */
	public void populateDialog() {
		// setting the name of the job entry
		final String name = meta.getName();
		if (name != null) {
			wName.setText(name);
		}
		wName.selectAll();
		final String source = meta.getSource();
		if (source != null) {
			wSource.setText(source);
		}
		final String targetFile = meta.getTargetFile();
		if (targetFile != null) {
			wTargetFile.setText(targetFile);
		}
	}

	/**
	 * This method is called once the dialog has been canceled.
	 */
	private void cancel() {
		// restore changed flag on the meta object, any changes done by the
		// modify listener
		// are being revoked here
		meta.setChanged(changed);
		// this variable will be returned by the open() method, setting it to
		// null, as open() needs
		// to return null when the dialog is cancelled
		meta = null;
		// close dialog window and clean up
		dispose();
	}

	/**
	 * This method is called once the dialog is confirmed. It may only close the
	 * window if the job entry has a non-empty name.
	 */
	private void ok() {
		// make sure the job entry name is set properly, return with an error
		// message if that is not the case
		if (Const.isEmpty(wName.getText())) {
			final MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
			mb.setText(BaseMessages.getString(PKG,
					"System.StepJobEntryNameMissing.Title"));
			mb.setMessage(BaseMessages.getString(PKG,
					"System.JobEntryNameMissing.Msg"));
			mb.open();
			return;
		}
		// update the meta object with the entered dialog settings
		meta.setName(wName.getText());
		meta.setSource(wSource.getText());
		meta.setTargetFile(wTargetFile.getText());
		// close dialog window and clean up
		dispose();
	}
}
