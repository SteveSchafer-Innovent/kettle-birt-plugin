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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.core.data.DataTypeUtil;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.core.script.ParameterAttribute;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.EngineConstants;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportEngineFactory;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.impl.ParameterSelectionChoice;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;
import org.pentaho.di.cluster.SlaveServer;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.RowMetaAndData;
import org.pentaho.di.core.annotations.JobEntry;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.entry.JobEntryBase;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

/**
 * This class is part of the demo job entry plug-in implementation. It
 * demonstrates the basics of developing a plug-in job entry for PDI.
 *
 * The demo job entry is configurable to yield a positive or negative result.
 * The job logic will follow the respective path during execution.
 *
 * This class is the implementation of JobEntryInterface. Classes implementing
 * this interface need to:
 *
 * - keep track of the job entry settings - serialize job entry settings both to
 * XML and a repository - indicate to PDI which class implements the dialog for
 * this job entry - indicate to PDI whether the job entry supports
 * unconditional, true or false, or both types of results - execute the job
 * entry logic and provide a job entry result
 *
 */
@JobEntry(id = "BirtJobEntry", image = "com/innoventsolutions/pentaho/di/birt/plugin/resources/demo.svg", i18nPackageName = "com.innoventsolutions.pentaho.di.birt.plugin", name = "BirtJobEntry.Name", description = "BirtJobEntry.TooltipDesc", categoryDescription = "i18n:org.pentaho.di.job:JobCategory.Category.Utility")
public class JobEntryBirt extends JobEntryBase implements Cloneable,
		JobEntryInterface {
	/**
	 * The PKG member is used when looking up internationalized strings. The
	 * properties file with localized keys is expected to reside in {the package
	 * of the class specified}/messages/messages_{locale}.properties
	 */
	private static Class<?> PKG = JobEntryBirt.class; // for i18n purposes
	// $NON-NLS-1$
	private static IReportEngine engine;
	private static final List<String[]> kettleFieldNames = new ArrayList<String[]>();
	private static final List<Object[]> kettleRows = new ArrayList<Object[]>();
	static protected Logger logger = Logger.getLogger(JobEntryBirt.class
			.getName());

	private static enum Mode {
		RUN, RENDER, RUN_AND_RENDER
	}

	private String source = null;
	private String resourcePath = null;
	private String tempDir = null;
	private String logDir = null;
	private String logFile = null;
	private String scripts = null;
	private Mode mode = Mode.RUN_AND_RENDER;
	private final Map<String, String> params = new HashMap<String, String>();
	private String format = "html";
	private String targetFile = null;
	private String htmlType = "HTML";
	private String encoding = "utf-8";
	private String locale = "en";

	/**
	 * The JobEntry constructor executes super() and initializes its fields with
	 * sensible defaults for new instances of the job entry.
	 *
	 * @param name
	 *            the name of the new job entry
	 */
	public JobEntryBirt(final String name) {
		super(name, "");
	}

	/**
	 * No-Arguments constructor for convenience purposes.
	 */
	public JobEntryBirt() {
		this("");
	}

	/**
	 * Let PDI know the class name to use for the dialog.
	 *
	 * @return the class name to use for the dialog for this job entry
	 */
	@Override
	public String getDialogClassName() {
		return this.getClass().getCanonicalName() + "Dialog";
	}

	/**
	 * This method is used when a job entry is duplicated in Spoon. It needs to
	 * return a deep copy of this job entry object. Be sure to create proper
	 * deep copies if the job entry configuration is stored in modifiable
	 * objects.
	 *
	 * See org.pentaho.di.trans.steps.rowgenerator.RowGeneratorMeta.clone() for
	 * an example on creating a deep copy of an object.
	 *
	 * @return a deep copy of this
	 */
	@Override
	public Object clone() {
		final JobEntryBirt je = (JobEntryBirt) super.clone();
		return je;
	}

	/**
	 * This method is called by Spoon when a job entry needs to serialize its
	 * configuration to XML. The expected return value is an XML fragment
	 * consisting of one or more XML tags.
	 *
	 * Please use org.pentaho.di.core.xml.XMLHandler to conveniently generate
	 * the XML.
	 *
	 * Note: the returned string must include the output of super.getXML() as
	 * well
	 *
	 * @return a string containing the XML serialization of this job entry
	 */
	@Override
	public String getXML() {
		final StringBuilder sb = new StringBuilder();
		sb.append(super.getXML());
		sb.append("      ").append(XMLHandler.addTagValue("source", source));
		sb.append("      ").append(
				XMLHandler.addTagValue("targetFile", targetFile));
		return sb.toString();
	}

	/**
	 * This method is called by PDI when a job entry needs to load its
	 * configuration from XML.
	 *
	 * Please use org.pentaho.di.core.xml.XMLHandler to conveniently read from
	 * the XML node passed in.
	 *
	 * Note: the implementation must call super.loadXML() to ensure correct
	 * behavior
	 *
	 * @param stepnode
	 *            the XML node containing the configuration
	 * @param databases
	 *            the databases available in the job
	 * @param slaveServers
	 *            the slave servers available in the job
	 * @param rep
	 *            the repository connected to, if any
	 * @param metaStore
	 *            the metastore to optionally read from
	 */
	@Override
	public void loadXML(final Node entrynode,
			final List<DatabaseMeta> databases,
			final List<SlaveServer> slaveServers, final Repository rep,
			final IMetaStore metaStore) throws KettleXMLException {
		try {
			super.loadXML(entrynode, databases, slaveServers);
			source = XMLHandler.getTagValue(entrynode, "source");
			targetFile = XMLHandler.getTagValue(entrynode, "targetFile");
		}
		catch (final Exception e) {
			throw new KettleXMLException(BaseMessages.getString(PKG,
					"Birt.Error.UnableToLoadFromXML"), e);
		}
	}

	/**
	 * This method is called by Spoon when a job entry needs to serialize its
	 * configuration to a repository. The repository implementation provides the
	 * necessary methods to save the job entry attributes.
	 *
	 * @param rep
	 *            the repository to save to
	 * @param id_job
	 *            the id to use for the job when saving
	 * @param metaStore
	 *            the metastore to optionally write to
	 */
	@Override
	public void saveRep(final Repository rep, final IMetaStore metaStore,
			final ObjectId id_job) throws KettleException {
		try {
			rep.saveJobEntryAttribute(id_job, getObjectId(), "source", source);
			rep.saveJobEntryAttribute(id_job, getObjectId(), "targetFile",
					targetFile);
		}
		catch (final KettleDatabaseException dbe) {
			throw new KettleException(BaseMessages.getString(PKG,
					"Demo.Error.UnableToSaveToRepository") + id_job, dbe);
		}
	}

	/**
	 * This method is called by PDI when a job entry needs to read its
	 * configuration from a repository. The repository implementation provides
	 * the necessary methods to read the job entry attributes.
	 *
	 * @param rep
	 *            the repository to read from
	 * @param metaStore
	 *            the metastore to optionally read from
	 * @param id_jobentry
	 *            the id of the job entry being read
	 * @param databases
	 *            the databases available in the job
	 * @param slaveServers
	 *            the slave servers available in the job
	 */
	@Override
	public void loadRep(final Repository rep, final IMetaStore metaStore,
			final ObjectId id_jobentry, final List<DatabaseMeta> databases,
			final List<SlaveServer> slaveServers) throws KettleException {
		try {
			source = rep.getJobEntryAttributeString(id_jobentry, "source");
			targetFile = rep.getJobEntryAttributeString(id_jobentry,
					"targetFile");
		}
		catch (final KettleDatabaseException dbe) {
			throw new KettleException(BaseMessages.getString(PKG,
					"Demo.Error.UnableToLoadFromRepository") + id_jobentry, dbe);
		}
	}

	/**
	 * This method is called when it is the job entry's turn to run during the
	 * execution of a job. It should return the passed in Result object, which
	 * has been updated to reflect the outcome of the job entry. The execute()
	 * method should call setResult(), setNrErrors() and modify the rows or
	 * files attached to the result object if required.
	 *
	 * @param result
	 *            The result of the previous execution
	 * @return The Result of the execution.
	 */
	@Override
	public Result execute(final Result result, final int nr) {
		// indicate there are no errors
		final List<RowMetaAndData> rows = result.getRows();
		kettleFieldNames.clear();
		kettleRows.clear();
		for (final RowMetaAndData row : rows) {
			System.out.println("birt row: ");
			final RowMetaInterface rmi = row.getRowMeta();
			final String[] fieldNames = rmi.getFieldNames();
			kettleFieldNames.add(fieldNames);
			final Object[] data = row.getData();
			kettleRows.add(data);
			for (int i = 0; i < fieldNames.length; i++) {
				System.out.print(fieldNames[i]);
				System.out.print(" = ");
				System.out.println(data[i]);
			}
			System.out.println();
		}
		int runResult = -1;
		try {
			if (engine == null) {
				System.out.println("Creating engine");
				final EngineConfig config = createEngineConfig();
				Platform.startup(config);
				final IReportEngineFactory factory = (IReportEngineFactory) Platform
						.createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY);
				engine = factory.createReportEngine(config);
				// JRE default level is INFO, which may reveal too much internal
				// logging information.
				engine.changeLogLevel(Level.WARNING);
			}
			switch (mode) {
			case RUN_AND_RENDER:
				runResult = runAndRenderReport();
				break;
			case RUN:
				runResult = runReport();
				break;
			case RENDER:
				runResult = renderReport();
				break;
			}
		}
		catch (final Exception ex) {
			logger.log(Level.SEVERE, "exception in parsing the parameters", ex);
			this.logError("exception in parsing the parameters", ex);
			result.setNrErrors(1);
			result.setResult(false);
			return result;
		}
		finally {
			Platform.shutdown();
		}
		result.setNrErrors(runResult == 0 ? 0 : 1);
		result.setResult(runResult == 0);
		return result;
	}

	private int renderReport() {
		// TODO Auto-generated method stub
		return -1;
	}

	private int runReport() {
		// TODO Auto-generated method stub
		return -1;
	}

	private int runAndRenderReport() {
		System.out
				.println("runAndRenderReport " + source + " -> " + targetFile);
		try {
			final IReportRunnable runnable = engine.openReportDesign(source);
			final Map<String, ParameterAttribute> inputValues = evaluateParameterValues(runnable);
			final IRunAndRenderTask task = engine
					.createRunAndRenderTask(runnable);
			for (final String paraName : inputValues.keySet()) {
				final ParameterAttribute pa = inputValues.get(paraName);
				final Object valueObject = pa.getValue();
				if (valueObject instanceof Object[]) {
					final Object[] valueArray = (Object[]) valueObject;
					final String[] displayTextArray = (String[]) pa
							.getDisplayText();
					task.setParameter(paraName, valueArray, displayTextArray);
				}
				else {
					task.setParameter(paraName, pa.getValue(),
							(String) pa.getDisplayText());
				}
			}
			final IRenderOption options = new RenderOption();
			options.setOutputFormat(format);
			options.setOutputFileName(targetFile);
			if (format.equalsIgnoreCase("html")) {
				final HTMLRenderOption htmlOptions = new HTMLRenderOption(
						options);
				if ("ReportletNoCSS".equals(htmlType)) {
					htmlOptions.setEmbeddable(true);
				}
				// setup the output encoding
				htmlOptions.setUrlEncoding(encoding);
				htmlOptions.setHtmlPagination(true);
				htmlOptions.setImageDirectory("image"); //$NON-NLS-1$
			}
			task.setRenderOption(options);
			task.setLocale(getLocale(locale));
			System.out.println("running...");
			task.run();
			System.out.println("finished");
			return 0;
		}
		catch (final EngineException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
			this.logError(e.getMessage(), e);
			return -1;
		}
	}

	private Locale getLocale(final String locale) {
		final int index = locale.indexOf('_');
		if (index != -1) {
			// e.g, zh_CN (language_country)
			final String language = locale.substring(0, index);
			final String country = locale.substring(index + 1);
			return new Locale(language, country);
		}
		// e.g, en (language)
		return new Locale(locale);
	}

	private Map<String, ParameterAttribute> evaluateParameterValues(
			final IReportRunnable runnable) {
		final Map<String, ParameterAttribute> inputValues = new HashMap<String, ParameterAttribute>();
		final IGetParameterDefinitionTask task = engine
				.createGetParameterDefinitionTask(runnable);
		@SuppressWarnings("unchecked")
		final Collection<IParameterDefnBase> paramDefns = task
				.getParameterDefns(false);
		for (final IParameterDefnBase pBase : paramDefns) {
			// now only support scalar parameter
			if (!(pBase instanceof IScalarParameterDefn)) {
				continue;
			}
			final IScalarParameterDefn paramDefn = (IScalarParameterDefn) pBase;
			final String paramName = paramDefn.getName();
			final String inputValue = params.get(paramName);
			final int paramDataType = paramDefn.getDataType();
			final String paramType = paramDefn.getScalarParameterType();
			// if allow multiple values
			boolean isAllowMutipleValues = false;
			try {
				Object paramValue = null;
				if (DesignChoiceConstants.SCALAR_PARAM_TYPE_MULTI_VALUE
						.equals(paramType)) {
					paramValue = stringToObjectArray(paramDataType, inputValue);
					isAllowMutipleValues = true;
				}
				else {
					paramValue = stringToObject(paramDataType, inputValue);
				}
				if (paramValue != null) {
					@SuppressWarnings({ "unchecked", "deprecation" })
					final List<ParameterSelectionChoice> selectList = paramDefn
							.getSelectionList();
					ParameterAttribute pa = null;
					if (isAllowMutipleValues) {
						final Object[] values = (Object[]) paramValue;
						final List<String> displayTextList = new ArrayList<String>();
						if (selectList != null && selectList.size() > 0) {
							for (final Object o : values) {
								for (final ParameterSelectionChoice select : selectList) {
									if (o.equals(select.getValue())) {
										displayTextList.add(select.getLabel());
									}
								}
							}
						}
						final String[] displayTexts = new String[displayTextList
								.size()];
						pa = new ParameterAttribute(values,
								displayTextList.toArray(displayTexts));
					}
					else {
						String displayText = null;
						if (selectList != null && selectList.size() > 0) {
							for (final ParameterSelectionChoice select : selectList) {
								if (paramValue.equals(select.getValue())) {
									displayText = select.getLabel();
									break;
								}
							}
						}
						pa = new ParameterAttribute(paramValue, displayText);
					}
					inputValues.put(paramName, pa);
				}
			}
			catch (final BirtException ex) {
				logger.log(Level.SEVERE, "the value of parameter " + paramName
						+ " is invalid", ex);
				this.logError("the value of parameter " + paramName
						+ " is invalid", ex);
			}
		}
		return inputValues;
	}

	private Object[] stringToObjectArray(final int paramDataType,
			final String inputValue) throws BirtException {
		if (inputValue == null)
			return null;
		final List<Object> result = new LinkedList<Object>();
		final String[] inputValues = inputValue.split(",[ ]*");
		for (final String value : inputValues) {
			result.add(stringToObject(paramDataType, value));
		}
		return result.toArray();
	}

	protected Object stringToObject(final int type, final String value)
			throws BirtException {
		if (value == null)
			return null;
		switch (type) {
		case IScalarParameterDefn.TYPE_BOOLEAN:
			return DataTypeUtil.toBoolean(value);
		case IScalarParameterDefn.TYPE_DATE:
			return DataTypeUtil.toSqlDate(value);
		case IScalarParameterDefn.TYPE_TIME:
			return DataTypeUtil.toSqlTime(value);
		case IScalarParameterDefn.TYPE_DATE_TIME:
			return DataTypeUtil.toDate(value);
		case IScalarParameterDefn.TYPE_DECIMAL:
			return DataTypeUtil.toBigDecimal(value);
		case IScalarParameterDefn.TYPE_FLOAT:
			return DataTypeUtil.toDouble(value);
		case IScalarParameterDefn.TYPE_STRING:
			return DataTypeUtil.toString(value);
		case IScalarParameterDefn.TYPE_INTEGER:
			return DataTypeUtil.toInteger(value);
		}
		return null;
	}

	private EngineConfig createEngineConfig() {
		final EngineConfig config = new EngineConfig();
		if (resourcePath != null) {
			config.setResourcePath(resourcePath.trim());
		}
		if (tempDir != null) {
			config.setTempDir(tempDir.trim());
		}
		Level level = null;
		final LogLevel logLevel = this.getLogLevel();
		switch (logLevel) {
		case NOTHING:
			level = Level.OFF;
			break;
		case ERROR:
			level = Level.SEVERE;
			break;
		case MINIMAL:
			level = Level.SEVERE;
			break;
		case BASIC:
			level = Level.INFO;
			break;
		case DETAILED:
			level = Level.FINE;
			break;
		case DEBUG:
			level = Level.FINEST;
			break;
		case ROWLEVEL:
			level = Level.FINER;
			break;
		default:
			level = Level.WARNING;
		}
		final String logD = logDir == null ? config.getLogDirectory() : logDir;
		final Level logL = level == null ? config.getLogLevel() : level;
		config.setLogConfig(logD, logL);
		if (logFile != null) {
			config.setLogFile(logFile.trim());
		}
		final HashMap<Object, Object> appContext = new HashMap<Object, Object>();
		appContext.put(EngineConstants.PROJECT_CLASSPATH_KEY, scripts);
		appContext.put("kettleRows", kettleRows);
		appContext.put("kettleFieldNames", kettleFieldNames);
		config.setAppContext(appContext);
		return config;
	}

	/**
	 * Returns true if the job entry offers a genuine true/false result upon
	 * execution, and thus supports separate "On TRUE" and "On FALSE" outgoing
	 * hops.
	 */
	@Override
	public boolean evaluates() {
		return true;
	}

	/**
	 * Returns true if the job entry supports unconditional outgoing hops.
	 */
	@Override
	public boolean isUnconditional() {
		return false;
	}

	public String getSource() {
		return source;
	}

	public void setSource(final String source) {
		this.source = source;
	}

	public String getResourcePath() {
		return resourcePath;
	}

	public void setResourcePath(final String resourcePath) {
		this.resourcePath = resourcePath;
	}

	public String getTempDir() {
		return tempDir;
	}

	public void setTempDir(final String tempDir) {
		this.tempDir = tempDir;
	}

	public String getLogDir() {
		return logDir;
	}

	public void setLogDir(final String logDir) {
		this.logDir = logDir;
	}

	public String getLogFile() {
		return logFile;
	}

	public void setLogFile(final String logFile) {
		this.logFile = logFile;
	}

	public String getScripts() {
		return scripts;
	}

	public void setScripts(final String scripts) {
		this.scripts = scripts;
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(final Mode mode) {
		this.mode = mode;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(final String format) {
		this.format = format;
	}

	public String getTargetFile() {
		return targetFile;
	}

	public void setTargetFile(final String targetFile) {
		this.targetFile = targetFile;
	}

	public String getHtmlType() {
		return htmlType;
	}

	public void setHtmlType(final String htmlType) {
		this.htmlType = htmlType;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(final String encoding) {
		this.encoding = encoding;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(final String locale) {
		this.locale = locale;
	}

	public Map<String, String> getParams() {
		return params;
	}
}
