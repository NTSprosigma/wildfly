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

package org.jboss.as.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.registry.ModelNodeRegistration;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * Handler for the "composite" operation; i.e. one that includes one or more child operations
 * as steps.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
final class NewCompositeOperationHandler implements NewStepHandler {

    public void execute(final NewOperationContext context, final ModelNode operation) {
        ModelNodeRegistration registry = context.getModelNodeRegistration();
        final List<ModelNode> list = operation.get(STEPS).asList();
        ModelNode responseMap = context.getResult().setEmptyObject();
        Map<String, NewStepHandler> stepHandlerMap = new HashMap<String, NewStepHandler>();
        final int size = list.size();
        // Validate all needed handlers are available.
        for (int i = 0; i < size; i++) {
            String stepName = "step-" + (i+1);
            // This makes the result steps appear in the correct order
            responseMap.get(stepName);
            final ModelNode subOperation = list.get(i);
            PathAddress stepAddress = PathAddress.pathAddress(subOperation.get(OP_ADDR));
            String stepOpName = subOperation.require(OP).asString();
            NewStepHandler stepHandler = registry.getOperationHandler(stepAddress, stepOpName);
            if (stepHandler == null) {
                context.getFailureDescription().set(String.format("No handler for %s at address %s", stepOpName, stepAddress));
                context.completeStep();
                return;
            }
            stepHandlerMap.put(stepName, stepHandler);
        }

        for (int i = size - 1; i >= 0; i --) {
            final ModelNode subOperation = list.get(i);
            String stepName = "step-" + (i+1);
            context.addStep(responseMap.get(stepName).setEmptyObject(), subOperation, stepHandlerMap.get(stepName), NewOperationContext.Stage.IMMEDIATE);
        }

        if (context.completeStep() == NewOperationContext.ResultAction.ROLLBACK) {
            final ModelNode failureMsg = new ModelNode();
            // TODO i18n
            final String baseMsg = "Composite operation failed and was rolled back. Steps that failed:";
            for (int i = 0; i < size; i++) {
                String stepName = "step-" + (i+1);
                ModelNode stepResponse = responseMap.get(stepName);
                if (stepResponse.hasDefined(FAILURE_DESCRIPTION)) {
                    failureMsg.get(baseMsg, "Operation " + stepName).set(stepResponse.get(FAILURE_DESCRIPTION));
                }
            }
            if (!failureMsg.isDefined()) {
                failureMsg.set("Composite operation was rolled back");
            }
            context.getFailureDescription().set(failureMsg);
        }
    }
}
