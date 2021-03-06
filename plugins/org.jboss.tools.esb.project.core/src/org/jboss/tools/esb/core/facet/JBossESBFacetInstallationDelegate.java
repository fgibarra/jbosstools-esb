/*******************************************************************************
 * Copyright (c) 2007 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.esb.core.facet;

import java.io.ByteArrayInputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jst.common.project.facet.WtpUtils;
import org.eclipse.jst.common.project.facet.core.ClasspathHelper;
import org.eclipse.wst.common.componentcore.internal.util.IComponentImplFactory;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.common.project.facet.core.IDelegate;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.jboss.ide.eclipse.as.wtp.core.util.VCFUtil;
import org.jboss.ide.eclipse.as.wtp.core.vcf.OutputFoldersVirtualComponent;
import org.jboss.tools.esb.core.ESBProjectConstant;
import org.jboss.tools.esb.core.ESBProjectCorePlugin;
import org.jboss.tools.esb.core.component.ESBVirtualComponent;
import org.osgi.service.prefs.BackingStoreException;

public class JBossESBFacetInstallationDelegate implements IDelegate {

	
	private IDataModel model;
	public static final String ESB_NATURE = "org.jboss.tools.esb.project.core.ESBNature";

	public void execute(IProject project, IProjectFacetVersion fv,
			Object config, IProgressMonitor monitor) throws CoreException {
		model = (IDataModel) config;
		final IJavaProject jproj = JavaCore.create(project);

		createProjectStructure(project);

		
		// Add WTP natures.
		WtpUtils.addNatures(project);

		// Setup the flexible project structure
		
		/* 
		 * This is necessary because at time, the project has NO facets
		 * So a call to createComponent(etc) returns a default implementation.
		 * Today, this WTP default implementation does not handle  
		 * new reference types in an acceptable fashion 
		 * (Does not use extension point). 
		 */
		IComponentImplFactory factory = new ESBVirtualComponent();
		IVirtualComponent newComponent = factory.createComponent(project);

		String outputLoc = jproj.readOutputLocation().removeFirstSegments(1).toString();
		newComponent.create(0, null);
		newComponent.setMetaProperty("java-output-path", outputLoc);
		
		final IVirtualFolder jbiRoot = newComponent.getRootFolder();

		// Map the esbcontent to root for deploy
		String resourcesFolder = model.getStringProperty(
				IJBossESBFacetDataModelProperties.ESB_CONTENT_FOLDER);
		jbiRoot.createLink(new Path("/" + resourcesFolder), 0, null);
				
		String sourcesFolder = model.getStringProperty(
				IJBossESBFacetDataModelProperties.ESB_SOURCE_FOLDER);
		jbiRoot.createLink(new Path("/" + sourcesFolder), 0, null);

		
		
		//addESBNature(project);
		IVirtualComponent outputFoldersComponent = new OutputFoldersVirtualComponent(project, newComponent);
		VCFUtil.addReference(outputFoldersComponent, newComponent, "/", null);

		
		JBossClassPathCommand command = new JBossClassPathCommand(project,
					model);
		IStatus status = command.executeOverride(monitor);
		if (!status.equals(Status.OK_STATUS)) {
			throw new CoreException(status);
		}
		
		ClasspathHelper.removeClasspathEntries(project, fv);
		ClasspathHelper.addClasspathEntries(project, fv);
	}
	
	
	private IFile createJBossESBXML(IFolder folder) throws CoreException{
		StringBuffer emptyESB = new StringBuffer();
		String configVersion = model.getStringProperty(IJBossESBFacetDataModelProperties.ESB_CONFIG_VERSION);
		emptyESB.append("<?xml version = \"1.0\" encoding = \"UTF-8\"?>");
		emptyESB.append("\n");
		String namespace = "http://anonsvn.labs.jboss.com/labs/jbossesb/trunk/product/etc/schemas/xml/jbossesb-" + configVersion + ".xsd";
		emptyESB.append("<jbossesb xmlns=\""+namespace+"\"");
		emptyESB.append("\n");
		String schemaLocation = "http://anonsvn.jboss.org/repos/labs/labs/jbossesb/trunk/product/etc/schemas/xml/jbossesb-" + configVersion + ".xsd";
		emptyESB.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\""+namespace + " " +schemaLocation+"\"");
		emptyESB.append("\n");
		emptyESB.append(" parameterReloadSecs=\"5\">");
		emptyESB.append("</jbossesb>");
		IFile esbfile = folder.getFile("jboss-esb.xml");
		esbfile.create(new ByteArrayInputStream(emptyESB.toString().getBytes()), true, null);
		
		return esbfile;
	}	

	private void createProjectStructure(IProject project) throws CoreException{
		String strContentFolder = model.getStringProperty(IJBossESBFacetDataModelProperties.ESB_CONTENT_FOLDER);
		project.setPersistentProperty(IJBossESBFacetDataModelProperties.QNAME_ESB_CONTENT_FOLDER, strContentFolder);
		
		String qualifier = ESBProjectCorePlugin.getDefault().getDescriptor().getUniqueIdentifier();
		IScopeContext context = new ProjectScope(project);
		IEclipsePreferences node = context.getNode(qualifier);
		if (node != null)
			node.putDouble(IJBossESBFacetDataModelProperties.ESB_PROJECT_VERSION, 2.0);
		
		try {
			node.flush();
		} catch (BackingStoreException e) {
			e.printStackTrace();
		}

		IFolder esbContent = project.getFolder(strContentFolder);
		if(!esbContent.exists()) {
			esbContent.create(true, true, null);			
		}
		
		esbContent.getFolder("lib").create(true, true, null);
		esbContent.getFolder(ESBProjectConstant.META_INF).create(true, true, null);
		createJBossESBXML(esbContent.getFolder(ESBProjectConstant.META_INF));
		project.refreshLocal(IResource.DEPTH_ZERO, null);
	}
	
	private static void addESBNature(IProject project) throws CoreException{
		IProjectDescription desc = project.getDescription();
		 final String[] current = desc.getNatureIds();
	        final String[] replacement = new String[ current.length + 1 ];
	        System.arraycopy( current, 0, replacement, 1, current.length );
	        replacement[ 0 ] = ESB_NATURE;
	        desc.setNatureIds( replacement );
	        project.setDescription( desc, null );
		
	}

}
