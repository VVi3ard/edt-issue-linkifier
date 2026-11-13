package com.github.vvi3ard.dt.linkifier.preferences;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.github.vvi3ard.dt.linkifier.LinkifierPlugin;
import com.github.vvi3ard.dt.linkifier.core.StyledTextLinkifier;
import com.github.vvi3ard.dt.linkifier.model.ActivationMode;
import com.github.vvi3ard.dt.linkifier.model.LinkRange;
import com.github.vvi3ard.dt.linkifier.model.LinkRule;
import com.github.vvi3ard.dt.linkifier.model.RuleManager;
import com.github.vvi3ard.dt.linkifier.model.ScopeItem;
import com.github.vvi3ard.dt.linkifier.model.ScopeRegistry;

/**
 * Master-Detail Preference Page for configuring EDT Issue Linkifier rules and scopes.
 */
public class LinkifierPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    private TableViewer tableViewer;
    private List<LinkRule> rulesList = new ArrayList<>();
    private LinkRule selectedRule = null;

    // Detail Panel Widgets
    private Group grpDetail;
    private TreeViewer treeViewer;
    private Button btnAllDisabled;
    private Button btnAllAlways;
    private Button btnAllCtrl;
    private Button btnAddExt;

    // Interactive Tester
    private Text txtTestInput;
    private Text txtTestResult;

    public LinkifierPreferencePage() {
        super();
        setDescription("Настройка правил распознавания ссылок и областей применения (редакторы кода, всплывающие окна, Git панели).");
    }

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(LinkifierPlugin.getDefault().getPreferenceStore());
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        container.setLayout(layout);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        // 1. Master: Rules Table
        createMasterRulesGroup(container);

        // 2. Detail: Scoping Hierarchy for Selected Rule
        createDetailScopesGroup(container);

        // 3. Live Tester
        createTesterGroup(container);

        // Load data from preferences
        loadPreferences();

        return container;
    }

    private void createMasterRulesGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Список правил (Master)");
        GridLayout gl = new GridLayout(2, false);
        group.setLayout(gl);
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Table table = new Table(group, SWT.BORDER | SWT.FULL_SELECTION | SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData gdTable = new GridData(GridData.FILL_BOTH);
        gdTable.heightHint = 130;
        table.setLayoutData(gdTable);

        tableViewer = new TableViewer(table);
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());

        // Col 1: Enabled
        TableViewerColumn colEnabled = new TableViewerColumn(tableViewer, SWT.CENTER);
        colEnabled.getColumn().setText("Вкл");
        colEnabled.getColumn().setWidth(45);
        colEnabled.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return "";
            }
        });

        // Col 2: Name
        TableViewerColumn colName = new TableViewerColumn(tableViewer, SWT.LEFT);
        colName.getColumn().setText("Название");
        colName.getColumn().setWidth(180);
        colName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((LinkRule) element).getName();
            }
        });

        // Col 3: Pattern
        TableViewerColumn colPattern = new TableViewerColumn(tableViewer, SWT.LEFT);
        colPattern.getColumn().setText("RegEx Паттерн");
        colPattern.getColumn().setWidth(200);
        colPattern.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((LinkRule) element).getPattern();
            }
        });

        // Col 4: URL
        TableViewerColumn colUrl = new TableViewerColumn(tableViewer, SWT.LEFT);
        colUrl.getColumn().setText("Целевой URL ($0, $1...)");
        colUrl.getColumn().setWidth(250);
        colUrl.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((LinkRule) element).getUrlTemplate();
            }
        });

        // Checkbox toggle
        table.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (e.detail == SWT.CHECK && e.item != null) {
                    LinkRule rule = (LinkRule) e.item.getData();
                    if (rule != null) {
                        rule.setEnabled(((org.eclipse.swt.widgets.TableItem) e.item).getChecked());
                        updateLiveTest();
                    }
                }
            }
        });

        // Selection change -> updates Detail panel
        tableViewer.addSelectionChangedListener(event -> {
            IStructuredSelection sel = (IStructuredSelection) event.getSelection();
            if (!sel.isEmpty()) {
                selectedRule = (LinkRule) sel.getFirstElement();
            } else {
                selectedRule = null;
            }
            updateDetailPanel();
        });

        // Buttons column
        Composite btnComposite = new Composite(group, SWT.NONE);
        GridLayout btnLayout = new GridLayout(1, true);
        btnLayout.marginWidth = 0;
        btnLayout.marginHeight = 0;
        btnComposite.setLayout(btnLayout);
        btnComposite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));

        Button btnAdd = new Button(btnComposite, SWT.PUSH);
        btnAdd.setText("Добавить...");
        btnAdd.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        btnAdd.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                RuleDialog dlg = new RuleDialog(getShell(), null, true);
                if (dlg.open() == RuleDialog.OK) {
                    LinkRule newRule = dlg.getRule();
                    // Set default scopes for newly created rule
                    newRule.setScopeMode(ScopeRegistry.SCOPE_EDITOR_EXT_FALLBACK, ActivationMode.CTRL_CLICK);
                    newRule.setScopeMode(ScopeRegistry.SCOPE_HOVER_BLAME, ActivationMode.ALWAYS);
                    newRule.setScopeMode(ScopeRegistry.SCOPE_HOVER_DIFF, ActivationMode.ALWAYS);
                    rulesList.add(newRule);
                    refreshTable();
                    tableViewer.setSelection(new StructuredSelection(newRule), true);
                    updateLiveTest();
                }
            }
        });

        Button btnEdit = new Button(btnComposite, SWT.PUSH);
        btnEdit.setText("Изменить...");
        btnEdit.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        btnEdit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                editSelectedRule();
            }
        });

        Button btnDuplicate = new Button(btnComposite, SWT.PUSH);
        btnDuplicate.setText("Дублировать");
        btnDuplicate.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        btnDuplicate.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (selectedRule != null) {
                    LinkRule copy = selectedRule.copy();
                    copy.setName(selectedRule.getName() + " (Копия)");
                    rulesList.add(copy);
                    refreshTable();
                    tableViewer.setSelection(new StructuredSelection(copy), true);
                    updateLiveTest();
                }
            }
        });

        Button btnRemove = new Button(btnComposite, SWT.PUSH);
        btnRemove.setText("Удалить");
        btnRemove.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        btnRemove.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (selectedRule != null) {
                    if (MessageDialog.openConfirm(getShell(), "Удаление правила", "Удалить правило '" + selectedRule.getName() + "'?")) {
                        rulesList.remove(selectedRule);
                        selectedRule = null;
                        refreshTable();
                        updateDetailPanel();
                        updateLiveTest();
                    }
                }
            }
        });

        tableViewer.addDoubleClickListener(event -> editSelectedRule());
    }

    private void editSelectedRule() {
        if (selectedRule != null) {
            RuleDialog dlg = new RuleDialog(getShell(), selectedRule, false);
            if (dlg.open() == RuleDialog.OK) {
                int index = rulesList.indexOf(selectedRule);
                if (index >= 0) {
                    LinkRule updated = dlg.getRule();
                    updated.setScopes(selectedRule.getScopes());
                    rulesList.set(index, updated);
                    selectedRule = updated;
                    refreshTable();
                    tableViewer.setSelection(new StructuredSelection(updated), true);
                    updateLiveTest();
                }
            }
        }
    }

    private void createDetailScopesGroup(Composite parent) {
        grpDetail = new Group(parent, SWT.NONE);
        grpDetail.setText("Области применения правила (Detail)");
        GridLayout gl = new GridLayout(1, false);
        grpDetail.setLayout(gl);
        grpDetail.setLayoutData(new GridData(GridData.FILL_BOTH));

        // Quick action toolbar
        Composite toolBar = new Composite(grpDetail, SWT.NONE);
        GridLayout tbLayout = new GridLayout(4, false);
        tbLayout.marginWidth = 0;
        tbLayout.marginHeight = 0;
        toolBar.setLayout(tbLayout);
        toolBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        btnAllDisabled = new Button(toolBar, SWT.PUSH);
        btnAllDisabled.setText("Везде: Откл");
        btnAllDisabled.setToolTipText("Отключить правило во всех областях");
        btnAllDisabled.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setAllScopesMode(ActivationMode.DISABLED);
            }
        });

        btnAllAlways = new Button(toolBar, SWT.PUSH);
        btnAllAlways.setText("Везде: Всегда");
        btnAllAlways.setToolTipText("Активировать правило везде без Ctrl");
        btnAllAlways.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setAllScopesMode(ActivationMode.ALWAYS);
            }
        });

        btnAllCtrl = new Button(toolBar, SWT.PUSH);
        btnAllCtrl.setText("Везде: Ctrl+Click");
        btnAllCtrl.setToolTipText("Активировать правило везде по нажатию Ctrl");
        btnAllCtrl.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setAllScopesMode(ActivationMode.CTRL_CLICK);
            }
        });

        btnAddExt = new Button(toolBar, SWT.PUSH);
        btnAddExt.setText("+ Добавить расширение...");
        btnAddExt.setToolTipText("Добавить пользовательское расширение файла (например: sql, json, 1c)");
        btnAddExt.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                promptAddExtension();
            }
        });

        // 2-Column Tree Viewer
        Tree tree = new Tree(grpDetail, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);
        GridData gdTree = new GridData(GridData.FILL_BOTH);
        gdTree.heightHint = 180;
        tree.setLayoutData(gdTree);

        treeViewer = new TreeViewer(tree);
        treeViewer.setContentProvider(new ITreeContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                return ScopeRegistry.getInstance().getRootScopes().toArray();
            }

            @Override
            public Object[] getChildren(Object parentElement) {
                return ((ScopeItem) parentElement).getChildren().toArray();
            }

            @Override
            public Object getParent(Object element) {
                return ((ScopeItem) element).getParent();
            }

            @Override
            public boolean hasChildren(Object element) {
                return ((ScopeItem) element).hasChildren();
            }

            @Override
            public void dispose() {}
            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
        });

        // Column 1: Scope Name
        TreeViewerColumn colScopeName = new TreeViewerColumn(treeViewer, SWT.LEFT);
        colScopeName.getColumn().setText("Область / Расширение");
        colScopeName.getColumn().setWidth(320);
        colScopeName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((ScopeItem) element).getLabel();
            }
        });

        // Column 2: Activation Mode
        TreeViewerColumn colScopeMode = new TreeViewerColumn(treeViewer, SWT.CENTER);
        colScopeMode.getColumn().setText("Режим (Клик для переключения)");
        colScopeMode.getColumn().setWidth(200);
        colScopeMode.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (selectedRule == null) {
                    return "";
                }
                ScopeItem item = (ScopeItem) element;
                if (item.isGroup()) {
                    return getGroupDisplayMode(selectedRule, item);
                }
                return selectedRule.getScopeMode(item.getId()).getLabel();
            }
        });

        // Fast cycle mode on clicking the mode column
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                if (selectedRule == null) return;
                Point pt = new Point(e.x, e.y);
                TreeItem item = tree.getItem(pt);
                if (item != null && item.getData() instanceof ScopeItem) {
                    if (item.getBounds(1).contains(pt)) {
                        cycleScopeMode((ScopeItem) item.getData());
                    }
                }
            }
        });

        treeViewer.setInput(ScopeRegistry.getInstance());
        treeViewer.expandAll();
    }

    private void cycleScopeMode(ScopeItem item) {
        if (selectedRule == null) return;

        if (item.isGroup()) {
            ActivationMode nextMode = getGroupNextMode(selectedRule, item);
            applyModeToGroup(selectedRule, item, nextMode);
        } else {
            ActivationMode current = selectedRule.getScopeMode(item.getId());
            selectedRule.setScopeMode(item.getId(), current.next());
        }

        treeViewer.refresh();
        updateLiveTest();
    }

    private String getGroupDisplayMode(LinkRule rule, ScopeItem group) {
        ActivationMode first = null;
        for (ScopeItem child : group.getChildren()) {
            ActivationMode m = rule.getScopeMode(child.getId());
            if (first == null) {
                first = m;
            } else if (first != m) {
                return "(смешанный...)";
            }
        }
        return first != null ? first.getLabel() : ActivationMode.DISABLED.getLabel();
    }

    private ActivationMode getGroupNextMode(LinkRule rule, ScopeItem group) {
        ActivationMode first = null;
        boolean mixed = false;
        for (ScopeItem child : group.getChildren()) {
            ActivationMode m = rule.getScopeMode(child.getId());
            if (first == null) {
                first = m;
            } else if (first != m) {
                mixed = true;
                break;
            }
        }
        if (mixed || first == null) {
            return ActivationMode.ALWAYS;
        }
        return first.next();
    }

    private void applyModeToGroup(LinkRule rule, ScopeItem group, ActivationMode mode) {
        for (ScopeItem child : group.getChildren()) {
            if (child.isGroup()) {
                applyModeToGroup(rule, child, mode);
            } else {
                rule.setScopeMode(child.getId(), mode);
            }
        }
    }

    private void setAllScopesMode(ActivationMode mode) {
        if (selectedRule == null) return;
        for (ScopeItem leaf : ScopeRegistry.getInstance().getAllLeafScopes()) {
            selectedRule.setScopeMode(leaf.getId(), mode);
        }
        treeViewer.refresh();
        updateLiveTest();
    }

    private void promptAddExtension() {
        if (selectedRule == null) return;

        InputDialog dlg = new InputDialog(getShell(), "Добавить расширение файла",
                "Введите расширение файла (например: sql, json, yaml, 1c):", "",
                new IInputValidator() {
                    @Override
                    public String isValid(String newText) {
                        if (newText == null || newText.trim().isEmpty()) {
                            return "Расширение не может быть пустым.";
                        }
                        String clean = newText.trim().replace("*.", "").replace(".", "");
                        if (!clean.matches("^[a-zA-Z0-9_-]+$")) {
                            return "Недопустимые символы в расширении.";
                        }
                        return null;
                    }
                });

        if (dlg.open() == Window.OK) {
            String ext = dlg.getValue().trim();
            ScopeItem newItem = ScopeRegistry.getInstance().registerExtension(ext);
            if (newItem != null) {
                selectedRule.setScopeMode(newItem.getId(), ActivationMode.CTRL_CLICK);
                treeViewer.refresh();
                treeViewer.expandAll();
                updateLiveTest();
            }
        }
    }

    private void updateDetailPanel() {
        if (selectedRule != null) {
            grpDetail.setText("Области применения правила: \"" + selectedRule.getName() + "\"");
            btnAllDisabled.setEnabled(true);
            btnAllAlways.setEnabled(true);
            btnAllCtrl.setEnabled(true);
            btnAddExt.setEnabled(true);
            treeViewer.getTree().setEnabled(true);
            treeViewer.refresh();
        } else {
            grpDetail.setText("Области применения правила (выберите правило выше)");
            btnAllDisabled.setEnabled(false);
            btnAllAlways.setEnabled(false);
            btnAllCtrl.setEnabled(false);
            btnAddExt.setEnabled(false);
            treeViewer.getTree().setEnabled(false);
            treeViewer.refresh();
        }
    }

    private void createTesterGroup(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Интерактивная проверка распознавания");
        group.setLayout(new GridLayout(2, false));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Label lblInput = new Label(group, SWT.NONE);
        lblInput.setText("Тестовый текст:");

        txtTestInput = new Text(group, SWT.BORDER);
        txtTestInput.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        txtTestInput.setText("// Внимание: проверить задачу TSK-213318 и diff TSK-208471");

        Label lblResult = new Label(group, SWT.NONE);
        lblResult.setText("Распознано ссылок:");

        txtTestResult = new Text(group, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gdRes = new GridData(GridData.FILL_HORIZONTAL);
        gdRes.heightHint = 50;
        txtTestResult.setLayoutData(gdRes);

        txtTestInput.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                updateLiveTest();
            }
        });
    }

    private void refreshTable() {
        tableViewer.setInput(rulesList);
        Table table = tableViewer.getTable();
        for (int i = 0; i < rulesList.size(); i++) {
            table.getItem(i).setChecked(rulesList.get(i).isEnabled());
        }
    }

    private void updateLiveTest() {
        if (txtTestInput == null || txtTestResult == null || txtTestInput.isDisposed() || txtTestResult.isDisposed()) {
            return;
        }

        String input = txtTestInput.getText();
        if (input == null || input.isEmpty()) {
            txtTestResult.setText("Текст пуст");
            return;
        }

        // Temporarily configure RuleManager for preview
        RuleManager.setRules(rulesList);
        List<LinkRange> found = RuleManager.findLinks(input);

        if (found.isEmpty()) {
            txtTestResult.setText("Ни одного совпадения по активным правилам не найдено.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (LinkRange lr : found) {
                sb.append(lr.getMatchedText())
                  .append("  -->  ")
                  .append(lr.getUrl())
                  .append("  [")
                  .append(lr.getRule().getName())
                  .append("]\n");
            }
            txtTestResult.setText(sb.toString().trim());
        }
    }

    private void loadPreferences() {
        IPreferenceStore store = getPreferenceStore();
        String json = store.getString(RuleManager.PREF_RULES_JSON);
        if (json != null && !json.trim().isEmpty()) {
            rulesList = new ArrayList<>(RuleManager.deserializeRulesFromJson(json));
        } else {
            rulesList = new ArrayList<>(RuleManager.getDefaultRules());
        }

        refreshTable();
        if (!rulesList.isEmpty()) {
            tableViewer.setSelection(new StructuredSelection(rulesList.get(0)), true);
        } else {
            updateDetailPanel();
        }
        updateLiveTest();
    }

    @Override
    protected void performDefaults() {
        rulesList = new ArrayList<>(RuleManager.getDefaultRules());
        refreshTable();
        if (!rulesList.isEmpty()) {
            tableViewer.setSelection(new StructuredSelection(rulesList.get(0)), true);
        } else {
            updateDetailPanel();
        }
        updateLiveTest();
        super.performDefaults();
    }

    @Override
    public boolean performOk() {
        IPreferenceStore store = getPreferenceStore();
        store.setValue(RuleManager.PREF_RULES_JSON, RuleManager.serializeRulesToJson(rulesList));

        // Update live rules immediately
        RuleManager.setRules(rulesList);
        StyledTextLinkifier.refreshAll();

        return super.performOk();
    }
}
