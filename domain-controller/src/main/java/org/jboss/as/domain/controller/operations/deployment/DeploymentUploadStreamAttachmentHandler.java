/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;

import java.io.InputStream;
import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.server.controller.descriptions.DeploymentDescription;
import org.jboss.as.server.deployment.api.DeploymentRepository;
import org.jboss.dmr.ModelNode;

/**
* Handler for the upload-deployment-stream operation.
*
* @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
* @version $Revision: 1.1 $
*/
public class DeploymentUploadStreamAttachmentHandler
extends AbstractDeploymentUploadHandler
implements DescriptionProvider {

    public static final String OPERATION_NAME = "upload-deployment-stream";

    private final ParametersValidator streamValidator = new ParametersValidator();

    public DeploymentUploadStreamAttachmentHandler(final DeploymentRepository repository) {
        super(repository);
        this.streamValidator.registerValidator(INPUT_STREAM_INDEX, new IntRangeValidator(0));
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        return DeploymentDescription.getUploadDeploymentStreamAttachmentOperation(locale);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream getContentInputStream(OperationContext operationContext, ModelNode operation) throws OperationFailedException {
        streamValidator.validate(operation);

        int streamIndex = operation.get(INPUT_STREAM_INDEX).asInt();
        if (streamIndex > operationContext.getInputStreams().size() - 1) {
            throw new IllegalArgumentException("Invalid '" + INPUT_STREAM_INDEX + "' value:" + streamIndex + ", the maximum index is " + streamIndex);
        }

        InputStream in = operationContext.getInputStreams().get(streamIndex);
        if (in == null) {
            throw new IllegalStateException("Null stream at index " + streamIndex);
        }

        return in;
    }

}
