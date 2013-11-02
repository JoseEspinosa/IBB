/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.hic.track;

import org.apache.log4j.Logger;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.track.AttributeManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.ui.util.LinkCheckBox;
import org.broad.igv.util.ResourceLocator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

import static org.broad.igv.util.ResourceLocator.AttributeType.*;

/**
 * Parses XML file of IGV resources, and displays them in tree format.
 *
 * @author eflakes
 */
public class ResourceTree {

    private static final Logger log = Logger.getLogger(ResourceTree.class);
    private final List<CheckableResource> leafResources = new ArrayList<CheckableResource>();
    private JDialog dialog;
    private final JTree dialogTree;
    private DefaultMutableTreeNode twoDFeatureRoot;
    private final Set<ResourceLocator> loadedLocators;
    private LinkedHashSet<ResourceLocator> newLocators;
    private LinkedHashSet<ResourceLocator> deselectedLocators;
    private boolean isRestricted = true;

    public ResourceTree(HiC hic, Document document, boolean isRestricted) {
        dialog = null;
        this.isRestricted = isRestricted;
        loadedLocators = new HashSet<ResourceLocator>();


        dialogTree = new JTree(new DefaultMutableTreeNode("Available feature sets"));
        dialogTree.setExpandsSelectedPaths(true);
        dialogTree.setCellRenderer(new NodeRenderer());
        dialogTree.setCellEditor(new ResourceEditor(dialogTree));
        dialogTree.setEditable(true);

        if (document != null) {
            DefaultMutableTreeNode node = createTreeFromDOM(document);
            ((DefaultMutableTreeNode) dialogTree.getModel().getRoot()).add(node);
        }

        if (!isRestricted) {
            createTreeFromDataset(hic, this);
        }


        expandTree();
        hic.setResourceTree(this);
        dialogTree.setRootVisible(false);
    }

    public LinkedHashSet<ResourceLocator> getLocators() {
        return newLocators;
    }
    public LinkedHashSet<ResourceLocator> getDeselectedLocators() {
        return deselectedLocators;
    }

