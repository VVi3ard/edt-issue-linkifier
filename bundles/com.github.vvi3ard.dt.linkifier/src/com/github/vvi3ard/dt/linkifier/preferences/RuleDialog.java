package com.github.vvi3ard.dt.linkifier.preferences;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.github.vvi3ard.dt.linkifier.model.LinkRule;

/**
 * Dialog to create or edit an issue linkification rule.
 */
public class RuleDialog extends Dialog {

    private final LinkRule rule;
    private final boolean isNew;

    private Text txtName;
    private Text txtPattern;
    private Text txtUrlTemplate;
    private Button chkEnabled;
    private Label lblError;

    public RuleDialog(Shell parentShell, LinkRule rule, boolean isNew) {
        super(parentShell);
        this.rule = rule != null ? rule.copy() : new LinkRule();
        this.isNew = isNew;
    }

    public LinkRule getRule() {
        return rule;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(isNew ? "Добавить правило ссылки" : "Редактировать правило ссылки");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        Composite container = new Composite(area, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 15;
        layout.marginHeight = 15;
        layout.verticalSpacing = 10;
        layout.horizontalSpacing = 10;
        container.setLayout(layout);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        // Name
        Label lblName = new Label(container, SWT.NONE);
        lblName.setText("Название правила:");
        txtName = new Text(container, SWT.BORDER);
        txtName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        txtName.setText(rule.getName() != null ? rule.getName() : "");

        // Pattern
        Label lblPattern = new Label(container, SWT.NONE);
        lblPattern.setText("RegEx паттерн:");
        txtPattern = new Text(container, SWT.BORDER);
        txtPattern.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        txtPattern.setMessage("например: (TSK-\\d{6})");
        txtPattern.setText(rule.getPattern() != null ? rule.getPattern() : "");

        // URL Template
        Label lblUrl = new Label(container, SWT.NONE);
        lblUrl.setText("Шаблон URL:");
        txtUrlTemplate = new Text(container, SWT.BORDER);
        txtUrlTemplate.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        txtUrlTemplate.setMessage("например: https://support.youjira.ru/browse/$1");
        txtUrlTemplate.setText(rule.getUrlTemplate() != null ? rule.getUrlTemplate() : "");

        // Enabled
        new Label(container, SWT.NONE); // spacer
        chkEnabled = new Button(container, SWT.CHECK);
        chkEnabled.setText("Правило активно");
        chkEnabled.setSelection(rule.isEnabled());

        // Hint label
        new Label(container, SWT.NONE);
        Label lblHint = new Label(container, SWT.WRAP);
        lblHint.setText("Подсказка: используйте $0 для всего совпадения, либо $1, $2 для групп захвата (...).\n"
                + "Пример: (TSK-\\d{6}) -> https://support.youjira.ru/browse/$1");
        GridData gdHint = new GridData(GridData.FILL_HORIZONTAL);
        gdHint.widthHint = 450;
        lblHint.setLayoutData(gdHint);

        // Error message
        new Label(container, SWT.NONE);
        lblError = new Label(container, SWT.NONE);
        lblError.setForeground(container.getDisplay().getSystemColor(SWT.COLOR_RED));
        lblError.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        ModifyListener validator = new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                validate();
            }
        };

        txtName.addModifyListener(validator);
        txtPattern.addModifyListener(validator);
        txtUrlTemplate.addModifyListener(validator);

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        validate();
    }

    private void validate() {
        Button btnOk = getButton(IDialogConstants.OK_ID);
        String name = txtName != null ? txtName.getText().trim() : "";
        String pattern = txtPattern != null ? txtPattern.getText().trim() : "";
        String url = txtUrlTemplate != null ? txtUrlTemplate.getText().trim() : "";

        if (name.isEmpty()) {
            setValidationStatus(btnOk, "Укажите название правила.");
            return;
        }

        if (pattern.isEmpty()) {
            setValidationStatus(btnOk, "Укажите регулярное выражение (паттерн).");
            return;
        }

        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            setValidationStatus(btnOk, "Ошибка синтаксиса RegEx: " + ex.getDescription());
            return;
        }

        if (url.isEmpty()) {
            setValidationStatus(btnOk, "Укажите шаблон целевого URL.");
            return;
        }

        if (lblError != null) {
            lblError.setText("");
        }
        if (btnOk != null) {
            btnOk.setEnabled(true);
        }
    }

    private void setValidationStatus(Button btnOk, String errorMessage) {
        if (lblError != null) {
            lblError.setText(errorMessage);
        }
        if (btnOk != null) {
            btnOk.setEnabled(false);
        }
    }

    @Override
    protected void okPressed() {
        rule.setName(txtName.getText().trim());
        rule.setPattern(txtPattern.getText().trim());
        rule.setUrlTemplate(txtUrlTemplate.getText().trim());
        rule.setEnabled(chkEnabled.getSelection());
        super.okPressed();
    }
}
