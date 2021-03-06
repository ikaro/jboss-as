/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.ejb3;

import org.jboss.as.controller.BasicOperationResult;
import org.jboss.as.controller.ModelAddOperationHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationResult;
import org.jboss.as.controller.ResultHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.ejb3.deployment.processors.EjbAnnotationProcessor;
import org.jboss.as.ejb3.deployment.processors.EjbDependencyDeploymentUnitProcessor;
import org.jboss.as.ejb3.deployment.processors.EjbJarParsingDeploymentUnitProcessor;
import org.jboss.as.ejb3.deployment.processors.EjbResourceInjectionAnnotationProcessor;
import org.jboss.as.ejb3.deployment.processors.LocalEjbViewAnnotationProcessor;
import org.jboss.as.ejb3.deployment.processors.NoInterfaceViewAnnotationProcessor;
import org.jboss.as.ejb3.deployment.processors.TransactionManagementAnnotationProcessor;
import org.jboss.as.server.BootOperationContext;
import org.jboss.as.server.BootOperationHandler;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

/**
 * @author Emanuel Muckenhuber
 */
class Ejb3SubsystemAdd implements ModelAddOperationHandler, BootOperationHandler {

    static final Ejb3SubsystemAdd INSTANCE = new Ejb3SubsystemAdd();

    private Ejb3SubsystemAdd() {
        //
    }

    /** {@inheritDoc} */
    @Override
    public OperationResult execute(OperationContext context, ModelNode operation, ResultHandler resultHandler) {

        final ModelNode compensatingOperation = Util.getResourceRemoveOperation(operation.require(OP_ADDR));

        if(context instanceof BootOperationContext) {
            final BootOperationContext updateContext = (BootOperationContext) context;

            // add the metadata parser deployment processor
            updateContext.addDeploymentProcessor(Phase.PARSE, Phase.PARSE_EJB_DEPLOYMENT, new EjbJarParsingDeploymentUnitProcessor());
            updateContext.addDeploymentProcessor(Phase.PARSE, Phase.PARSE_EJB_ANNOTATION, new EjbAnnotationProcessor());
            updateContext.addDeploymentProcessor(Phase.DEPENDENCIES, Phase.DEPENDENCIES_EJB, new EjbDependencyDeploymentUnitProcessor());
            updateContext.addDeploymentProcessor(Phase.POST_MODULE, Phase.POST_MODULE_EJB_TRANSACTION_MANAGEMENT, new TransactionManagementAnnotationProcessor());
            updateContext.addDeploymentProcessor(Phase.POST_MODULE, Phase.POST_MODULE_EJB_LOCAL_VIEW_ANNOTATION, new LocalEjbViewAnnotationProcessor());
            updateContext.addDeploymentProcessor(Phase.POST_MODULE, Phase.POST_MODULE_EJB_NO_INTERFACE_VIEW_ANNOTATION, new NoInterfaceViewAnnotationProcessor());
            updateContext.addDeploymentProcessor(Phase.POST_MODULE, Phase.POST_MODULE_EJB_INJECTION_ANNOTATION, new EjbResourceInjectionAnnotationProcessor());

            // add the real deployment processor
            // TODO: add the proper deployment processors
            // updateContext.addDeploymentProcessor(processor, priority);
        }

        context.getSubModel().setEmptyObject();
        resultHandler.handleResultComplete();
        return new BasicOperationResult(compensatingOperation);
    }

}