    /**
     * Shows a tree of selectable resources.
     *
     * @param parent      Parent window
     * @return the resources selected by user.
     */
    public void showResourceTreeDialog(JFrame parent) {
        newLocators = new LinkedHashSet<ResourceLocator>();

        dialog = new JDialog(parent, "Available Features", true);

        JPanel treePanel = new JPanel();
        JScrollPane pane = new JScrollPane(dialogTree);
        treePanel.add(pane);

        pane.setPreferredSize(new Dimension(650, 500));
        pane.setOpaque(true);
        pane.setBackground(Color.WHITE);

        dialog.setBackground(Color.WHITE);
        dialog.getContentPane().setBackground(Color.WHITE);

        Component[] children = treePanel.getComponents();
        if (children != null) {
            for (Component child : children) {
                child.setBackground(Color.WHITE);
            }
        }

        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        JButton add2DButton = new JButton("Add 2D...");
        JButton add1DButton = new JButton("Add 1D...");

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dialog.dispose();
            }
        });

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dialog.dispose();
                LinkedHashSet<ResourceLocator> selectedLocators = getSelectedResourceLocators();
                deselectedLocators = new LinkedHashSet<ResourceLocator>();
                for (ResourceLocator locator : selectedLocators) {
                    if (!loadedLocators.contains(locator)) {
                        newLocators.add(locator);
                    }
                }
                for (ResourceLocator locator : loadedLocators) {
                    if (!selectedLocators.contains(locator)) {
                        deselectedLocators.add(locator);
                    }

                }
                // add these to loaded ones for next use
                for (ResourceLocator locator : newLocators) {
                    loadedLocators.add(locator);
                }
                for  (ResourceLocator locator : deselectedLocators) {
                    loadedLocators.remove(locator);
                }
                dialogTree.clearSelection();
            }
        });

        add2DButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                FileDialog dlg = new FileDialog(dialog);
                dlg.setMode(FileDialog.LOAD);
                dlg.setVisible(true);
                final String file = dlg.getFile();
                if (file != null) {

                    String path = (new java.io.File(dlg.getDirectory(), dlg.getFile())).getAbsolutePath();
                    ResourceLocator locator = new ResourceLocator(path);
                    locator.setName(file);
                    locator.setType("loop");
                    CheckableResource resource = new CheckableResource(file, true, locator);
                    leafResources.add(resource);

                    DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(file);
                    twoDFeatureRoot.add(treeNode);

                    ((CheckableResource)twoDFeatureRoot.getUserObject()).setSelected(true);
                    treeNode.setUserObject(resource);

                    expandTree();
                    dialogTree.updateUI();

                }

            }
        });

        buttonPanel.add(okButton);
        if (!isRestricted) {
            buttonPanel.add(add2DButton);
        }
        buttonPanel.add(cancelButton);

        dialog.add(treePanel);
        dialog.add(buttonPanel, BorderLayout.PAGE_END);

        dialog.setResizable(true);
        dialog.pack();

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

    }

    private void createTreeFromDataset(HiC hic, ResourceTree resourceTree) {
        DefaultMutableTreeNode oneDFeatureRoot = new DefaultMutableTreeNode("Dataset-specific 1-D Features");
        ResourceLocator locator = new ResourceLocator("Dataset-specific 1-D Features");
        locator.setName("Dataset-specific 1-D Features");
        locator.setType("norm");
        CheckableResource rootResource = new CheckableResource("Dataset-specific 1-D Features", false, locator);
        oneDFeatureRoot.setUserObject(rootResource);
        oneDFeatureRoot.setAllowsChildren(true);

        twoDFeatureRoot = new DefaultMutableTreeNode("Dataset-specific 2-D Features");
        locator = new ResourceLocator("Dataset-specific 2-D Features");
        locator.setName("Dataset-specific 2-D Features");
        locator.setType("loop");
        CheckableResource resource = new CheckableResource("Dataset-specific 2-D Features", false, locator);
        twoDFeatureRoot.setUserObject(resource);
        twoDFeatureRoot.setAllowsChildren(true);


        if (hic.getDataset().getVersion() == 6) {

            for (NormalizationType t : hic.getDataset().getNormalizationTypes()) {

                String label = t.getLabel();
                locator = new ResourceLocator(label);
                locator.setType("norm");
                locator.setName(label);
                resource = new CheckableResource(label, false, locator);

                DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(label);
                oneDFeatureRoot.add(treeNode);
                treeNode.setUserObject(resource);
                resource.setEnabled(resourceTree.dialogTree.isEnabled());
                treeNode.setAllowsChildren(false);
                leafResources.add(resource);
              //  leafNodeMap.put(getPath(treeNode), treeNode);
            }
        }
        ((DefaultMutableTreeNode) dialogTree.getModel().getRoot()).add(oneDFeatureRoot);
        ((DefaultMutableTreeNode) dialogTree.getModel().getRoot()).add(twoDFeatureRoot);
    }

    private DefaultMutableTreeNode createTreeFromDOM(Document document) {

        Element rootElement =
                (Element) document.getElementsByTagName("Global").item(0);

        if (rootElement == null) {
            return new DefaultMutableTreeNode("");
        }

        String nodeName = rootElement.getNodeName();
        if (!nodeName.equalsIgnoreCase("Global")) {
            throw new RuntimeException(rootElement +
                    " is not the root of the xml document!");
        }

        String rootLabel = getAttribute(rootElement, "name");
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(rootLabel);

        // Build and attach descendants of the root node to the tree
        buildLocatorTree(rootNode, rootElement);

        return rootNode;
    }

    /**
     * Build a tree of all resources, placed under {@code treeNode}, starting
     * from {@code xmlNode}.
     *
     * @param treeNode
     * @param xmlNode
    */
    private void buildLocatorTree(DefaultMutableTreeNode treeNode, Element xmlNode) {

        String name = getAttribute(xmlNode, NAME.getText());

        ResourceLocator locator = new ResourceLocator(
                getAttribute(xmlNode, DB_URL.getText()),
                getAttribute(xmlNode, PATH.getText())
        );
        locator.setName(name);

        if (xmlNode.getTagName().equalsIgnoreCase("Resource")) {

            String resourceType = getAttribute(xmlNode, RESOURCE_TYPE.getText());
            locator.setType(resourceType);

            String infoLink = getAttribute(xmlNode, HYPERLINK.getText());
            if (infoLink == null) {
                infoLink = getAttribute(xmlNode, INFOLINK.getText());
            }
            locator.setTrackInforURL(infoLink);

            String sampleId = getAttribute(xmlNode, SAMPLE_ID.getText());
            if (sampleId == null) {
                // legacy option
                sampleId = getAttribute(xmlNode, ID.getText());
            }
            locator.setSampleId(sampleId);
            locator.setFeatureInfoURL (getAttribute(xmlNode, URL.getText()));
            locator.setDescription(getAttribute(xmlNode, DESCRIPTION.getText()));
            locator.setTrackLine(getAttribute(xmlNode, TRACK_LINE.getText()));
            locator.setName(name);
            // Special element for alignment tracks
            locator.setCoverage(getAttribute(xmlNode, COVERAGE.getText()));

            String colorString = getAttribute(xmlNode, COLOR.getText());
            if (colorString != null) {
                try {
                    Color c = ColorUtilities.stringToColor(colorString);
                    locator.setColor(c);
                } catch (Exception e) {
                    log.error("Error setting color: ", e);
                }
            }
        }

        NodeList nodeList = xmlNode.getChildNodes();
        Node xmlChildNode;

        // If we have children treat it as a category not a leaf
        for (int i = 0; i < nodeList.getLength(); i++) {

            xmlChildNode = nodeList.item(i);
            String nodeName = xmlChildNode.getNodeName();
            if (nodeName.equalsIgnoreCase("#text")) {
                continue;
            }

            // Need to check class of child node, its not necessarily an
            // element (could be a comment for example).
            if (xmlChildNode instanceof Element) {
                String categoryLabel = getAttribute((Element) xmlChildNode, NAME.getText());
                DefaultMutableTreeNode treeChildNode = new DefaultMutableTreeNode(categoryLabel);
                treeNode.add(treeChildNode);
                buildLocatorTree(treeChildNode, (Element) xmlChildNode);
            }
        }

        CheckableResource resource = new CheckableResource(name, false, locator);
        treeNode.setUserObject(resource);

        // If it's a leaf set the checkbox to represent the resource
        if (treeNode.isLeaf()) {
            treeNode.setAllowsChildren(false);
            leafResources.add(resource);
        } else {
            treeNode.setAllowsChildren(true);
        }
    }

    public void remove(ResourceLocator locator) {
        loadedLocators.remove(locator);
        Enumeration enumeration = ((DefaultMutableTreeNode)dialogTree.getModel().getRoot()).preorderEnumeration();
        // skip root
        enumeration.nextElement();
        while (enumeration.hasMoreElements()){
            DefaultMutableTreeNode node  = (DefaultMutableTreeNode)enumeration.nextElement();
            String test = locator.getName();
            if (test == null) test = locator.getPath();
            if (test.equals(((CheckableResource) node.getUserObject()).getText())) {
                CheckableResource resource = (CheckableResource)node.getUserObject();
                resource.setSelected(false);
            }
        }
    }

    private LinkedHashSet<ResourceLocator> getSelectedResourceLocators() {

        LinkedHashSet<ResourceLocator> resourceLocators = new LinkedHashSet<ResourceLocator>();

        for (CheckableResource resource : leafResources) {

            if (resource.isSelected()) {
                resourceLocators.add(resource.getResourceLocator());
            }
        }
        return resourceLocators;
    }

    private static String getAttribute(Element element, String key) {

        String value = element.getAttribute(key);
        if (value != null) {
            if (value.trim().equals("")) {
                value = null;
            }
        }
        return value;
    }

    /**
     * Node's Renderer
     */
    static class NodeRenderer implements TreeCellRenderer {

        private final LinkCheckBox renderer = new LinkCheckBox();
        private final Color selectionForeground;
        private final Color selectionBackground;
        private final Color textForeground;
        private final Color textBackground;

        public NodeRenderer() {

            Font fontValue;
            fontValue = UIManager.getFont("Tree.font");
            if (fontValue != null) {
                renderer.setFont(fontValue);
            }
            Boolean booleanValue =
                    (Boolean) UIManager.get("Tree.drawsFocusBorderAroundIcon");
            renderer.setFocusPainted(
                    (booleanValue != null) && booleanValue);

            selectionForeground = UIManager.getColor("Tree.selectionForeground");
            selectionBackground = UIManager.getColor("Tree.selectionBackground");
            textForeground = UIManager.getColor("Tree.textForeground");
            textBackground = UIManager.getColor("Tree.textBackground");
            renderer.setSelected(false);
        }

        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean isNodeSelected, boolean isNodeExpanded, boolean isLeaf,
                                                      int row, boolean hasFocus) {

            // Convert value into a usable string
            String stringValue = "";
            if (value != null) {
                String toStringValue = value.toString();
                if (toStringValue != null) {
                    stringValue = toStringValue;
                }
            }

            // Initialize checkbox state and selection
            renderer.setSelected(false);
            renderer.setText(stringValue);
            renderer.setEnabled(tree.isEnabled());

            // Tell renderer how to highlight nodes on selection
            if (isNodeSelected) {
                renderer.setForeground(selectionForeground);
                renderer.setBackground(selectionBackground);
            } else {
                renderer.setForeground(textForeground);
                renderer.setBackground(textBackground);
            }

            if (value != null) {
                if (value instanceof DefaultMutableTreeNode) {

                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) value;

                    Object userObject = node.getUserObject();
                    if (userObject instanceof CheckableResource) {

                        CheckableResource resource = (CheckableResource) userObject;
                        renderer.setText(resource.getText());
                        renderer.setSelected(resource.isSelected());
                        renderer.setEnabled(resource.isEnabled());

                        String hyperLink = resource.getResourceLocator().getTrackInforURL();
                        if (hyperLink == null) {
                            renderer.showHyperLink(false);
                        } else {
                            renderer.setHyperLink(hyperLink);
                            renderer.showHyperLink(true);
                        }
                    }
                }
            }

            return renderer;
        }

        LinkCheckBox getRendereringComponent() {
            return renderer;
        }
    }

    /**
     * Node's Resource Editor
     */
    static class ResourceEditor extends AbstractCellEditor
            implements TreeCellEditor {

        final NodeRenderer renderer = new NodeRenderer();
        final JTree tree;

        public ResourceEditor(JTree tree) {
            this.tree = tree;
        }

        public Object getCellEditorValue() {

            DataResource resource = null;
            TreePath treePath = tree.getEditingPath();
            if (treePath != null) {

                Object node = treePath.getLastPathComponent();

                if ((node != null) && (node instanceof DefaultMutableTreeNode)) {

                    LinkCheckBox checkbox = renderer.getRendereringComponent();

                    DefaultMutableTreeNode treeNode =
                            (DefaultMutableTreeNode) node;

                    Object userObject = treeNode.getUserObject();

                    resource = (CheckableResource) userObject;

                    // Don't change resource if disabled
                    if (!resource.isEnabled()) {
                        return resource;
                    }

                    boolean isChecked = checkbox.isSelected();

                    // Check/Uncheck the selected node. This code ONLY handles
                    // the clicked node. Not it's ancestors or decendants.
                    if (isChecked) {
                        ((CheckableResource) resource).setSelected(true);
                    } else {

                        // See if we are allowed to unchecking this specific 
                        // node - if not, it won't be done. This does not 
                        // prevent it's children from being unchecked.
                        uncheckCurrentNodeIfAllowed((CheckableResource) resource,
                                treeNode);
                    }


                    /*
                    * Now we have to check or uncheck the descendants and
                    * ancestors depending on what we did above.
                    */

                    boolean checkRelatives = isChecked;

                    // If we found a mix of select leave and selected but
                    // but disabled leave we must be trying to toggle off
                    // the children
                    if (hasSelectedAndLockedDescendants(treeNode)) {
                        checkRelatives = false;
                    }
                    // If we found only locked leave we must be trying to toggle 
                    // on the unlocked children
                    else if (hasLockedDescendants(treeNode)) {
                        checkRelatives = true;
                    }
                    // Otherwise, just use the value of the checkbox


                    if (!treeNode.isLeaf()) { //check up and down the tree

                        // If not a leaf check/uncheck children as requested
                        checkOrUncheckChildNodesRecursively(treeNode, checkRelatives);

                        // If not a leaf check/uncheck ancestors
                        checkOrUncheckParentNodesRecursively(treeNode,
                                ((CheckableResource) resource).isSelected());
                    } else { // it must be a leaf - so check up the tree
                        checkOrUncheckParentNodesRecursively(treeNode, checkRelatives);
                    }
                }
                tree.treeDidChange();
            }
            return resource;
        }

        /*
        * Uncheck a node unless rule prevent this behavior.
        */

        private void uncheckCurrentNodeIfAllowed(CheckableResource resource,
                                                 TreeNode treeNode) {

            // If we are unchecking a parent make sure there are
            // no checked children
            if (!hasSelectedChildren(treeNode)) {
                resource.setSelected(false);
            } else {

                // If node has selected children and has disabled descendants we
                // must not unselect
                if (hasLockedDescendants(treeNode)) {
                    resource.setSelected(true);
                } else {
                    // No disabled descendants so we can uncheck at will
                    resource.setSelected(false);
                }
            }
        }

        /**
         * Call to recursively check or uncheck the parent ancestors of the
         * passed node.
         */
        static public void checkOrUncheckParentNodesRecursively(TreeNode node,
                                                                boolean checkParentNode) {

            if (node == null) {
                return;
            }

            TreeNode parentNode = node.getParent();
            if (parentNode == null) {
                return;
            }

            Object parentUserObject =
                    ((DefaultMutableTreeNode) parentNode).getUserObject();

            CheckableResource parentNodeResource = null;
            if (parentUserObject instanceof CheckableResource) {
                parentNodeResource = ((CheckableResource) parentUserObject);
            }

            if (parentNodeResource != null) {

                // If parent's current check state matchs what we want there
                // is nothing to do so just leave
                if (parentNodeResource.isSelected() == checkParentNode) {
                    return;
                } else if (checkParentNode) {
                    parentNodeResource.setSelected(true);
                } else { // Uncheck Only if their are no selected descendants

                    if (!hasSelectedChildren(parentNode)) {
                        parentNodeResource.setSelected(false);
                    }
                }
            }

            checkOrUncheckParentNodesRecursively(parentNode,
                    checkParentNode);
        }

        /**
         * Can only be called from getCellEditorValue() to recursively check
         * or uncheck the children of the passed parent node.
         */
        private void checkOrUncheckChildNodesRecursively(TreeNode currentNode,
                                                         boolean isCheckingNeeded) {

            Object parentUserObject =
                    ((DefaultMutableTreeNode) currentNode).getUserObject();

            CheckableResource currentTreeNodeResource = null;
            if (parentUserObject instanceof CheckableResource) {
                currentTreeNodeResource = ((CheckableResource) parentUserObject);
            }

            if (currentTreeNodeResource != null) {

                // Set all enabled children to the checked state of their parent
                Enumeration children = currentNode.children();
                while (children.hasMoreElements()) {

                    TreeNode childNode = (TreeNode) children.nextElement();

                    Object childsUserObject =
                            ((DefaultMutableTreeNode) childNode).getUserObject();
                    if (childsUserObject instanceof CheckableResource) {

                        CheckableResource childResource =
                                ((CheckableResource) childsUserObject);

                        if (childResource.isEnabled()) {

                            // Child must be checked if it has selected
                            // selected and disabled descendants
                            if (hasLockedDescendants(childNode)) {
                                childResource.setSelected(true);
                            } else { // else check/uncheck  as requested
                                childResource.setSelected(isCheckingNeeded);
                            }
                        }
                    }
                    checkOrUncheckChildNodesRecursively(childNode,
                            isCheckingNeeded);
                }
            }
        }

        public boolean hasLockedDescendants(TreeNode treeNode) {

            Enumeration children = treeNode.children();
            while (children.hasMoreElements()) {

                TreeNode childNode = (TreeNode) children.nextElement();

                Object childsUserObject =
                        ((DefaultMutableTreeNode) childNode).getUserObject();
                if (childsUserObject instanceof CheckableResource) {
                    CheckableResource childResource =
                            ((CheckableResource) childsUserObject);

                    // If disabled say so
                    if (!childResource.isEnabled()) {
                        return true;
                    }
                }

                // If a descendant is disabled say so
                if (hasLockedDescendants(childNode)) {
                    return true;
                }
            }
            return false;
        }

        static public boolean hasSelectedDescendants(TreeNode treeNode) {

            Enumeration children = treeNode.children();
            while (children.hasMoreElements()) {

                TreeNode childNode = (TreeNode) children.nextElement();

                Object childsUserObject =
                        ((DefaultMutableTreeNode) childNode).getUserObject();
                if (childsUserObject instanceof CheckableResource) {
                    CheckableResource childResource =
                            ((CheckableResource) childsUserObject);

                    // If has selected say so
                    if (childResource.isSelected()) {
                        return true;
                    }
                }

                // If has selected descendant say so
                if (hasSelectedDescendants(childNode)) {
                    return true;
                }
            }
            return false;
        }

        static public boolean hasSelectedChildren(TreeNode treeNode) {

            Enumeration children = treeNode.children();
            while (children.hasMoreElements()) {

                TreeNode childNode = (TreeNode) children.nextElement();

                Object childsUserObject =
                        ((DefaultMutableTreeNode) childNode).getUserObject();
                if (childsUserObject instanceof CheckableResource) {
                    CheckableResource childResource =
                            ((CheckableResource) childsUserObject);
                    if (childResource.isSelected()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Return true if it find nodes that ar both selected and disabled
         *
         * @param treeNode
         * @return true if we are working with preselected nodes
         */
      /*  public boolean hasLockedChildren(TreeNode treeNode) {

            boolean hasSelectedAndDisabled = false;
            Enumeration children = treeNode.children();
            while (children.hasMoreElements()) {

                TreeNode childNode = (TreeNode) children.nextElement();

                Object childsUserObject =
                        ((DefaultMutableTreeNode) childNode).getUserObject();
                if (childsUserObject instanceof CheckableResource) {

                    CheckableResource childResource =
                            ((CheckableResource) childsUserObject);

                    if (!childResource.isEnabled() && childResource.isSelected()) {
                        hasSelectedAndDisabled = true;
                    }

                    if (hasSelectedAndDisabled) {
                        break;
                    }
                }
            }
            return (hasSelectedAndDisabled);
        }          */

        /**
         * @param treeNode
         * @return true if we are working with preselected nodes
         */
     /*   public boolean hasSelectedAndLockedChildren(TreeNode treeNode) {

            boolean hasSelected = false;
            boolean hasSelectedAndDisabled = false;
            Enumeration children = treeNode.children();
            while (children.hasMoreElements()) {

                TreeNode childNode = (TreeNode) children.nextElement();

                Object childsUserObject =
                        ((DefaultMutableTreeNode) childNode).getUserObject();
                if (childsUserObject instanceof CheckableResource) {

                    CheckableResource childResource =
                            ((CheckableResource) childsUserObject);

                    if (childResource.isSelected() && childResource.isEnabled()) {
                        hasSelected = true;
                    }
                    if (!childResource.isEnabled() && childResource.isSelected()) {
                        hasSelectedAndDisabled = true;
                    }

                    if (hasSelected & hasSelectedAndDisabled) {
                        break;
                    }
                }
            }

            // If we have both we can return true
            return (hasSelected & hasSelectedAndDisabled);
        }                    /*

        /**
         * @param treeNode
         * @return true if we are working with preselected nodes
         */
        public boolean hasSelectedAndLockedDescendants(TreeNode treeNode) {

            boolean hasSelected = false;
            boolean hasSelectedAndDisabled = false;
            Enumeration children = treeNode.children();
            while (children.hasMoreElements()) {

                TreeNode childNode = (TreeNode) children.nextElement();

                Object childsUserObject =
                        ((DefaultMutableTreeNode) childNode).getUserObject();
                if (childsUserObject instanceof CheckableResource) {

                    CheckableResource childResource =
                            ((CheckableResource) childsUserObject);

                    if (childResource.isSelected() && childResource.isEnabled()) {
                        hasSelected = true;
                    }
                    if (!childResource.isEnabled() && childResource.isSelected()) {
                        hasSelectedAndDisabled = true;
                    }

                    if (hasSelected & hasSelectedAndDisabled) {
                        break;
                    }
                }

                // If has a mix of selected and checked but disableddescendant
                if (hasSelectedAndLockedDescendants(childNode)) {
                    return true;
                }

            }

            // If we have both we can return true
            return (hasSelected & hasSelectedAndDisabled);
        }

        @Override
        public boolean isCellEditable(EventObject event) {

            boolean returnValue = false;

            if (event instanceof MouseEvent) {

                MouseEvent mouseEvent = (MouseEvent) event;
                TreePath treePath = tree.getPathForLocation(
                        mouseEvent.getX(), mouseEvent.getY());

                if (treePath != null) {

                    Object node = treePath.getLastPathComponent();
                    if ((node != null) &&
                            (node instanceof DefaultMutableTreeNode)) {
                        DefaultMutableTreeNode treeNode =
                                (DefaultMutableTreeNode) node;
                        Object userObject = treeNode.getUserObject();

                        if (userObject instanceof CheckableResource) {
                            returnValue = true;
                        }
                    }
                }
            }
            return returnValue;
        }

        public Component getTreeCellEditorComponent(JTree tree, Object value,
                                                    boolean selected, boolean expanded, boolean leaf, int row) {

            Component rendererComponent = renderer.getTreeCellRendererComponent(
                    tree, value, true, expanded, leaf, row, true);


            ItemListener itemListener = new ItemListener() {

                public void itemStateChanged(ItemEvent itemEvent) {
                    if (stopCellEditing()) {
                        fireEditingStopped();
                    }
                }
            };
            if (rendererComponent instanceof LinkCheckBox) {
                ((LinkCheckBox) rendererComponent).addItemListener(itemListener);
            }

            return rendererComponent;
        }
    }

    public static class CheckableResource implements SelectableResource {

        final static Color partialSelectionColor =
                new Color(255, 128, 128);
        final boolean isParentOfPartiallySelectedChildren = false;
        String text;
        boolean selected;
        final ResourceLocator dataResourceLocator;
        boolean isEnabled = true;

        public CheckableResource(String text, boolean selected,
                                 ResourceLocator dataResourceLocator) {

            this.text = text;
            this.selected = selected;
            this.dataResourceLocator = dataResourceLocator;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean newValue) {
            selected = newValue;
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        public void setEnabled(boolean value) {
            isEnabled = value;
        }

        public String getText() {
            return text;
        }

        public void setText(String newValue) {
            text = newValue;
        }

        public ResourceLocator getResourceLocator() {
            return dataResourceLocator;
        }

        public boolean isParentOfPartiallySelectedChildren() {
            return isParentOfPartiallySelectedChildren;
        }

        public Color getBackground() {

            if (isParentOfPartiallySelectedChildren()) {
                return partialSelectionColor;
            } else {
                return Color.WHITE;
            }
        }

        @Override
        public String toString() {
            return text + ":" + selected;
        }
    }


    /**
     *
     */
    static interface SelectableResource extends DataResource {

        public boolean isSelected();

        public void setSelected(boolean newValue);
    }

    /**
     *
     */
    static interface DataResource {

        public ResourceLocator getResourceLocator();

        public void setText(String newValue);

        public String getText();

        public void setEnabled(boolean value);

        public boolean isEnabled();
    }

    /**
     * Expands tree.
     */
    private void expandTree() {
        TreeNode root = (TreeNode) dialogTree.getModel().getRoot();
        TreePath rootPath = new TreePath(root);
        TreeNode node = (TreeNode) rootPath.getLastPathComponent();
        for (Enumeration e = node.children(); e.hasMoreElements(); ) {
            TreePath childPath = rootPath.pathByAddingChild(e.nextElement());
            if (!dialogTree.isExpanded(childPath)) {
                dialogTree.expandPath(childPath);
            }
        }
        if (!dialogTree.isExpanded(rootPath)) {
            dialogTree.expandPath(rootPath);
        }
    }
}
