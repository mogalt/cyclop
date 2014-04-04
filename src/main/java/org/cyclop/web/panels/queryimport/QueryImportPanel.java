package org.cyclop.web.panels.queryimport;

import java.io.ByteArrayInputStream;

import javax.inject.Inject;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptReferenceHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.util.lang.Bytes;
import org.cyclop.common.AppConfig;
import org.cyclop.service.importer.QueryImporter;
import org.cyclop.web.common.ImmutableListModel;
import org.cyclop.web.common.ImmutableListModel.ModelChangeListener;
import org.cyclop.web.components.pagination.BootstrapPagingNavigator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/** @author Maciej Miklas */
public class QueryImportPanel extends Panel {

    private final static Logger LOG = LoggerFactory.getLogger(QueryImportPanel.class);

    private final ImmutableListModel<ImportResult> resultModel;

    @Inject
    private QueryImporter impoter;

    private final AppConfig conf = AppConfig.get();

    private final WebMarkupContainer importResultContainer;

    private static final JavaScriptResourceReference JS_IMPORT = new JavaScriptResourceReference(
	    QueryImportPanel.class,
	    "queryImport.js");

    public QueryImportPanel(String id) {
	super(id);
	initFileUpload();
	importResultContainer = initImportResultContainer();
	resultModel = initResultTable(importResultContainer);
    }

    private void executeImport(AjaxRequestTarget target, FileUpload upload) {
	byte[] fileContentBytes = upload.getBytes();
	LOG.debug("Importing file of {} bytes", fileContentBytes.length);

	ImportResultWritter result = new ImportResultWritter();
	impoter.importScript(new ByteArrayInputStream(fileContentBytes), result);

	importResultContainer.setVisible(true);
	target.add(importResultContainer);
	resultModel.setObject(result.getResult());
    }

    private void initFileUpload() {
	final FileUploadField scriptFile = new FileUploadField("scriptFile");

	final Form<?> form = new Form<Void>("form") {
	    @Override
	    protected void onSubmit() {
	    }
	};
	form.setMaxSize(Bytes.megabytes(conf.cqlImport.maxFileSizeMb));
	add(form);
	form.add(scriptFile);

	form.add(new AjaxButton("ajaxSubmit") {
	    @Override
	    protected void onError(AjaxRequestTarget target, Form<?> form) {
		removeProgressBar(target);
	    }

	    @Override
	    protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
		removeProgressBar(target);
		FileUpload upload = scriptFile.getFileUpload();
		if (upload == null) {
		    return;
		}
		executeImport(target, upload);
	    }
	});
    }

    private WebMarkupContainer initImportResultContainer() {
	WebMarkupContainer historyContainer = new WebMarkupContainer("importResultContainer");
	historyContainer.setOutputMarkupPlaceholderTag(true);
	historyContainer.setVisible(false);
	add(historyContainer);
	return historyContainer;
    }

    private ImmutableListModel<ImportResult> initResultTable(final WebMarkupContainer container) {
	ImmutableListModel<ImportResult> model = new ImmutableListModel<>();

	PageableListView<ImportResult> historyTable = new PageableListView<ImportResult>(
		"resultRow",
		model,
		AppConfig.get().cqlImport.resultsPerPage) {

	    @Override
	    protected void populateItem(ListItem<ImportResult> item) {
		ImportResult entry = item.getModel().getObject();

		populateRuntime(item, entry);
		populateQuery(item, entry);
	    }
	};
	container.add(historyTable);
	final BootstrapPagingNavigator importResultPager = new BootstrapPagingNavigator(
		"importResultPager",
		historyTable);
	container.add(importResultPager);

	model.registerOnChangeListener(new ModelChangeListener<ImportResult>() {
	    @Override
	    public void onModelChanged(ImmutableList<? extends ImportResult> object) {
		importResultPager.getPageable().setCurrentPage(0);
	    }
	});

	return model;
    }

    private void populateQuery(ListItem<ImportResult> item, ImportResult entry) {
	Label queryLabel = new Label("query", entry.query.part);
	queryLabel.setEscapeModelStrings(false);
	item.add(queryLabel);

	WebMarkupContainer errorContainer = new WebMarkupContainer("queryError");
	item.add(errorContainer);
	if (entry.error == null) {
	    errorContainer.setVisible(false);
	}
	else {
	    Label errorLabel = new Label("error", entry.error.getMessage());
	    errorContainer.add(errorLabel);
	}
    }

    private void populateRuntime(ListItem<ImportResult> item, ImportResult entry) {
	String dateStr = Long.toString(entry.runTime);
	Label executedOn = new Label("runtime", dateStr);
	item.add(executedOn);
    }

    private void removeProgressBar(AjaxRequestTarget target) {
	target.appendJavaScript("onQueryImportResponse()");
    }

    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
	response.render(JavaScriptReferenceHeaderItem.forReference(JS_IMPORT));
    }

}