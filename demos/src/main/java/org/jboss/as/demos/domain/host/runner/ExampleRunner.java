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

package org.jboss.as.demos.domain.host.runner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPENSATING_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CRITERIA;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.IOException;
import java.net.InetAddress;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;

/**
 * Host specific operations example runner.
 *
 * @author Emanuel Muckenhuber
 */
public class ExampleRunner {
    public static void main(String[] args) throws Exception {
        final ModelControllerClient client = ModelControllerClient.Factory.create(InetAddress.getByName("localhost"), 9999);
        try {
            new ExampleRunner().run(client);
        } finally {
            StreamUtils.safeClose(client);
        }
    }

    protected void run(ModelControllerClient client) throws Exception {
        // system properties
        runSysProperties(client);
        // paths
        runPaths(client);
        // interfaces
        runInetface(client);
    }

    void runSysProperties(final ModelControllerClient client) throws Exception {
        final ModelNode address = new ModelNode();
        address.add(HOST, "local");

        final ModelNode operation = new ModelNode();
        operation.get(OP).set("add-system-property");
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set("test-property");
        operation.get(VALUE).set("test-value");

        runOperationAndRollback(operation, client);
    }

    void runPaths(final ModelControllerClient client) throws Exception {
        final ModelNode address = new ModelNode();
        address.add(HOST, "local");
        address.add(PATH, "temp");

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set("temp");
        operation.get(PATH).set("temp");

        runOperationAndRollback(operation,client);
    }

    void runInetface(final ModelControllerClient client) throws IOException {
        final ModelNode address = new ModelNode();
        address.add(HOST, "local");
        address.add(INTERFACE, "new");

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set("new");
        operation.get(CRITERIA).set("any-address");

        runOperationAndRollback(operation, client);
    }

    void runOperationAndRollback(final ModelNode operation, final ModelControllerClient client) throws IOException {
        final ModelNode result = client.execute(operation);
        try {
            checkSuccess(result);

            final ModelNode readResource = new ModelNode();
            readResource.get(OP).set(READ_RESOURCE_OPERATION);
            readResource.get(OP_ADDR).set(operation.require(OP_ADDR));

            final ModelNode readResult = client.execute(readResource);
            checkSuccess(readResult);

        } finally {
            final ModelNode compensating = result.get(COMPENSATING_OPERATION);
            checkSuccess(client.execute(compensating));
        }
    }

    void checkSuccess(final ModelNode result) {
        if(!SUCCESS.equals(result.get(OUTCOME).asString())) {
            throw new IllegalStateException();
        }
    }

}
